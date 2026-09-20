using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using RentMate.Api.Data;
using RentMate.Api.DTOs;
using RentMate.Api.Models;
using RentMate.Api.Services;

namespace RentMate.Api.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController : ControllerBase
{
    private readonly RentMateDbContext _db;
    private readonly IGoogleAuthValidator _googleAuthValidator;
    private readonly IJwtTokenService _jwtTokenService;
    private readonly IConfiguration _configuration;

    public AuthController(
        RentMateDbContext db,
        IGoogleAuthValidator googleAuthValidator,
        IJwtTokenService jwtTokenService,
        IConfiguration configuration)
    {
        _db = db;
        _googleAuthValidator = googleAuthValidator;
        _jwtTokenService = jwtTokenService;
        _configuration = configuration;
    }

    /// <summary>
    /// US-1: registers a new tenant or signs in a returning one using a
    /// Google ID token obtained on-device. Creates the User record on
    /// first sign-in and always returns a fresh RentMate access + refresh
    /// token pair.
    /// </summary>
    [HttpPost("google-signin")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponse>> GoogleSignIn([FromBody] GoogleSignInRequest request)
    {
        GoogleIdentity identity;
        try
        {
            identity = await _googleAuthValidator.ValidateAsync(request.IdToken);
        }
        catch (InvalidGoogleTokenException)
        {
            // US-1: "If the sign-in fails or is cancelled, the user is taken
            // back to the sign-in screen" - a 401 here is what tells the app
            // to do that.
            return Unauthorized(new { message = "Google sign-in failed. Please try again." });
        }

        var user = await _db.Users.FirstOrDefaultAsync(u => u.GoogleSubjectId == identity.Subject);

        if (user == null)
        {
            user = new User
            {
                Email = identity.Email,
                DisplayName = identity.DisplayName,
                GoogleSubjectId = identity.Subject,
                FcmToken = request.FcmToken
            };
            _db.Users.Add(user);
        }
        else if (!string.IsNullOrWhiteSpace(request.FcmToken))
        {
            user.FcmToken = request.FcmToken;
        }

        var (accessToken, expiresAt) = _jwtTokenService.CreateAccessToken(user);
        var refreshTokenString = _jwtTokenService.GenerateRefreshTokenString();
        var refreshDays = int.TryParse(_configuration["Jwt:RefreshTokenDays"], out var d) ? d : 30;

        _db.RefreshTokens.Add(new RefreshToken
        {
            UserId = user.Id,
            Token = refreshTokenString,
            ExpiresAt = DateTime.UtcNow.AddDays(refreshDays)
        });

        await _db.SaveChangesAsync();

        return Ok(new AuthResponse(accessToken, refreshTokenString, expiresAt, ToUserResponse(user)));
    }

    /// <summary>
    /// Exchanges a still-valid refresh token for a new access token. This is
    /// what the app calls after a successful biometric unlock (US-2) - the
    /// biometric prompt itself never touches the server; it only gates
    /// access to this refresh token, which is stored securely on-device.
    /// </summary>
    [HttpPost("refresh")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponse>> Refresh([FromBody] RefreshTokenRequest request)
    {
        var storedToken = await _db.RefreshTokens
            .Include(rt => rt.User)
            .FirstOrDefaultAsync(rt => rt.Token == request.RefreshToken);

        if (storedToken == null || !storedToken.IsActive)
        {
            // US-2: "Three consecutive failures reverts to full SSO sign-in" -
            // the app is expected to count 401s from this endpoint and fall
            // back to the Google sign-in screen after the third.
            return Unauthorized(new { message = "Refresh token is invalid or expired. Please sign in again." });
        }

        var (accessToken, expiresAt) = _jwtTokenService.CreateAccessToken(storedToken.User);

        return Ok(new AuthResponse(accessToken, storedToken.Token, expiresAt, ToUserResponse(storedToken.User)));
    }

    /// <summary>Revokes a refresh token (sign-out from Settings, S10).</summary>
    [HttpPost("logout")]
    [Authorize]
    public async Task<IActionResult> Logout([FromBody] RefreshTokenRequest request)
    {
        var storedToken = await _db.RefreshTokens.FirstOrDefaultAsync(rt => rt.Token == request.RefreshToken);
        if (storedToken != null)
        {
            storedToken.Revoked = true;
            await _db.SaveChangesAsync();
        }

        return NoContent();
    }

    /// <summary>Returns the signed-in user's profile and preferences (for Settings, S10).</summary>
    [HttpGet("me")]
    [Authorize]
    public async Task<ActionResult<UserResponse>> Me()
    {
        var user = await _db.Users.FindAsync(User.GetUserId());
        if (user == null) return NotFound();

        return Ok(ToUserResponse(user));
    }

    /// <summary>US-3: updates notification, language, leaderboard and biometric preferences.</summary>
    [HttpPut("me/settings")]
    [Authorize]
    public async Task<ActionResult<UserResponse>> UpdateSettings([FromBody] UpdateSettingsRequest request)
    {
        var user = await _db.Users.FindAsync(User.GetUserId());
        if (user == null) return NotFound();

        if (request.PreferredLanguage != null) user.PreferredLanguage = request.PreferredLanguage;
        if (request.ShowLeaderboard.HasValue) user.ShowLeaderboard = request.ShowLeaderboard.Value;
        if (request.NotifyBills.HasValue) user.NotifyBills = request.NotifyBills.Value;
        if (request.NotifyChores.HasValue) user.NotifyChores = request.NotifyChores.Value;
        if (request.NotifyShoppingList.HasValue) user.NotifyShoppingList = request.NotifyShoppingList.Value;
        if (request.NotifyMaintenance.HasValue) user.NotifyMaintenance = request.NotifyMaintenance.Value;
        if (request.BiometricEnabled.HasValue) user.BiometricEnabled = request.BiometricEnabled.Value;
        if (request.FcmToken != null) user.FcmToken = request.FcmToken;

        await _db.SaveChangesAsync();

        return Ok(ToUserResponse(user));
    }

    private static UserResponse ToUserResponse(User user) => new(
        user.Id,
        user.Email,
        user.DisplayName,
        user.ShowLeaderboard,
        user.PreferredLanguage,
        user.BiometricEnabled,
        user.NotifyBills,
        user.NotifyChores,
        user.NotifyShoppingList,
        user.NotifyMaintenance);
}
