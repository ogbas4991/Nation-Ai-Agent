package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiDirectClient {

    private const val TAG = "GeminiDirectClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves the active API key from BuildConfig or user configuration
     */
    fun resolveApiKey(userKey: String?): String {
        if (!userKey.isNullOrBlank()) {
            return userKey.trim()
        }
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Streams or generates content from Gemini model with multi-turn & multimodal vision support
     */
    suspend fun streamGenerate(
        modelName: String,
        apiKey: String,
        systemInstruction: String,
        messagesHistory: List<Pair<String, String>>, // (role, content)
        imageBase64: String? = null,
        imageMimeType: String? = "image/jpeg",
        onTokenDelta: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetModel = when {
            modelName.contains("pro", ignoreCase = true) -> "gemini-3.1-pro-preview"
            else -> "gemini-3.5-flash"
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API Key provided. Set GEMINI_API_KEY in Secrets or Gateway Settings."))
        }

        val url = "$BASE_URL$targetModel:streamGenerateContent?key=$apiKey&alt=sse"

        try {
            val rootJson = JSONObject()

            // System instructions
            if (systemInstruction.isNotBlank()) {
                val sysContent = JSONObject()
                val parts = JSONArray().put(JSONObject().put("text", systemInstruction))
                sysContent.put("parts", parts)
                rootJson.put("systemInstruction", sysContent)
            }

            // Contents array
            val contentsArr = JSONArray()
            val historySize = messagesHistory.size
            for (i in 0 until historySize) {
                val (role, text) = messagesHistory[i]
                if (text.isBlank() && (i < historySize - 1 || imageBase64.isNullOrBlank())) continue
                
                val geminiRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                val cObj = JSONObject()
                cObj.put("role", geminiRole)
                val parts = JSONArray()
                
                // If last user message and image is attached, append inlineData part
                if (i == historySize - 1 && geminiRole == "user" && !imageBase64.isNullOrBlank()) {
                    val inlineData = JSONObject().apply {
                        put("mimeType", imageMimeType ?: "image/jpeg")
                        put("data", imageBase64)
                    }
                    parts.put(JSONObject().put("inlineData", inlineData))
                }
                
                if (text.isNotBlank()) {
                    parts.put(JSONObject().put("text", text))
                }
                cObj.put("parts", parts)
                contentsArr.put(cObj)
            }
            rootJson.put("contents", contentsArr)

            // Generation config
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.7)
            genConfig.put("topP", 0.95)
            rootJson.put("generationConfig", genConfig)

            val body = rootJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API error (${response.code}): $errBody")
                return@withContext Result.failure(RuntimeException("Gemini API error (${response.code}): $errBody"))
            }

            val fullResponseBuffer = StringBuilder()
            response.body?.byteStream()?.bufferedReader()?.use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val jsonStr = currentLine.removePrefix("data: ").trim()
                        if (jsonStr.isNotBlank()) {
                            try {
                                val chunkJson = JSONObject(jsonStr)
                                val candidates = chunkJson.optJSONArray("candidates")
                                val firstCand = candidates?.optJSONObject(0)
                                val content = firstCand?.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                val text = parts?.optJSONObject(0)?.optString("text", "")
                                if (!text.isNullOrEmpty()) {
                                    fullResponseBuffer.append(text)
                                    onTokenDelta(text)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing SSE chunk: $jsonStr", e)
                            }
                        }
                    }
                }
            }

            return@withContext Result.success(fullResponseBuffer.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Gemini direct call failed", e)
            return@withContext Result.failure(e)
        }
    }
}
