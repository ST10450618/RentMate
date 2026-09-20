using System.Security.Cryptography;

namespace RentMate.Api.Services;

public interface IInviteCodeGenerator
{
    /// <summary>Generates a 6-character alphanumeric invite code (US-5).</summary>
    string Generate();
}

/// <summary>
/// Excludes visually ambiguous characters (0/O, 1/I/L) so a code read
/// aloud or copied by hand between housemates is less error-prone.
/// </summary>
public class InviteCodeGenerator : IInviteCodeGenerator
{
    private const string Alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private const int Length = 6;

    public string Generate()
    {
        Span<char> code = stackalloc char[Length];
        var randomBytes = RandomNumberGenerator.GetBytes(Length);

        for (var i = 0; i < Length; i++)
        {
            code[i] = Alphabet[randomBytes[i] % Alphabet.Length];
        }

        return new string(code);
    }
}
