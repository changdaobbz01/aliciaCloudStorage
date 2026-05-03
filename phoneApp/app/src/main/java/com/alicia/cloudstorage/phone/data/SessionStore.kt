package com.alicia.cloudstorage.phone.data

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

private const val SESSION_STORE_NAME = "alicia_mobile_session"

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SESSION_STORE_NAME,
)

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("auth_token")
    private val baseUrlKey = stringPreferencesKey("api_base_url")

    fun sessionFlow(defaultBaseUrl: String): Flow<SavedSession> =
        context.sessionDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                SavedSession(
                    token = preferences[tokenKey],
                    baseUrl = preferences[baseUrlKey] ?: defaultBaseUrl,
                )
            }

    suspend fun saveBaseUrl(baseUrl: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[baseUrlKey] = baseUrl
        }
    }

    suspend fun saveSession(token: String, baseUrl: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[tokenKey] = token
            preferences[baseUrlKey] = baseUrl
        }
    }

    suspend fun clearToken(keepBaseUrl: String) {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(tokenKey)
            preferences[baseUrlKey] = keepBaseUrl
        }
    }
}
