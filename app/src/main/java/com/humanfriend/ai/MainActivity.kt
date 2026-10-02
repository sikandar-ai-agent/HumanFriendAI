package com.humanfriend.ai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val defaultServerUrl = "https://YOUR-HTTPS-SERVER.example/chat"
    private var activeServerUrl = defaultServerUrl
    private var selectedProvider = "gemini"
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("humanfriend_settings", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", defaultServerUrl) ?: defaultServerUrl
        val savedKey = prefs.getString("optional_api_key", "") ?: ""
        val savedProvider = prefs.getString("provider", "gemini") ?: "gemini"
        activeServerUrl = savedUrl
        selectedProvider = savedProvider
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                HumanFriendScreen(
                    initialServerUrl = savedUrl,
                    initialApiKey = savedKey,
                    initialProvider = savedProvider,
                    initialDark = dark,
                    onSaveSettings = { url, key, provider, isDark ->
                        activeServerUrl = url.trim().trimEnd('/')
                        selectedProvider = provider
                        dark = isDark
                        prefs.edit().putString("server_url", activeServerUrl).putString("optional_api_key", key)
                            .putString("provider", provider).putBoolean("dark", isDark).apply()
                    },
                    onSend = { text, provider, key, update -> sendToServer(text, provider, key, update) }
                )
            }
        }
    }

    private fun sendToServer(message: String, provider: String, apiKey: String, update: (String) -> Unit) {
        update("Thinking… / جواب تیار ہو رہا ہے")
        val url = if (activeServerUrl.endsWith("/chat")) activeServerUrl else "$activeServerUrl/chat"
        val payload = JSONObject().put("message", message).put("provider", provider)
        // Optional user-supplied key is sent only to the configured server; prefer server-side environment secrets.
        if (apiKey.isNotBlank()) payload.put("api_key", apiKey)
        val request = Request.Builder().url(url).post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { update("Connection failed: ${e.localizedMessage ?: "network error"}. Check HTTPS server URL in Settings.") } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    val answer = try { JSONObject(raw).optString("response", JSONObject(raw).optString("error", "Empty server response")) }
                    catch (_: Exception) { if (it.isSuccessful) raw else "Server error ${it.code}: $raw" }
                    runOnUiThread { update(answer) }
                }
            }
        })
    }
}

@Composable
private fun HumanFriendScreen(
    initialServerUrl: String, initialApiKey: String, initialProvider: String, initialDark: Boolean,
    onSaveSettings: (String, String, String, Boolean) -> Unit,
    onSend: (String, String, String, (String) -> Unit) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf(listOf("HumanFriendAI: Assalam-o-alaikum! I’m your AI friend. / السلام علیکم!")) }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var serverEdit by remember { mutableStateOf(initialServerUrl) }
    var keyEdit by remember { mutableStateOf(initialApiKey) }
    var provider by remember { mutableStateOf(initialProvider) }
    var dark by remember { mutableStateOf(initialDark) }
    var language by remember { mutableStateOf("Auto") }
    val sendLabel = when (language) { "English" -> "Send"; "Roman Urdu", "Hinglish" -> "Bhejein"; else -> "پیغام بھیجیں" }

    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("HumanFriendAI", style = MaterialTheme.typography.headlineSmall); Text("Your AI Friend Always", style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Chat" else "⚙ Settings") }
        }
        if (showSettings) {
            Text("AI Provider")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = provider == "gemini", onClick = { provider = "gemini" }, label = { Text("Google Gemini") })
                FilterChip(selected = provider == "huggingface", onClick = { provider = "huggingface" }, label = { Text("Hugging Face") })
            }
            OutlinedTextField(serverEdit, { serverEdit = it }, Modifier.fillMaxWidth(), label = { Text("Backend HTTPS URL") }, singleLine = true)
            OutlinedTextField(keyEdit, { keyEdit = it }, Modifier.fillMaxWidth(), label = { Text("Optional personal API key (sent to your backend)") }, singleLine = true)
            Text("For shared use, configure provider secrets on the backend instead of storing keys on the phone.", style = MaterialTheme.typography.bodySmall)
            Text("Language")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Auto", "اردو", "English", "Hinglish", "Roman Urdu").forEach { lang -> FilterChip(selected = language == lang, onClick = { language = lang }, label = { Text(lang) }) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dark theme")
                Switch(checked = dark, onCheckedChange = { dark = it })
            }
            Button(onClick = { onSaveSettings(serverEdit, keyEdit, provider, dark); transcript = transcript + "Settings saved." }, modifier = Modifier.fillMaxWidth()) { Text("Save Settings") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Chat", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { transcript = listOf("New chat started. / نئی گفتگو شروع") }) { Text("＋ New Chat") }
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                transcript.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text("Message / اپنا پیغام") }, enabled = !busy, minLines = 2)
        Button(onClick = {
            val text = message.trim()
            if (text.isNotEmpty() && !busy) {
                transcript = transcript + "You: $text"
                busy = true
                message = ""
                onSend(text, provider, keyEdit.trim()) { result -> transcript = transcript + "HumanFriendAI: $result"; busy = false }
            }
        }, Modifier.fillMaxWidth(), enabled = message.isNotBlank() && !busy) { Text(if (busy) "…" else sendLabel) }
    }
}
