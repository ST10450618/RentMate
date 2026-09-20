using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using RentMate.Api.Data;
using RentMate.Api.DTOs;
using RentMate.Api.Models;
using RentMate.Api.Services;

namespace RentMate.Api.Controllers;

[ApiController]
[Route("api/households")]
[Authorize]
public class HouseholdsController : ControllerBase
{
    private readonly RentMateDbContext _db;
    private readonly IInviteCodeGenerator _inviteCodeGenerator;

    public HouseholdsController(RentMateDbContext db, IInviteCodeGenerator inviteCodeGenerator)
    {
        _db = db;
        _inviteCodeGenerator = inviteCodeGenerator;
    }

    /// <summary>US-5: creates a new household and returns its 6-character invite code.</summary>
    [HttpPost]
    public async Task<ActionResult<HouseholdResponse>> Create([FromBody] CreateHouseholdRequest request)
    {
        var userId = User.GetUserId();

        string inviteCode;
        do
        {
            inviteCode = _inviteCodeGenerator.Generate();
        }
        while (await _db.Households.AnyAsync(h => h.InviteCode == inviteCode));

        var household = new Household
        {
            Name = request.Name,
            InviteCode = inviteCode
        };
        household.Members.Add(new HouseholdMember { UserId = userId, Household = household });

        _db.Households.Add(household);
        await _db.SaveChangesAsync();

        return CreatedAtAction(nameof(GetById), new { id = household.Id }, await ToHouseholdResponseAsync(household.Id));
    }

    /// <summary>US-5: joins an existing household using its invite code.</summary>
    [HttpPost("join")]
    public async Task<ActionResult<HouseholdResponse>> Join([FromBody] JoinHouseholdRequest request)
    {
        var userId = User.GetUserId();
        var code = request.InviteCode.Trim().ToUpperInvariant();

        var household = await _db.Households.FirstOrDefaultAsync(h => h.InviteCode == code);

        // US-5: "membership record is not returned in case of an
        // invalid/invalidated code" - a generic 404 gives no hint about
        // which part of the code was wrong.
        if (household == null) return NotFound(new { message = "Invalid invite code." });

        var alreadyMember = await _db.HouseholdMembers
            .AnyAsync(hm => hm.HouseholdId == household.Id && hm.UserId == userId);

        if (!alreadyMember)
        {
            _db.HouseholdMembers.Add(new HouseholdMember { HouseholdId = household.Id, UserId = userId });
            await _db.SaveChangesAsync();
        }

        return Ok(await ToHouseholdResponseAsync(household.Id));
    }

    /// <summary>US-5: lists every household the current user belongs to, for the Dashboard's household switcher.</summary>
    [HttpGet("mine")]
    public async Task<ActionResult<List<HouseholdResponse>>> Mine()
    {
        var userId = User.GetUserId();

        var householdIds = await _db.HouseholdMembers
            .Where(hm => hm.UserId == userId)
            .Select(hm => hm.HouseholdId)
            .ToListAsync();

        var results = new List<HouseholdResponse>();
        foreach (var id in householdIds)
        {
            results.Add(await ToHouseholdResponseAsync(id));
        }

        return Ok(results);
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<HouseholdResponse>> GetById(Guid id)
    {
        if (!await IsMemberAsync(id)) return Forbid();

        var household = await _db.Households.FindAsync(id);
        if (household == null) return NotFound();

        return Ok(await ToHouseholdResponseAsync(id));
    }

    /// <summary>Sets or updates the landlord contact used for the maintenance letter (US-10).</summary>
    [HttpPut("{id:guid}/landlord")]
    public async Task<ActionResult<HouseholdResponse>> UpdateLandlord(Guid id, [FromBody] UpdateLandlordRequest request)
    {
        if (!await IsMemberAsync(id)) return Forbid();

        var household = await _db.Households.FindAsync(id);
        if (household == null) return NotFound();

        household.LandlordName = request.LandlordName;
        household.LandlordEmail = request.LandlordEmail;
        await _db.SaveChangesAsync();

        return Ok(await ToHouseholdResponseAsync(id));
    }

    /// <summary>
    /// US-7: the "pull" half of offline sync. The Android client calls this
    /// after reconnecting (once its WorkManager job has replayed its local
    /// queue) to fetch anything that changed in the household - including
    /// changes made by other members' devices - since its last successful
    /// sync. Everything returned here is the server's authoritative state,
    /// which is how a "losing device" conflict (US-7) is resolved: the
    /// client overwrites its local copy of any record with a later
    /// UpdatedAt than the client had.
    /// </summary>
    [HttpGet("{id:guid}/sync")]
    public async Task<ActionResult<SyncResponse>> Sync(Guid id, [FromQuery] DateTime? since)
    {
        if (!await IsMemberAsync(id)) return Forbid();

        var cutoff = since ?? DateTime.MinValue;

        var bills = await _db.Bills
            .Include(b => b.Shares).ThenInclude(s => s.User)
            .Include(b => b.IssuedByUser)
            .Where(b => b.HouseholdId == id && b.UpdatedAt > cutoff)
            .ToListAsync();

        var chores = await _db.Chores
            .Where(c => c.HouseholdId == id && c.CreatedAt > cutoff)
            .ToListAsync();

        var shoppingItems = await _db.ShoppingItems
            .Include(si => si.AddedByUser)
            .Where(si => si.HouseholdId == id &&
                (si.CreatedAt > cutoff || (si.PurchasedAt != null && si.PurchasedAt > cutoff)))
            .ToListAsync();

        var maintenanceRequests = await _db.MaintenanceRequests
            .Include(m => m.RaisedByUser)
            .Where(m => m.HouseholdId == id && m.CreatedAt > cutoff)
            .ToListAsync();

        var userIds = chores.SelectMany(c => c.GetRotationOrder()).Distinct().ToList();
        var userLookup = await _db.Users.Where(u => userIds.Contains(u.Id)).ToDictionaryAsync(u => u.Id, u => u.DisplayName);

        return Ok(new SyncResponse(
            ServerTime: DateTime.UtcNow,
            UpdatedBills: bills.Select(MapBill).ToList(),
            UpdatedChores: chores.Select(c => MapChore(c, userLookup)).ToList(),
            UpdatedShoppingItems: shoppingItems.Select(MapShoppingItem).ToList(),
            UpdatedMaintenanceRequests: maintenanceRequests.Select(MapMaintenance).ToList()));
    }

    private async Task<bool> IsMemberAsync(Guid householdId)
    {
        var userId = User.GetUserId();
        return await _db.HouseholdMembers.AnyAsync(hm => hm.HouseholdId == householdId && hm.UserId == userId);
    }

    private async Task<HouseholdResponse> ToHouseholdResponseAsync(Guid householdId)
    {
        var household = await _db.Households
            .Include(h => h.Members).ThenInclude(m => m.User)
            .FirstAsync(h => h.Id == householdId);

        return new HouseholdResponse(
            household.Id,
            household.Name,
            household.InviteCode,
            household.LandlordName,
            household.LandlordEmail,
            household.Members.Select(m => new HouseholdMemberResponse(m.UserId, m.User.DisplayName, m.User.Email)).ToList());
    }

    public static BillResponse MapBill(Bill b) => new(
        b.Id, b.HouseholdId, b.Title, b.Amount, b.DueDate, b.SplitMethod,
        b.IssuedByUserId, b.IssuedByUser.DisplayName, b.CreatedAt, b.UpdatedAt,
        b.Shares.Select(s => new BillShareResponse(s.Id, s.UserId, s.User.DisplayName, s.AmountOwed, s.IsPaid, s.PaidAt)).ToList());

    public static ChoreResponse MapChore(Chore c, Dictionary<Guid, string> userLookup)
    {
        var order = c.GetRotationOrder();
        var currentAssignee = c.CurrentAssigneeId();
        var forecast = order.Count == 0
            ? new List<ChoreForecastEntry>()
            : Enumerable.Range(0, 4)
                .Select(i => order[(c.CurrentRotationIndex + i) % order.Count])
                .Select((uid, i) => new ChoreForecastEntry(i + 1, uid, userLookup.TryGetValue(uid, out var n) ? n : "Unknown"))
                .ToList();

        return new ChoreResponse(
            c.Id, c.HouseholdId, c.Title, c.Recurrence, c.PointValue,
            currentAssignee,
            currentAssignee.HasValue && userLookup.TryGetValue(currentAssignee.Value, out var name) ? name : null,
            forecast, c.CreatedAt);
    }

    public static ShoppingItemResponse MapShoppingItem(ShoppingItem s) => new(
        s.Id, s.HouseholdId, s.Name, s.AddedByUserId, s.AddedByUser.DisplayName,
        s.IsPurchased, s.PurchasedByUserId, s.PurchasedAt, s.ConvertedToBillId, s.CreatedAt);

    public static MaintenanceRequestResponse MapMaintenance(MaintenanceRequest m) => new(
        m.Id, m.HouseholdId, m.RaisedByUserId, m.RaisedByUser.DisplayName, m.Title, m.Description,
        m.PhotoUrl, m.Category, m.Urgency, m.Status, m.CreatedAt, m.SentToLandlordAt, m.AcknowledgedAt, m.ResolvedAt);
}
