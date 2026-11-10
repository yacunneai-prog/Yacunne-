package com.yacunne.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

private val NeonPurple = Color(0xFF8B5CF6)
private val NeonBlue = Color(0xFF3B82F6)
private val DeepNavy = Color(0xFF0B0F1A)
private val MidNavy = Color(0xFF0B1140)
private val DeepViolet = Color(0xFF1A0B3B)
private val BubbleDark = Color(0xFF141A2B)
private val BubbleBorder = Color(0xFF2A3350)
private val SoftText = Color(0xFF9AA3C0)
private val Gold = Color(0xFFF5B63D)

private val NeonColors = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.White,
    background = DeepNavy,
    surface = DeepNavy,
    onBackground = Color(0xFFF1F2FF),
    onSurface = Color(0xFFF1F2FF)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = NeonColors) {
                ChatScreen()
            }
        }
    }
}

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val clipboard = LocalClipboardManager.current

    val conversations = remember {
        mutableStateListOf<Conversation>().apply { addAll(ChatStore.load(context)) }
    }
    var currentId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var runId by remember { mutableStateOf(0) }
    var runningConv by remember { mutableStateOf("") }

    val messages: List<ChatMessage> =
        conversations.firstOrNull { it.id == currentId }?.messages ?: emptyList()

    fun persist() {
        ChatStore.save(context, conversations.toList())
    }

    fun addMessage(convId: String, msg: ChatMessage) {
        val idx = conversations.indexOfFirst { it.id == convId }
        if (idx >= 0) {
            val c = conversations[idx]
            conversations[idx] = c.copy(messages = c.messages + msg)
        } else {
            conversations.add(0, Conversation(convId, msg.text.take(32), listOf(msg)))
        }
    }

    fun stopGenerating() {
        runId++
        job?.cancel()
        job = null
        val partial = streaming
        if (!partial.isNullOrEmpty() && conversations.any { it.id == runningConv }) {
            addMessage(runningConv, ChatMessage(partial, false))
            persist()
        }
        streaming = null
        loading = false
        errorMsg = null
    }

    fun newChat() {
        stopGenerating()
        currentId = UUID.randomUUID().toString()
        input = ""
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        if (id == currentId) {
            stopGenerating()
            currentId = UUID.randomUUID().toString()
        }
        persist()
    }

    fun clearAll() {
        stopGenerating()
        conversations.clear()
        currentId = UUID.randomUUID().toString()
        persist()
    }

    fun shareChat() {
        if (messages.isEmpty()) {
            android.widget.Toast.makeText(context, "Nta kiganiro kiriho", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val body = messages.joinToString("\n\n") {
            (if (it.fromUser) "Wowe: " else "Yacunne AI: ") + it.text
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Sangiza ikiganiro"))
    }

    fun ask(convId: String) {
        errorMsg = null
        loading = true
        runningConv = convId
        runId++
        val myRun = runId
        val history = conversations.first { it.id == convId }.messages
        job = scope.launch {
            val full = askGeminiWithFallback(history)
            if (myRun != runId) return@launch
            if (isErrorReply(full)) {
                errorMsg = full
                loading = false
                job = null
                return@launch
            }
            var i = 0
            while (i < full.length) {
                i = minOf(full.length, i + 3)
                streaming = full.substring(0, i)
                delay(14)
            }
            if (myRun == runId) {
                addMessage(convId, ChatMessage(full, false))
                streaming = null
                loading = false
                job = null
                persist()
            }
        }
    }

    fun sendText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || loading) return
        val convId = currentId
        addMessage(convId, ChatMessage(text, true))
        persist()
        input = ""
        ask(convId)
    }

    LaunchedEffect(currentId, messages.size, streaming?.length) {
        if (messages.isNotEmpty() || streaming != null) {
            listState.scrollBy(100000f)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = DeepNavy) {
                DrawerContent(
                    conversations = conversations,
                    currentId = currentId,
                    onNew = {
                        newChat()
                        scope.launch { drawerState.close() }
                    },
                    onOpen = { id ->
                        stopGenerating()
                        currentId = id
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { id -> deleteConversation(id) }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            ChatHeader(
                onMenu = { scope.launch { drawerState.open() } },
                onNewChat = { newChat() },
                onShare = { shareChat() },
                onDeleteChat = { deleteConversation(currentId) },
                onClearAll = { showClearConfirm = true },
                onAbout = { showAbout = true }
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && streaming == null) {
                    EmptyState(onPick = { sendText(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->
                            MessageRow(
                                message = message,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(it))
                                    android.widget.Toast.makeText(context, "Vyakoporowe", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        val live = streaming
                        if (live != null) {
                            item {
                                MessageRow(
                                    message = ChatMessage(live, false),
                                    onCopy = null
                                )
                            }
                        }
                    }
                }
            }
            if (loading && streaming == null) {
                Text(
                    "Irimo gutekereza...",
                    color = SoftText,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            val err = errorMsg
            if (err != null && !loading) {
                ErrorBanner(
                    text = err,
                    onRetry = { ask(currentId) },
                    onClose = { errorMsg = null }
                )
            }
            InputBar(
                input = input,
                onInputChange = { input = it },
                loading = loading,
                onSend = { sendText(input) },
                onStop = { stopGenerating() }
            )
            if (showAbout) {
                AlertDialog(
                    onDismissRequest = { showAbout = false },
                    confirmButton = {
                        TextButton(onClick = { showAbout = false }) { Text("Sawa") }
                    },
                    title = { Text("Yacunne AI") },
                    text = { Text("Umufasha wawe wa AI. Ibiganiro byawe bibikwa kuri telefone yawe.") }
                )
            }
            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearConfirm = false
                            clearAll()
                        }) { Text("Siba byose") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirm = false }) { Text("Reka") }
                    },
                    title = { Text("Gusiba amateka yose?") },
                    text = { Text("Ibiganiro byose bizasibwa kandi ntibishobora kugarurwa.") }
                )
            }
        }
    }
}

@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    currentId: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Yacunne AI",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NeonBlue)
                .clickable { onNew() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ikiganiro gishya", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text("Amateka", color = SoftText, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (conversations.isEmpty()) {
            Text(
                "Nta kiganiro kirabikwa.",
                color = SoftText,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(conversations, key = { it.id }) { c ->
                val selected = c.id == currentId
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (selected) Color(0xFF1B2440) else Color.Transparent)
                        .border(1.dp, if (selected) NeonBlue else Color.Transparent, shape)
                        .clickable { onOpen(c.id) }
                        .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        c.title,
                        color = Color(0xFFF1F2FF),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Siba",
                        tint = SoftText,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onDelete(c.id) }
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(onPick: (String) -> Unit) {
    val suggestions = listOf(
        "Mfasha kwandika ubutumwa",
        "Sobanura ikintu mu magambo make",
        "Mpa igitekerezo cy'umushinga",
        "Ndashaka kwiga Icongereza"
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Yacunne ",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "AI",
                color = NeonBlue,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = if (hour < 12) "Mwaramutse!" else "Mwiriwe!"
        Text(greeting + " Nagufasha iki uyu munsi?", color = SoftText, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(24.dp))
        suggestions.forEach { s ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(shape)
                    .background(BubbleDark)
                    .border(0.5.dp, BubbleBorder, shape)
                    .clickable { onPick(s) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(s, color = Color(0xFFF1F2FF), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    loading: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val barShape = RoundedCornerShape(32.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(barShape)
            .background(BubbleDark)
            .border(1.dp, BubbleBorder, barShape)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            maxLines = 4,
            placeholder = {
                Text(
                    "Andika hano...",
                    color = Color(0xFF7A839F)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = NeonBlue
            )
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .alpha(if (!loading && input.isBlank()) 0.4f else 1f)
                .clip(CircleShape)
                .background(NeonBlue)
                .clickable { if (loading) onStop() else onSend() },
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White, RoundedCornerShape(3.dp))
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Ohereza",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ChatHeader(
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onShare: () -> Unit,
    onDeleteChat: () -> Unit,
    onClearAll: () -> Unit,
    onAbout: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "Amateka",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onMenu() }
                    .padding(7.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Ikiganiro gishya",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onNewChat() }
                    .padding(7.dp)
            )
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Igenamiterere",
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true }
                        .padding(7.dp)
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(BubbleDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Sangiza ikiganiro", color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Siba ikiganiro", color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onDeleteChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Siba amateka yose", color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onClearAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ibijyanye na app", color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageRow(message: ChatMessage, onCopy: ((String) -> Unit)?) {
    if (message.fromUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            Bubble(message.text, true, Modifier.weight(1f, fill = false))
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Bubble(message.text, false)
            if (onCopy != null) {
                Text(
                    "Copy",
                    color = SoftText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCopy(message.text) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun Bubble(text: String, fromUser: Boolean, modifier: Modifier = Modifier) {
    if (fromUser) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color(0xFF1E3A8A))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(text, color = Color(0xFFF1F2FF), fontSize = 16.sp)
            }
        }
    } else {
        Box(modifier = modifier) {
            SelectionContainer {
                Text(
                    text,
                    color = Color(0xFFE8EAF6),
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

private fun isErrorReply(text: String): Boolean {
    return text.startsWith("Ikosa ") ||
        text.startsWith("Internet ntikora") ||
        text.startsWith("Ntibishoboye") ||
        text.startsWith("Limit ya Gemini") ||
        text.startsWith("Gemini ntiyashoboye")
}

@Composable
fun ErrorBanner(text: String, onRetry: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(Color(0xFF2A1620))
            .border(0.5.dp, Color(0xFF7F2D45), shape)
            .padding(12.dp)
    ) {
        Text(text, color = Color(0xFFFFB4C2), fontSize = 14.sp, maxLines = 4)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("Funga", color = SoftText) }
            TextButton(onClick = onRetry) { Text("Ongera ugerageze", color = NeonBlue) }
        }
    }
}
