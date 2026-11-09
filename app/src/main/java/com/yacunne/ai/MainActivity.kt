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
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

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
                                val reply = askGeminiWithFallback(messages.toList())
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
