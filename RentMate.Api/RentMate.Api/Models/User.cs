using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

/// <summary>
/// A registered RentMate user. Created automatically the first time
/// someone signs in with Google (US-1). There is no password anywhere
/// on this entity by design - authentication is delegated to Google.
/// </summary>
public class User
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [MaxLength(256)]
    public string Email { get; set; } = string.Empty;

    [Required]
    [MaxLength(128)]
    public string DisplayName { get; set; } = string.Empty;

    /// <summary>
    /// The Google account's stable subject identifier ("sub" claim).
    /// Used to look up returning users instead of trusting email alone.
    /// </summary>
    [Required]
    [MaxLength(128)]
    public string GoogleSubjectId { get; set; } = string.Empty;

    /// <summary>US-3: whether this user wants the chore points leaderboard visible.</summary>
    public bool ShowLeaderboard { get; set; } = true;

    /// <summary>US-3: preferred UI language - "en", "af", or "xh" (US-4).</summary>
    [MaxLength(2)]
    public string PreferredLanguage { get; set; } = "en";

    /// <summary>US-3: per-category notification toggles, stored as comma-separated categories the user has muted.</summary>
    public bool NotifyBills { get; set; } = true;
    public bool NotifyChores { get; set; } = true;
    public bool NotifyShoppingList { get; set; } = true;
    public bool NotifyMaintenance { get; set; } = true;

    /// <summary>US-2: whether biometric unlock has been enabled on at least one device.</summary>
    public bool BiometricEnabled { get; set; } = false;

    /// <summary>US-8: Firebase Cloud Messaging device token, used to send push notifications.</summary>
    [MaxLength(512)]
    public string? FcmToken { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public ICollection<HouseholdMember> HouseholdMemberships { get; set; } = new List<HouseholdMember>();
    public ICollection<RefreshToken> RefreshTokens { get; set; } = new List<RefreshToken>();
}
