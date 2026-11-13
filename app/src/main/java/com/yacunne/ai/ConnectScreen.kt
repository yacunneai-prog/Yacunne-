package com.yacunne.ai

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------
// Ibikwa kuri telefone: key z'izindi AI n'iyo ikoreshwa ubu
// ---------------------------------------------------------------
object ConnectStore {
    private const val PREFS = "yacunne_connect"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun key(c: Context, id: String): String = p(c).getString("key_" + id, "") ?: ""

    fun model(c: Context, id: String): String = p(c).getString("model_" + id, "") ?: ""

    fun active(c: Context): String = p(c).getString("active", "yacunne") ?: "yacunne"

    fun setActive(c: Context, id: String) {
        p(c).edit().putString("active", id).apply()
    }

    fun save(c: Context, id: String, key: String, model: String) {
        p(c).edit()
            .putString("key_" + id, key.trim())
            .putString("model_" + id, model.trim())
            .apply()
    }

    fun remove(c: Context, id: String) {
        val e = p(c).edit()
        e.remove("key_" + id)
        e.remove("model_" + id)
        if (active(c) == id) e.putString("active", "yacunne")
        e.apply()
    }
}

// ---------------------------------------------------------------
// Guhamagara izindi AI (ukoresheje key y'umukoresha)
// ---------------------------------------------------------------
fun defaultModel(id: String): String = when (id) {
    "openai" -> "gpt-4o-mini"
    "claude" -> "claude-haiku-5-5"
    "gemini" -> "gemini-flash-latest"
    "deepseek" -> "deepseek-chat"
    "grok" -> "grok-3-mini"
    "mistral" -> "mistral-small-latest"
    "cohere" -> "command-r-plus"
    else -> ""
}

private const val PROVIDER_SYSTEM =
    "You are Yacunne AI, a friendly and helpful AI assistant. " +
        "Always answer in the language the user writes in. " +
        "When the user writes in Kirundi (the language of Burundi), answer in real Kirundi, not Kinyarwanda, " +
        "with short simple sentences, a space between every word and careful spelling. " +
        "Write plain text only. Never use markdown symbols such as asterisks, hash signs or backticks. " +
        "For lists put each item on a new line starting with 1. 2. 3. " +
        "Answer the question directly first, then explain with enough detail to be useful. " +
        "Be honest when you are not sure. If someone asks whether you are a human, say that you are an AI."

private fun errText(body: String): String {
    return try {
        val o = JSONObject(body)
        val e = o.opt("error")
        val msg = when (e) {
            is JSONObject -> e.optString("message", "")
            is String -> e
            else -> o.optString("message", "")
        }
        (if (msg.isBlank()) body else msg).take(120)
    } catch (ex: Exception) {
        body.take(120)
    }
}

private fun postJson(urlStr: String, headers: Map<String, String>, body: String): Pair<Int, String> {
    val conn = URL(urlStr).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.useCaches = false
        conn.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        conn.connectTimeout = 10000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return Pair(code, text)
    } finally {
        conn.disconnect()
    }
}

suspend fun askProvider(
    id: String,
    apiKey: String,
    model: String,
    history: List<ChatMessage>,
    extra: String
): String = withContext(Dispatchers.IO) {
    val m = if (model.isBlank()) defaultModel(id) else model
    val system = if (extra.isBlank()) PROVIDER_SYSTEM else PROVIDER_SYSTEM + " " + extra.trim()
    val turns = history.takeLast(24)
        .map { Pair(if (it.fromUser) "user" else "assistant", it.text) }
        .dropWhile { it.first != "user" }
    try {
        val result: Pair<Int, String>
        val kind: String
        when (id) {
            "claude" -> {
                kind = "claude"
                val arr = JSONArray()
                for (t in turns) {
                    arr.put(JSONObject().put("role", t.first).put("content", t.second))
                }
                val body = JSONObject()
                    .put("model", m)
                    .put("max_tokens", 2048)
                    .put("system", system)
                    .put("messages", arr)
                result = postJson(
                    "https://api.anthropic.com/v1/messages",
                    mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                    body.toString()
                )
            }
            "gemini" -> {
                kind = "gemini"
                val arr = JSONArray()
                for (t in turns) {
                    val role = if (t.first == "user") "user" else "model"
                    arr.put(
                        JSONObject().put("role", role)
                            .put("parts", JSONArray().put(JSONObject().put("text", t.second)))
                    )
                }
                val body = JSONObject()
                    .put(
                        "system_instruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                    )
                    .put("contents", arr)
                result = postJson(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent",
                    mapOf("x-goog-api-key" to apiKey),
                    body.toString()
                )
            }
            else -> {
                kind = if (id == "cohere") "cohere" else "openai"
                val url = when (id) {
                    "deepseek" -> "https://api.deepseek.com/chat/completions"
                    "grok" -> "https://api.x.ai/v1/chat/completions"
                    "mistral" -> "https://api.mistral.ai/v1/chat/completions"
                    "cohere" -> "https://api.cohere.com/v2/chat"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                val arr = JSONArray()
                arr.put(JSONObject().put("role", "system").put("content", system))
                for (t in turns) {
                    arr.put(JSONObject().put("role", t.first).put("content", t.second))
                }
                val body = JSONObject().put("model", m).put("messages", arr)
                result = postJson(url, mapOf("Authorization" to "Bearer " + apiKey), body.toString())
            }
        }
        if (result.first !in 200..299) {
            return@withContext "Ikosa (" + result.first + ") " + id + ": " + errText(result.second)
        }
        val root = JSONObject(result.second)
        val text = when (kind) {
            "claude" -> {
                val parts = root.getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text", ""))
                }
                sb.toString()
            }
            "gemini" -> {
                val parts = root.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text", ""))
                }
                sb.toString()
            }
            "cohere" -> root.getJSONObject("message").getJSONArray("content")
                .getJSONObject(0).optString("text", "")
            else -> root.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "")
        }
        if (text.isBlank()) "Gemini ntiyashoboye gusubiza iki kibazo." else text
    } catch (e: java.io.IOException) {
        "Internet ntikora neza muri iki gihe. Ongera ugerageze mu kanya."
    } catch (e: Exception) {
        "Ikosa " + id + ": " + e.javaClass.simpleName
    }
}

// ---------------------------------------------------------------
// Ecran ya "Guhuza"
// ---------------------------------------------------------------
private data class ConnectProvider(
    val id: String,
    val name: String,
    val description: String,
    val symbol: String,
    val type: String
)

@Composable
fun ConnectScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var dialogFor by remember { mutableStateOf<ConnectProvider?>(null) }

    val providers = listOf(
        ConnectProvider("openai", "OpenAI", "ChatGPT models", "O", "AI"),
        ConnectProvider("claude", "Claude", "Anthropic AI models", "C", "AI"),
        ConnectProvider("gemini", "Gemini", "Google AI (your own key)", "G", "AI"),
        ConnectProvider("deepseek", "DeepSeek", "DeepSeek AI models", "D", "AI"),
        ConnectProvider("grok", "Grok", "xAI models", "X", "AI"),
        ConnectProvider("mistral", "Mistral", "Mistral AI models", "M", "AI"),
        ConnectProvider("cohere", "Cohere", "Cohere AI services", "C", "AI"),

        ConnectProvider("drive", "Google Drive", "Access your files", "G", "APP"),
        ConnectProvider("gmail", "Gmail", "Connect your email", "M", "APP"),
        ConnectProvider("calendar", "Google Calendar", "Access your calendar", "C", "APP"),
        ConnectProvider("github", "GitHub", "Connect repositories", "G", "APP"),
        ConnectProvider("dropbox", "Dropbox", "Cloud storage", "D", "APP"),
        ConnectProvider("onedrive", "OneDrive", "Microsoft cloud storage", "O", "APP"),
        ConnectProvider("notion", "Notion", "Connect your workspace", "N", "APP"),
        ConnectProvider("slack", "Slack", "Connect team workspace", "S", "APP")
    )

    val visibleProviders = providers.filter {
        it.type == if (selectedTab == 0) "APP" else "AI"
    }

    val colors = MaterialTheme.colorScheme
    val refresh = tick
    val activeId = ConnectStore.active(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ " + tx("back"), color = colors.onBackground)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                tx("connect_title"),
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground
            )
        }

        Spacer(Modifier.height(5.dp))

        Text(
            tx("connect_sub"),
            color = colors.onBackground.copy(alpha = 0.65f),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            listOf(tx("tab_apps"), tx("tab_ai")).forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTab == index) colors.primary.copy(alpha = 0.18f)
                            else Color.Transparent,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = colors.onBackground,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(bottom = 25.dp)
        ) {
            if (selectedTab == 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(15.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tx("default_ai"),
                                color = colors.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tx("default_ai_sub"),
                                color = colors.onBackground.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                ConnectStore.setActive(context, "yacunne")
                                tick++
                            },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                if (activeId == "yacunne") tx("btn_in_use") else tx("btn_use"),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            items(visibleProviders) { provider ->
                val connected = provider.type == "AI" && ConnectStore.key(context, provider.id).isNotBlank()
                val isActive = connected && activeId == provider.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(15.dp))
                        .clickable { dialogFor = provider }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(43.dp)
                            .background(
                                colors.primary.copy(alpha = 0.13f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            provider.symbol,
                            color = colors.onBackground,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            provider.name + (if (connected) "  ✓" else ""),
                            color = colors.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            provider.description,
                            color = colors.onBackground.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (connected && !isActive) {
                                ConnectStore.setActive(context, provider.id)
                                tick++
                            } else {
                                dialogFor = provider
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (isActive) tx("btn_in_use")
                            else if (connected) tx("btn_use")
                            else tx("btn_connect"),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    val provider = dialogFor
    if (provider != null) {
        if (provider.type == "AI") {
            var keyDraft by remember(provider.id) { mutableStateOf(ConnectStore.key(context, provider.id)) }
            var modelDraft by remember(provider.id) { mutableStateOf(ConnectStore.model(context, provider.id)) }
            val already = ConnectStore.key(context, provider.id).isNotBlank()
            AlertDialog(
                onDismissRequest = { dialogFor = null },
                title = { Text(tx("btn_connect") + " " + provider.name) },
                text = {
                    Column {
                        TextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            placeholder = { Text(tx("key_hint")) }
                        )
                        Spacer(Modifier.height(10.dp))
                        TextField(
                            value = modelDraft,
                            onValueChange = { modelDraft = it },
                            singleLine = true,
                            placeholder = { Text(defaultModel(provider.id)) }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(tx("model_hint"), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(tx("key_note"), fontSize = 12.sp)
                        if (already) {
                            TextButton(onClick = {
                                ConnectStore.remove(context, provider.id)
                                tick++
                                dialogFor = null
                            }) { Text(tx("btn_disconnect"), color = Color(0xFFDC2626)) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (keyDraft.isNotBlank()) {
                            ConnectStore.save(context, provider.id, keyDraft, modelDraft)
                            ConnectStore.setActive(context, provider.id)
                            tick++
                            dialogFor = null
                        }
                    }) { Text(tx("save")) }
                },
                dismissButton = {
                    TextButton(onClick = { dialogFor = null }) { Text(tx("cancel")) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { dialogFor = null },
                title = { Text(tx("btn_connect") + " " + provider.name) },
                text = { Text(tx("app_needs_server")) },
                confirmButton = {
                    TextButton(onClick = { dialogFor = null }) { Text(tx("ok")) }
                }
            )
        }
    }
}
