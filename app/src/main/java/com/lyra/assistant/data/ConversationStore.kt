package com.lyra.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "lyra_local")

data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

data class ProviderSettings(val baseUrl: String, val model: String)

class ConversationStore(private val context: Context) {
    private val gson = Gson()
    private val historyKey = stringPreferencesKey("conversation_json")
    private val baseUrlKey = stringPreferencesKey("provider_base_url")
    private val modelKey = stringPreferencesKey("provider_model")
    private val secretKey = stringPreferencesKey("provider_secret_ciphertext")

    val messages: Flow<List<ChatMessage>> = context.dataStore.data.map { prefs ->
        runCatching {
            gson.fromJson<List<ChatMessage>>(prefs[historyKey].orEmpty(), object : TypeToken<List<ChatMessage>>() {}.type)
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    val settings: Flow<ProviderSettings> = context.dataStore.data.map { prefs ->
        ProviderSettings(prefs[baseUrlKey] ?: "https://api.openai.com/v1", prefs[modelKey] ?: "gpt-4o-mini")
    }

    suspend fun append(message: ChatMessage) {
        context.dataStore.edit { prefs ->
            val old = runCatching {
                gson.fromJson<List<ChatMessage>>(prefs[historyKey].orEmpty(), object : TypeToken<List<ChatMessage>>() {}.type) ?: emptyList()
            }.getOrDefault(emptyList())
            prefs[historyKey] = gson.toJson((old + message).takeLast(300))
        }
    }

    suspend fun clearConversation() { context.dataStore.edit { it.remove(historyKey) } }
    suspend fun saveProvider(baseUrl: String, model: String) {
        context.dataStore.edit { it[baseUrlKey] = baseUrl.trim().trimEnd('/'); it[modelKey] = model.trim() }
    }
    suspend fun saveSecret(ciphertext: String) { context.dataStore.edit { it[secretKey] = ciphertext } }
    val encryptedSecret: Flow<String?> = context.dataStore.data.map { it[secretKey] }
}
