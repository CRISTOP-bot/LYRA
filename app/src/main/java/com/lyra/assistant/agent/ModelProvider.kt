package com.lyra.assistant.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** OpenAI-compatible HTTP provider. No provider is enabled until the user supplies a credential. */
interface ModelProvider {
    suspend fun reply(baseUrl: String, model: String, apiKey: String, messages: List<Pair<String, String>>): String
}

class OpenAiCompatibleProvider : ModelProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()

    override suspend fun reply(baseUrl: String, model: String, apiKey: String, messages: List<Pair<String, String>>): String {
        require(apiKey.isNotBlank()) { "Configura una clave de proveedor en Ajustes." }
        val uri = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().also { array ->
                array.add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", "Eres LYRA, asistente útil, amigable y honesta. Habla principalmente en español. No afirmes haber realizado acciones que no ejecutaste.")
                })
                messages.takeLast(24).forEach { (role, text) ->
                    array.add(JsonObject().apply { addProperty("role", role); addProperty("content", text) })
                }
            })
        }
        val request = Request.Builder().url(uri)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonObject("error").get("message")?.asString }.getOrNull()
                throw IllegalStateException(detail ?: "El proveedor respondió HTTP ${response.code}.")
            }
            val root = JsonParser.parseString(body).asJsonObject
            return root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString
                ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("El proveedor devolvió una respuesta vacía.")
        }
    }
}
