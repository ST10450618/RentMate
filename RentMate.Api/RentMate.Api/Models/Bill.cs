using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

public enum SplitMethod
{
    Equal,
    Percentage,
    ExactAmount
}

/// <summary>
/// A shared household expense (US-6). Amount is always in ZAR per the spec.
/// </summary>
public class Bill
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid HouseholdId { get; set; }
    public Household Household { get; set; } = null!;

    [Required]
    [MaxLength(128)]
    public string Title { get; set; } = string.Empty;

    /// <summary>Total amount in ZAR. Must be greater than 0 (US-6 validation rule).</summary>
    [Range(0.01, double.MaxValue)]
    public decimal Amount { get; set; }

    public DateTime DueDate { get; set; }

    public SplitMethod SplitMethod { get; set; } = SplitMethod.Equal;

    /// <summary>The user who is owed the money for this bill.</summary>
    [Required]
    public Guid IssuedByUserId { get; set; }
    public User IssuedByUser { get; set; } = null!;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    /// <summary>
    /// Last-write-wins conflict detection field for offline sync (US-7).
    /// Bumped every time the bill or any of its shares changes.
    /// </summary>
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<BillShare> Shares { get; set; } = new List<BillShare>();
}

/// <summary>
/// One household member's portion of a Bill. A bill with 4 equal shares
/// has 4 BillShare rows so that each member's paid/unpaid state and the
/// settle-up calculation (US-11) can be tracked independently.
/// </summary>
public class BillShare
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid BillId { get; set; }
    public Bill Bill { get; set; } = null!;

    [Required]
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    /// <summary>This member's portion of the bill amount, in ZAR.</summary>
    [Range(0, double.MaxValue)]
    public decimal AmountOwed { get; set; }

    public bool IsPaid { get; set; } = false;

    public DateTime? PaidAt { get; set; }

    /// <summary>
    /// The timestamp the client recorded when marking this paid while
    /// offline (US-7). Used, alongside UpdatedAt, to detect edit conflicts
    /// when the device eventually reconnects and replays its sync queue.
    /// </summary>
    public DateTime? ClientRecordedAt { get; set; }
}
