using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

/// <summary>
/// One entry on the household's shared shopping list (US-13).
/// </summary>
public class ShoppingItem
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid HouseholdId { get; set; }
    public Household Household { get; set; } = null!;

    [Required]
    [MaxLength(128)]
    public string Name { get; set; } = string.Empty;

    [Required]
    public Guid AddedByUserId { get; set; }
    public User AddedByUser { get; set; } = null!;

    public bool IsPurchased { get; set; } = false;

    public Guid? PurchasedByUserId { get; set; }

    public DateTime? PurchasedAt { get; set; }

    /// <summary>Device-recorded purchase time when ticked off offline (US-7 sync rules apply here per US-13).</summary>
    public DateTime? ClientRecordedAt { get; set; }

    /// <summary>Set once the purchase has been converted into a Bill, with the buyer as payer (US-13).</summary>
    public Guid? ConvertedToBillId { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
