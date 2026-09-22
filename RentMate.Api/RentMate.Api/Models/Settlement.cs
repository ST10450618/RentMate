using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

/// <summary>
/// A confirmed settle-up payment between two household members (US-11).
/// Created only once a user confirms the suggested settlement; the
/// suggestion itself is computed on demand and never persisted.
/// </summary>
public class Settlement
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid HouseholdId { get; set; }
    public Household Household { get; set; } = null!;

    [Required]
    public Guid FromUserId { get; set; }
    public User FromUser { get; set; } = null!;

    [Required]
    public Guid ToUserId { get; set; }
    public User ToUser { get; set; } = null!;

    [Range(0.01, double.MaxValue)]
    public decimal Amount { get; set; }

    public DateTime SettledAt { get; set; } = DateTime.UtcNow;
}
