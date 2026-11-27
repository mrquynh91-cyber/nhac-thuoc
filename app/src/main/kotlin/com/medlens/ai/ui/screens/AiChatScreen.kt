package com.medlens.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.RecordChatMessage
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg
import com.medlens.ai.voice.InAppVoiceRecorder
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AiChatScreen(
    activeProfile: FamilyProfile,
    profileRecords: List<MedicalRecord>,
    messages: List<RecordChatMessage>,
    geminiModel: String,
    hasGeminiApiKey: Boolean,
    isReplying: Boolean,
    onSendMessage: (question: String, focusedRecordId: String?) -> Unit,
    onSendVoice: (audioFile: File, focusedRecordId: String?) -> Unit,
    onClearChat: () -> Unit,
    onGoToSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf("") }
    var focusedRecordId by remember(activeProfile.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Dedicated in-app voice recorder (independent of keyboard voice)
    val voiceRecorder = remember(context) { InAppVoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.stopAndDiscard() }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds++
                if (recordingSeconds >= 60) {
                    isRecording = false
                    val file = voiceRecorder.stopAndGetFile()
                    if (file != null) onSendVoice(file, focusedRecordId)
                }
            }
        }
    }

    fun startInAppRecording() {
        keyboardController?.hide()
        voiceRecorder.startRecording()
            .onSuccess {
                isRecording = true
            }
            .onFailure { err ->
                isRecording = false
                onShowMessage("Không thể mở micro ghi âm: ${err.localizedMessage}")
            }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInAppRecording()
        } else {
            onShowMessage("Cần cấp quyền Micro để ghi âm hỏi đáp trực tiếp trong app.")
        }
    }

    LaunchedEffect(messages.size, isReplying) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    val quickSuggestions = remember(focusedRecordId) {
        if (focusedRecordId != null) {
            listOf(
                "Tóm tắt ngắn gọn phiếu này",
                "Thuốc trong đơn có tương tác không?",
                "Nên ăn và kiêng gì theo phiếu này?",
                "Cần hỏi lại bác sĩ câu gì?"
            )
        } else {
            listOf(
                "Bị virus viêm gan B uống thuốc gì?",
                "Thuốc trước ăn và sau ăn cách nhau bao lâu?",
                "Men gan, mỡ máu cao nên kiêng gì?",
                "Chỉ số HbA1c bao nhiêu là tiểu đường?",
                "Uống thuốc chung với sữa có sao không?"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Fixed Header — stays pinned at the top even when keyboard is open
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TealLightContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hỏi đáp Gemini AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Hồ sơ: ${activeProfile.name} • $geminiModel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

                if (profileRecords.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = WarningAmberBg),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chưa nhập Gemini API Key trong Cài đặt.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = onGoToSettings,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Scrollable chat history takes all remaining space cleanly
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TealLightContainer.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Trợ lý Chuyên ngành Y khoa Gemini AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = TealDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Hỏi bất kỳ câu hỏi y khoa nào (bệnh lý, phác đồ thuốc, chỉ số xét nghiệm, tương tác thuốc, dinh dưỡng...) bằng cách gõ chữ, chọn câu gợi ý bên dưới hoặc bấm nút Micro ghi âm trực tiếp!",
                                style = MaterialTheme.typography.bodyMedium
                            )
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

        // Bottom Dock: Quick Suggestions Strip (right above input) + In-app Voice Bar / Input Box
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Suggestions pinned right above typing area — visible even when keyboard opens!
                AnimatedVisibility(visible = !isRecording) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quickSuggestions) { q ->
                            AssistChip(
                                onClick = {
                                    if (!isReplying) {
                                        keyboardController?.hide()
                                        onSendMessage(q, focusedRecordId)
                                    }
                                },
                                label = {
                                    Text(
                                        text = q,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

                if (isRecording) {
                    // Dedicated In-App Voice Recording Bar (does NOT use keyboard voice)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlertRedBg.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AlertRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Đang ghi âm giọng nói...",
                                style = MaterialTheme.typography.titleSmall,
                                color = AlertRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "00:%02d • Bấm Gửi khi nói xong".format(recordingSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                        IconButton(
                            onClick = {
                                isRecording = false
                                voiceRecorder.stopAndDiscard()
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Hủy ghi âm", tint = AlertRed)
                        }
                        FilledIconButton(
                            onClick = {
                                isRecording = false
                                val audioFile = voiceRecorder.stopAndGetFile()
                                if (audioFile != null) {
                                    onSendVoice(audioFile, focusedRecordId)
                                } else {
                                    onShowMessage("Đoạn ghi âm quá ngắn, hãy giữ và nói rõ hơn nhé.")
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = TealPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Dừng và gửi giọng nói")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Hỏi kỹ hơn về y khoa, thuốc...") },
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startInAppRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isReplying
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Ghi âm trực tiếp", tint = TealPrimary)
                        }
                        FilledTonalIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    keyboardController?.hide()
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
            modifier = Modifier.fillMaxWidth(0.92f)
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
                    text = if (message.isUser) AnnotatedString(message.text) else formatMedicalMarkdown(message.text),
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun formatMedicalMarkdown(raw: String): AnnotatedString {
    val normalized = raw
        .replace(Regex("(?m)^\\s*\\*\\s+"), "• ")
        .replace(Regex("(?m)^\\s*-\\s+"), "• ")
    return buildAnnotatedString {
        var idx = 0
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        for (match in boldRegex.findAll(normalized)) {
            if (match.range.first > idx) {
                append(normalized.substring(idx, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            idx = match.range.last + 1
        }
        if (idx < normalized.length) {
            append(normalized.substring(idx))
        }
    }
}
