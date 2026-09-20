using Google.Apis.Auth;

namespace RentMate.Api.Services;

public record GoogleIdentity(string Subject, string Email, string DisplayName);

public interface IGoogleAuthValidator
{
    /// <summary>
    /// Validates a Google ID token's signature, issuer, audience and
    /// expiry (US-1). Throws InvalidGoogleTokenException if the token is
    /// not a genuine, current token issued for this app's OAuth client.
    /// </summary>
    Task<GoogleIdentity> ValidateAsync(string idToken);
}

public class InvalidGoogleTokenException : Exception
{
    public InvalidGoogleTokenException(string message, Exception? inner = null) : base(message, inner) { }
}

public class GoogleAuthValidator : IGoogleAuthValidator
{
    private readonly IConfiguration _configuration;

    public GoogleAuthValidator(IConfiguration configuration)
    {
        _configuration = configuration;
    }

    public async Task<GoogleIdentity> ValidateAsync(string idToken)
    {
        var clientId = _configuration["Google:AndroidClientId"];

        try
        {
            var settings = new GoogleJsonWebSignature.ValidationSettings();
            if (!string.IsNullOrWhiteSpace(clientId))
            {
                // Restricts acceptance to ID tokens issued for RentMate's own
                // OAuth client - without this, any Google-signed token for any
                // app would be accepted.
                settings.Audience = new[] { clientId };
            }

            var payload = await GoogleJsonWebSignature.ValidateAsync(idToken, settings);

            return new GoogleIdentity(
                Subject: payload.Subject,
                Email: payload.Email,
                DisplayName: string.IsNullOrWhiteSpace(payload.Name) ? payload.Email : payload.Name);
        }
        catch (InvalidJwtException ex)
        {
            throw new InvalidGoogleTokenException("The Google ID token is invalid, expired, or was not issued for this app.", ex);
        }
    }
}
