package com.twchat.app

import com.twchat.app.data.AppSession
import com.twchat.app.data.Person
import com.twchat.app.data.supabase.TokenSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionTest {

    private val me = Person(id = "uuid-me", displayName = "Minh", color = 0xFFE0567B)

    @Test
    fun `fresh install is neither configured nor logged in`() {
        val session = AppSession()
        assertFalse(session.isConfigured)
        assertFalse(session.isLoggedIn)
        assertFalse(session.isPaired)
    }

    @Test
    fun `tokens plus user id mean logged in`() {
        val session = AppSession(
            serverUrl = "https://abcd.supabase.co",
            anonKey = "anon",
            accessToken = "jwt",
            userId = "uuid-me",
            me = me,
        )
        assertTrue(session.isConfigured)
        assertTrue(session.isLoggedIn)
        assertFalse(session.isPaired)
    }

    @Test
    fun `partner makes the session paired`() {
        val session = AppSession(
            serverUrl = "https://abcd.supabase.co",
            anonKey = "anon",
            accessToken = "jwt",
            userId = "uuid-me",
            me = me,
            partner = Person(id = "uuid-you", displayName = "Lan", color = 0xFF3A82C4),
        )
        assertTrue(session.isPaired)
    }

    @Test
    fun `token refresh happens in the last minute`() {
        val fresh = TokenSet(
            accessToken = "jwt",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() + 30 * 60_000L,
            userId = "uuid-me",
            email = null,
            isAnonymous = true,
        )
        val expiring = fresh.copy(expiresAt = System.currentTimeMillis() + 15_000L)

        assertFalse(fresh.needsRefresh)
        assertTrue(expiring.needsRefresh)
    }

    @Test
    fun `person initial falls back to question mark`() {
        assertEquals("M", me.initial)
        assertEquals("?", me.copy(displayName = "   ").initial)
    }
}
