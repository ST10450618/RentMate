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
public class ShoppingListController : ControllerBase
{
    private readonly RentMateDbContext _db;

    public ShoppingListController(RentMateDbContext db)
    {
        _db = db;
    }

    /// <summary>US-13: adds an item to the household's shared shopping list.</summary>
    [HttpPost("api/households/{householdId:guid}/shopping-items")]
    public async Task<ActionResult<ShoppingItemResponse>> Add(Guid householdId, [FromBody] AddShoppingItemRequest request)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var item = new ShoppingItem
        {
            HouseholdId = householdId,
            Name = request.Name,
            AddedByUserId = User.GetUserId()
        };

        _db.ShoppingItems.Add(item);
        await _db.SaveChangesAsync();

        var withUser = await _db.ShoppingItems.Include(i => i.AddedByUser).FirstAsync(i => i.Id == item.Id);

        return CreatedAtAction(nameof(GetById), new { id = item.Id }, HouseholdsController.MapShoppingItem(withUser));
    }

    [HttpGet("api/households/{householdId:guid}/shopping-items")]
    public async Task<ActionResult<List<ShoppingItemResponse>>> List(Guid householdId)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var items = await _db.ShoppingItems
            .Include(i => i.AddedByUser)
            .Where(i => i.HouseholdId == householdId)
            .OrderBy(i => i.IsPurchased)
            .ThenByDescending(i => i.CreatedAt)
            .ToListAsync();

        return Ok(items.Select(HouseholdsController.MapShoppingItem).ToList());
    }

    [HttpGet("api/shopping-items/{id:guid}")]
    public async Task<ActionResult<ShoppingItemResponse>> GetById(Guid id)
    {
        var item = await _db.ShoppingItems.Include(i => i.AddedByUser).FirstOrDefaultAsync(i => i.Id == id);
        if (item == null) return NotFound();
        if (!await IsMemberAsync(item.HouseholdId)) return Forbid();

        return Ok(HouseholdsController.MapShoppingItem(item));
    }

    /// <summary>
    /// US-13/US-7: ticks an item off, following the same offline sync rules
    /// as bills. Optionally converts the purchase into a Bill with the
    /// buyer as the payer, split equally among the household by default.
    /// </summary>
    [HttpPut("api/shopping-items/{id:guid}/purchase")]
    public async Task<ActionResult<ShoppingItemResponse>> Purchase(Guid id, [FromBody] PurchaseShoppingItemRequest request)
    {
        var item = await _db.ShoppingItems.Include(i => i.AddedByUser).FirstOrDefaultAsync(i => i.Id == id);
        if (item == null) return NotFound();
        if (!await IsMemberAsync(item.HouseholdId)) return Forbid();

        var userId = User.GetUserId();

        item.IsPurchased = true;
        item.PurchasedByUserId = userId;
        item.PurchasedAt = DateTime.UtcNow;
        item.ClientRecordedAt = request.ClientTimestamp;

        if (request.ConvertToBill)
        {
            var memberIds = await _db.HouseholdMembers
                .Where(hm => hm.HouseholdId == item.HouseholdId)
                .Select(hm => hm.UserId)
                .ToListAsync();

            // A converted item has no known price on this endpoint, so it is
            // created as a placeholder R0 bill that the buyer edits with the
            // real amount from the Bills screen (S4) - this keeps the
            // shopping list simple while still avoiding a second manual
            // "add bill" step for the common case.
            var bill = new Bill
            {
                HouseholdId = item.HouseholdId,
                Title = $"Shopping: {item.Name}",
                Amount = 0.01m,
                DueDate = DateTime.UtcNow.AddDays(7),
                SplitMethod = SplitMethod.Equal,
                IssuedByUserId = userId
            };

            var equalShare = Math.Round(bill.Amount / memberIds.Count, 2, MidpointRounding.AwayFromZero);
            foreach (var memberId in memberIds)
            {
                bill.Shares.Add(new BillShare { UserId = memberId, AmountOwed = equalShare });
            }

            _db.Bills.Add(bill);
            await _db.SaveChangesAsync();

            item.ConvertedToBillId = bill.Id;
        }

        await _db.SaveChangesAsync();

        return Ok(HouseholdsController.MapShoppingItem(item));
    }

    private async Task<bool> IsMemberAsync(Guid householdId)
    {
        var userId = User.GetUserId();
        return await _db.HouseholdMembers.AnyAsync(hm => hm.HouseholdId == householdId && hm.UserId == userId);
    }
}
