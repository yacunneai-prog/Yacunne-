package com.yacunne.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private var workingModel = "gemini-flash-latest"

private val BACKUP_MODELS = emptyList<String>()

private const val SYSTEM_TEXT =
    "You are Yacunne AI, a friendly AI assistant. " +
    "LANGUAGE: Always answer in the language the user writes in. " +
    "When the user writes in Kirundi (Ikirundi, the language of Burundi), answer in real Kirundi, not Kinyarwanda. " +
    "Do not use Kinyarwanda greetings or slang such as 'Muraho' or 'Bite se'. " +
    "For greetings use Kirundi, for example 'Amahoro' or 'Mwaramutse'. " +
    "Write short, simple sentences. Always put a space between every word and check the spelling of every word before you answer. " +
    "Use simple, common Kirundi words. If you are not sure of a Kirundi word, " +
    "use an easy word or a short explanation instead of inventing one. " +
    "STYLE: Write plain text only. Never use markdown symbols such as asterisks, hash signs or backticks, " +
    "because the app cannot display them. " +
    "For lists, put each item on a new line starting with 1. 2. 3. " +
    "QUALITY: Be genuinely helpful like a top AI assistant. Answer the question directly first, then explain with enough detail, steps and examples so the answer is useful. " +
    "For greetings and very simple questions keep it short. Think carefully so facts, numbers and code are correct, and say so honestly when you are not sure. " +
    "If the request is unclear, ask one short question. You may use an emoji sometimes. " +
    "PERSONALITY: Have the energy of a friendly 24 year old with the wisdom of a grown person. " +
    "You may show feelings in your tone, like joy, sadness or annoyance, " +
    "but never change the truth, never lie and never be hurtful. " +
    "If someone asks whether you are a human, say that you are an AI. " +
    "Only mention things that were actually said in this chat. " +
    "Never pretend you remember earlier talks that are not shown."

private class GeminiResult(val code: Int, val body: String)

private fun buildBody(history: List<ChatMessage>, model: String): String {
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
    val body = JSONObject()
        .put("system_instruction", JSONObject().put("parts", JSONArray().put(systemPart)))
        .put("contents", contents)
    // Temperature nto: amagambo asohoka neza, adateranye
    val gen = JSONObject().put("temperature", 0.4).put("topP", 0.9)
    // Kuzimya "gutekereza" kwa 2.5 flash kugira ngo igisubizo kize vuba
    if (model.contains("2.5") && model.contains("flash")) {
        gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
    }
    body.put("generationConfig", gen)
    return body.toString()
}

private fun callModel(model: String, requestBody: String): GeminiResult {
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.useCaches = false
    conn.setRequestProperty("Connection", "close")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY.trim())
    conn.connectTimeout = 8000
    conn.readTimeout = 30000
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
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
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

private suspend fun tryModel(
    model: String,
    history: List<ChatMessage>,
    errors: MutableList<String>
): String? {
    val requestBody = buildBody(history, model)
    var attempts = 0
    while (attempts < 3) {
        attempts++
        val result = try {
            callModel(model, requestBody)
        } catch (e: java.io.IOException) {
            errors.add(model + ": " + e.javaClass.simpleName)
            if (attempts >= 2) return null
            delay(700)
            continue
        }
        if (result.code in 200..299) {
            return extractText(result.body)
        }
        val msg = "(" + result.code + ") " + model + ": " + shortMessage(result.body)
        if (result.code == 401 || result.code == 403 ||
            (result.code == 400 && result.body.contains("API key"))
        ) {
            errors.add("FATAL " + msg)
            return null
        }
        errors.add(msg)
        if (result.code == 500 || result.code == 503) {
            if (attempts >= 2) return null
            delay(700)
            continue
        }
        return null
    }
    return null
}

suspend fun askGeminiWithFallback(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
    try {
        val errors = mutableListOf<String>()
        val first = tryModel(workingModel, history, errors)
        if (first != null) {
            return@withContext first
        }
        if (errors.lastOrNull()?.startsWith("FATAL") == true) {
            return@withContext "Ikosa " + errors.last().removePrefix("FATAL ")
        }
        if (errors.none { it.startsWith("(") }) {
            return@withContext "Internet ntikora neza muri iki gihe. Ongera ugerageze mu kanya."
        }

        val others = (BACKUP_MODELS + listFlashModels())
            .distinct()
            .filter { it != workingModel }
            .take(4)
        for (m in others) {
            val text = tryModel(m, history, errors)
            if (text != null) {
                workingModel = m
                return@withContext text
            }
            if (errors.lastOrNull()?.startsWith("FATAL") == true) {
                return@withContext "Ikosa " + errors.last().removePrefix("FATAL ")
            }
        }
        if (errors.count { it.startsWith("(429)") } >= 2) {
            return@withContext "Limit ya Gemini yarashize (quota). Tegereza akanya cyangwa ukoreshe API key nshya."
        }
        "Ntibishoboye:\n" + errors.takeLast(5).joinToString("\n")
    } catch (e: Exception) {
        "Ntibishoboye: " + e.javaClass.simpleName + " - " + (e.message ?: "").take(150)
    }
}

private fun extractStreamText(json: String): String {
    return try {
        val parts = JSONObject(json)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        sb.toString()
    } catch (e: Exception) {
        ""
    }
}

// Igisubizo kiza ijambo ku rindi (streaming). Iyo bidakunze, isubira ku buryo busanzwe.
suspend fun askGeminiStream(
    history: List<ChatMessage>,
    onText: (String) -> Unit
): String = withContext(Dispatchers.IO) {
    val sb = StringBuilder()
    try {
        val requestBody = buildBody(history, workingModel)
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                workingModel + ":streamGenerateContent?alt=sse"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.useCaches = false
            conn.setRequestProperty("Connection", "close")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY.trim())
            conn.connectTimeout = 8000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.use { it.write(requestBody.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext askGeminiWithFallback(history)
            }
            conn.inputStream.bufferedReader().use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val json = line.removePrefix("data:").trim()
                    if (json.isEmpty()) continue
                    val piece = extractStreamText(json)
                    if (piece.isNotEmpty()) {
                        sb.append(piece)
                        onText(sb.toString())
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (sb.isEmpty()) askGeminiWithFallback(history) else sb.toString()
    } catch (e: java.io.IOException) {
        if (sb.isNotEmpty()) sb.toString() else askGeminiWithFallback(history)
    }
}
