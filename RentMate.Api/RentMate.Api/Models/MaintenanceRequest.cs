using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

public enum MaintenanceCategory
{
    Plumbing,
    Electrical,
    Structural,
    Appliance,
    Other
}

public enum MaintenanceUrgency
{
    Low,
    Medium,
    High
}

public enum MaintenanceStatus
{
    Open,
    Sent,
    Acknowledged,
    Resolved
}

/// <summary>
/// A logged fault that will be batched with other open requests into a
/// single itemised letter to the landlord (US-10). This is RentMate's
/// key differentiator over Splitwise, Flatastic and Tricount - none of
/// which offer landlord-facing maintenance logging.
/// </summary>
public class MaintenanceRequest
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid HouseholdId { get; set; }
    public Household Household { get; set; } = null!;

    [Required]
    public Guid RaisedByUserId { get; set; }
    public User RaisedByUser { get; set; } = null!;

    [Required]
    [MaxLength(128)]
    public string Title { get; set; } = string.Empty;

    [Required]
    [MaxLength(1024)]
    public string Description { get; set; } = string.Empty;

    /// <summary>
    /// URL of the fault photo. The app uploads the photo itself (e.g. to
    /// Firebase Storage or Azure Blob Storage) and passes the resulting
    /// URL here - this API does not handle binary file uploads directly,
    /// matching the "photoUrl: String (nullable)" field in the Part 1
    /// data model.
    /// </summary>
    [MaxLength(1024)]
    public string? PhotoUrl { get; set; }

    public MaintenanceCategory Category { get; set; }

    public MaintenanceUrgency Urgency { get; set; }

    public MaintenanceStatus Status { get; set; } = MaintenanceStatus.Open;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime? SentToLandlordAt { get; set; }

    public DateTime? AcknowledgedAt { get; set; }

    public DateTime? ResolvedAt { get; set; }
}
