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
private val DeepNavy = Color(0xFF050A1F)
private val MidNavy = Color(0xFF0B1140)
private val DeepViolet = Color(0xFF1A0B3B)
private val BubbleDark = Color(0xFF0F1535)
private val BubbleBorder = Color(0xFF3F4BB8)
private val SoftText = Color(0xFFB8BEE8)
private val Gold = Color(0xFFF5B63D)

private val NeonColors = darkColorScheme(
    primary = NeonPurple,
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
        job?.cancel()
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
        if (messages.isEmpty()) return
        val body = messages.joinToString("\n\n") {
            (if (it.fromUser) "Wowe: " else "Yacunne AI: ") + it.text
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Sangiza ikiganiro"))
    }

    fun sendText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || loading) return
        val convId = currentId
        addMessage(convId, ChatMessage(text, true))
        persist()
        input = ""
        loading = true
        val history = conversations.first { it.id == convId }.messages
        job = scope.launch {
            var shown = ""
            try {
                val full = askGeminiWithFallback(history)
                var i = 0
                while (i < full.length) {
                    i = minOf(full.length, i + 3)
                    shown = full.substring(0, i)
                    streaming = shown
                    delay(14)
                }
            } finally {
                if (shown.isNotEmpty()) {
                    addMessage(convId, ChatMessage(shown, false))
                }
                streaming = null
                loading = false
                job = null
                persist()
            }
        }
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
                .background(Brush.verticalGradient(listOf(DeepNavy, MidNavy, DeepViolet)))
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonBlue.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(0f, size.height * 0.12f),
                            radius = size.width * 0.9f
                        ),
                        radius = size.width * 0.9f,
                        center = Offset(0f, size.height * 0.12f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonPurple.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(size.width, size.height * 0.85f),
                            radius = size.width * 0.9f
                        ),
                        radius = size.width * 0.9f,
                        center = Offset(size.width, size.height * 0.85f)
                    )
                }
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
                                onCopy = { clipboard.setText(AnnotatedString(it)) }
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
            LogoY(36)
            Spacer(modifier = Modifier.width(10.dp))
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
                .background(Brush.linearGradient(listOf(NeonPurple, NeonBlue)))
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
                        .background(if (selected) Color(0xFF1A2154) else Color.Transparent)
                        .border(1.dp, if (selected) NeonPurple else Color.Transparent, shape)
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
        LogoY(72)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Amahoro! \uD83D\uDC4B",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text("Nagufasha iki uyu munsi?", color = SoftText, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(24.dp))
        suggestions.forEach { s ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(shape)
                    .background(BubbleDark)
                    .border(1.dp, BubbleBorder, shape)
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
            .background(Color(0xFF0D1233))
            .border(2.dp, Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)), barShape)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A2154))
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Ongeraho", tint = Color.White)
        }
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            maxLines = 4,
            placeholder = {
                Text(
                    "Andika hano...",
                    color = Color(0xFF8E94C9),
                    fontStyle = FontStyle.Italic
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = NeonPurple
            )
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .alpha(if (!loading && input.isBlank()) 0.5f else 1f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeonPurple, NeonBlue)))
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
fun LogoY(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(NeonBlue, NeonPurple)))
            .border(1.5.dp, Color(0xFF6366F1), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Y",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size * 0.5f).sp
        )
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
                    modifier = Modifier.background(Color(0xFF0D1233))
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
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, NeonPurple, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))))
            .border(1.5.dp, Color(0xFF6366F1), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
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
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            LogoY(38)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Bubble(message.text, false)
                if (onCopy != null) {
                    Text(
                        "Copy",
                        color = SoftText,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp, start = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCopy(message.text) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Bubble(text: String, fromUser: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val fill = if (fromUser) {
        Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF5B21B6)))
    } else {
        Brush.linearGradient(listOf(BubbleDark, BubbleDark))
    }
    val edge = if (fromUser) Color(0xFFA78BFA) else BubbleBorder
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.5.dp, edge, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        SelectionContainer {
            Text(text, color = Color(0xFFF1F2FF), fontSize = 16.sp)
        }
    }
}
