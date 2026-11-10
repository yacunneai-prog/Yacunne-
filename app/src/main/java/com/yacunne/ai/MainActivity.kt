package com.yacunne.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.vector.ImageVector
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
private val BubbleDark = Color(0xFF1C2540)
private val BubbleBorder = Color(0xFF3D4A75)
private val SoftText = Color(0xFFBCC4E0)
private val Gold = Color(0xFFF5B63D)
private val DrawerBg = Color(0xFFF5F4EF)
private val DrawerText = Color(0xFF1B1B1F)
private val DrawerMuted = Color(0xFF6B6B73)
private val DrawerLine = Color(0xFFDAD8CF)
private val DrawerSel = Color(0xFFE6E4DB)
private val DrawerAccent = Color(0xFF2563EB)

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
    var userName by remember { mutableStateOf(ProfileStore.load(context)) }
    val projects = remember {
        mutableStateListOf<Project>().apply { addAll(ProjectStore.load(context)) }
    }
    var activeProjectId by remember { mutableStateOf("") }
    var newMode by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<String?>(null) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    val textSize = when (settings.fontSize) {
        0 -> 14
        2 -> 18
        else -> 16
    }
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
            val title = if (newMode.isNotEmpty()) modeTitle(newMode) else msg.text.take(32)
            conversations.add(
                0,
                Conversation(convId, title, listOf(msg), false, activeProjectId, newMode)
            )
        }
    }

    fun toggleStar(id: String) {
        val idx = conversations.indexOfFirst { it.id == id }
        if (idx >= 0) {
            conversations[idx] = conversations[idx].copy(starred = !conversations[idx].starred)
            persist()
        }
    }

    fun moveToProject(id: String, pid: String) {
        val idx = conversations.indexOfFirst { it.id == id }
        if (idx >= 0) {
            conversations[idx] = conversations[idx].copy(projectId = pid)
            persist()
        }
    }

    fun renameConv(id: String, title: String) {
        val idx = conversations.indexOfFirst { it.id == id }
        if (idx >= 0) {
            conversations[idx] = conversations[idx].copy(title = title)
            persist()
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
        newMode = ""
        input = ""
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        if (id == currentId) {
            stopGenerating()
            currentId = UUID.randomUUID().toString()
            newMode = ""
        }
        persist()
    }

    fun clearAll() {
        stopGenerating()
        conversations.clear()
        currentId = UUID.randomUUID().toString()
        newMode = ""
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
        val conv = conversations.first { it.id == convId }
        val history = conv.messages
        val extra = buildExtra(
            conv.mode,
            projects.firstOrNull { it.id == conv.projectId }?.instructions ?: "",
            settings
        )
        job = scope.launch {
            val target = java.util.concurrent.atomic.AtomicReference("")
            val result = java.util.concurrent.atomic.AtomicReference("")
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            launch {
                val r = askGeminiStream(history, extra) { target.set(it) }
                if (!isErrorReply(r)) target.set(r)
                result.set(r)
                done.set(true)
            }
            var shown = 0
            while (true) {
                val t = target.get()
                if (shown < t.length) {
                    val backlog = t.length - shown
                    val step = if (backlog > 200) 8 else if (backlog > 60) 4 else 2
                    shown = minOf(t.length, shown + step)
                    if (myRun == runId) streaming = t.substring(0, shown)
                    delay(16)
                } else if (done.get()) {
                    break
                } else {
                    delay(30)
                }
            }
            if (myRun != runId) return@launch
            val full = result.get()
            if (isErrorReply(full)) {
                errorMsg = full
                streaming = null
                loading = false
                job = null
                return@launch
            }
            addMessage(convId, ChatMessage(full, false))
            streaming = null
            loading = false
            job = null
            persist()
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

    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = DrawerBg) {
                DrawerContent(
                    conversations = conversations,
                    projects = projects,
                    currentId = currentId,
                    activeProjectId = activeProjectId,
                    onNew = {
                        newChat()
                        scope.launch { drawerState.close() }
                    },
                    onOpen = { id ->
                        stopGenerating()
                        currentId = id
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { id -> deleteConversation(id) },
                    onToggleStar = { id -> toggleStar(id) },
                    onRename = { id -> renameId = id },
                    onMove = { id, pid -> moveToProject(id, pid) },
                    onSelectProject = { id ->
                        activeProjectId = if (activeProjectId == id) "" else id
                    },
                    onNewProject = {
                        editingProject = null
                        showProjectDialog = true
                    },
                    onEditProject = { p ->
                        editingProject = p
                        showProjectDialog = true
                    },
                    onSettings = {
                        showSettings = true
                        scope.launch { drawerState.close() }
                    },
                    onStart = { mode ->
                        newChat()
                        newMode = mode
                        sendText(modePrompt(mode))
                        scope.launch { drawerState.close() }
                    }
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
                    EmptyState(userName = userName, onPick = { sendText(it) })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->
                            MessageRow(
                                textSize = textSize,
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
                                    textSize = textSize,
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
            if (showProfile) {
                var draft by remember { mutableStateOf(userName) }
                AlertDialog(
                    onDismissRequest = { showProfile = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val n = draft.trim().take(30)
                            ProfileStore.save(context, n)
                            userName = n
                            showProfile = false
                        }) { Text("Bika") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showProfile = false }) { Text("Reka") }
                    },
                    title = { Text("Konti yawe") },
                    text = {
                        Column {
                            Text("Andika izina ryawe. Ibiganiro byawe ntibizosibwa.")
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = draft,
                                onValueChange = { draft = it },
                                singleLine = true,
                                placeholder = { Text("Izina ryawe") }
                            )
                        }
                    }
                )
            }
            if (showProjectDialog) {
                val editing = editingProject
                var nameDraft by remember { mutableStateOf(editing?.name ?: "") }
                var instrDraft by remember { mutableStateOf(editing?.instructions ?: "") }
                AlertDialog(
                    onDismissRequest = { showProjectDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val n = nameDraft.trim().take(30)
                            if (n.isNotEmpty()) {
                                val instr = instrDraft.trim().take(600)
                                if (editing == null) {
                                    projects.add(Project(UUID.randomUUID().toString(), n, instr))
                                } else {
                                    val i = projects.indexOfFirst { it.id == editing.id }
                                    if (i >= 0) projects[i] = editing.copy(name = n, instructions = instr)
                                }
                                ProjectStore.save(context, projects.toList())
                                showProjectDialog = false
                            }
                        }) { Text("Bika") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showProjectDialog = false }) { Text("Reka") }
                    },
                    title = { Text(if (editing == null) "Umushinga mushasha" else "Hindura umushinga") },
                    text = {
                        Column {
                            TextField(
                                value = nameDraft,
                                onValueChange = { nameDraft = it },
                                singleLine = true,
                                placeholder = { Text("Izina ry'umushinga") }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            TextField(
                                value = instrDraft,
                                onValueChange = { instrDraft = it },
                                maxLines = 5,
                                placeholder = { Text("Amabwiriza ya Yacunne kuri uyu mushinga (si ngombwa)") }
                            )
                            if (editing != null) {
                                TextButton(onClick = {
                                    projects.removeAll { it.id == editing.id }
                                    for (i in conversations.indices) {
                                        if (conversations[i].projectId == editing.id) {
                                            conversations[i] = conversations[i].copy(projectId = "")
                                        }
                                    }
                                    if (activeProjectId == editing.id) activeProjectId = ""
                                    ProjectStore.save(context, projects.toList())
                                    persist()
                                    showProjectDialog = false
                                }) { Text("Siba umushinga", color = Color(0xFFFF8A8A)) }
                            }
                        }
                    }
                )
            }
            val rid = renameId
            if (rid != null) {
                var titleDraft by remember {
                    mutableStateOf(conversations.firstOrNull { it.id == rid }?.title ?: "")
                }
                AlertDialog(
                    onDismissRequest = { renameId = null },
                    confirmButton = {
                        TextButton(onClick = {
                            val t = titleDraft.trim().take(40)
                            if (t.isNotEmpty()) renameConv(rid, t)
                            renameId = null
                        }) { Text("Bika") }
                    },
                    dismissButton = {
                        TextButton(onClick = { renameId = null }) { Text("Reka") }
                    },
                    title = { Text("Hindura izina ry'ikiganiro") },
                    text = {
                        TextField(
                            value = titleDraft,
                            onValueChange = { titleDraft = it },
                            singleLine = true
                        )
                    }
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
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            settings = settings,
            userName = userName,
            onChange = { st ->
                settings = st
                SettingsStore.save(context, st)
            },
            onBack = { showSettings = false },
            onEditName = { showProfile = true },
            onClearAll = { showClearConfirm = true },
            onAbout = { showAbout = true }
        )
    }
    }
}

private fun modeTitle(mode: String): String = when (mode) {
    "learn" -> "Kwiga"
    "write" -> "Kwandika"
    "plan" -> "Gutegura"
    "translate" -> "Guhindura"
    else -> ""
}

private fun modePrompt(mode: String): String = when (mode) {
    "learn" -> "Ndashaka kwiga. Mbaza ikintu nshaka kwiga hanyuma unyigishe buhoro buhoro."
    "write" -> "Ndashaka ko umfasha kwandika ubutumwa. Mbaza uwo ndandikira n'ico nshaka kuvuga."
    "plan" -> "Ndashaka igitekerezo c'umushinga. Mbaza ibibazo bike hanyuma umpe ibitekerezo."
    "translate" -> "Ndashaka ko umfasha guhindura amagambo mu zindi ndimi. Mbaza ururimi n'amagambo."
    else -> ""
}

private fun modeInstruction(mode: String): String = when (mode) {
    "learn" -> "MODE: You are a patient teacher. Ask what the user wants to learn and their level, " +
        "then teach step by step with short lessons, simple examples and one small exercise at the end."
    "write" -> "MODE: You are a writing helper. Help write messages, letters and posts in the user's language. " +
        "Ask who it is for if needed, then give a ready-to-send draft and offer a shorter or warmer version."
    "plan" -> "MODE: You are a planner. Ask a few short questions, then give a clear plan with steps, " +
        "priorities and the next three actions. Think about money, time and risks."
    "translate" -> "MODE: You are a careful translator. Ask which languages if unclear. " +
        "Give the translation first, then short notes about tone or alternatives. Be extra careful with Kirundi."
    else -> ""
}

private fun settingsInstruction(st: AppSettings): String {
    var t = ""
    when (st.answerLang) {
        "rn" -> t += " Always answer in real Kirundi (not Kinyarwanda) unless the user asks for another language."
        "en" -> t += " Always answer in English unless the user asks for another language."
        "fr" -> t += " Always answer in French unless the user asks for another language."
        "sw" -> t += " Always answer in Swahili unless the user asks for another language."
    }
    when (st.style) {
        "short" -> t += " ANSWER LENGTH: keep answers short and to the point."
        "long" -> t += " ANSWER LENGTH: give detailed and thorough answers."
    }
    if (st.custom.isNotBlank()) {
        t += " THE USER'S OWN INSTRUCTIONS FOR ALL CHATS: " + st.custom.trim()
    }
    return t
}

private fun buildExtra(mode: String, projectInstructions: String, st: AppSettings): String {
    var t = modeInstruction(mode) + settingsInstruction(st)
    if (projectInstructions.isNotBlank()) {
        t = t + " PROJECT INSTRUCTIONS FROM THE USER: " + projectInstructions.trim()
    }
    return t
}

@Composable
fun DrawerRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    selected: Boolean,
    trailing: ImageVector?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DrawerSel else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, color = DrawerText, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = DrawerMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun DrawerSubRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DrawerSel else Color.Transparent)
            .clickable { onClick() }
            .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = DrawerText,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    projects: List<Project>,
    currentId: String,
    activeProjectId: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleStar: (String) -> Unit,
    onRename: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onSelectProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onEditProject: (Project) -> Unit,
    onSettings: () -> Unit,
    onStart: (String) -> Unit
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var projectsOpen by remember { mutableStateOf(false) }
    var modesOpen by remember { mutableStateOf(false) }
    var onlyStarred by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf("") }

    val base = conversations.filter {
        (activeProjectId.isEmpty() || it.projectId == activeProjectId) &&
            (!onlyStarred || it.starred)
    }
    val filtered = if (query.isBlank()) {
        base
    } else {
        base.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val sorted = filtered.sortedByDescending { it.starred }
    val visible = if (showAll || query.isNotBlank() || onlyStarred || sorted.size <= 8) {
        sorted
    } else {
        sorted.take(8)
    }
    val activeProject = projects.firstOrNull { it.id == activeProjectId }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Shakisha ibiganiro", color = DrawerMuted) },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = DrawerText,
                        unfocusedTextColor = DrawerText,
                        cursorColor = DrawerAccent
                    )
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Funga",
                    tint = DrawerText,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            searching = false
                            query = ""
                        }
                        .padding(10.dp)
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Yacunne AI",
                    color = DrawerText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Shakisha",
                    tint = DrawerText,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { searching = true }
                        .padding(10.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Ikiganiro gishya",
                    tint = Color.White,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(DrawerAccent)
                        .clickable { onNew() }
                        .padding(11.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        DrawerRow(
            Icons.Filled.Home, "Imishinga", DrawerAccent, activeProjectId.isNotEmpty(),
            if (projectsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
        ) { projectsOpen = !projectsOpen }
        if (projectsOpen) {
            projects.forEach { p ->
                DrawerSubRow(p.name, p.id == activeProjectId, { onSelectProject(p.id) }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Hindura",
                        tint = DrawerMuted,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onEditProject(p) }
                            .padding(9.dp)
                    )
                }
            }
            Text(
                "+ Umushinga mushasha",
                color = DrawerAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNewProject() }
                    .padding(start = 48.dp, top = 10.dp, bottom = 10.dp)
            )
        }
        DrawerRow(
            Icons.Filled.Star, "Ibyo nkunda", Color(0xFFD97706), onlyStarred, null
        ) { onlyStarred = !onlyStarred }
        DrawerRow(
            Icons.Filled.Settings, "Uburyo", Color(0xFF059669), false,
            if (modesOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
        ) { modesOpen = !modesOpen }
        if (modesOpen) {
            DrawerSubRow("Kwiga", false, { onStart("learn") }) {
                Icon(Icons.Filled.Info, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
            }
            DrawerSubRow("Kwandika", false, { onStart("write") }) {
                Icon(Icons.Filled.Create, null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
            }
            DrawerSubRow("Gutegura", false, { onStart("plan") }) {
                Icon(Icons.Filled.Build, null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
            }
            DrawerSubRow("Guhindura", false, { onStart("translate") }) {
                Icon(Icons.Filled.Refresh, null, tint = Color(0xFFDB2777), modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DrawerLine))
        Spacer(modifier = Modifier.height(10.dp))
        if (activeProject != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DrawerSel)
                    .clickable { onSelectProject(activeProject.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Umushinga: " + activeProject.name,
                    color = DrawerText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Filled.Close, null, tint = DrawerMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text("Amateka", color = DrawerMuted, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        if (visible.isEmpty()) {
            Text(
                if (query.isBlank()) "Nta kiganiro kirabikwa." else "Nta cabonetse.",
                color = DrawerMuted,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(visible, key = { it.id }) { c ->
                val selected = c.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) DrawerSel else Color.Transparent)
                        .clickable { onOpen(c.id) }
                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (c.starred) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        c.title,
                        color = DrawerText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Ibindi",
                            tint = DrawerMuted,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { menuFor = c.id }
                                .padding(9.dp)
                        )
                        DropdownMenu(
                            expanded = menuFor == c.id,
                            onDismissRequest = { menuFor = "" }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (c.starred) "Vana mu byo nkunda" else "Shira mu byo nkunda")
                                },
                                onClick = {
                                    menuFor = ""
                                    onToggleStar(c.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hindura izina") },
                                onClick = {
                                    menuFor = ""
                                    onRename(c.id)
                                }
                            )
                            projects.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("Shira muri " + p.name) },
                                    onClick = {
                                        menuFor = ""
                                        onMove(c.id, p.id)
                                    }
                                )
                            }
                            if (c.projectId.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Vana mu mushinga") },
                                    onClick = {
                                        menuFor = ""
                                        onMove(c.id, "")
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Siba") },
                                onClick = {
                                    menuFor = ""
                                    onDelete(c.id)
                                }
                            )
                        }
                    }
                }
            }
            if (query.isBlank() && !onlyStarred && sorted.size > 8) {
                item {
                    Text(
                        if (showAll) "Reba bike" else "Reba vyose",
                        color = DrawerAccent,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showAll = !showAll }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DrawerAccent)
                    .clickable { onSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Igenamiterere",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState(userName: String, onPick: (String) -> Unit) {
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
        val hello = if (hour < 12) "Mwaramutse" else "Mwiriwe"
        val greeting = if (userName.isBlank()) hello + "!" else hello + ", " + userName.trim() + "!"
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
                    color = Color(0xFFA3ABC8)
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
                .alpha(if (!loading && input.isBlank()) 0.6f else 1f)
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
fun MessageRow(message: ChatMessage, onCopy: ((String) -> Unit)?, textSize: Int = 16) {
    if (message.fromUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            Bubble(message.text, true, textSize, Modifier.weight(1f, fill = false))
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Bubble(message.text, false, textSize)
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
fun Bubble(text: String, fromUser: Boolean, textSize: Int = 16, modifier: Modifier = Modifier) {
    if (fromUser) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = modifier
                .clip(shape)
                .background(Color(0xFF1E3A8A))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(text, color = Color(0xFFF1F2FF), fontSize = textSize.sp)
            }
        }
    } else {
        Box(modifier = modifier) {
            SelectionContainer {
                Text(
                    text,
                    color = Color(0xFFE8EAF6),
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 3 / 2).sp
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

object ProfileStore {
    private const val PREFS = "yacunne_profile"
    private const val KEY = "name"

    fun load(context: android.content.Context): String {
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
    }

    fun save(context: android.content.Context, name: String) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, name).apply()
    }
}

data class AppSettings(
    val fontSize: Int = 1,
    val answerLang: String = "auto",
    val style: String = "normal",
    val custom: String = ""
)

object SettingsStore {
    private const val PREFS = "yacunne_settings"

    fun load(context: android.content.Context): AppSettings {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return AppSettings(
            p.getInt("fontSize", 1),
            p.getString("answerLang", "auto") ?: "auto",
            p.getString("style", "normal") ?: "normal",
            p.getString("custom", "") ?: ""
        )
    }

    fun save(context: android.content.Context, st: AppSettings) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("fontSize", st.fontSize)
            .putString("answerLang", st.answerLang)
            .putString("style", st.style)
            .putString("custom", st.custom)
            .apply()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = DrawerMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingRow(title: String, value: String, titleColor: Color, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, color = titleColor, fontSize = 16.sp)
        if (value.isNotEmpty()) {
            Text(value, color = DrawerMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun SettingChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DrawerAccent else Color.White)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else DrawerText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    userName: String,
    onChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onClearAll: () -> Unit,
    onAbout: () -> Unit
) {
    var custom by remember { mutableStateOf(settings.custom) }
    val langs = listOf(
        "auto" to "Ruhuje n'ururimi nanditse",
        "rn" to "Ikirundi",
        "en" to "Icongereza",
        "fr" to "Igifaransa",
        "sw" to "Igiswahili"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DrawerBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Garuka",
                tint = DrawerText,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Igenamiterere",
                color = DrawerText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            SectionTitle("Konti")
            SettingRow(
                "Izina ryawe",
                if (userName.isBlank()) "Nta ryashyizweho. Kanda ugirishyireho." else userName,
                DrawerText
            ) { onEditName() }

            SectionTitle("Isura")
            Text("Ingano y'inyuguti", color = DrawerText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("Nto", settings.fontSize == 0, Modifier.weight(1f)) { onChange(settings.copy(fontSize = 0)) }
                SettingChip("Hagati", settings.fontSize == 1, Modifier.weight(1f)) { onChange(settings.copy(fontSize = 1)) }
                SettingChip("Nini", settings.fontSize == 2, Modifier.weight(1f)) { onChange(settings.copy(fontSize = 2)) }
            }

            SectionTitle("Uko Yacunne isubiza")
            Text("Uburyo bwo gusubiza", color = DrawerText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip("Ngufi", settings.style == "short", Modifier.weight(1f)) { onChange(settings.copy(style = "short")) }
                SettingChip("Isanzwe", settings.style == "normal", Modifier.weight(1f)) { onChange(settings.copy(style = "normal")) }
                SettingChip("Irambuye", settings.style == "long", Modifier.weight(1f)) { onChange(settings.copy(style = "long")) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Ururimi rw'ibisubizo", color = DrawerText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            langs.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (settings.answerLang == key) DrawerSel else Color.White)
                        .clickable { onChange(settings.copy(answerLang = key)) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = DrawerText, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (settings.answerLang == key) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = DrawerAccent)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Amabwiriza yanjye (kuri ibiganiro vyose)", color = DrawerText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
            TextField(
                value = custom,
                onValueChange = {
                    custom = it.take(500)
                    onChange(settings.copy(custom = custom))
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Urugero: Nitwa Yacu, ndi umushoferi wa Uber muri Afurika y'Epfo.", color = DrawerMuted) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = DrawerText,
                    unfocusedTextColor = DrawerText,
                    cursorColor = DrawerAccent
                )
            )

            SectionTitle("Ibiganiro")
            SettingRow("Siba amateka yose", "Ibiganiro vyose bizosibwa kuri iyi telefone.", Color(0xFFDC2626)) { onClearAll() }

            SectionTitle("Guhuza n'izindi app")
            SettingRow(
                "Google Calendar, Gmail, Drive, WhatsApp",
                "Biraza vuba. Bisaba seriveri n'uruhushya rw'izo app, nta bwo bishoboka muri iyi verisiyo.",
                DrawerMuted,
                null
            )

            SectionTitle("Ibijyanye na Yacunne")
            SettingRow("Yacunne AI", "Verisiyo 1.0. Soma amakuru ya app.", DrawerText) { onAbout() }
        }
    }
}
