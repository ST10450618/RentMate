using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Microsoft.IdentityModel.Tokens;
using RentMate.Api.Models;

namespace RentMate.Api.Services;

/// <summary>
/// Issues the RentMate-specific JWT that the Android app uses on every
/// subsequent request (US-1). This token is separate from, and issued
/// after validating, the Google ID token - RentMate never trusts Google's
/// token directly on later requests, since it also needs to encode the
/// user's internal ID and an explicit RentMate-controlled expiry (US-1).
/// </summary>
public class JwtTokenService : IJwtTokenService
{
    private readonly IConfiguration _configuration;
    private readonly int _accessTokenMinutes;

    public JwtTokenService(IConfiguration configuration)
    {
        _configuration = configuration;
        _accessTokenMinutes = int.TryParse(configuration["Jwt:AccessTokenMinutes"], out var m) ? m : 60;
    }

    public (string token, DateTime expiresAt) CreateAccessToken(User user)
    {
        var jwtKey = _configuration["Jwt:Key"]
            ?? throw new InvalidOperationException("Jwt:Key is not configured. Set it via user-secrets or an environment variable, never commit it to source control.");
        var issuer = _configuration["Jwt:Issuer"] ?? "RentMate.Api";
        var audience = _configuration["Jwt:Audience"] ?? "RentMate.App";

        var expiresAt = DateTime.UtcNow.AddMinutes(_accessTokenMinutes);

        var claims = new List<Claim>
        {
            new(JwtRegisteredClaimNames.Sub, user.Id.ToString()),
            new(JwtRegisteredClaimNames.Email, user.Email),
            new("displayName", user.DisplayName),
            new(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
        };

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: issuer,
            audience: audience,
            claims: claims,
            expires: expiresAt,
            signingCredentials: credentials);

        return (new JwtSecurityTokenHandler().WriteToken(token), expiresAt);
    }

    public string GenerateRefreshTokenString()
    {
        var randomBytes = RandomNumberGenerator.GetBytes(64);
        return Convert.ToBase64String(randomBytes);
    }
}
