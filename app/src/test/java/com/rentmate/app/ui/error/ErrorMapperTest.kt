package com.rentmate.app.ui.error

import com.rentmate.app.R
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** RM-0004: every failure maps to a friendly string resource, never raw exception text. */
class ErrorMapperTest {

    @Test
    fun `network failures map to no connection`() {
        listOf(
            IOException("boom"),
            UnknownHostException("crmomoazxwrdsicfphav.supabase.co"),
            SocketTimeoutException("timeout"),
            HttpRequestTimeoutException("https://example.com", 10_000L)
        ).forEach { assertEquals(it.toString(), AppError.NO_CONNECTION, ErrorMapper.classify(it)) }
    }

    @Test
    fun `a network failure wrapped in another exception is still found`() {
        val wrapped = IllegalStateException("decode failed", RuntimeException(UnknownHostException("host")))
        assertEquals(AppError.NO_CONNECTION, ErrorMapper.classify(wrapped))
    }

    @Test
    fun `401 and expired JWT codes map to session expired`() {
        assertEquals(AppError.SESSION_EXPIRED, ErrorMapper.fromServer(401, null))
        assertEquals(AppError.SESSION_EXPIRED, ErrorMapper.fromServer(400, "PGRST301"))
        assertEquals(AppError.SESSION_EXPIRED, ErrorMapper.fromServer(400, "PGRST303"))
    }

    @Test
    fun `403 and RLS violations map to not allowed`() {
        assertEquals(AppError.NOT_ALLOWED, ErrorMapper.fromServer(403, null))
        assertEquals(AppError.NOT_ALLOWED, ErrorMapper.fromServer(400, "42501"))
    }

    @Test
    fun `an expired session wins over a permission error`() {
        // The user must sign in again before permissions mean anything.
        assertEquals(AppError.SESSION_EXPIRED, ErrorMapper.fromServer(401, "42501"))
    }

    @Test
    fun `409 and unique violations map to already exists`() {
        assertEquals(AppError.ALREADY_EXISTS, ErrorMapper.fromServer(409, null))
        assertEquals(AppError.ALREADY_EXISTS, ErrorMapper.fromServer(400, "23505"))
    }

    @Test
    fun `anything else is generic`() {
        assertEquals(AppError.GENERIC, ErrorMapper.fromServer(500, null))
        assertEquals(AppError.GENERIC, ErrorMapper.fromServer(400, "P0001"))
        assertEquals(AppError.GENERIC, ErrorMapper.classify(IllegalStateException("Signed in but no user id")))
    }

    @Test
    fun `toMessage returns a string resource, using the fallback for generic errors`() {
        assertEquals(R.string.error_no_connection, ErrorMapper.toMessage(IOException()))
        assertEquals(R.string.error_generic, ErrorMapper.toMessage(IllegalStateException()))
        assertEquals(
            R.string.error_invalid_invite_code,
            ErrorMapper.toMessage(IllegalStateException("Invalid invite code"), fallback = R.string.error_invalid_invite_code)
        )
    }

    @Test
    fun `the fallback never hides a real connection problem`() {
        assertEquals(
            R.string.error_no_connection,
            ErrorMapper.toMessage(UnknownHostException(), fallback = R.string.error_invalid_invite_code)
        )
    }
}
