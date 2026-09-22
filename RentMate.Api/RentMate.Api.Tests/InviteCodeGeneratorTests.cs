using RentMate.Api.Services;
using Xunit;

namespace RentMate.Api.Tests;

public class InviteCodeGeneratorTests
{
    private readonly InviteCodeGenerator _generator = new();

    [Fact]
    public void Generate_ReturnsSixCharacters()
    {
        var code = _generator.Generate();

        Assert.Equal(6, code.Length);
    }

    [Fact]
    public void Generate_DoesNotContainAmbiguousCharacters()
    {
        // 0/O and 1/I/L are excluded so a code read aloud between
        // housemates is not misheard.
        var ambiguous = new[] { '0', 'O', '1', 'I', 'L' };

        for (var i = 0; i < 200; i++)
        {
            var code = _generator.Generate();
            Assert.All(code, ch => Assert.DoesNotContain(ch, ambiguous));
        }
    }

    [Fact]
    public void Generate_ProducesVariedCodes()
    {
        var codes = Enumerable.Range(0, 100).Select(_ => _generator.Generate()).ToHashSet();

        // Not a strict uniqueness guarantee (it's random), but 100 draws
        // from a 32^6 alphabet collapsing to fewer than ~95 distinct values
        // would indicate something is badly wrong with the randomness.
        Assert.True(codes.Count > 95);
    }
}
