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

private val BACKUP_MODELS = listOf("gemini-flash-lite-latest")

// "Gutekereza" (thinking): tugerageza urwego ruto cyane kugira ngo igisubizo kize vuba.
// Iyo Gemini ivuze ko ritemewe, tuva ku rundi rwego tukagerageza irindi.
private val thinkIndex = java.util.concurrent.ConcurrentHashMap<String, Int>()

private fun thinkingFor(model: String): JSONObject? {
    val idx = thinkIndex[model] ?: 0
    return if (model.contains("pro")) {
        if (idx == 0) JSONObject().put("thinkingLevel", "low") else null
    } else {
        when (idx) {
            0 -> JSONObject().put("thinkingLevel", "minimal")
            1 -> JSONObject().put("thinkingBudget", 0)
            else -> null
        }
    }
}

private fun downgradeThinking(model: String, code: Int, body: String): Boolean {
    if (code != 400) return false
    if (!body.lowercase().contains("thinking")) return false
    val idx = thinkIndex[model] ?: 0
    val max = if (model.contains("pro")) 1 else 2
    if (idx >= max) return false
    thinkIndex[model] = idx + 1
    return true
}

// Hitamwo ubwenge bwa Yacunne: "pro" = bwinshi, ikindi cyose = bwihuta
fun setPreferredModel(key: String) {
    workingModel = if (key == "pro") "gemini-pro-latest" else "gemini-flash-latest"
}

private const val SYSTEM_TEXT =
    "You are Yacunne AI, a friendly AI assistant. " +
    "LANGUAGE (the most important rule): Reply in the same language as the user's latest message. " +
    "If the user writes in English, reply in English. If in French, reply in French. If in Swahili, reply in Swahili. " +
    "If in Kirundi, reply in Kirundi. If in any other language, reply in that language. " +
    "If the user asks you to switch language, switch immediately and keep it. " +
    "Only when the message is too short to tell the language (for example one emoji), use the language of the earlier messages. " +
    "Never answer in Kirundi to a user who did not write in Kirundi. " +
    "KIRUNDI RULES (apply only when the user writes in Kirundi): answer in real Kirundi, not Kinyarwanda. " +
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

private fun buildBody(history: List<ChatMessage>, model: String, extra: String): String {
    // Twohereza amajambo 24 ya nyuma gusa kugira ngo igisubizo kidatinda mu biganiro birebire
    var recent: List<ChatMessage> = if (history.size > 24) history.takeLast(24) else history
    while (recent.isNotEmpty() && !recent.first().fromUser) {
        recent = recent.drop(1)
    }
    val contents = JSONArray()
    for (m in recent) {
        val part = JSONObject().put("text", m.text)
        contents.put(
            JSONObject()
                .put("role", if (m.fromUser) "user" else "model")
                .put("parts", JSONArray().put(part))
        )
    }
    val systemText = if (extra.isBlank()) SYSTEM_TEXT else SYSTEM_TEXT + " " + extra.trim()
    val systemPart = JSONObject().put("text", systemText)
    val body = JSONObject()
        .put("system_instruction", JSONObject().put("parts", JSONArray().put(systemPart)))
        .put("contents", contents)
    // Temperature nto: amagambo asohoka neza, adateranye
    val gen = JSONObject().put("temperature", 0.4).put("topP", 0.9)
    val thinking = thinkingFor(model)
    if (thinking != null) {
        gen.put("thinkingConfig", thinking)
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
    errors: MutableList<String>,
    extra: String
): String? {
    var requestBody = buildBody(history, model, extra)
    var attempts = 0
    var downgrades = 0
    while (attempts < 5) {
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
        if (downgrades < 2 && downgradeThinking(model, result.code, result.body)) {
            downgrades++
            requestBody = buildBody(history, model, extra)
            continue
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

suspend fun askGeminiWithFallback(
    history: List<ChatMessage>,
    extra: String = "",
    avoid: String = ""
): String = withContext(Dispatchers.IO) {
    try {
        val errors = mutableListOf<String>()
        val first = if (avoid.isEmpty()) tryModel(workingModel, history, errors, extra) else null
        if (first != null) {
            return@withContext first
        }
        if (errors.lastOrNull()?.startsWith("FATAL") == true) {
            return@withContext "Ikosa " + errors.last().removePrefix("FATAL ")
        }
        if (avoid.isEmpty() && errors.none { it.startsWith("(") }) {
            return@withContext "Internet ntikora neza muri iki gihe. Ongera ugerageze mu kanya."
        }

        val others = (BACKUP_MODELS + listFlashModels())
            .distinct()
            .filter { it != workingModel && it != avoid }
            .take(3)
        for (m in others) {
            val text = tryModel(m, history, errors, extra)
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

private fun openStreamConn(model: String, requestBody: String): HttpURLConnection {
    val url = URL(
        "https://generativelanguage.googleapis.com/v1beta/models/" +
            model + ":streamGenerateContent?alt=sse"
    )
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.useCaches = false
    conn.setRequestProperty("Connection", "close")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY.trim())
    conn.connectTimeout = 8000
    conn.readTimeout = 25000
    conn.doOutput = true
    conn.outputStream.use { it.write(requestBody.toByteArray()) }
    return conn
}

// Igisubizo kiza ijambo ku rindi (streaming). Iyo bidakunze, isubira ku buryo busanzwe.
suspend fun askGeminiStream(
    history: List<ChatMessage>,
    extra: String,
    onText: (String) -> Unit
): String = withContext(Dispatchers.IO) {
    val sb = StringBuilder()
    val model = workingModel
    try {
        var conn: HttpURLConnection? = null
        var tries = 0
        while (conn == null) {
            tries++
            val c = openStreamConn(model, buildBody(history, model, extra))
            val code = c.responseCode
            if (code in 200..299) {
                conn = c
            } else {
                val errBody = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()
                if (tries < 3 && downgradeThinking(model, code, errBody)) continue
                return@withContext askGeminiWithFallback(history, extra)
            }
        }
        val live: HttpURLConnection = conn ?: return@withContext askGeminiWithFallback(history, extra)
        try {
            live.inputStream.bufferedReader().use { reader ->
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
            live.disconnect()
        }
        if (sb.isEmpty()) askGeminiWithFallback(history, extra) else sb.toString()
    } catch (e: java.net.SocketTimeoutException) {
        // Model yatinze: tugerageze indi model aho gusubiramo iyo imwe
        if (sb.isNotEmpty()) sb.toString() else askGeminiWithFallback(history, extra, model)
    } catch (e: java.io.IOException) {
        if (sb.isNotEmpty()) sb.toString() else askGeminiWithFallback(history, extra)
    }
}
