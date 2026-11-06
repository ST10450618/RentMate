package com.rentmate.app.data.sync

import com.rentmate.app.data.local.OutboxEntry
import com.rentmate.app.data.local.OutboxOp
import com.rentmate.app.ui.error.AppError
import com.rentmate.app.ui.error.ErrorMapper
import io.github.jan.supabase.auth.exception.AuthSessionMissingException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException

/** RM-0018: what happened when the worker sent one outbox entry to Supabase. */
sealed interface ReplayOutcome {
    /** The server accepted it. [serverId] is the id of the row it created or changed, if known. */
    data class Success(val serverId: String?) : ReplayOutcome

    /** No connection (IOException somewhere in the cause chain). */
    data object Offline : ReplayOutcome

    /** 401, an expired JWT, or no session at all. The user may sign in again. */
    data object SessionExpired : ReplayOutcome

    /** 409 or 23505: a row with that key is already on the server. */
    data object AlreadyExists : ReplayOutcome

    /** The row to change is not there: 0 rows updated, 404, or PGRST116. */
    data object TargetMissing : ReplayOutcome

    /** A 4xx that sending again cannot fix: RLS ([AppError.NOT_ALLOWED]) or validation ([AppError.GENERIC]). */
    data class Rejected(val error: AppError, val message: String?) : ReplayOutcome

    /** A 5xx, a timeout-like 4xx, or an exception we do not know. Worth trying again. */
    data class ServerError(val message: String?) : ReplayOutcome
}

/** Why the worker gave up on an entry. Stored as [com.rentmate.app.data.local.SyncRejectionEntity.reason]. */
enum class DropReason {
    /** RLS or a missing permission (403 / 42501). */
    NOT_ALLOWED,

    /** The server refused the data itself (4xx validation, check constraint, unknown op). */
    INVALID,

    /** The row the change was for no longer exists on the server. */
    TARGET_MISSING,

    /** Failed with a retryable error [ConflictRules.MAX_ATTEMPTS] times. */
    TOO_MANY_ATTEMPTS,

    /** Dropped because an earlier change to the same row was dropped. */
    DEPENDS_ON_DROPPED
}

/** What to do with the local row of a dropped entry. */
enum class LocalChange {
    /** Remove the pending row: it never existed on the server, or no longer does. */
    DELETE_ROW,

    /** Mark the row SYNCED so the next refresh puts the server copy back. */
    REVERT_ROW
}

/** RM-0018: what the worker does after one replay. */
sealed interface Decision {
    /** Delete the entry and settle the local row. [serverId] is the id the server uses for the row. */
    data class Applied(val serverId: String?) : Decision

    /** Keep the entry as it is, stop this run and let WorkManager retry later. */
    data object StopAndRetry : Decision

    /** Count a failed attempt on the entry, stop this run and retry later. */
    data class RecordFailureAndRetry(val message: String?) : Decision

    /** Drop the entry and every later entry for the same row, change the local row, record why. */
    data class Drop(val reason: DropReason, val local: LocalChange, val message: String?) : Decision
}

/**
 * RM-0018: the conflict rules of the sync worker, as pure functions so they can be
 * unit tested without Room, WorkManager or a network.
 *
 * The queue is replayed strictly oldest first. A retryable failure stops the run
 * instead of skipping ahead, because a later entry can depend on an earlier one
 * (a purchase depends on its item's insert).
 */
object ConflictRules {

    /** A retryable failure becomes permanent on this attempt. */
    const val MAX_ATTEMPTS = 5

    /** Decides what to do with [entry] after a replay ended with [outcome]. */
    fun decide(entry: OutboxEntry, outcome: ReplayOutcome, maxAttempts: Int = MAX_ATTEMPTS): Decision =
        when (outcome) {
            is ReplayOutcome.Success -> Decision.Applied(outcome.serverId)
            ReplayOutcome.Offline, ReplayOutcome.SessionExpired -> Decision.StopAndRetry
            // The client generates insert ids, so a duplicate key means an earlier
            // attempt reached the server. Anything else hitting 409 is a real conflict.
            ReplayOutcome.AlreadyExists ->
                if (entry.op == OutboxOp.INSERT) Decision.Applied(entry.entityId)
                else Decision.Drop(DropReason.INVALID, localChangeFor(entry), "already exists")
            // The server wins: the row was deleted there, so the local copy goes too.
            ReplayOutcome.TargetMissing ->
                if (entry.op == OutboxOp.UPDATE) Decision.Drop(DropReason.TARGET_MISSING, LocalChange.DELETE_ROW, "row not found")
                else Decision.Drop(DropReason.INVALID, localChangeFor(entry), "not found")
            is ReplayOutcome.Rejected -> Decision.Drop(
                if (outcome.error == AppError.NOT_ALLOWED) DropReason.NOT_ALLOWED else DropReason.INVALID,
                localChangeFor(entry),
                outcome.message
            )
            is ReplayOutcome.ServerError ->
                if (entry.attemptCount + 1 >= maxAttempts) {
                    Decision.Drop(DropReason.TOO_MANY_ATTEMPTS, localChangeFor(entry), outcome.message)
                } else {
                    Decision.RecordFailureAndRetry(outcome.message)
                }
        }

    /**
     * A dropped create never existed on the server, so its row is deleted. A
     * dropped update is reverted, so the next refresh restores the server state.
     */
    fun localChangeFor(entry: OutboxEntry): LocalChange =
        if (entry.op == OutboxOp.UPDATE) LocalChange.REVERT_ROW else LocalChange.DELETE_ROW

    /**
     * Entries in [queue] queued after [dropped] for the same local row. They build
     * on the dropped change, so they are dropped with it. Entries without an
     * entity id (from before v3) never count as dependants.
     */
    fun dependants(dropped: OutboxEntry, queue: List<OutboxEntry>): List<OutboxEntry> {
        if (dropped.entityId.isBlank()) return emptyList()
        return queue.filter {
            it.id != dropped.id && it.entityId == dropped.entityId &&
                (it.createdAt > dropped.createdAt || (it.createdAt == dropped.createdAt && it.id > dropped.id))
        }
    }

    /** Maps a replay failure to an outcome, walking the cause chain like [ErrorMapper.classify]. */
    fun outcomeOf(throwable: Throwable): ReplayOutcome {
        var current: Throwable? = throwable
        repeat(MAX_CAUSE_DEPTH) {
            when (val t = current ?: return ReplayOutcome.ServerError(throwable.message)) {
                is IOException -> return ReplayOutcome.Offline
                is AuthSessionMissingException -> return ReplayOutcome.SessionExpired
                is PostgrestRestException -> return fromServer(t.statusCode, t.code, t.message)
                is RestException -> return fromServer(t.statusCode, null, t.message)
            }
            current = current?.cause
        }
        return ReplayOutcome.ServerError(throwable.message)
    }

    /**
     * Maps an HTTP status and optional Postgres/PostgREST code. Session, RLS and
     * duplicate codes follow [ErrorMapper.fromServer]. 404 and PGRST116 mean the
     * target is missing. 408 and 429 are 4xx but pass with time, so they retry.
     */
    fun fromServer(status: Int, code: String?, message: String?): ReplayOutcome {
        if (code == "PGRST116" || status == 404) return ReplayOutcome.TargetMissing
        return when (val error = ErrorMapper.fromServer(status, code)) {
            AppError.SESSION_EXPIRED -> ReplayOutcome.SessionExpired
            AppError.ALREADY_EXISTS -> ReplayOutcome.AlreadyExists
            AppError.NOT_ALLOWED -> ReplayOutcome.Rejected(error, message)
            else -> when (status) {
                408, 429 -> ReplayOutcome.ServerError(message)
                in 400..499 -> ReplayOutcome.Rejected(AppError.GENERIC, message)
                else -> ReplayOutcome.ServerError(message)
            }
        }
    }

    /** How many causes [outcomeOf] follows before it gives up and calls the error a server error. */
    private const val MAX_CAUSE_DEPTH = 5
}
