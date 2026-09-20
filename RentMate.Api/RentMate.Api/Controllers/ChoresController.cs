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
public class ChoresController : ControllerBase
{
    private readonly RentMateDbContext _db;
    private readonly IChoreRotationService _rotationService;
    private readonly INotificationService _notificationService;

    public ChoresController(RentMateDbContext db, IChoreRotationService rotationService, INotificationService notificationService)
    {
        _db = db;
        _rotationService = rotationService;
        _notificationService = notificationService;
    }

    /// <summary>US-9: creates a chore with its rotation order; the next 4 cycles are computed immediately.</summary>
    [HttpPost("api/households/{householdId:guid}/chores")]
    public async Task<ActionResult<ChoreResponse>> Create(Guid householdId, [FromBody] CreateChoreRequest request)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        if (request.RotationOrder.Count == 0)
        {
            return BadRequest(new { message = "A chore needs at least one member in its rotation order." });
        }

        var memberIds = await _db.HouseholdMembers
            .Where(hm => hm.HouseholdId == householdId)
            .Select(hm => hm.UserId)
            .ToListAsync();

        if (request.RotationOrder.Except(memberIds).Any())
        {
            return BadRequest(new { message = "Rotation order contains a user who is not a member of this household." });
        }

        var chore = new Chore
        {
            HouseholdId = householdId,
            Title = request.Title,
            Recurrence = request.Recurrence,
            PointValue = request.PointValue > 0 ? request.PointValue : 1
        };
        chore.SetRotationOrder(request.RotationOrder);

        _db.Chores.Add(chore);
        await _db.SaveChangesAsync();

        await NotifyAssigneeAsync(chore, "New chore assigned");

        var userLookup = await _db.Users.Where(u => request.RotationOrder.Contains(u.Id))
            .ToDictionaryAsync(u => u.Id, u => u.DisplayName);

        return CreatedAtAction(nameof(GetById), new { id = chore.Id }, HouseholdsController.MapChore(chore, userLookup));
    }

    [HttpGet("api/households/{householdId:guid}/chores")]
    public async Task<ActionResult<List<ChoreResponse>>> List(Guid householdId)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var chores = await _db.Chores.Where(c => c.HouseholdId == householdId).ToListAsync();
        var userIds = chores.SelectMany(c => c.GetRotationOrder()).Distinct().ToList();
        var userLookup = await _db.Users.Where(u => userIds.Contains(u.Id)).ToDictionaryAsync(u => u.Id, u => u.DisplayName);

        return Ok(chores.Select(c => HouseholdsController.MapChore(c, userLookup)).ToList());
    }

    [HttpGet("api/chores/{id:guid}")]
    public async Task<ActionResult<ChoreResponse>> GetById(Guid id)
    {
        var chore = await _db.Chores.FindAsync(id);
        if (chore == null) return NotFound();
        if (!await IsMemberAsync(chore.HouseholdId)) return Forbid();

        var order = chore.GetRotationOrder();
        var userLookup = await _db.Users.Where(u => order.Contains(u.Id)).ToDictionaryAsync(u => u.Id, u => u.DisplayName);

        return Ok(HouseholdsController.MapChore(chore, userLookup));
    }

    /// <summary>US-9/US-7: completes a chore (optionally recorded while offline), awards points and rotates to the next member.</summary>
    [HttpPost("api/chores/{id:guid}/complete")]
    public async Task<ActionResult<ChoreCompletionResponse>> Complete(Guid id, [FromBody] CompleteChoreRequest request)
    {
        var chore = await _db.Chores.FindAsync(id);
        if (chore == null) return NotFound();
        if (!await IsMemberAsync(chore.HouseholdId)) return Forbid();

        var userId = User.GetUserId();

        var completion = new ChoreCompletion
        {
            ChoreId = chore.Id,
            CompletedByUserId = userId,
            OfflineTimestamp = request.OfflineTimestamp,
            PointsAwarded = chore.PointValue
        };
        _db.ChoreCompletions.Add(completion);

        var order = chore.GetRotationOrder();
        chore.CurrentRotationIndex = _rotationService.AdvanceRotation(order, chore.CurrentRotationIndex);

        await _db.SaveChangesAsync();

        await NotifyAssigneeAsync(chore, "It's your turn for a chore");

        var user = await _db.Users.FindAsync(userId);

        return Ok(new ChoreCompletionResponse(
            completion.Id, completion.ChoreId, completion.CompletedByUserId,
            user?.DisplayName ?? "Unknown", completion.CompletedAt, completion.PointsAwarded));
    }

    /// <summary>US-12: points totalled per member for the given calendar month (defaults to the current month).</summary>
    [HttpGet("api/households/{householdId:guid}/leaderboard")]
    public async Task<ActionResult<LeaderboardResponse>> Leaderboard(Guid householdId, [FromQuery] int? year, [FromQuery] int? month)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var targetYear = year ?? DateTime.UtcNow.Year;
        var targetMonth = month ?? DateTime.UtcNow.Month;

        var completions = await _db.ChoreCompletions
            .Include(cc => cc.Chore)
            .Include(cc => cc.CompletedByUser)
            .Where(cc => cc.Chore.HouseholdId == householdId
                && cc.CompletedAt.Year == targetYear
                && cc.CompletedAt.Month == targetMonth)
            .ToListAsync();

        var allCompletions = await _db.ChoreCompletions
            .Include(cc => cc.Chore)
            .Where(cc => cc.Chore.HouseholdId == householdId)
            .ToListAsync();

        var monthTotals = completions
            .GroupBy(c => new { c.CompletedByUserId, c.CompletedByUser.DisplayName })
            .Select(g => new { g.Key.CompletedByUserId, g.Key.DisplayName, Points = g.Sum(c => c.PointsAwarded) })
            .ToList();

        var allTimeTotals = allCompletions
            .GroupBy(c => c.CompletedByUserId)
            .ToDictionary(g => g.Key, g => g.Sum(c => c.PointsAwarded));

        var entries = monthTotals
            .Select(t => new LeaderboardEntry(t.CompletedByUserId, t.DisplayName, t.Points, allTimeTotals.GetValueOrDefault(t.CompletedByUserId)))
            .OrderByDescending(e => e.MonthPoints)
            .ToList();

        // US-12: the leaderboard-visibility toggle is a per-user client-side
        // display choice with no effect on points earned, so this endpoint
        // always returns the real numbers - the app is responsible for
        // hiding the screen element when the current user's ShowLeaderboard
        // preference (from GET /api/auth/me) is false.
        return Ok(new LeaderboardResponse(targetYear, targetMonth, entries));
    }

    private async Task<bool> IsMemberAsync(Guid householdId)
    {
        var userId = User.GetUserId();
        return await _db.HouseholdMembers.AnyAsync(hm => hm.HouseholdId == householdId && hm.UserId == userId);
    }

    private async Task NotifyAssigneeAsync(Chore chore, string title)
    {
        var assigneeId = chore.CurrentAssigneeId();
        if (assigneeId == null) return;

        var assignee = await _db.Users.FindAsync(assigneeId.Value);
        if (assignee == null || !assignee.NotifyChores || string.IsNullOrWhiteSpace(assignee.FcmToken)) return;

        await _notificationService.SendAsync(assignee.FcmToken, NotificationCategory.Chores, title, chore.Title, "rentmate://chores");
    }
}
