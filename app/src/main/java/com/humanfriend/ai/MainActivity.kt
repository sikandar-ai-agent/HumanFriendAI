
package com.humanfriend.ai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val defaultServerUrl =
        "https://YOUR-HTTPS-SERVER.example/chat"

    private var activeServerUrl = defaultServerUrl

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            "humanfriend_settings",
            Context.MODE_PRIVATE
        )

        val savedUrl = prefs.getString(
            "server_url",
            defaultServerUrl
        ) ?: defaultServerUrl

        val savedKey = prefs.getString(
            "optional_api_key",
            ""
        ) ?: ""

        val savedProvider = prefs.getString(
            "provider",
            "gemini"
        ) ?: "gemini"

        val savedLanguage = prefs.getString(
            "language",
            "Auto"
        ) ?: "Auto"

        activeServerUrl = savedUrl

        setContent {
            var dark by remember {
                mutableStateOf(prefs.getBoolean("dark", true))
            }

            MaterialTheme(
                colorScheme = if (dark) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                }
            ) {
                HumanFriendScreen(
                    initialServerUrl = savedUrl,
                    initialApiKey = savedKey,
                    initialProvider = savedProvider,
                    initialLanguage = savedLanguage,
                    initialDark = dark,

                    onSaveSettings = { url, key, provider, language, isDark ->

                        activeServerUrl = url.trim().trimEnd('/')

                        dark = isDark

                        prefs.edit()
                            .putString("server_url", activeServerUrl)
                            .putString("optional_api_key", key)
                            .putString("provider", provider)
                            .putString("language", language)
                            .putBoolean("dark", isDark)
                            .apply()
                    },

                    onSend = { text, provider, key, update ->
                        sendToServer(text, provider, key, update)
                    }
                )
            }
        }
    }

    private fun sendToServer(
        message: String,
        provider: String,
        apiKey: String,
        update: (String) -> Unit
    ) {
        update("Thinking… / جواب تیار ہو رہا ہے")

        val url = if (activeServerUrl.endsWith("/chat")) {
            activeServerUrl
        } else {
            "$activeServerUrl/chat"
        }

        val payload = JSONObject()
            .put("message", message)
            .put("provider", provider)

        if (apiKey.isNotBlank()) {
            payload.put("api_key", apiKey)
        }

        val request = Request.Builder()
            .url(url)
            .post(
                payload.toString().toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    update(
                        "Connection failed: ${e.localizedMessage ?: "Network error"}. Check backend URL in Settings."
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()

                    val answer = try {
                        val json = JSONObject(raw)
                        json.optString(
                            "response",
                            json.optString("error", "Empty server response")
                        )
                    } catch (_: Exception) {
                        if (it.isSuccessful) {
                            raw
                        } else {
                            "Server error ${it.code}: $raw"
                        }
                    }

                    runOnUiThread {
                        update(answer)
                    }
                }
            }
        })
    }
}

@Composable
private fun HumanFriendScreen(
    initialServerUrl: String,
    initialApiKey: String,
    initialProvider: String,
    initialLanguage: String,
    initialDark: Boolean,
    onSaveSettings: (String, String, String, String, Boolean) -> Unit,
    onSend: (String, String, String, (String) -> Unit) -> Unit
) {
    var message by remember { mutableStateOf("") }

    var transcript by remember {
        mutableStateOf(
            listOf(
                "HumanFriendAI: Assalam-o-alaikum! I’m your AI friend. / السلام علیکم!"
            )
        )
    }

    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var serverEdit by remember { mutableStateOf(initialServerUrl) }
    var keyEdit by remember { mutableStateOf(initialApiKey) }
    var provider by remember { mutableStateOf(initialProvider) }
    var language by remember { mutableStateOf(initialLanguage) }
    var dark by remember { mutableStateOf(initialDark) }

    var saveMessage by remember { mutableStateOf("") }

    val keyboardController = LocalSoftwareKeyboardController.current

    val sendLabel = when (language) {
        "English" -> "Send"
        "Roman Urdu", "Hinglish" -> "Bhejein"
        else -> "پیغام بھیجیں"
    }

    fun sendMessage() {
        val text = message.trim()

        if (text.isEmpty() || busy) return

        transcript = transcript + "You: $text"
        message = ""
        busy = true

        keyboardController?.hide()

        onSend(text, provider, keyEdit.trim()) { result ->
            transcript = transcript + "HumanFriendAI: $result"
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "HumanFriendAI",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Your AI Friend Always",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(
                onClick = {
                    showSettings = !showSettings
                    saveMessage = ""
                }
            ) {
                Text(if (showSettings) "Chat" else "⚙ Settings")
            }
        }

        if (showSettings) {
            Text("AI Provider")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = provider == "gemini",
                    onClick = { provider = "gemini" },
                    label = { Text("Google Gemini") }
                )

                FilterChip(
                    selected = provider == "huggingface",
                    onClick = { provider = "huggingface" },
                    label = { Text("Hugging Face") }
                )
            }

            OutlinedTextField(
                value = serverEdit,
                onValueChange = { serverEdit = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Backend HTTPS URL") },
                singleLine = true
            )

            OutlinedTextField(
                value = keyEdit,
                onValueChange = { keyEdit = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional personal API key") },
                singleLine = true
            )

            Text(
                "For shared use, configure provider secrets on the backend.",
                style = MaterialTheme.typography.bodySmall
            )

            Text("Language")

            Column {
                listOf(
                    "Auto",
                    "اردو",
                    "English",
                    "Hinglish",
                    "Roman Urdu"
                ).forEach { lang ->
                    FilterChip(
                        selected = language == lang,
                        onClick = { language = lang },
                        label = { Text(lang) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dark theme")

                Switch(
                    checked = dark,
                    onCheckedChange = { dark = it }
                )
            }

            Button(
                onClick = {
                    onSaveSettings(
                        serverEdit,
                        keyEdit,
                        provider,
                        language,
                        dark
                    )

                    saveMessage = "Settings saved successfully!"
                    transcript = transcript + "Settings saved."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            if (saveMessage.isNotBlank()) {
                Text(
                    saveMessage,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Chat", style = MaterialTheme.typography.titleMedium)

            TextButton(
                onClick = {
                    transcript = listOf(
                        "New chat started. / نئی گفتگو شروع"
                    )
                    message = ""
                    busy = false
                }
            ) {
                Text("＋ New Chat")
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                transcript.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message / اپنا پیغام") },
            enabled = !busy,
            minLines = 2,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    sendMessage()
                }
            )
        )

        Button(
            onClick = { sendMessage() },
            modifier = Modifier.fillMaxWidth(),
            enabled = message.isNotBlank() && !busy
        ) {
            Text(if (busy) "…" else sendLabel)
        }
    }
}
