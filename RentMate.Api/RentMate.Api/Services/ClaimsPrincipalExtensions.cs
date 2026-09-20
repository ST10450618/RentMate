using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace RentMate.Api.Services;

public static class ClaimsPrincipalExtensions
{
    /// <summary>Reads the current user's ID out of the validated JWT's "sub" claim.</summary>
    public static Guid GetUserId(this ClaimsPrincipal principal)
    {
        var subject = principal.FindFirstValue(JwtRegisteredClaimNames.Sub)
            ?? principal.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? throw new InvalidOperationException("No user id claim present on the current request.");

        return Guid.Parse(subject);
    }
}
