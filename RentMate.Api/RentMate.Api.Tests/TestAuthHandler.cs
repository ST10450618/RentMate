using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using RentMate.Api.Data;
using RentMate.Api.Models;

namespace RentMate.Api.Tests;

/// <summary>
/// A fake authentication scheme used only in tests. Every request is
/// treated as already signed in as whatever user ID is in the
/// "Test-User-Id" header, so controller/integration tests can exercise
/// [Authorize] endpoints without needing a real Google ID token or a
/// real signed JWT.
///
/// The handler also creates a matching User record in the test database
/// when that user does not already exist. This keeps the fake authenticated
/// identity consistent with the database relationships used by the API.
/// </summary>
public class TestAuthHandler : AuthenticationHandler<AuthenticationSchemeOptions>
{
    public const string SchemeName = "Test";

    public TestAuthHandler(
        IOptionsMonitor<AuthenticationSchemeOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder)
        : base(options, logger, encoder)
    {
    }

    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        // -----------------------------------------------------------------
        // Get the fake user's ID from the test request header.
        // -----------------------------------------------------------------

        var userId = Request.Headers.TryGetValue("Test-User-Id", out var value)
            ? value.ToString()
            : Guid.Empty.ToString();

        // -----------------------------------------------------------------
        // Make sure the test supplied a valid user ID.
        // -----------------------------------------------------------------

        if (!Guid.TryParse(userId, out var parsedUserId) ||
            parsedUserId == Guid.Empty)
        {
            return AuthenticateResult.Fail(
                "A valid Test-User-Id header is required.");
        }

        // -----------------------------------------------------------------
        // Make sure the authenticated test user actually exists in the
        // test database.
        //
        // Production users are created during Google sign-in, but these
        // integration tests bypass Google sign-in, so we create the test
        // user here instead.
        // -----------------------------------------------------------------

        var db = Context.RequestServices
            .GetRequiredService<RentMateDbContext>();

        if (!await db.Users.AnyAsync(u => u.Id == parsedUserId))
        {
            db.Users.Add(new User
            {
                Id = parsedUserId,
                Email = $"{parsedUserId}@test.com",
                DisplayName = $"Test User {parsedUserId.ToString()[..8]}",
                GoogleSubjectId = $"test-google-{parsedUserId}"
            });

            await db.SaveChangesAsync();
        }

        // -----------------------------------------------------------------
        // Build the authenticated ClaimsPrincipal.
        // The API's ClaimsPrincipalExtensions.GetUserId() reads the "sub"
        // claim, so the fake authentication must provide it.
        // -----------------------------------------------------------------

        var claims = new[]
        {
            new Claim(
                System.IdentityModel.Tokens.Jwt.JwtRegisteredClaimNames.Sub,
                parsedUserId.ToString()),

            new Claim(
                ClaimTypes.Email,
                $"{parsedUserId}@test.com")
        };

        var identity = new ClaimsIdentity(
            claims,
            SchemeName);

        var principal = new ClaimsPrincipal(identity);

        var ticket = new AuthenticationTicket(
            principal,
            SchemeName);

        return AuthenticateResult.Success(ticket);
    }
}