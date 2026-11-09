package com.yacunne.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val BACKUP_MODELS = listOf("gemini-2.5-flash")

private const val SYSTEM_TEXT =
    "Uri Yacunne AI, umufasha w'ubwenge bwa mudasobwa. " +
    "Subiza mu rurimi umuntu agukoresheje, kandi ukoreshe Kirundi iyo akwandikiye mu Kirundi. " +
    "Ushobora kwerekana amarangamutima mu mvugo, nk'umusore w'imyaka 24 ufise ubwenge bw'umuntu mukuru, " +
    "ariko ntukabeshye kandi ntuhindure ukuri. " +
    "Iyo umuntu akubajije niba uri umuntu, vuga ko uri AI."

private class GeminiResult(val code: Int, val body: String)

private fun callModel(model: String, requestBody: String): GeminiResult {
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.useCaches = false
    conn.setRequestProperty("Connection", "close")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY.trim())
    conn.connectTimeout = 20000
    conn.readTimeout = 60000
    conn.doOutput = true
    try {
        conn.outputStream.use { it.write(requestBody.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return GeminiResult(code, text)
    } finally {
        conn.disconnect()
    }
}

private fun listFlashModels(): List<String> {
    return try {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY.trim())
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val arr = JSONObject(body).getJSONArray("models")
        val skip = listOf("image", "tts", "live", "audio", "robotics", "computer", "embedding", "thinking", "exp")
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val name = item.getString("name").removePrefix("models/")
            val methods = item.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            if (!methods.contains("generateContent")) continue
            if (!name.contains("flash")) continue
            if (skip.any { name.contains(it) }) continue
            names.add(name)
        }
        names.sortedDescending()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun shortMessage(body: String): String {
    return try {
        JSONObject(body).getJSONObject("error").getString("message").take(70)
    } catch (e: Exception) {
        body.take(70)
    }
}

private fun extractText(body: String): String {
    return try {
        JSONObject(body)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    } catch (e: Exception) {
        "Gemini ntiyashoboye gusubiza iki kibazo."
    }
}

suspend fun askGeminiWithFallback(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
    try {
        val contents = JSONArray()
        for (m in history) {
            val part = JSONObject().put("text", m.text)
            contents.put(
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(part))
            )
        }
        val systemPart = JSONObject().put("text", SYSTEM_TEXT)
        val requestBody = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(systemPart)))
            .put("contents", contents)
            .toString()

        val models = (listOf("gemini-flash-latest") + listFlashModels() + BACKUP_MODELS)
            .distinct()
            .take(6)

        val errors = mutableListOf<String>()
        for (model in models) {
            for (attempt in 1..2) {
                val result = try {
                    callModel(model, requestBody)
                } catch (e: java.io.IOException) {
                    errors.add(model + ": " + e.javaClass.simpleName)
                    null
                }
                if (result == null) {
                    delay(1000)
                    continue
                }
                if (result.code in 200..299) {
                    return@withContext extractText(result.body)
                }
                errors.add("(" + result.code + ") " + model + ": " + shortMessage(result.body))
                if (result.code == 401 || result.code == 403) {
                    return@withContext "Ikosa " + errors.last()
                }
                if (result.code == 400 && result.body.contains("API key")) {
                    return@withContext "Ikosa " + errors.last()
                }
                if (result.code == 400 || result.code == 404 || result.code == 429) break
                delay(1000)
            }
        }
        "Ntibishoboye:\n" + errors.takeLast(6).joinToString("\n")
    } catch (e: Exception) {
        "Ntibishoboye: " + e.javaClass.simpleName + " - " + (e.message ?: "").take(150)
    }
}
