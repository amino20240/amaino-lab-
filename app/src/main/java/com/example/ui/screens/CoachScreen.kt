package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.IndigoPrimary
import java.util.Locale

@Composable
fun CoachScreen(
    strings: AppStrings,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    coachError: String?,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isAr = strings.isAr
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val tts = remember {
        var inst: TextToSpeech? = null
        inst = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                inst?.language = if (isAr) Locale.forLanguageTag("ar-EG") else Locale.US
            }
        }
        inst
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val prompts = if (isAr) {
        listOf(
            "اقترح لي وجبة عالية البروتين بميزانية 60 جنيه",
            "إزاي أطور تمرين البنش برس للأسبوع الجاي؟",
            "إيه خطة الاستشفاء المناسبة ليا النهاردة؟"
        )
    } else {
        listOf(
            "Build me a high-protein budget meal under 3 USD",
            "How should I progress my bench press next week?",
            "What recovery adjustments fit my readiness today?"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.aiTitle, style = MaterialTheme.typography.headlineMedium)
                Text(strings.aiSub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (messages.isNotEmpty()) {
                IconButton(onClick = onClearChat, modifier = Modifier.testTag("clear_coach_chat_button")) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Chat")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEachIndexed { idx, p ->
                AssistChip(
                    onClick = { onSendMessage(p) },
                    label = { Text(p, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("coach_prompt_chip_$idx")
                )
            }
        }

        if (!coachError.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = coachError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (isAr) "اسأل مدربك الشخصي عن التمرين، التغذية، الميزانية، أو تعديل الجهد!"
                            else "Ask your Gemini coach about training volume, macros, budget meals, or recovery!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isFromUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 310.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isFromUser) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (msg.isFromUser) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (!msg.isFromUser && msg.text.length > 2) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(
                                            onClick = {
                                                tts.language = if (isAr) Locale.forLanguageTag("ar-EG") else Locale.US
                                                tts.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, msg.id)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Speak reply",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isAr) "ar-EG" else "en-US")
                    }
                    runCatching { voiceLauncher.launch(intent) }.onFailure {
                        onShowMessage(if (isAr) "ميزة الإملاء الصوتي غير متوفرة على هذا الجهاز" else "Speech recognition unavailable on this device")
                    }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("coach_voice_button")
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Voice Input")
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text(strings.aiPlaceholder) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("coach_message_input")
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank() && !isStreaming) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = !isStreaming && inputText.isNotBlank(),
                modifier = Modifier.height(52.dp).testTag("coach_send_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = strings.send)
                }
            }
        }
    }
}
