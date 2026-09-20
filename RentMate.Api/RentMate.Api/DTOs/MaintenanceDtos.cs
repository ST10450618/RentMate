using RentMate.Api.Models;

namespace RentMate.Api.DTOs;

public record CreateMaintenanceRequest(
    string Title,
    string Description,
    string? PhotoUrl,
    MaintenanceCategory Category,
    MaintenanceUrgency Urgency);

public record UpdateMaintenanceStatusRequest(MaintenanceStatus Status);

public record MaintenanceRequestResponse(
    Guid Id,
    Guid HouseholdId,
    Guid RaisedByUserId,
    string RaisedByDisplayName,
    string Title,
    string Description,
    string? PhotoUrl,
    MaintenanceCategory Category,
    MaintenanceUrgency Urgency,
    MaintenanceStatus Status,
    DateTime CreatedAt,
    DateTime? SentToLandlordAt,
    DateTime? AcknowledgedAt,
    DateTime? ResolvedAt);

public record SendToLandlordResponse(int RequestsIncluded, DateTime SentAt, string LetterPreview);
