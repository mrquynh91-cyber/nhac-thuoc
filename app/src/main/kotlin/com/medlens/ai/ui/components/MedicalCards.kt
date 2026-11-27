package com.medlens.ai.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.medlens.ai.data.AppSettings
import com.medlens.ai.data.DaySlot
import com.medlens.ai.data.IndicatorStatus
import com.medlens.ai.data.LabIndicator
import com.medlens.ai.data.LocalMedicalStore
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.MedicationItem
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.data.RecordChatMessage
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.InfoBlue
import com.medlens.ai.ui.theme.InfoBlueBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.NormalGreen
import com.medlens.ai.ui.theme.NormalGreenBg
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg
import com.medlens.ai.voice.InAppVoiceRecorder
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun IndicatorStatusBadge(status: IndicatorStatus) {
    val (emoji, fg, bg) = when (status) {
        IndicatorStatus.NORMAL -> Triple("🟢", NormalGreen, NormalGreenBg)
        IndicatorStatus.BORDERLINE -> Triple("🟡", WarningAmber, WarningAmberBg)
        IndicatorStatus.HIGH, IndicatorStatus.LOW, IndicatorStatus.CRITICAL -> Triple("🔴", AlertRed, AlertRedBg)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = "$emoji ${status.labelVi}",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LabIndicatorCard(indicator: LabIndicator) {
    val borderColor = when (indicator.status) {
        IndicatorStatus.NORMAL -> NormalGreen.copy(alpha = 0.25f)
        IndicatorStatus.BORDERLINE -> WarningAmber.copy(alpha = 0.45f)
        else -> AlertRed.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = indicator.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (indicator.referenceRange.isNotBlank()) {
                        Text(
                            text = "Ngưỡng chuẩn: ${indicator.referenceRange}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${indicator.value} ${indicator.unit}".trim(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (indicator.status) {
                            IndicatorStatus.NORMAL -> NormalGreen
                            IndicatorStatus.BORDERLINE -> WarningAmber
                            else -> AlertRed
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    IndicatorStatusBadge(indicator.status)
                }
            }

            if (indicator.plainExplanation.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = indicator.plainExplanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (indicator.dietAndLifestyleAdvice.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TealLightContainer.copy(alpha = 0.45f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = indicator.dietAndLifestyleAdvice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationCard(
    medication: MedicationItem,
    existingReminder: MedicationReminder?,
    onCreateOrEditReminder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(InfoBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Medication,
                        contentDescription = null,
                        tint = InfoBlue
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (medication.activeIngredient.isNotBlank()) {
                        Text(
                            text = "Hoạt chất: ${medication.activeIngredient}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
            }

            if (medication.categoryShort.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(color = TealLightContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "Công dụng: ${medication.categoryShort}",
                        color = TealDark,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoChip(text = "Liều: ${medication.dosage}")
                InfoChip(text = existingReminder?.mealTiming?.labelVi ?: medication.mealTiming.labelVi)
                if (medication.daySlots.isNotEmpty()) {
                    InfoChip(text = "Buổi: ${medication.daySlots.joinToString(" • ") { it.labelVi }}")
                }
                InfoChip(text = "${medication.durationDays} ngày")
            }

            if (medication.purposePlainVietnamese.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = medication.purposePlainVietnamese,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (medication.sideEffectsWarning.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WarningAmberBg.copy(alpha = 0.75f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = medication.sideEffectsWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78350F)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            val scheduledTimesText = existingReminder?.times
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
            val suggestedTimesText = medication.suggestedReminderTimes
                .ifEmpty { listOf("08:00") }
                .joinToString(", ")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (existingReminder != null) NormalGreenBg.copy(alpha = 0.55f)
                        else TealLightContainer.copy(alpha = 0.45f)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (existingReminder != null) Icons.Default.AlarmOn else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (existingReminder != null) NormalGreen else TealPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (existingReminder != null) {
                                if (existingReminder.isActive) "Đã đặt nhắc: $scheduledTimesText"
                                else "Đang tắt: $scheduledTimesText"
                            } else {
                                "Giờ nhắc: $suggestedTimesText"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (existingReminder != null) NormalGreen else TealDark
                        )
                    }
                    Text(
                        text = if (existingReminder != null) {
                            "${existingReminder.dosage} • ${existingReminder.mealTiming.labelVi}"
                        } else {
                            "Bấm để chọn giờ uống (9h, 10h...)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (existingReminder != null) {
                    FilledTonalButton(
                        onClick = onCreateOrEditReminder,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Sửa giờ")
                    }
                } else {
                    Button(
                        onClick = onCreateOrEditReminder,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AlarmAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Chọn giờ & Nhắc", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualPrescriptionScheduleCard(
    medications: List<MedicationItem>,
    reminderByMedKey: Map<String, MedicationReminder>,
    profileId: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TealPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lịch uống trực quan Sáng – Trưa – Chiều – Tối",
                    style = MaterialTheme.typography.titleMedium,
                    color = TealPrimary
                )
            }
            DaySlot.entries.forEach { slot ->
                val medsInSlot = medications.filter { it.daySlots.contains(slot) }
                if (medsInSlot.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = TealLightContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(66.dp)
                        ) {
                            Text(
                                text = slot.labelVi,
                                color = TealDark,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            medsInSlot.forEach { med ->
                                val saved = reminderByMedKey[LocalMedicalStore.normalizedMedicationKey(profileId, med.name)]
                                val timeText = saved?.times?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                    ?: med.suggestedReminderTimes.takeIf { it.isNotEmpty() }?.joinToString(", ")
                                val timingLabel = saved?.mealTiming?.labelVi ?: med.mealTiming.labelVi
                                val suffix = listOfNotNull(timeText, timingLabel).joinToString(" • ")
                                Text(
                                    text = "• ${med.name} (${med.dosage}) — $suffix",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MedicalDisclaimerCard(disclaimer: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = InfoBlueBg.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = InfoBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = disclaimer.ifBlank { MedicalRecord.DEFAULT_MEDICAL_DISCLAIMER },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1E3A8A)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordDetailScreen(
    record: MedicalRecord,
    existingReminders: List<MedicationReminder>,
    settings: AppSettings,
    isChatReplying: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSaveCustomReminder: (MedicationReminder) -> Unit,
    onSendChatQuestion: (String) -> Unit,
    onSendChatVoice: (File) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    var showNormalIndicators by remember(record.id) { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var pendingReminderForDialog by remember { mutableStateOf<MedicationReminder?>(null) }

    val reminderByMedKey = remember(existingReminders, record.profileId) {
        existingReminders
            .filter { it.profileId == record.profileId }
            .associateBy { LocalMedicalStore.normalizedMedicationKey(it.profileId, it.medicationName) }
    }

    // Dedicated compact in-app voice recorder (replaces full-screen Google Speech popup)
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
                    if (file != null) onSendChatVoice(file)
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

    // Auto-scroll to Q&A section when user sends a question or AI replies
    LaunchedEffect(record.chatHistory.size, isChatReplying) {
        if (record.chatHistory.isNotEmpty() || isChatReplying) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                listState.animateScrollToItem((totalItems - 2).coerceAtLeast(0))
            }
        }
    }

    // Split lab indicators into Traffic-Light priority groups
    val alertIndicators = remember(record.labIndicators) {
        record.labIndicators
            .filter { it.status != IndicatorStatus.NORMAL }
            .sortedBy { it.status.sortPriority }
    }
    val normalIndicators = remember(record.labIndicators) {
        record.labIndicators.filter { it.status == IndicatorStatus.NORMAL }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = record.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = listOf(record.documentType.labelVi, record.recordDate)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa phiếu", tint = AlertRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Docked bottom Q&A bar: rises directly above the keyboard (never hidden) and hosts compact in-app mic bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    if (isRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AlertRedBg.copy(alpha = 0.78f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
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
                                    text = "Đang ghi âm câu hỏi...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AlertRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "00:%02d • Bấm nút xanh để gửi".format(recordingSeconds),
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
                                        onSendChatVoice(audioFile)
                                    } else {
                                        onShowMessage("Đoạn ghi âm quá ngắn, hãy nói rõ hơn nhé.")
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
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                placeholder = { Text("Hỏi thêm về phiếu này...") },
                                singleLine = false,
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
                                enabled = !isChatReplying
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Ghi âm trực tiếp", tint = TealPrimary)
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    if (chatInput.isNotBlank()) {
                                        keyboardController?.hide()
                                        onSendChatQuestion(chatInput)
                                        chatInput = ""
                                    }
                                },
                                enabled = chatInput.isNotBlank() && !isChatReplying
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi", tint = TealPrimary)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Original scanned image preview
            val imageSource = record.localImagePath?.takeIf { File(it).exists() } ?: record.cloudImageUrl
            if (imageSource != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                    ) {
                        AsyncImage(
                            model = if (imageSource.startsWith("http")) imageSource else File(imageSource),
                            contentDescription = "Ảnh tài liệu gốc",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Summary Bullets Card (3-4 bullet points, easy to scan)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = TealLightContainer.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TealPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Tóm tắt tổng quan dễ hiểu",
                                style = MaterialTheme.typography.titleMedium,
                                color = TealDark
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (record.summaryBullets.isNotEmpty()) {
                            record.summaryBullets.forEach { bullet ->
                                Text(
                                    text = "• $bullet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        } else {
                            Text(
                                text = record.overallSummary.ifBlank { "Chưa có tóm tắt." },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (record.facilityName.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Cơ sở y tế: ${record.facilityName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText
                            )
                        }
                    }
                }
            }

            // Traffic-Light Lab Results Section: Red & Yellow pushed to top, Green collapsible
            if (record.labIndicators.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = TealPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (alertIndicators.isNotEmpty()) {
                                "Chỉ số cần chú ý trước (${alertIndicators.size})"
                            } else {
                                "Kết quả chỉ số xét nghiệm"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                items(alertIndicators) { indicator ->
                    LabIndicatorCard(indicator)
                }

                if (normalIndicators.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNormalIndicators = !showNormalIndicators },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = NormalGreenBg.copy(alpha = 0.65f)),
                            border = BorderStroke(1.dp, NormalGreen.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🟢 ${normalIndicators.size} chỉ số bình thường (bấm để ${if (showNormalIndicators) "thu gọn" else "xem chi tiết"})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NormalGreen
                                )
                                Icon(
                                    imageVector = if (showNormalIndicators) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = NormalGreen
                                )
                            }
                        }
                    }

                    if (showNormalIndicators) {
                        items(normalIndicators) { indicator ->
                            LabIndicatorCard(indicator)
                        }
                    }
                }
            }

            // Prescription Section: Visual Overview + Unified Per-Medication Reminder Control
            if (record.medications.isNotEmpty()) {
                item {
                    VisualPrescriptionScheduleCard(
                        medications = record.medications,
                        reminderByMedKey = reminderByMedKey,
                        profileId = record.profileId
                    )
                }

                item {
                    Text(
                        text = "Chi tiết từng loại thuốc & Lịch nhắc uống",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(record.medications) { med ->
                    val existing = reminderByMedKey[
                        LocalMedicalStore.normalizedMedicationKey(record.profileId, med.name)
                    ]
                    MedicationCard(
                        medication = med,
                        existingReminder = existing,
                        onCreateOrEditReminder = {
                            if (existing != null) {
                                pendingReminderForDialog = existing
                            } else {
                                val defaultNote = listOf(med.sideEffectsWarning, med.categoryShort)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                    .ifBlank { med.purposePlainVietnamese.take(120) }
                                pendingReminderForDialog = MedicationReminder(
                                    id = "",
                                    profileId = record.profileId,
                                    medicationName = med.name,
                                    dosage = med.dosage.ifBlank { "Theo đơn" },
                                    mealTiming = med.mealTiming,
                                    times = med.suggestedReminderTimes.ifEmpty { listOf("09:00") },
                                    note = defaultNote,
                                    sourceRecordId = record.id,
                                    isActive = true
                                )
                            }
                        }
                    )
                }
            }

            // Diet & Lifestyle Advice
            if (record.generalDietAdvice.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NormalGreenBg.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Lời khuyên ăn uống & sinh hoạt an toàn",
                                style = MaterialTheme.typography.titleMedium,
                                color = NormalGreen
                            )
                            Spacer(Modifier.height(6.dp))
                            record.generalDietAdvice.forEach { advice ->
                                Text(
                                    text = "• $advice",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Suggested Smart Questions for the Doctor
            if (record.suggestedDoctorQuestions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = InfoBlueBg.copy(alpha = 0.45f)),
                        border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = InfoBlue)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Gợi ý câu hỏi cho bác sĩ",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = InfoBlue
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val allQ = record.suggestedDoctorQuestions
                                            .mapIndexed { i, q -> "${i + 1}. $q" }
                                            .joinToString("\n")
                                        clipboard.setText(AnnotatedString(allQ))
                                        onShowMessage("Đã sao chép danh sách câu hỏi cho bác sĩ!")
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Sao chép câu hỏi",
                                        tint = InfoBlue
                                    )
                                }
                            }
                            record.suggestedDoctorQuestions.forEachIndexed { idx, q ->
                                Text(
                                    text = "${idx + 1}. $q",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Red Flags
            if (record.redFlags.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AlertRedBg.copy(alpha = 0.65f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Dấu hiệu cần gặp bác sĩ ngay",
                                style = MaterialTheme.typography.titleMedium,
                                color = AlertRed
                            )
                            Spacer(Modifier.height(6.dp))
                            record.redFlags.forEach { flag ->
                                Text(
                                    text = "• $flag",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF7F1D1D),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Feature C: Contextual Q&A Chat + Mic right under the scanned result!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, TealPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = TealPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Trợ lý Hỏi – Đáp về phiếu này",
                                style = MaterialTheme.typography.titleMedium,
                                color = TealPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Hỏi bằng chữ hoặc bấm Micro để nói (ví dụ: uống chung với sữa được không, đau dạ dày cần lưu ý gì?).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )

                        Spacer(Modifier.height(8.dp))
                        val quickPrompts = if (record.medications.isNotEmpty()) {
                            listOf(
                                "Thuốc trong đơn uống chung với sữa được không?",
                                "Người bị đau dạ dày uống đơn này cần lưu ý gì?"
                            )
                        } else {
                            listOf(
                                "Nên ăn uống thế nào trong 1 tháng tới?",
                                "Các chỉ số cao này có nguy hiểm ngay không?"
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickPrompts.forEach { prompt ->
                                AssistChip(
                                    onClick = { onSendChatQuestion(prompt) },
                                    label = { Text(prompt, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }

                        if (record.chatHistory.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                record.chatHistory.forEach { msg ->
                                    ChatBubble(msg)
                                }
                            }
                        }

                        if (isChatReplying) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Trợ lý AI đang trả lời...", style = MaterialTheme.typography.bodySmall, color = MutedText)
                            }
                        }
                    }
                }
            }

            item {
                MedicalDisclaimerCard(record.medicalDisclaimer)
            }
        }
    }

    pendingReminderForDialog?.let { initialReminder ->
        AddEditMedicationReminderDialog(
            profileId = record.profileId,
            initial = initialReminder,
            defaultTimes = listOf(
                settings.morningTime,
                settings.noonTime,
                settings.afternoonTime,
                settings.eveningTime
            ),
            onDismiss = { pendingReminderForDialog = null },
            onSave = { saved ->
                onSaveCustomReminder(saved)
                pendingReminderForDialog = null
            }
        )
    }
}

@Composable
private fun ChatBubble(message: RecordChatMessage) {
    val bg = if (message.isUser) TealPrimary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Text(
                text = if (message.isUser) AnnotatedString(message.text) else formatRecordChatMarkdown(message.text),
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(11.dp)
            )
        }
    }
}

private fun formatRecordChatMarkdown(raw: String): AnnotatedString {
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
