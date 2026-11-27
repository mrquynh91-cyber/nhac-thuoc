package com.medlens.ai.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medlens.ai.BuildConfig
import com.medlens.ai.data.AppSettings
import com.medlens.ai.data.DocumentType
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.IndicatorStatus
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.ota.OtaUpdateState
import com.medlens.ai.ui.ScheduledDoseItem
import com.medlens.ai.ui.components.AddEditMedicationReminderDialog
import com.medlens.ai.ui.components.MedicalDisclaimerCard
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.InfoBlue
import com.medlens.ai.ui.theme.InfoBlueBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.NormalGreen
import com.medlens.ai.ui.theme.NormalGreenBg
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg

@Composable
fun HomeScreen(
    profiles: List<FamilyProfile>,
    activeProfile: FamilyProfile,
    records: List<MedicalRecord>,
    todayDoses: List<ScheduledDoseItem>,
    settings: AppSettings,
    otaState: OtaUpdateState,
    onSelectProfile: (String) -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToRecords: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenMedicationManager: () -> Unit,
    onSaveReminder: (MedicationReminder) -> Unit,
    onOpenRecord: (MedicalRecord) -> Unit,
    onMarkDoseTaken: (ScheduledDoseItem) -> Unit,
    onLoadSampleLab: () -> Unit,
    onLoadSamplePrescription: () -> Unit
) {
    val takenCount = todayDoses.count { it.todayStatus == IntakeStatus.TAKEN }
    val totalCount = todayDoses.size
    val progress = if (totalCount > 0) takenCount.toFloat() / totalCount.toFloat() else 0f
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var showQuickAddReminder by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Family Profile Selector Bar at top
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { profileMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = TealDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Hồ sơ: ${activeProfile.name}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TealDark
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Chọn hồ sơ",
                                tint = TealDark
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = profileMenuExpanded,
                        onDismissRequest = { profileMenuExpanded = false }
                    ) {
                        profiles.forEach { prof ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${prof.name} (${prof.relationship})",
                                        fontWeight = if (prof.id == activeProfile.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    profileMenuExpanded = false
                                    onSelectProfile(prof.id)
                                }
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Hero Banner: Hiểu Đơn tagline + Quick Scan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(TealPrimary, TealDark)))
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            text = "Hiểu Đơn • MedRead VN",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Chụp là hiểu – Không còn hoang mang trước chữ bác sĩ và bảng chỉ số y khoa.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onNavigateToScan,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = TealDark
                                )
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quét AI ngay", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = onNavigateToRecords,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Sổ khám (${records.size})")
                            }
                        }
                    }
                }
            }
        }

        // OTA Update Banner if available
        if (otaState is OtaUpdateState.UpdateAvailable) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSettings() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmberBg),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = WarningAmber)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Có bản cập nhật OTA mới (${otaState.release.tagName})",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = "Bấm để tải và cài đặt trực tiếp từ GitHub Releases.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WarningAmber)
                    }
                }
            }
        }

        // Today's Medication Doses Card ("Cữ thuốc hôm nay")
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = TealPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Cữ thuốc hôm nay • ${activeProfile.name}",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Quản lý ›",
                            style = MaterialTheme.typography.labelLarge,
                            color = TealPrimary,
                            modifier = Modifier.clickable { onOpenMedicationManager() }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (totalCount == 0) "Chưa có lịch uống thuốc hôm nay" else "Hoàn thành $takenCount / $totalCount cữ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                        if (totalCount > 0) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = TealPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = NormalGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    if (todayDoses.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            todayDoses.take(4).forEach { dose ->
                                val isTaken = dose.todayStatus == IntakeStatus.TAKEN
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isTaken) NormalGreenBg.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isTaken) NormalGreenBg else InfoBlueBg,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = dose.scheduledTime,
                                            color = if (isTaken) NormalGreen else InfoBlue,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dose.reminder.medicationName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${dose.reminder.dosage} • ${dose.reminder.mealTiming.labelVi}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedText
                                        )
                                        dose.reminder.courseProgressSummary()?.let { progress ->
                                            Text(
                                                text = progress,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TealPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (isTaken) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Đã uống",
                                            tint = NormalGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        FilledTonalButton(
                                            onClick = { onMarkDoseTaken(dose) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Đã uống")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showQuickAddReminder = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thêm thuốc")
                        }
                        OutlinedButton(
                            onClick = onOpenMedicationManager,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Sửa / Xóa lịch")
                        }
                    }
                }
            }
        }

        // Latest 3 Medical Records
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3 phiếu khám gần nhất (${activeProfile.name})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (records.isNotEmpty()) {
                    Text(
                        text = "Xem sổ khám",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealPrimary,
                        modifier = Modifier.clickable { onNavigateToRecords() }
                    )
                }
            }
        }

        if (records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Hồ sơ '${activeProfile.name}' chưa có phiếu khám nào",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Chụp ảnh đơn thuốc, phiếu xét nghiệm máu hoặc tải file PDF bệnh viện để AI giải mã tức thì.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onLoadSampleLab,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text("Mẫu xét nghiệm", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onLoadSamplePrescription,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text("Mẫu đơn thuốc", maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            items(records.take(3), key = { it.id }) { record ->
                MedicalRecordSummaryRow(record = record, onClick = { onOpenRecord(record) })
            }
        }

        item {
            MedicalDisclaimerCard(MedicalRecord.DEFAULT_MEDICAL_DISCLAIMER)
        }
    }

    if (showQuickAddReminder) {
        AddEditMedicationReminderDialog(
            profileId = activeProfile.id,
            initial = null,
            defaultTimes = listOf(
                settings.morningTime,
                settings.noonTime,
                settings.afternoonTime,
                settings.eveningTime
            ),
            onDismiss = { showQuickAddReminder = false },
            onSave = { saved ->
                onSaveReminder(saved)
                showQuickAddReminder = false
            }
        )
    }
}

@Composable
fun MedicalRecordSummaryRow(
    record: MedicalRecord,
    onClick: () -> Unit
) {
    val redCount = record.labIndicators.count {
        it.status == IndicatorStatus.HIGH || it.status == IndicatorStatus.LOW || it.status == IndicatorStatus.CRITICAL
    }
    val yellowCount = record.labIndicators.count { it.status == IndicatorStatus.BORDERLINE }
    val icon = when (record.documentType) {
        DocumentType.LAB_RESULT -> Icons.Default.Science
        DocumentType.PRESCRIPTION -> Icons.Default.Medication
        DocumentType.MIXED -> Icons.Default.Description
    }
    val badgeBg = when (record.documentType) {
        DocumentType.LAB_RESULT -> NormalGreenBg
        DocumentType.PRESCRIPTION -> InfoBlueBg
        DocumentType.MIXED -> WarningAmberBg
    }
    val badgeFg = when (record.documentType) {
        DocumentType.LAB_RESULT -> NormalGreen
        DocumentType.PRESCRIPTION -> InfoBlue
        DocumentType.MIXED -> WarningAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = badgeFg)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(record.documentType.labelVi, record.recordDate)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (redCount > 0) {
                        Surface(color = AlertRedBg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "🔴 $redCount bất thường",
                                color = AlertRed,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (yellowCount > 0) {
                        Surface(color = WarningAmberBg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "🟡 $yellowCount cận ngưỡng",
                                color = WarningAmber,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (record.labIndicators.isNotEmpty() && redCount == 0 && yellowCount == 0) {
                        Surface(color = NormalGreenBg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "🟢 Tất cả bình thường",
                                color = NormalGreen,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (record.medications.isNotEmpty()) {
                        Surface(color = InfoBlueBg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "${record.medications.size} thuốc",
                                color = InfoBlue,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MutedText)
        }
    }
}
