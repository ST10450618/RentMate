namespace RentMate.Api.Services;

public record Balance(Guid UserId, decimal NetAmount);

public record SimplifiedTransfer(Guid FromUserId, Guid ToUserId, decimal Amount);

public interface IDebtSimplificationService
{
    /// <summary>
    /// Given each member's net balance (positive = owed money, negative =
    /// owes money), returns the smallest set of transfers that settles the
    /// household (US-11) - the same debt-simplification idea RentMate
    /// adopted from Splitwise in the Research Report.
    /// </summary>
    List<SimplifiedTransfer> Simplify(IEnumerable<Balance> balances);
}

/// <summary>
/// Greedy debt-simplification: repeatedly matches the member owed the most
/// against the member who owes the most, transferring the smaller of the
/// two amounts, until every balance is settled (within one cent). This is
/// the standard, well-known approach to minimising transaction count and
/// is deterministic and easy to unit test.
/// </summary>
public class DebtSimplificationService : IDebtSimplificationService
{
    private const decimal Tolerance = 0.01m;

    public List<SimplifiedTransfer> Simplify(IEnumerable<Balance> balances)
    {
        // Work on a mutable copy, ignoring anyone who is already settled.
        var working = balances
            .Where(b => Math.Abs(b.NetAmount) > Tolerance)
            .Select(b => new { b.UserId, Amount = b.NetAmount })
            .ToList();

        var creditors = new List<(Guid UserId, decimal Amount)>(
            working.Where(b => b.Amount > 0).Select(b => (b.UserId, b.Amount)));
        var debtors = new List<(Guid UserId, decimal Amount)>(
            working.Where(b => b.Amount < 0).Select(b => (b.UserId, -b.Amount)));

        var transfers = new List<SimplifiedTransfer>();

        int ci = 0, di = 0;
        while (ci < creditors.Count && di < debtors.Count)
        {
            var (creditorId, creditorAmount) = creditors[ci];
            var (debtorId, debtorAmount) = debtors[di];

            var transferAmount = Math.Min(creditorAmount, debtorAmount);
            transferAmount = Math.Round(transferAmount, 2, MidpointRounding.AwayFromZero);

            if (transferAmount > 0)
            {
                transfers.Add(new SimplifiedTransfer(debtorId, creditorId, transferAmount));
            }

            var remainingCreditor = creditorAmount - transferAmount;
            var remainingDebtor = debtorAmount - transferAmount;

            creditors[ci] = (creditorId, remainingCreditor);
            debtors[di] = (debtorId, remainingDebtor);

            if (remainingCreditor <= Tolerance) ci++;
            if (remainingDebtor <= Tolerance) di++;
        }

        return transfers;
    }
}
