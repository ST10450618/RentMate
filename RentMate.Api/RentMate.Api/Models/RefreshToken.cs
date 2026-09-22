using System.ComponentModel.DataAnnotations;

namespace RentMate.Api.Models;

/// <summary>
/// A long-lived opaque refresh token issued alongside the short-lived JWT
/// access token. The Android app stores this behind biometric unlock
/// (US-2) - the server-side Google sign-in is never replaced by
/// biometrics, only re-entry to an already-authenticated session is.
/// </summary>
public class RefreshToken
{
    [Key]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    [Required]
    [MaxLength(256)]
    public string Token { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public bool Revoked { get; set; } = false;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public bool IsActive => !Revoked && ExpiresAt > DateTime.UtcNow;
}
