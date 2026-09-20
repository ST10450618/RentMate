namespace RentMate.Api.DTOs;

/// <summary>Sent by the Android app after Google Sign-In / Credential Manager returns an ID token.</summary>
public record GoogleSignInRequest(string IdToken, string? FcmToken);

public record RefreshTokenRequest(string RefreshToken);

public record AuthResponse(
    string AccessToken,
    string RefreshToken,
    DateTime ExpiresAt,
    UserResponse User);

public record UserResponse(
    Guid Id,
    string Email,
    string DisplayName,
    bool ShowLeaderboard,
    string PreferredLanguage,
    bool BiometricEnabled,
    bool NotifyBills,
    bool NotifyChores,
    bool NotifyShoppingList,
    bool NotifyMaintenance);

/// <summary>US-3: updating notification/language/leaderboard preferences from Settings.</summary>
public record UpdateSettingsRequest(
    string? PreferredLanguage,
    bool? ShowLeaderboard,
    bool? NotifyBills,
    bool? NotifyChores,
    bool? NotifyShoppingList,
    bool? NotifyMaintenance,
    bool? BiometricEnabled,
    string? FcmToken);
