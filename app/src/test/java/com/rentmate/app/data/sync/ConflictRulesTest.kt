package com.rentmate.app.data.sync

import com.rentmate.app.data.local.OutboxEntry
import com.rentmate.app.data.local.OutboxOp
import com.rentmate.app.ui.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/** RM-0018: one test per conflict rule of the sync worker. */
class ConflictRulesTest {

    private fun entry(op: String, id: Long = 1, entityId: String = "e1", attempts: Int = 0, createdAt: Long = 100) =
        OutboxEntry(
            id = id, op = op, tableName = if (op == OutboxOp.RPC) "create_bill" else "shopping_items",
            payloadJson = "{}", createdAt = createdAt, entityId = entityId, attemptCount = attempts
        )

    private val insert = entry(OutboxOp.INSERT)
    private val update = entry(OutboxOp.UPDATE)
    private val rpc = entry(OutboxOp.RPC)

    @Test
    fun success_isApplied() {
        assertEquals(Decision.Applied("server-1"), ConflictRules.decide(rpc, ReplayOutcome.Success("server-1")))
        assertEquals(Decision.Applied("e1"), ConflictRules.decide(update, ReplayOutcome.Success("e1")))
    }

    @Test
    fun alreadyExistsOnInsert_countsAsSuccess() {
        assertEquals(Decision.Applied("e1"), ConflictRules.decide(insert, ReplayOutcome.AlreadyExists))
        // 409 and 23505 both map to AlreadyExists.
        assertEquals(ReplayOutcome.AlreadyExists, ConflictRules.fromServer(409, null, null))
        assertEquals(ReplayOutcome.AlreadyExists, ConflictRules.fromServer(400, "23505", null))
    }

    @Test
    fun alreadyExistsOnUpdate_isDroppedAndReverted() {
        val decision = ConflictRules.decide(update, ReplayOutcome.AlreadyExists)
        assertEquals(DropReason.INVALID, (decision as Decision.Drop).reason)
        assertEquals(LocalChange.REVERT_ROW, decision.local)
    }

    @Test
    fun missingTargetOnUpdate_dropsEntryAndLocalRow() {
        val decision = ConflictRules.decide(update, ReplayOutcome.TargetMissing)
        assertEquals(Decision.Drop(DropReason.TARGET_MISSING, LocalChange.DELETE_ROW, "row not found"), decision)
        // 404 and PGRST116 both mean the row is gone.
        assertEquals(ReplayOutcome.TargetMissing, ConflictRules.fromServer(404, null, null))
        assertEquals(ReplayOutcome.TargetMissing, ConflictRules.fromServer(406, "PGRST116", null))
    }

    @Test
    fun offline_stopsAndRetriesWithoutCountingAnAttempt() {
        assertEquals(Decision.StopAndRetry, ConflictRules.decide(insert, ReplayOutcome.Offline))
        assertEquals(Decision.StopAndRetry, ConflictRules.decide(rpc.copy(attemptCount = 99), ReplayOutcome.Offline))
        assertEquals(ReplayOutcome.Offline, ConflictRules.outcomeOf(IOException("no route")))
        assertEquals(ReplayOutcome.Offline, ConflictRules.outcomeOf(IllegalStateException("wrapped", UnknownHostException("h"))))
    }

    @Test
    fun sessionExpired_stopsAndRetries() {
        assertEquals(Decision.StopAndRetry, ConflictRules.decide(update, ReplayOutcome.SessionExpired))
        assertEquals(ReplayOutcome.SessionExpired, ConflictRules.fromServer(401, null, null))
        assertEquals(ReplayOutcome.SessionExpired, ConflictRules.fromServer(400, "PGRST303", null))
    }

    @Test
    fun notAllowed_dropsWithReason() {
        val outcome = ConflictRules.fromServer(403, "42501", "violates row-level security")
        assertEquals(ReplayOutcome.Rejected(AppError.NOT_ALLOWED, "violates row-level security"), outcome)

        assertEquals(
            Decision.Drop(DropReason.NOT_ALLOWED, LocalChange.DELETE_ROW, "violates row-level security"),
            ConflictRules.decide(insert, outcome)
        )
        // A refused update is reverted so the next refresh restores the server state.
        assertEquals(LocalChange.REVERT_ROW, (ConflictRules.decide(update, outcome) as Decision.Drop).local)
    }

    @Test
    fun validation4xx_dropsAsInvalid() {
        val outcome = ConflictRules.fromServer(400, "23514", "check constraint")
        assertEquals(ReplayOutcome.Rejected(AppError.GENERIC, "check constraint"), outcome)
        assertEquals(DropReason.INVALID, (ConflictRules.decide(rpc, outcome) as Decision.Drop).reason)
    }

    @Test
    fun serverError_recordsFailureAndRetries() {
        assertEquals(ReplayOutcome.ServerError("boom"), ConflictRules.fromServer(503, null, "boom"))
        // Rate limits and timeouts are 4xx but pass with time.
        assertTrue(ConflictRules.fromServer(429, null, null) is ReplayOutcome.ServerError)
        assertTrue(ConflictRules.outcomeOf(IllegalStateException("odd")) is ReplayOutcome.ServerError)

        assertEquals(Decision.RecordFailureAndRetry("boom"), ConflictRules.decide(insert, ReplayOutcome.ServerError("boom")))
    }

    @Test
    fun serverError_becomesPermanentAtTheAttemptCap() {
        val almost = rpc.copy(attemptCount = ConflictRules.MAX_ATTEMPTS - 2)
        assertTrue(ConflictRules.decide(almost, ReplayOutcome.ServerError("x")) is Decision.RecordFailureAndRetry)

        val last = rpc.copy(attemptCount = ConflictRules.MAX_ATTEMPTS - 1)
        assertEquals(
            Decision.Drop(DropReason.TOO_MANY_ATTEMPTS, LocalChange.DELETE_ROW, "x"),
            ConflictRules.decide(last, ReplayOutcome.ServerError("x"))
        )
    }

    @Test
    fun droppedEntry_takesLaterEntriesForTheSameRowWithIt() {
        val rejected = entry(OutboxOp.INSERT, id = 1, entityId = "item", createdAt = 100)
        val purchase = entry(OutboxOp.UPDATE, id = 2, entityId = "item", createdAt = 200)
        val other = entry(OutboxOp.INSERT, id = 3, entityId = "other", createdAt = 150)
        val older = entry(OutboxOp.UPDATE, id = 0, entityId = "item", createdAt = 50)

        assertEquals(listOf(purchase), ConflictRules.dependants(rejected, listOf(older, rejected, other, purchase)))
    }

    @Test
    fun entriesWithoutEntityId_haveNoDependants() {
        val legacy = entry(OutboxOp.INSERT, id = 1, entityId = "")
        val alsoLegacy = entry(OutboxOp.INSERT, id = 2, entityId = "", createdAt = 200)
        assertTrue(ConflictRules.dependants(legacy, listOf(legacy, alsoLegacy)).isEmpty())
    }
}
