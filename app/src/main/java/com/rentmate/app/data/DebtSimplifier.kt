package com.rentmate.app.data

/** One unpaid share: [debtor] owes [creditor] (the bill issuer) [cents]. */
data class Obligation(val creditor: String, val debtor: String, val cents: Long)

/** One suggested payment, in cents. */
data class Transfer(val from: String, val to: String, val cents: Long)

/**
 * Greedy debt simplification. This must stay identical to settle_up in migration
 * 0012; both are checked against supabase/tests/fixtures/settle_up_fixtures.json.
 *
 * Creditors and debtors are each ordered by amount descending, then user id
 * ascending (plain string order, same as uuid order for lowercase ids). The
 * largest debtor pays the largest creditor; each side moves on when it reaches
 * zero. Lists are not re-sorted after a partial payment. Zero balances are skipped.
 */
object DebtSimplifier {
    /** Net cents per user: positive means the household owes them. */
    fun netBalances(obligations: List<Obligation>): Map<String, Long> {
        val net = HashMap<String, Long>()
        for (o in obligations) {
            if (o.creditor == o.debtor) continue
            net[o.debtor] = (net[o.debtor] ?: 0L) - o.cents
            net[o.creditor] = (net[o.creditor] ?: 0L) + o.cents
        }
        return net
    }

    fun simplify(obligations: List<Obligation>): List<Transfer> = simplify(netBalances(obligations))

    fun simplify(balances: Map<String, Long>): List<Transfer> {
        val order = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
        val creditors = balances.filter { it.value > 0 }.toList().sortedWith(order)
            .map { it.first to it.second }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { it.key to -it.value }
            .sortedWith(order).toMutableList()

        val out = ArrayList<Transfer>()
        var ci = 0
        var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val pay = minOf(creditors[ci].second, debtors[di].second)
            out += Transfer(debtors[di].first, creditors[ci].first, pay)
            creditors[ci] = creditors[ci].first to creditors[ci].second - pay
            debtors[di] = debtors[di].first to debtors[di].second - pay
            if (creditors[ci].second == 0L) ci++
            if (debtors[di].second == 0L) di++
        }
        return out
    }
}
