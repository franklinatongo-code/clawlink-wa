package app.waweb.local

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plug point for the "AI replies" half of the bridge.
 *
 * Works with anything that speaks the OpenAI chat-completions shape:
 *   OpenAI     https://api.openai.com/v1/chat/completions
 *   Groq       https://api.groq.com/openai/v1/chat/completions
 *   OpenRouter https://openrouter.ai/api/v1/chat/completions
 *   Ollama     http://<pc-lan-ip>:11434/v1/chat/completions   (key can be any non-empty string)
 *
 * Leave API_KEY blank to disable auto-replies. This runs on MainActivity's io executor,
 * so the blocking HTTP call is fine.
 */
object Llm {
    private const val BASE_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"
    private const val API_KEY = "" // <-- put your key here, or point BASE_URL at a local Ollama

    private const val SYSTEM_PROMPT =
        "You are replying to a WhatsApp message on the owner's phone. " +
            "Answer naturally and briefly. Plain text only, no markdown, no code fences."

    fun isEnabled(): Boolean = API_KEY.isNotBlank()

    fun reply(userText: String): String? {
        if (!isEnabled()) return null
        val body = JSONObject().apply {
            put("model", MODEL)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                })
            })
        }

        var conn: HttpURLConnection? = null
        return try {
            val c = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $API_KEY")
            }
            conn = c
            c.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("WALocal", "LLM HTTP $code: ${err.take(300)}")
                return null
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
        } catch (e: Exception) {
            Log.e("WALocal", "LLM call failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
}
