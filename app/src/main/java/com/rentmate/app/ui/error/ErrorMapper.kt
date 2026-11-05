package com.rentmate.app.ui.error

import android.util.Log
import androidx.annotation.StringRes
import com.rentmate.app.R
import io.github.jan.supabase.auth.exception.AuthSessionMissingException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException

private const val TAG = "ErrorMapper"

/** The kinds of failure a user can act on, each with a friendly, translatable message. */
enum class AppError(@StringRes val messageRes: Int) {
    NO_CONNECTION(R.string.error_no_connection),
    SESSION_EXPIRED(R.string.error_session_expired),
    NOT_ALLOWED(R.string.error_not_allowed),
    ALREADY_EXISTS(R.string.error_already_exists),
    GENERIC(R.string.error_generic)
}

/**
 * RM-0004: turns any exception from Supabase or the network into a string
 * resource, so no screen ever shows raw exception text. The raw exception is
 * logged with [Log.e] for debugging; only the mapped message reaches the UI.
 */
object ErrorMapper {

    /** Logs [throwable] and returns the message to show. [fallback] replaces the generic message. */
    @StringRes
    fun toMessage(throwable: Throwable, @StringRes fallback: Int = R.string.error_generic): Int {
        Log.e(TAG, "Request failed", throwable)
        val error = classify(throwable)
        return if (error == AppError.GENERIC) fallback else error.messageRes
    }

    /** Walks the cause chain, since Ktor and Supabase sometimes wrap the real failure. */
    fun classify(throwable: Throwable): AppError {
        var current: Throwable? = throwable
        repeat(MAX_CAUSE_DEPTH) {
            when (val t = current ?: return AppError.GENERIC) {
                // Offline, DNS failure, timeouts, Supabase's HttpRequestException.
                is IOException -> return AppError.NO_CONNECTION
                is AuthSessionMissingException -> return AppError.SESSION_EXPIRED
                is PostgrestRestException -> return fromServer(t.statusCode, t.code)
                is RestException -> return fromServer(t.statusCode, null)
            }
            current = current?.cause
        }
        return AppError.GENERIC
    }

    /**
     * Maps an HTTP status and optional Postgres/PostgREST error code.
     * 42501 = insufficient privilege (RLS), 23505 = unique violation,
     * PGRST301/PGRST303 = JWT invalid or expired.
     */
    fun fromServer(status: Int, code: String?): AppError = when {
        code == "PGRST301" || code == "PGRST303" || status == 401 -> AppError.SESSION_EXPIRED
        code == "42501" || status == 403 -> AppError.NOT_ALLOWED
        code == "23505" || status == 409 -> AppError.ALREADY_EXISTS
        else -> AppError.GENERIC
    }

    private const val MAX_CAUSE_DEPTH = 5
}
