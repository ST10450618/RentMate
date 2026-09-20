using RentMate.Api.Models;

namespace RentMate.Api.Services;

public interface IJwtTokenService
{
    /// <summary>Creates a short-lived signed JWT access token for the given user.</summary>
    (string token, DateTime expiresAt) CreateAccessToken(User user);

    /// <summary>Creates a new opaque, cryptographically random refresh token string.</summary>
    string GenerateRefreshTokenString();
}
