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

private const val RAG_CONVERSATION_STORE_NAME = "alicia_mobile_rag_conversation"

private val Context.ragConversationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = RAG_CONVERSATION_STORE_NAME,
)

internal class RagConversationStore(private val context: Context) {
    private val conversationIdKey = stringPreferencesKey("conversation_id")

    fun conversationIdFlow(): Flow<String?> =
        context.ragConversationDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences -> preferences[conversationIdKey]?.takeIf { it.isNotBlank() } }

    suspend fun saveConversationId(conversationId: String?) {
        context.ragConversationDataStore.edit { preferences ->
            val normalized = conversationId?.trim().orEmpty()
            if (normalized.isBlank()) {
                preferences.remove(conversationIdKey)
            } else {
                preferences[conversationIdKey] = normalized
            }
        }
    }

    suspend fun clearConversation() {
        context.ragConversationDataStore.edit { preferences ->
            preferences.remove(conversationIdKey)
        }
    }
}
