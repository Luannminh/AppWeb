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

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/** Persists [Profile] with Jetpack DataStore (small key/value settings). */
class ProfileStore(context: Context) {

    private val dataStore = context.applicationContext.profileDataStore

    val profile: Flow<Profile> = dataStore.data
        .catch { throwable ->
            // A corrupted file should not crash the app; fall back to defaults.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            Profile(
                meName = prefs[Keys.ME_NAME] ?: Profile.DEFAULT_ME_NAME,
                partnerName = prefs[Keys.PARTNER_NAME] ?: Profile.DEFAULT_PARTNER_NAME,
                meColor = prefs[Keys.ME_COLOR] ?: Profile.DEFAULT_ME_COLOR,
                partnerColor = prefs[Keys.PARTNER_COLOR] ?: Profile.DEFAULT_PARTNER_COLOR,
                onboarded = prefs[Keys.ONBOARDED] ?: false,
            )
        }

    suspend fun save(profile: Profile) {
        dataStore.edit { prefs ->
            prefs[Keys.ME_NAME] = profile.meName.trim().ifEmpty { Profile.DEFAULT_ME_NAME }
            prefs[Keys.PARTNER_NAME] =
                profile.partnerName.trim().ifEmpty { Profile.DEFAULT_PARTNER_NAME }
            prefs[Keys.ME_COLOR] = profile.meColor
            prefs[Keys.PARTNER_COLOR] = profile.partnerColor
            prefs[Keys.ONBOARDED] = profile.onboarded
        }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val ME_NAME = stringPreferencesKey("me_name")
        val PARTNER_NAME = stringPreferencesKey("partner_name")
        val ME_COLOR = longPreferencesKey("me_color")
        val PARTNER_COLOR = longPreferencesKey("partner_color")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }
}
