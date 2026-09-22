using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

/// <summary>
/// A shared home. Created by one tenant (US-5); other tenants join with
/// the 6-character invite code that is generated when the household is created.
/// </summary>
public class Household
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(128)]
    public string Name { get; set; } = string.Empty;

    /// <summary>6-character alphanumeric code, unique, used to join (US-5).</summary>
    [Required]
    [MaxLength(6)]
    public string InviteCode { get; set; } = string.Empty;

    /// <summary>Landlord contact used for the maintenance letter (US-10). Nullable - can be added later.</summary>
    [MaxLength(128)]
    public string? LandlordName { get; set; }

    [MaxLength(256)]
    public string? LandlordEmail { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<HouseholdMember> Members { get; set; } = new List<HouseholdMember>();
    public ICollection<Bill> Bills { get; set; } = new List<Bill>();
    public ICollection<Chore> Chores { get; set; } = new List<Chore>();
    public ICollection<ShoppingItem> ShoppingItems { get; set; } = new List<ShoppingItem>();
    public ICollection<MaintenanceRequest> MaintenanceRequests { get; set; } = new List<MaintenanceRequest>();
    public ICollection<Settlement> Settlements { get; set; } = new List<Settlement>();
}

/// <summary>
/// Join entity between User and Household (a user can belong to more than
/// one household and switch between them from the Dashboard, per US-5).
/// </summary>
public class HouseholdMember
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid HouseholdId { get; set; }
    public Household Household { get; set; } = null!;

    [Required]
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    public DateTime JoinedAt { get; set; } = DateTime.UtcNow;
}
