package com.yacunne.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val MODEL_LIST = listOf(
    "gemini-flash-latest",
    "gemini-flash-lite-latest",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite"
)

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

private fun testGoogle(): String {
    return try {
        val conn = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val code = conn.responseCode
        conn.disconnect()
        "google.com OK (" + code + ")"
    } catch (e: Exception) {
        "google.com: " + e.javaClass.simpleName
    }
}

private fun shortMessage(body: String): String {
    return try {
        JSONObject(body).getJSONObject("error").getString("message").take(120)
    } catch (e: Exception) {
        body.take(120)
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

        var lastError = ""
        for (model in MODEL_LIST) {
            for (attempt in 1..3) {
                val result = try {
                    callModel(model, requestBody)
                } catch (e: java.io.IOException) {
                    lastError = e.javaClass.simpleName + ": " + (e.message ?: "").take(80)
                    null
                }
                if (result == null) {
                    delay(1500)
                    continue
                }
                if (result.code in 200..299) {
                    return@withContext extractText(result.body)
                }
                lastError = "(" + result.code + ") " + model + ": " + shortMessage(result.body)
                if (result.code == 400 || result.code == 401 || result.code == 403) {
                    return@withContext "Ikosa " + lastError
                }
                if (result.code == 404) break
                delay(1500)
            }
        }
        "Ntibishoboye. " + lastError + " | " + testGoogle()
    } catch (e: Exception) {
        "Ntibishoboye: " + e.javaClass.simpleName + " - " + (e.message ?: "").take(150)
    }
}
