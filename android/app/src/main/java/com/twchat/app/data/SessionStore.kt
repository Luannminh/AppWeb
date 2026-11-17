package com.twchat.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Persists Supabase settings, auth tokens and the cached profiles. */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    val session: Flow<AppSession> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            AppSession(
                serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
                anonKey = prefs[Keys.ANON_KEY].orEmpty(),
                accessToken = prefs[Keys.ACCESS_TOKEN]?.takeIf { it.isNotBlank() },
                refreshToken = prefs[Keys.REFRESH_TOKEN]?.takeIf { it.isNotBlank() },
                expiresAt = prefs[Keys.EXPIRES_AT] ?: 0L,
                userId = prefs[Keys.USER_ID]?.takeIf { it.isNotBlank() },
                email = prefs[Keys.EMAIL]?.takeIf { it.isNotBlank() },
                isAnonymous = prefs[Keys.ANONYMOUS] ?: false,
                me = PersonJson.parse(prefs[Keys.ME]),
                partner = PersonJson.parse(prefs[Keys.PARTNER]),
            )
        }

    suspend fun saveConfig(serverUrl: String, anonKey: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl
            prefs[Keys.ANON_KEY] = anonKey
        }
    }

    suspend fun saveAuth(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        userId: String,
        email: String?,
        isAnonymous: Boolean,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.EXPIRES_AT] = expiresAt
            prefs[Keys.USER_ID] = userId
            if (email.isNullOrBlank()) prefs.remove(Keys.EMAIL) else prefs[Keys.EMAIL] = email
            prefs[Keys.ANONYMOUS] = isAnonymous
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.EXPIRES_AT] = expiresAt
        }
    }

    suspend fun saveMe(me: Person) {
        dataStore.edit { prefs -> prefs[Keys.ME] = PersonJson.toJson(me) }
    }

    suspend fun savePartner(partner: Person?) {
        dataStore.edit { prefs ->
            if (partner != null) {
                prefs[Keys.PARTNER] = PersonJson.toJson(partner)
            } else {
                prefs.remove(Keys.PARTNER)
            }
        }
    }

    suspend fun clearAuth() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.EXPIRES_AT)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.ANONYMOUS)
            prefs.remove(Keys.ME)
            prefs.remove(Keys.PARTNER)
        }
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ANON_KEY = stringPreferencesKey("anon_key")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val ANONYMOUS = booleanPreferencesKey("anonymous")
        val ME = stringPreferencesKey("me")
        val PARTNER = stringPreferencesKey("partner")
    }
}
