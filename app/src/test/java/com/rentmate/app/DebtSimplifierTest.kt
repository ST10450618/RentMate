package com.rentmate.app

import com.rentmate.app.data.DebtSimplifier
import com.rentmate.app.data.Obligation
import com.rentmate.app.data.Transfer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** RM-0030: the client port must give the same transfers as settle_up (migration 0012). */
class DebtSimplifierTest {
    private val fixtures: List<JsonObject> by lazy {
        // Gradle runs unit tests from the app module directory.
        val file = listOf("../supabase/tests/fixtures/settle_up_fixtures.json", "supabase/tests/fixtures/settle_up_fixtures.json")
            .map(::File).first { it.exists() }
        Json.parseToJsonElement(file.readText()).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }
    }

    @Test
    fun fixturesMatchServerSettleUp() {
        assertTrue(fixtures.size >= 6)
        for (case in fixtures) {
            val obligations = case["obligations"]!!.jsonArray.map {
                val o = it.jsonObject
                Obligation(o["creditor"]!!.jsonPrimitive.content, o["debtor"]!!.jsonPrimitive.content, o["cents"]!!.jsonPrimitive.long)
            }
            val expected = case["expected"]!!.jsonArray.map {
                val e = it.jsonObject
                Transfer(e["from"]!!.jsonPrimitive.content, e["to"]!!.jsonPrimitive.content, e["cents"]!!.jsonPrimitive.long)
            }
            assertEquals(case["name"]!!.jsonPrimitive.content, expected, DebtSimplifier.simplify(obligations))
        }
    }

    @Test
    fun resultIsIndependentOfInputOrder() {
        for (case in fixtures) {
            val obligations = case["obligations"]!!.jsonArray.map {
                val o = it.jsonObject
                Obligation(o["creditor"]!!.jsonPrimitive.content, o["debtor"]!!.jsonPrimitive.content, o["cents"]!!.jsonPrimitive.long)
            }
            assertEquals(DebtSimplifier.simplify(obligations), DebtSimplifier.simplify(obligations.reversed()))
        }
    }

    @Test
    fun transfersSettleEveryBalance() {
        val balances = mapOf("a" to 700L, "b" to -300L, "c" to -400L)
        val net = HashMap(balances)
        for (t in DebtSimplifier.simplify(balances)) {
            net[t.from] = net.getValue(t.from) + t.cents
            net[t.to] = net.getValue(t.to) - t.cents
        }
        assertTrue(net.values.all { it == 0L })
    }

    @Test
    fun zeroBalancesAreSkipped() {
        assertEquals(emptyList<Transfer>(), DebtSimplifier.simplify(mapOf("a" to 0L, "b" to 0L)))
    }
}
