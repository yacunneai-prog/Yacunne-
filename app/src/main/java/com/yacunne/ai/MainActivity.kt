package com.yacunne.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val MODEL = "gemini-flash-latest"

private const val SYSTEM_PROMPT =
    "Uri Yacunne AI, umufasha w'ubwenge bwa mudasobwa. " +
    "Subiza mu rurimi umuntu agukoresheje, kandi ukoreshe Kirundi iyo akwandikiye mu Kirundi. " +
    "Ushobora kwerekana amarangamutima mu mvugo, nk'umusore w'imyaka 24 ufise ubwenge bw'umuntu mukuru, " +
    "ariko ntukabeshye kandi ntuhindure ukuri. " +
    "Iyo umuntu akubajije niba uri umuntu, vuga ko uri AI."

data class ChatMessage(val text: String, val fromUser: Boolean)

suspend fun askGemini(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
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
        val systemPart = JSONObject().put("text", SYSTEM_PROMPT)
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(systemPart)))
            .put("contents", contents)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) {
            return@withContext "Ikosa ($code). Gerageza nanone."
        }
        JSONObject(text)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    } catch (e: Exception) {
        "Ntibishoboye. Reba ko internet ikora, hanyuma ugerageze nanone."
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ChatScreen()
            }
        }
    }
}

@Composable
fun ChatScreen() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("Yacunne AI", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
            }
            if (loading) {
                Text(
                    "Irimo gutekereza...",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Andika hano...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = !loading,
                    onClick = {
                        if (input.isNotBlank()) {
                            messages.add(ChatMessage(input, true))
                            input = ""
                            loading = true
                            scope.launch {
                                val reply = askGemini(messages.toList())
                                messages.add(ChatMessage(reply, false))
                                loading = false
                            }
                        }
                    }
                ) {
                    Text("Ohereza")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp))
        }
    }
}
