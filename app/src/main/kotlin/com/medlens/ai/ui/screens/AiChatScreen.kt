package com.medlens.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontStyle
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
import androidx.compose.ui.layout.ContentScale
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.RecordChatMessage
import com.medlens.ai.data.SavedAiChatSession
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg
import com.medlens.ai.voice.InAppSpeechDictator
import com.medlens.ai.voice.InAppVoiceRecorder
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiChatScreen(
    activeProfile: FamilyProfile,
    profileRecords: List<MedicalRecord>,
    messages: List<RecordChatMessage>,
    savedChats: List<SavedAiChatSession>,
    geminiModel: String,
    hasGeminiApiKey: Boolean,
    isReplying: Boolean,
    onSendMessage: (question: String, focusedRecordId: String?) -> Unit,
    onSendImage: (imageUri: Uri, question: String, focusedRecordId: String?) -> Unit,
    onTranscribeVoiceToText: (audioFile: File, onResult: (String) -> Unit) -> Unit,
    onSaveChatHistory: (title: String, focusedRecordTitle: String) -> Unit,
    onLoadSavedChat: (SavedAiChatSession) -> Unit,
    onDeleteSavedChat: (String) -> Unit,
    onClearChat: () -> Unit,
    onGoToSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf("") }
    var focusedRecordId by remember(activeProfile.id) { mutableStateOf<String?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var viewingSavedSession by remember { mutableStateOf<SavedAiChatSession?>(null) }
    val listState = rememberLazyListState()

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            pendingImageUri = uri
        }
    }

    fun launchChatCamera() {
        val capturesDir = File(context.cacheDir, "chat_captures").apply { mkdirs() }
        val photoFile = File(capturesDir, "chat_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = uri
        takePhotoLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchChatCamera()
        } else {
            onShowMessage("Cần cấp quyền Camera để chụp ảnh gửi hỏi Gemini AI.")
        }
    }

    // Live headless in-app Vietnamese speech-to-text into the chat box (NO full-screen Google popup, NEVER auto-sends)
    val speechDictator = remember(context) { InAppSpeechDictator(context) }
    val fallbackRecorder = remember(context) { InAppVoiceRecorder(context) }
    var isDictating by remember { mutableStateOf(false) }
    var isFallbackRecording by remember { mutableStateOf(false) }
    var isTranscribingFallback by remember { mutableStateOf(false) }
    var fallbackSeconds by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            speechDictator.stopAndRelease()
            fallbackRecorder.stopAndDiscard()
        }
    }

    fun appendSpokenIntoInput(spoken: String) {
        val clean = spoken.trim()
        if (clean.isNotEmpty()) {
            inputText = if (inputText.isBlank()) clean else "${inputText.trimEnd()} $clean"
        }
    }

    fun finishFallbackRecordingIntoInput() {
        isFallbackRecording = false
        val file = fallbackRecorder.stopAndGetFile()
        if (file != null) {
            isTranscribingFallback = true
            onTranscribeVoiceToText(file) { transcript ->
                isTranscribingFallback = false
                appendSpokenIntoInput(transcript)
            }
        } else {
            onShowMessage("Chưa nghe rõ giọng nói, hãy bấm Micro và nói rõ hơn nhé.")
        }
    }

    LaunchedEffect(isFallbackRecording) {
        if (isFallbackRecording) {
            fallbackSeconds = 0
            while (isFallbackRecording) {
                delay(1000L)
                fallbackSeconds++
                if (fallbackSeconds >= 60) {
                    finishFallbackRecordingIntoInput()
                }
            }
        }
    }

    fun startFallbackRecording() {
        fallbackRecorder.startRecording()
            .onSuccess {
                isFallbackRecording = true
            }
            .onFailure { err ->
                isFallbackRecording = false
                onShowMessage("Không thể mở micro: ${err.localizedMessage}")
            }
    }

    fun startLiveDictation() {
        speechDictator.startListening(
            baseText = inputText,
            onTextChanged = { updated -> inputText = updated },
            onListeningChanged = { active -> isDictating = active },
            onInfoMessage = { msg -> onShowMessage(msg) },
            onFallbackNeeded = { startFallbackRecording() }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startLiveDictation()
        } else {
            onShowMessage("Cần cấp quyền Micro để nhập giọng nói vào ô chat.")
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
        // Fixed Compact Header — single clean bar (record selector tucked into dropdown, no 2nd chip bar)
        val focusedRecord = profileRecords.firstOrNull { it.id == focusedRecordId }
        var contextMenuExpanded by remember { mutableStateOf(false) }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hỏi đáp Gemini AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = profileRecords.isNotEmpty()) {
                                    contextMenuExpanded = true
                                }
                            ) {
                                Text(
                                    text = if (focusedRecord != null) {
                                        "Phiếu: ${focusedRecord.title}"
                                    } else {
                                        "Hồ sơ ${activeProfile.name} • $geminiModel"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (focusedRecord != null) TealPrimary else MutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (profileRecords.isNotEmpty()) {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Chọn phiếu khám",
                                        tint = MutedText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = contextMenuExpanded,
                                onDismissRequest = { contextMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Toàn bộ hồ sơ ${activeProfile.name}",
                                            fontWeight = if (focusedRecordId == null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        focusedRecordId = null
                                        contextMenuExpanded = false
                                    }
                                )
                                profileRecords.take(6).forEach { rec ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Ghim phiếu: ${rec.title}",
                                                fontWeight = if (focusedRecordId == rec.id) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            focusedRecordId = rec.id
                                            contextMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = "Lưu lịch sử chat",
                                tint = TealPrimary
                            )
                        }
                    }
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Xem lịch sử đã lưu",
                            tint = if (savedChats.isNotEmpty()) TealPrimary else MutedText
                        )
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
                    FilledTonalIconButton(onClick = onGoToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt",
                            tint = TealPrimary
                        )
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
                                text = "Hỏi bất kỳ câu hỏi y khoa nào (bệnh lý, phác đồ thuốc, chỉ số xét nghiệm, tương tác thuốc, dinh dưỡng...) bằng cách gõ chữ, gửi hình ảnh chụp thuốc/món ăn, hoặc bấm Micro ghi âm trực tiếp!",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (savedChats.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = { showHistoryDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Xem lịch sử đã lưu (${savedChats.size})")
                                }
                            }
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    AssistantChatBubble(msg)
                }
                item(key = "save_chat_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Lưu lịch sử cuộc trò chuyện này để xem sau")
                        }
                    }
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
                val isListeningAny = isDictating || isFallbackRecording
                AnimatedVisibility(visible = !isListeningAny) {
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

                // Compact live listening status pill — NEVER hides the chat box or attached image!
                AnimatedVisibility(visible = isListeningAny || isTranscribingFallback) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlertRedBg.copy(alpha = 0.72f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isTranscribingFallback) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AlertRed
                            )
                            Text(
                                text = "Đang chuyển lời nói thành chữ vào ô chat...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7F1D1D),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(AlertRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = if (isDictating) {
                                    "Đang nghe trực tiếp vào ô chat... Bấm dừng khi nói xong"
                                } else {
                                    "Đang nghe (00:%02d)... Bấm dừng để điền chữ vào ô chat".format(fallbackSeconds)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7F1D1D),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    if (isDictating) {
                                        speechDictator.stopListening { isDictating = it }
                                    } else if (isFallbackRecording) {
                                        finishFallbackRecordingIntoInput()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Dừng", color = AlertRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Selected image preview strip right above the typing box (stays visible while speaking!)
                pendingImageUri?.let { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TealLightContainer.copy(alpha = 0.55f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Ảnh đính kèm",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Đã đính kèm hình ảnh",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = TealDark
                            )
                            Text(
                                text = "Gõ chữ hoặc bấm Micro để nói câu hỏi rồi bấm Gửi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText
                            )
                        }
                        IconButton(onClick = { pendingImageUri = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Bỏ ảnh", tint = AlertRed)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box {
                        FilledTonalIconButton(
                            onClick = { showImageSourceMenu = true },
                            enabled = !isReplying
                        ) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Gửi hình ảnh",
                                tint = TealPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showImageSourceMenu,
                            onDismissRequest = { showImageSourceMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chụp ảnh ngay") },
                                leadingIcon = {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TealPrimary)
                                },
                                onClick = {
                                    showImageSourceMenu = false
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        launchChatCamera()
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chọn ảnh từ thư viện") },
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TealPrimary)
                                },
                                onClick = {
                                    showImageSourceMenu = false
                                    galleryPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (pendingImageUri != null) "Hỏi gì về ảnh này..."
                                else "Hỏi y khoa, thuốc hoặc gửi ảnh..."
                            )
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    if (isListeningAny) {
                        FilledIconButton(
                            onClick = {
                                if (isDictating) {
                                    speechDictator.stopListening { isDictating = it }
                                } else if (isFallbackRecording) {
                                    finishFallbackRecordingIntoInput()
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = AlertRed,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Dừng nói")
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startLiveDictation()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isReplying && !isTranscribingFallback
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Nói trực tiếp vào ô chat", tint = TealPrimary)
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (isDictating) speechDictator.stopListening { isDictating = it }
                            val attachedUri = pendingImageUri
                            if (attachedUri != null) {
                                keyboardController?.hide()
                                onSendImage(attachedUri, inputText, focusedRecordId)
                                pendingImageUri = null
                                inputText = ""
                            } else if (inputText.isNotBlank()) {
                                keyboardController?.hide()
                                onSendMessage(inputText, focusedRecordId)
                                inputText = ""
                            }
                        },
                        enabled = (inputText.isNotBlank() || pendingImageUri != null) && !isReplying && !isTranscribingFallback
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = TealPrimary)
                    }
                }
            }
        }
    }

    // Save current AI chat session dialog
    if (showSaveDialog) {
        val suggestedTitle = remember(messages) {
            messages.firstOrNull { it.isUser }?.text
                ?.removePrefix("🎤 ")
                ?.trim()
                ?.take(60)
                ?.ifBlank { "Hỏi đáp AI • ${activeProfile.name}" }
                ?: "Hỏi đáp AI • ${activeProfile.name}"
        }
        var titleInput by remember(showSaveDialog, suggestedTitle) { mutableStateOf(suggestedTitle) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Lưu lịch sử trò chuyện AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Đặt tên ngắn gọn cho cuộc trò chuyện (${messages.size} tin nhắn) để dễ tìm lại sau này:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Tiêu đề bản lưu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentFocusedTitle = profileRecords
                            .firstOrNull { it.id == focusedRecordId }
                            ?.title
                            .orEmpty()
                        onSaveChatHistory(titleInput, currentFocusedTitle)
                        showSaveDialog = false
                    }
                ) {
                    Text("Lưu lại")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Hủy") }
            }
        )
    }

    // Saved AI Chat Archive Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Lịch sử trò chuyện đã lưu (${activeProfile.name})") },
            text = {
                if (savedChats.isEmpty()) {
                    Text(
                        text = "Chưa có cuộc trò chuyện nào được lưu. Khi trò chuyện với Gemini AI, hãy bấm nút 'Lưu lịch sử' để lưu lại xem sau nhé.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(savedChats, key = { it.id }) { session ->
                            val dateStr = remember(session.savedAt) {
                                SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale("vi", "VN")).format(Date(session.savedAt))
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHistoryDialog = false
                                        viewingSavedSession = session
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "$dateStr • ${session.messages.size} tin nhắn",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                        if (session.focusedRecordTitle.isNotBlank()) {
                                            Text(
                                                text = "Phiếu: ${session.focusedRecordTitle}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TealPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteSavedChat(session.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Xóa bản lưu",
                                            tint = AlertRed,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Đóng") }
            }
        )
    }

    // View a saved AI chat session in full + option to resume in active chat
    viewingSavedSession?.let { session ->
        AlertDialog(
            onDismissRequest = { viewingSavedSession = null },
            title = {
                Column {
                    Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        text = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale("vi", "VN")).format(Date(session.savedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    items(session.messages, key = { it.id }) { msg ->
                        AssistantChatBubble(msg)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLoadSavedChat(session)
                        viewingSavedSession = null
                    }
                ) {
                    Text("Mở tiếp vào khung chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingSavedSession = null }) { Text("Đóng") }
            }
        )
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
                val imgFile = message.imagePath?.let { File(it) }?.takeIf { it.exists() }
                if (imgFile != null) {
                    AsyncImage(
                        model = imgFile,
                        contentDescription = "Ảnh đã gửi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(8.dp))
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
    val tokenRegex = Regex("\\*\\*(.+?)\\*\\*|\\*([^*\n]+?)\\*")
    return buildAnnotatedString {
        var idx = 0
        for (match in tokenRegex.findAll(normalized)) {
            if (match.range.first > idx) {
                append(normalized.substring(idx, match.range.first))
            }
            val boldText = match.groups[1]?.value
            val italicText = match.groups[2]?.value
            when {
                boldText != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldText)
                }
                italicText != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicText)
                }
            }
            idx = match.range.last + 1
        }
        if (idx < normalized.length) {
            append(normalized.substring(idx))
        }
    }
}
