using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using RentMate.Api.Data;
using RentMate.Api.DTOs;
using RentMate.Api.Models;
using RentMate.Api.Services;

namespace RentMate.Api.Controllers;

[ApiController]
[Authorize]
public class BillsController : ControllerBase
{
    private readonly RentMateDbContext _db;
    private readonly IDebtSimplificationService _debtSimplificationService;
    private readonly INotificationService _notificationService;

    public BillsController(
        RentMateDbContext db,
        IDebtSimplificationService debtSimplificationService,
        INotificationService notificationService)
    {
        _db = db;
        _debtSimplificationService = debtSimplificationService;
        _notificationService = notificationService;
    }

    /// <summary>
    /// US-6: creates a bill and its per-member shares. Rejected with 400 if
    /// the amount is not greater than 0, or if a Percentage split's shares
    /// do not sum to 100.
    /// </summary>
    [HttpPost("api/households/{householdId:guid}/bills")]
    public async Task<ActionResult<BillResponse>> Create(Guid householdId, [FromBody] CreateBillRequest request)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        if (request.Amount <= 0)
        {
            return BadRequest(new { message = "Amount must be greater than 0." });
        }

        var memberIds = await _db.HouseholdMembers
            .Where(hm => hm.HouseholdId == householdId)
            .Select(hm => hm.UserId)
            .ToListAsync();

        var shareUserIds = request.Shares.Select(s => s.UserId).ToList();
        if (shareUserIds.Except(memberIds).Any())
        {
            return BadRequest(new { message = "One or more share recipients are not members of this household." });
        }

        var shares = new List<BillShare>();

        switch (request.SplitMethod)
        {
            case SplitMethod.Equal:
                var equalAmount = Math.Round(request.Amount / request.Shares.Count, 2, MidpointRounding.AwayFromZero);
                var runningTotal = 0m;
                for (var i = 0; i < request.Shares.Count; i++)
                {
                    // Give the last share whatever is left, so rounding never
                    // causes the shares to under- or over-shoot the total.
                    var amount = i == request.Shares.Count - 1
                        ? request.Amount - runningTotal
                        : equalAmount;
                    runningTotal += amount;
                    shares.Add(new BillShare { UserId = request.Shares[i].UserId, AmountOwed = amount });
                }
                break;

            case SplitMethod.Percentage:
                var totalPercentage = request.Shares.Sum(s => s.AmountOrPercentage ?? 0);
                if (Math.Abs(totalPercentage - 100m) > 0.01m)
                {
                    return BadRequest(new { message = $"Percentage splits must add up to 100% (got {totalPercentage}%)." });
                }
                foreach (var s in request.Shares)
                {
                    var amount = Math.Round(request.Amount * (s.AmountOrPercentage ?? 0) / 100m, 2, MidpointRounding.AwayFromZero);
                    shares.Add(new BillShare { UserId = s.UserId, AmountOwed = amount });
                }
                break;

            case SplitMethod.ExactAmount:
                var totalExact = request.Shares.Sum(s => s.AmountOrPercentage ?? 0);
                if (Math.Abs(totalExact - request.Amount) > 0.01m)
                {
                    return BadRequest(new { message = $"Exact amounts must add up to the bill total (got {totalExact}, expected {request.Amount})." });
                }
                foreach (var s in request.Shares)
                {
                    shares.Add(new BillShare { UserId = s.UserId, AmountOwed = s.AmountOrPercentage ?? 0 });
                }
                break;
        }

        var bill = new Bill
        {
            HouseholdId = householdId,
            Title = request.Title,
            Amount = request.Amount,
            DueDate = request.DueDate,
            SplitMethod = request.SplitMethod,
            IssuedByUserId = User.GetUserId(),
            Shares = shares
        };

        _db.Bills.Add(bill);
        await _db.SaveChangesAsync();

        await NotifyHouseholdAsync(householdId, bill.IssuedByUserId, NotificationCategory.Bills,
            "New bill added", $"{request.Title} - R{request.Amount:0.00}", "rentmate://bills");

        var created = await _db.Bills
            .Include(b => b.Shares).ThenInclude(s => s.User)
            .Include(b => b.IssuedByUser)
            .FirstAsync(b => b.Id == bill.Id);

        return CreatedAtAction(nameof(GetById), new { id = bill.Id }, HouseholdsController.MapBill(created));
    }

    [HttpGet("api/households/{householdId:guid}/bills")]
    public async Task<ActionResult<List<BillResponse>>> List(Guid householdId)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var bills = await _db.Bills
            .Include(b => b.Shares).ThenInclude(s => s.User)
            .Include(b => b.IssuedByUser)
            .Where(b => b.HouseholdId == householdId)
            .OrderByDescending(b => b.DueDate)
            .ToListAsync();

        return Ok(bills.Select(HouseholdsController.MapBill).ToList());
    }

    [HttpGet("api/bills/{id:guid}")]
    public async Task<ActionResult<BillResponse>> GetById(Guid id)
    {
        var bill = await _db.Bills
            .Include(b => b.Shares).ThenInclude(s => s.User)
            .Include(b => b.IssuedByUser)
            .FirstOrDefaultAsync(b => b.Id == id);

        if (bill == null) return NotFound();
        if (!await IsMemberAsync(bill.HouseholdId)) return Forbid();

        return Ok(HouseholdsController.MapBill(bill));
    }

    /// <summary>
    /// US-7: marks one member's share as paid, including while offline (the
    /// client sends the request once its WorkManager sync worker replays
    /// the queue). Returns 409 if the share or its parent bill was already
    /// updated on the server more recently than the client's own record of
    /// it - the "non-blocking conflict notice" from US-7 is what the app
    /// shows when it receives that 409.
    /// </summary>
    [HttpPut("api/bills/{billId:guid}/shares/{shareId:guid}/pay")]
    public async Task<ActionResult<BillResponse>> PayShare(Guid billId, Guid shareId, [FromBody] PayBillShareRequest request)
    {
        var bill = await _db.Bills
            .Include(b => b.Shares).ThenInclude(s => s.User)
            .Include(b => b.IssuedByUser)
            .FirstOrDefaultAsync(b => b.Id == billId);

        if (bill == null) return NotFound();
        if (!await IsMemberAsync(bill.HouseholdId)) return Forbid();

        var share = bill.Shares.FirstOrDefault(s => s.Id == shareId);
        if (share == null) return NotFound();

        // Conflict rule from US-7: "the server recorded the time first" wins.
        if (bill.UpdatedAt > request.ClientTimestamp)
        {
            return Conflict(new
            {
                message = "This bill was updated on the server after your offline change was recorded.",
                serverBill = HouseholdsController.MapBill(bill)
            });
        }

        share.IsPaid = true;
        share.PaidAt = DateTime.UtcNow;
        share.ClientRecordedAt = request.ClientTimestamp;
        bill.UpdatedAt = DateTime.UtcNow;

        await _db.SaveChangesAsync();

        return Ok(HouseholdsController.MapBill(bill));
    }

    /// <summary>US-11: the smallest set of payments that would settle the household right now.</summary>
    [HttpGet("api/households/{householdId:guid}/settle-up")]
    public async Task<ActionResult<SettleUpResponse>> SettleUp(Guid householdId)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var unpaidShares = await _db.BillShares
            .Include(s => s.Bill)
            .Include(s => s.User)
            .Where(s => s.Bill.HouseholdId == householdId && !s.IsPaid)
            .ToListAsync();

        var bills = await _db.Bills
            .Where(b => b.HouseholdId == householdId)
            .ToDictionaryAsync(b => b.Id);

        // Net balance per user: positive means the household owes them
        // money (they issued bills that others haven't paid yet); negative
        // means they still owe money on shares that are not theirs.
        var balances = new Dictionary<Guid, decimal>();

        foreach (var share in unpaidShares)
        {
            var bill = bills[share.BillId];
            if (share.UserId == bill.IssuedByUserId) continue; // the issuer doesn't owe themselves

            balances[share.UserId] = balances.GetValueOrDefault(share.UserId) - share.AmountOwed;
            balances[bill.IssuedByUserId] = balances.GetValueOrDefault(bill.IssuedByUserId) + share.AmountOwed;
        }

        var userIds = balances.Keys.ToList();
        var users = await _db.Users.Where(u => userIds.Contains(u.Id)).ToDictionaryAsync(u => u.Id, u => u.DisplayName);

        var transfers = _debtSimplificationService.Simplify(
            balances.Select(kv => new Balance(kv.Key, kv.Value)));

        var suggestions = transfers.Select(t => new SettlementSuggestion(
            t.FromUserId, users.GetValueOrDefault(t.FromUserId, "Unknown"),
            t.ToUserId, users.GetValueOrDefault(t.ToUserId, "Unknown"),
            t.Amount)).ToList();

        // US-11: "available offline" (computed from the locally cached
        // ledger) and "marked as provisional until synced" - from the
        // server's point of view every settle-up result is provisional
        // until the tenant explicitly confirms it via ConfirmSettlement.
        return Ok(new SettleUpResponse(IsProvisional: true, suggestions));
    }

    /// <summary>US-11: confirms a suggested settlement and marks the relevant bill shares paid.</summary>
    [HttpPost("api/households/{householdId:guid}/settlements")]
    public async Task<ActionResult<Settlement>> ConfirmSettlement(Guid householdId, [FromBody] ConfirmSettlementRequest request)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var fromUserId = User.GetUserId();

        var settlement = new Settlement
        {
            HouseholdId = householdId,
            FromUserId = fromUserId,
            ToUserId = request.ToUserId,
            Amount = request.Amount
        };
        _db.Settlements.Add(settlement);

        // Mark this user's unpaid shares on bills issued by the payee as
        // paid, up to the settled amount, oldest first.
        var sharesToSettle = await _db.BillShares
            .Include(s => s.Bill)
            .Where(s => s.UserId == fromUserId && !s.IsPaid && s.Bill.HouseholdId == householdId && s.Bill.IssuedByUserId == request.ToUserId)
            .OrderBy(s => s.Bill.DueDate)
            .ToListAsync();

        var remaining = request.Amount;
        foreach (var share in sharesToSettle)
        {
            if (remaining <= 0) break;
            if (share.AmountOwed <= remaining)
            {
                share.IsPaid = true;
                share.PaidAt = DateTime.UtcNow;
                share.Bill.UpdatedAt = DateTime.UtcNow;
                remaining -= share.AmountOwed;
            }
        }

        await _db.SaveChangesAsync();

        return Ok(settlement);
    }

    private async Task<bool> IsMemberAsync(Guid householdId)
    {
        var userId = User.GetUserId();
        return await _db.HouseholdMembers.AnyAsync(hm => hm.HouseholdId == householdId && hm.UserId == userId);
    }

    private async Task NotifyHouseholdAsync(Guid householdId, Guid excludeUserId, NotificationCategory category, string title, string body, string deepLink)
    {
        var recipients = await _db.HouseholdMembers
            .Where(hm => hm.HouseholdId == householdId && hm.UserId != excludeUserId)
            .Select(hm => hm.User)
            .ToListAsync();

        foreach (var recipient in recipients)
        {
            var wantsNotification = category switch
            {
                NotificationCategory.Bills => recipient.NotifyBills,
                NotificationCategory.Chores => recipient.NotifyChores,
                NotificationCategory.ShoppingList => recipient.NotifyShoppingList,
                NotificationCategory.Maintenance => recipient.NotifyMaintenance,
                _ => true
            };

            if (wantsNotification && !string.IsNullOrWhiteSpace(recipient.FcmToken))
            {
                await _notificationService.SendAsync(recipient.FcmToken, category, title, body, deepLink);
            }
        }
    }
}
