
package com.yacunne.ai

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ConnectProvider(
    val name: String,
    val description: String,
    val symbol: String,
    val type: String
)

@Composable
fun ConnectScreen(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedProvider by remember {
        mutableStateOf<ConnectProvider?>(null)
    }

    val providers = listOf(
        ConnectProvider("OpenAI", "Access OpenAI models", "O", "AI"),
        ConnectProvider("Claude", "Anthropic AI models", "C", "AI"),
        ConnectProvider("Gemini", "Google AI models", "G", "AI"),
        ConnectProvider("DeepSeek", "DeepSeek AI models", "D", "AI"),
        ConnectProvider("Grok", "xAI models", "X", "AI"),
        ConnectProvider("Mistral", "Mistral AI models", "M", "AI"),
        ConnectProvider("Cohere", "Cohere AI services", "C", "AI"),

        ConnectProvider("Google Drive", "Access your files", "G", "APP"),
        ConnectProvider("Gmail", "Connect your email", "M", "APP"),
        ConnectProvider("Google Calendar", "Access your calendar", "C", "APP"),
        ConnectProvider("GitHub", "Connect repositories", "G", "APP"),
        ConnectProvider("Dropbox", "Cloud storage", "D", "APP"),
        ConnectProvider("OneDrive", "Microsoft cloud storage", "O", "APP"),
        ConnectProvider("Notion", "Connect your workspace", "N", "APP"),
        ConnectProvider("Slack", "Connect team workspace", "S", "APP")
    )

    val visibleProviders = providers.filter {
        it.type == if (selectedTab == 0) "APP" else "AI"
    }

    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Back", color = colors.onBackground)
            }

            Spacer(Modifier.width(8.dp))

            Text(
                "Connect",
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground
            )
        }

        Spacer(Modifier.height(5.dp))

        Text(
            "Connect your apps and AI models to YACUNNE AI.",
            color = colors.onBackground.copy(alpha = 0.65f),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    colors.surface,
                    RoundedCornerShape(12.dp)
                )
                .padding(4.dp)
        ) {
            listOf("Apps", "AI Models").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTab == index)
                                colors.primary.copy(alpha = 0.18f)
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
                        fontWeight = if (selectedTab == index)
                            FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(bottom = 25.dp)
        ) {
            items(visibleProviders) { provider ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            colors.surface,
                            RoundedCornerShape(15.dp)
                        )
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

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            provider.name,
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
                            selectedProvider = provider
                        },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(
                            horizontal = 12.dp,
                            vertical = 5.dp
                        )
                    ) {
                        Text("Connect", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    selectedProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = {
                selectedProvider = null
            },
            title = {
                Text("Connect ${provider.name}")
            },
            text = {
                Text(
                    if (provider.type == "AI") {
                        "This AI provider needs API authentication and a secure backend before it can be connected."
                    } else {
                        "This app needs OAuth authentication and a secure backend before it can be connected."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedProvider = null }
                ) {
                    Text("OK")
                }
            }
        )
    }
}
