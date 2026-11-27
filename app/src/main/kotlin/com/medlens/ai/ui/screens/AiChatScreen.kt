package com.medlens.ai.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.RecordChatMessage
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    activeProfile: FamilyProfile,
    profileRecords: List<MedicalRecord>,
    messages: List<RecordChatMessage>,
    geminiModel: String,
    hasGeminiApiKey: Boolean,
    isReplying: Boolean,
    onSendMessage: (question: String, focusedRecordId: String?) -> Unit,
    onClearChat: () -> Unit,
    onGoToSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var focusedRecordId by remember(activeProfile.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                onSendMessage(spoken, focusedRecordId)
            }
        }
    }

    LaunchedEffect(messages.size, isReplying) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TealLightContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TealPrimary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hỏi đáp Gemini AI",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Hồ sơ: ${activeProfile.name} • $geminiModel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText
                            )
                        }
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = onClearChat) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Cuộc trò chuyện mới",
                                tint = MutedText
                            )
                        }
                    }
                }

                // Context selector: ask generally across profile OR pin a specific scanned record
                if (profileRecords.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = focusedRecordId == null,
                                onClick = { focusedRecordId = null },
                                label = { Text("Toàn bộ hồ sơ ${activeProfile.name}") }
                            )
                        }
                        items(profileRecords.take(5), key = { it.id }) { rec ->
                            FilterChip(
                                selected = focusedRecordId == rec.id,
                                onClick = {
                                    focusedRecordId = if (focusedRecordId == rec.id) null else rec.id
                                },
                                label = {
                                    Text(
                                        text = rec.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (!hasGeminiApiKey) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WarningAmberBg),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chưa có Gemini API Key. Nhập key ở Cài đặt để chat trực tiếp.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(
                        onClick = onGoToSettings,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Chat message history
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = TealLightContainer.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Chào bạn! Mình là Trợ lý Y khoa chuyên sâu Gemini AI.",
                                style = MaterialTheme.typography.titleMedium,
                                color = TealDark
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Hỏi bất kỳ câu hỏi nào về chuyên ngành y học: bệnh lý (viêm gan B, huyết áp, dạ dày, tiểu đường...), thuốc & phác đồ y văn chuẩn, chỉ số xét nghiệm, tương tác thuốc và dinh dưỡng cho hồ sơ ${activeProfile.name}.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Gợi ý hỏi nhanh:",
                                style = MaterialTheme.typography.labelLarge,
                                color = TealPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            val starters = listOf(
                                "Bị virus viêm gan B khi nào cần uống thuốc và dùng nhóm thuốc nào?",
                                "Các thuốc tôi đang đặt lịch có tương tác với nhau không?",
                                "Men gan hoặc mỡ máu cao nên ăn và kiêng món gì cụ thể?",
                                "Chỉ số HbA1c bao nhiêu thì cần khám tiểu đường?"
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                starters.forEach { q ->
                                    AssistChip(
                                        onClick = { onSendMessage(q, focusedRecordId) },
                                        label = { Text(q, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    AssistantChatBubble(msg)
                }
            }

            if (isReplying) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = TealPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Gemini AI ($geminiModel) đang trả lời...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
            }
        }

        // Bottom Chat Input Bar + Mic
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Hỏi kỹ hơn về thuốc, xét nghiệm...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói câu hỏi y tế của bạn...")
                            }
                            runCatching { voiceLauncher.launch(intent) }.onFailure {
                                onShowMessage("Thiết bị chưa hỗ trợ nhập giọng nói Google.")
                            }
                        },
                        enabled = !isReplying
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Nói câu hỏi", tint = TealPrimary)
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText, focusedRecordId)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isReplying
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = TealPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantChatBubble(message: RecordChatMessage) {
    val bg = if (message.isUser) TealPrimary else MaterialTheme.colorScheme.surface
    val fg = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
    val border = if (message.isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            border = border,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.fillMaxWidth(0.90f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isUser) {
                    Text(
                        text = "Gemini AI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    text = message.text,
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
