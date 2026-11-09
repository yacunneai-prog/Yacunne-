package com.yacunne.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val send: () -> Unit = {
        if (input.isNotBlank() && !loading) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepNavy, MidNavy, DeepViolet)))
            .drawBehind {
                // Urumuri rw'ubururu hejuru n'urwa purple hasi (imiraba y'urumuri)
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
        ChatHeader()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageRow(message)
            }
        }
        if (loading) {
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
            onSend = send
        )
        BottomNav()
    }
}

@Composable
fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    loading: Boolean,
    onSend: () -> Unit
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
                .alpha(if (loading) 0.5f else 1f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeonPurple, NeonBlue)))
                .clickable { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Ohereza",
                tint = Color.White
            )
        }
    }
}

@Composable
fun BottomNav() {
    var selected by remember { mutableStateOf(0) }
    val icons = listOf(
        Icons.Filled.Home,
        Icons.Filled.Star,
        Icons.Filled.Email,
        Icons.Filled.Notifications,
        Icons.Filled.Settings
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEachIndexed { index, icon ->
            val isSelected = index == selected
            val fill = if (isSelected) {
                Brush.linearGradient(listOf(NeonPurple, NeonBlue))
            } else {
                Brush.linearGradient(listOf(BubbleDark, BubbleDark))
            }
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(40.dp)
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, if (isSelected) NeonPurple else BubbleBorder, shape)
                    .clickable { selected = index },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else SoftText
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
fun ChatHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { }
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            LogoY(40)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "YACUNNE",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "AI",
                        color = NeonPurple,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    "Your Smart AI Assistant",
                    color = SoftText,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A1F0A))
                    .border(1.5.dp, Gold, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("👑 Pro", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Ibindi",
                tint = Color.White,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { }
                    .padding(8.dp)
            )
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
fun MessageRow(message: ChatMessage) {
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
            Bubble(message.text, false, Modifier.weight(1f, fill = false))
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
        Text(text, color = Color(0xFFF1F2FF), fontSize = 16.sp)
    }
}
