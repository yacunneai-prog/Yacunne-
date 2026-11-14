package com.yacunne.ai

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ---- Supabase (project ya Yacunne). Iyi key ni rusange (publishable), ntacyo itwaye. ----
private const val SUPABASE_URL = "https://wmjhjyauqoarplbkhhnw.supabase.co"
private const val SUPABASE_KEY = "sb_publishable__el60eNuqck0g4VGhFULdQ_6ouXo4lA"

object AuthState {
    var ok by mutableStateOf(false)
}

object AuthStore {
    private fun sp(c: Context) = c.getSharedPreferences("yacunne_auth", Context.MODE_PRIVATE)
    fun hasSession(c: Context): Boolean = sp(c).getString("access", "").orEmpty().isNotBlank()
    fun isGuest(c: Context): Boolean = sp(c).getBoolean("guest", false)
    fun email(c: Context): String = sp(c).getString("email", "").orEmpty()
    fun accessToken(c: Context): String = sp(c).getString("access", "").orEmpty()
    fun refreshToken(c: Context): String = sp(c).getString("refresh", "").orEmpty()
    fun saveSession(c: Context, access: String, refresh: String, email: String) {
        sp(c).edit().putString("access", access).putString("refresh", refresh)
            .putString("email", email).putBoolean("guest", false).apply()
        AuthState.ok = true
    }
    fun setGuest(c: Context) {
        sp(c).edit().putBoolean("guest", true).apply()
        AuthState.ok = true
    }
    fun signOut(c: Context) {
        sp(c).edit().clear().apply()
        AuthState.ok = false
    }
}

private fun au(rn: String, en: String): String = if (UiLang.current == "rn") rn else en

private fun postJson(path: String, body: JSONObject): Pair<Int, JSONObject> {
    val c = URL(SUPABASE_URL + path).openConnection() as HttpURLConnection
    try {
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 20000
        c.setRequestProperty("apikey", SUPABASE_KEY)
        c.setRequestProperty("Content-Type", "application/json")
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
        val j = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
        return Pair(code, j)
    } finally {
        c.disconnect()
    }
}

private fun friendlyError(j: JSONObject): String {
    val raw = listOf("msg", "error_description", "message", "error")
        .map { j.optString(it, "") }.firstOrNull { it.isNotBlank() } ?: "Error"
    val l = raw.lowercase()
    return when {
        l.contains("invalid login") -> au("Email canke ijambo ry'ibanga ntivyo.", "Wrong email or password.")
        l.contains("already registered") || l.contains("already been registered") ->
            au("Iyi email isanzwe yanditse. Ihitemwo \"Injira\".", "This email is already registered. Use Sign in.")
        l.contains("at least") -> au("Ijambo ry'ibanga rigomba kuba nibura inyuguti 6.", "Password must be at least 6 characters.")
        l.contains("valid email") || l.contains("invalid email") -> au("Email ntiyanditse neza.", "Invalid email address.")
        l.contains("rate limit") || l.contains("too many") -> au("Mugerageje kenshi. Tegereza gato.", "Too many tries. Wait a little.")
        l.contains("not confirmed") -> au("Banza wemeze email yawe.", "Please confirm your email first.")
        else -> raw
    }
}

// Isubiza null iyo vyaciye neza; ubundi ubutumwa bw'ikosa. "CONFIRM" = hakenewe kwemeza email.
private suspend fun doAuth(ctx: Context, signUp: Boolean, email: String, pass: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("email", email).put("password", pass)
            val path = if (signUp) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
            val (code, j) = postJson(path, body)
            if (code in 200..299) {
                val access = j.optString("access_token", "")
                if (access.isBlank()) {
                    "CONFIRM"
                } else {
                    val refresh = j.optString("refresh_token", "")
                    val mail = j.optJSONObject("user")?.optString("email", email) ?: email
                    AuthStore.saveSession(ctx, access, refresh, mail)
                    null
                }
            } else {
                friendlyError(j)
            }
        } catch (e: Exception) {
            au("Nta internet. Genzura hanyuma ugerageze.", "No internet. Check and try again.")
        }
    }

private suspend fun doReset(email: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val (code, j) = postJson("/auth/v1/recover", JSONObject().put("email", email))
            if (code in 200..299) null else friendlyError(j)
        } catch (e: Exception) {
            au("Nta internet. Genzura hanyuma ugerageze.", "No internet. Check and try again.")
        }
    }

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    remember {
        AuthState.ok = AuthStore.hasSession(ctx) || AuthStore.isGuest(ctx)
        0
    }
    if (AuthState.ok) content() else AuthScreen()
}

@Composable
fun AuthScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var signUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF67E8F9),
        unfocusedBorderColor = Color(0xFF475569),
        focusedLabelColor = Color(0xFF67E8F9),
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = Color(0xFF67E8F9)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF0B1020), Color(0xFF312E81))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text("✦", fontSize = 56.sp, color = Color(0xFFFBBF24))
            Text("Yacunne AI", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (signUp) au("Kora konti nshasha", "Create your account")
                else au("Injira muri konti yawe", "Sign in to your account"),
                fontSize = 15.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(au("Ijambo ry'ibanga", "Password")) },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                trailingIcon = {
                    TextButton(onClick = { show = !show }) {
                        Text(if (show) au("Hisha", "Hide") else au("Erekana", "Show"), color = Color(0xFF67E8F9), fontSize = 12.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (msg.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    msg,
                    color = if (msgOk) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (busy) return@Button
                    msg = ""
                    msgOk = false
                    if (!email.contains("@") || email.length < 5) {
                        msg = au("Andika email neza.", "Enter a valid email.")
                        return@Button
                    }
                    if (pass.length < 6) {
                        msg = au("Ijambo ry'ibanga rigomba kuba nibura inyuguti 6.", "Password must be at least 6 characters.")
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        val r = doAuth(ctx, signUp, email, pass)
                        busy = false
                        if (r == "CONFIRM") {
                            msgOk = true
                            msg = au("Twagutumiye email. Yemeze, hanyuma winjire.", "We sent you an email. Confirm it, then sign in.")
                            signUp = false
                        } else if (r != null) {
                            msg = r
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        if (signUp) au("Kora konti", "Sign up") else au("Injira", "Sign in"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                signUp = !signUp
                msg = ""
            }) {
                Text(
                    if (signUp) au("Ufise konti? Injira", "Already have an account? Sign in")
                    else au("Nta konti? Kora nshasha", "No account? Sign up"),
                    color = Color(0xFF67E8F9)
                )
            }
            if (!signUp) {
                TextButton(onClick = {
                    if (busy) return@TextButton
                    if (!email.contains("@")) {
                        msgOk = false
                        msg = au("Banza wandike email yawe hejuru.", "Enter your email above first.")
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        val r = doReset(email)
                        busy = false
                        if (r == null) {
                            msgOk = true
                            msg = au("Twagutumiye email yo guhindura ijambo ry'ibanga.", "We sent a password reset email.")
                        } else {
                            msgOk = false
                            msg = r
                        }
                    }
                }) {
                    Text(au("Wibagiwe ijambo ry'ibanga?", "Forgot password?"), color = Color(0xFF94A3B8))
                }
            }
            TextButton(onClick = { AuthStore.setGuest(ctx) }) {
                Text(au("Komeza udafise konti", "Continue without an account"), color = Color(0xFF94A3B8))
            }
        }
    }
}
