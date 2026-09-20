using System.Text;
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
public class MaintenanceController : ControllerBase
{
    private readonly RentMateDbContext _db;
    private readonly INotificationService _notificationService;

    public MaintenanceController(RentMateDbContext db, INotificationService notificationService)
    {
        _db = db;
        _notificationService = notificationService;
    }

    /// <summary>US-10: logs a maintenance fault. Starts life with Status = Open.</summary>
    [HttpPost("api/households/{householdId:guid}/maintenance")]
    public async Task<ActionResult<MaintenanceRequestResponse>> Create(Guid householdId, [FromBody] CreateMaintenanceRequest request)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var maintenanceRequest = new MaintenanceRequest
        {
            HouseholdId = householdId,
            RaisedByUserId = User.GetUserId(),
            Title = request.Title,
            Description = request.Description,
            PhotoUrl = request.PhotoUrl,
            Category = request.Category,
            Urgency = request.Urgency,
            Status = MaintenanceStatus.Open
        };

        _db.MaintenanceRequests.Add(maintenanceRequest);
        await _db.SaveChangesAsync();

        var withUser = await _db.MaintenanceRequests.Include(m => m.RaisedByUser).FirstAsync(m => m.Id == maintenanceRequest.Id);

        return CreatedAtAction(nameof(GetById), new { id = maintenanceRequest.Id }, HouseholdsController.MapMaintenance(withUser));
    }

    [HttpGet("api/households/{householdId:guid}/maintenance")]
    public async Task<ActionResult<List<MaintenanceRequestResponse>>> List(Guid householdId, [FromQuery] MaintenanceStatus? status)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var query = _db.MaintenanceRequests.Include(m => m.RaisedByUser).Where(m => m.HouseholdId == householdId);
        if (status.HasValue) query = query.Where(m => m.Status == status.Value);

        var results = await query.OrderByDescending(m => m.CreatedAt).ToListAsync();

        return Ok(results.Select(HouseholdsController.MapMaintenance).ToList());
    }

    [HttpGet("api/maintenance/{id:guid}")]
    public async Task<ActionResult<MaintenanceRequestResponse>> GetById(Guid id)
    {
        var request = await _db.MaintenanceRequests.Include(m => m.RaisedByUser).FirstOrDefaultAsync(m => m.Id == id);
        if (request == null) return NotFound();
        if (!await IsMemberAsync(request.HouseholdId)) return Forbid();

        return Ok(HouseholdsController.MapMaintenance(request));
    }

    /// <summary>
    /// US-10: moves a request through open -> sent -> acknowledged ->
    /// resolved, time-stamping each transition for the tenant's own record.
    /// </summary>
    [HttpPut("api/maintenance/{id:guid}/status")]
    public async Task<ActionResult<MaintenanceRequestResponse>> UpdateStatus(Guid id, [FromBody] UpdateMaintenanceStatusRequest request)
    {
        var maintenanceRequest = await _db.MaintenanceRequests.Include(m => m.RaisedByUser).FirstOrDefaultAsync(m => m.Id == id);
        if (maintenanceRequest == null) return NotFound();
        if (!await IsMemberAsync(maintenanceRequest.HouseholdId)) return Forbid();

        maintenanceRequest.Status = request.Status;

        switch (request.Status)
        {
            case MaintenanceStatus.Sent:
                maintenanceRequest.SentToLandlordAt = DateTime.UtcNow;
                break;
            case MaintenanceStatus.Acknowledged:
                maintenanceRequest.AcknowledgedAt = DateTime.UtcNow;
                break;
            case MaintenanceStatus.Resolved:
                maintenanceRequest.ResolvedAt = DateTime.UtcNow;
                break;
        }

        await _db.SaveChangesAsync();

        await NotifyHouseholdOfStatusChangeAsync(maintenanceRequest);

        return Ok(HouseholdsController.MapMaintenance(maintenanceRequest));
    }

    /// <summary>
    /// US-10: batches every currently open request into a single dated,
    /// itemised letter addressed to the household's landlord contact, then
    /// marks each of those requests Sent.
    /// </summary>
    [HttpPost("api/households/{householdId:guid}/maintenance/send-to-landlord")]
    public async Task<ActionResult<SendToLandlordResponse>> SendToLandlord(Guid householdId)
    {
        if (!await IsMemberAsync(householdId)) return Forbid();

        var household = await _db.Households.FindAsync(householdId);
        if (household == null) return NotFound();

        var openRequests = await _db.MaintenanceRequests
            .Include(m => m.RaisedByUser)
            .Where(m => m.HouseholdId == householdId && m.Status == MaintenanceStatus.Open)
            .OrderBy(m => m.CreatedAt)
            .ToListAsync();

        if (openRequests.Count == 0)
        {
            return BadRequest(new { message = "There are no open maintenance requests to send." });
        }

        var sentAt = DateTime.UtcNow;
        var letter = BuildLandlordLetter(household, openRequests, sentAt);

        foreach (var r in openRequests)
        {
            r.Status = MaintenanceStatus.Sent;
            r.SentToLandlordAt = sentAt;
        }

        await _db.SaveChangesAsync();

        // Sending the actual email to household.LandlordEmail is a small,
        // separate integration (e.g. SendGrid or SMTP) that depends on
        // which provider your team's Azure for Students credits cover -
        // see the README's "Extending: emailing the landlord letter"
        // section for exactly where to plug that in.

        return Ok(new SendToLandlordResponse(openRequests.Count, sentAt, letter));
    }

    private static string BuildLandlordLetter(Household household, List<MaintenanceRequest> requests, DateTime sentAt)
    {
        var sb = new StringBuilder();
        sb.AppendLine($"Maintenance requests for {household.Name}");
        sb.AppendLine($"Date: {sentAt:yyyy-MM-dd}");
        sb.AppendLine();

        var i = 1;
        foreach (var r in requests)
        {
            sb.AppendLine($"{i}. [{r.Category}/{r.Urgency}] {r.Title}");
            sb.AppendLine($"   Raised by {r.RaisedByUser.DisplayName} on {r.CreatedAt:yyyy-MM-dd}");
            sb.AppendLine($"   {r.Description}");
            if (!string.IsNullOrWhiteSpace(r.PhotoUrl))
            {
                sb.AppendLine($"   Photo: {r.PhotoUrl}");
            }
            sb.AppendLine();
            i++;
        }

        return sb.ToString();
    }

    private async Task<bool> IsMemberAsync(Guid householdId)
    {
        var userId = User.GetUserId();
        return await _db.HouseholdMembers.AnyAsync(hm => hm.HouseholdId == householdId && hm.UserId == userId);
    }

    private async Task NotifyHouseholdOfStatusChangeAsync(MaintenanceRequest request)
    {
        var recipients = await _db.HouseholdMembers
            .Include(hm => hm.User)
            .Where(hm => hm.HouseholdId == request.HouseholdId)
            .Select(hm => hm.User)
            .ToListAsync();

        foreach (var recipient in recipients)
        {
            if (recipient.NotifyMaintenance && !string.IsNullOrWhiteSpace(recipient.FcmToken))
            {
                await _notificationService.SendAsync(
                    recipient.FcmToken,
                    NotificationCategory.Maintenance,
                    "Maintenance update",
                    $"{request.Title} is now {request.Status}",
                    "rentmate://maintenance");
            }
        }
    }
}
