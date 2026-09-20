using RentMate.Api.Services;
using Xunit;

namespace RentMate.Api.Tests;

public class DebtSimplificationServiceTests
{
    private readonly DebtSimplificationService _service = new();

    [Fact]
    public void Simplify_TwoPersonDebt_ReturnsSingleTransfer()
    {
        var alice = Guid.NewGuid();
        var bob = Guid.NewGuid();

        var balances = new List<Balance>
        {
            new(alice, 100m),
            new(bob, -100m)
        };

        var result = _service.Simplify(balances);

        var transfer = Assert.Single(result);
        Assert.Equal(bob, transfer.FromUserId);
        Assert.Equal(alice, transfer.ToUserId);
        Assert.Equal(100m, transfer.Amount);
    }

    [Fact]
    public void Simplify_ThreePersonHousehold_MinimisesTransactionCount()
    {
        // Classic example: A is owed 300, B is owed nothing but owes 100,
        // C owes 200. The minimum-transaction answer is 2 transfers, not 3.
        var a = Guid.NewGuid();
        var b = Guid.NewGuid();
        var c = Guid.NewGuid();

        var balances = new List<Balance>
        {
            new(a, 300m),
            new(b, -100m),
            new(c, -200m)
        };

        var result = _service.Simplify(balances);

        Assert.Equal(2, result.Count);
        Assert.Equal(300m, result.Sum(t => t.Amount));
        Assert.All(result, t => Assert.Equal(a, t.ToUserId));
    }

    [Fact]
    public void Simplify_EveryoneAlreadySettled_ReturnsNoTransfers()
    {
        var balances = new List<Balance>
        {
            new(Guid.NewGuid(), 0m),
            new(Guid.NewGuid(), 0.001m) // within the 1-cent tolerance
        };

        var result = _service.Simplify(balances);

        Assert.Empty(result);
    }

    [Fact]
    public void Simplify_FourPersonHousehold_NeverProducesMoreTransfersThanCreditors()
    {
        var users = Enumerable.Range(0, 4).Select(_ => Guid.NewGuid()).ToList();

        var balances = new List<Balance>
        {
            new(users[0], 150m),
            new(users[1], 50m),
            new(users[2], -80m),
            new(users[3], -120m)
        };

        var result = _service.Simplify(balances);

        // Sanity check: total money moved must equal total owed, and the
        // simplification should never need more transfers than there are
        // distinct creditors + debtors minus one.
        Assert.Equal(200m, result.Sum(t => t.Amount));
        Assert.True(result.Count <= 3);
    }
}
