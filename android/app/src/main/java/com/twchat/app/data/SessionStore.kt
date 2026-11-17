package com.twchat.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Persists the logged-in account, the chosen server and the cached partner. */
class SessionStore(context: Context) {

    private val dataStore = context.applicationContext.sessionDataStore

    val session: Flow<Session> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            Session(
                serverUrl = prefs[Keys.SERVER_URL] ?: Session.DEFAULT_SERVER_URL,
                token = prefs[Keys.TOKEN]?.takeIf { it.isNotBlank() },
                me = PersonJson.parse(prefs[Keys.ME]),
                partner = PersonJson.parse(prefs[Keys.PARTNER]),
            )
        }

    suspend fun saveServerUrl(url: String) {
        dataStore.edit { prefs -> prefs[Keys.SERVER_URL] = url }
    }

    suspend fun saveAuth(serverUrl: String, token: String, me: Person, partner: Person?) {
        dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl
            prefs[Keys.TOKEN] = token
            prefs[Keys.ME] = PersonJson.toJson(me)
            if (partner != null) {
                prefs[Keys.PARTNER] = PersonJson.toJson(partner)
            } else {
                prefs.remove(Keys.PARTNER)
            }
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
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.ME)
            prefs.remove(Keys.PARTNER)
        }
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
        val ME = stringPreferencesKey("me")
        val PARTNER = stringPreferencesKey("partner")
    }
}
