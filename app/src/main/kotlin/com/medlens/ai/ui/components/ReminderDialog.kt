package com.medlens.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.reminder.MedicationReminderScheduler
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.MutedText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationReminderDialog(
    profileId: String,
    initial: MedicationReminder?,
    defaultTimes: List<String>,
    onDismiss: () -> Unit,
    onSave: (MedicationReminder) -> Unit
) {
    var name by remember { mutableStateOf(initial?.medicationName ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "1 viên") }
    var mealTiming by remember { mutableStateOf(initial?.mealTiming ?: MealTiming.AFTER_MEAL) }
    var selectedTimes by remember {
        mutableStateOf(
            initial?.times?.ifEmpty { listOf(defaultTimes.first(), defaultTimes.last()) }
                ?: listOf(defaultTimes.first(), defaultTimes.last())
        )
    }
    var customTimeInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm lịch nhắc uống thuốc" else "Sửa lịch nhắc uống thuốc") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên thuốc *") },
                    placeholder = { Text("Ví dụ: Esomeprazole 40mg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Liều dùng") },
                    placeholder = { Text("Ví dụ: 1 viên / 1 gói") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Thời điểm uống", style = MaterialTheme.typography.labelMedium, color = MutedText)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealTiming.entries.forEach { timing ->
                        FilterChip(
                            selected = mealTiming == timing,
                            onClick = { mealTiming = timing },
                            label = { Text(timing.labelVi) }
                        )
                    }
                }
                Text("Khung giờ nhắc (chọn 1 hoặc nhiều)", style = MaterialTheme.typography.labelMedium, color = MutedText)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (defaultTimes + selectedTimes).distinct().sorted().forEach { t ->
                        val active = selectedTimes.contains(t)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedTimes = if (active) selectedTimes - t else (selectedTimes + t).sorted()
                            },
                            label = { Text(t) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customTimeInput,
                        onValueChange = { customTimeInput = it },
                        label = { Text("Giờ khác (HH:mm)") },
                        placeholder = { Text("08:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            val parsed = MedicationReminderScheduler.parseTime(customTimeInput)
                            if (parsed != null) {
                                val normalized = "%02d:%02d".format(parsed.first, parsed.second)
                                selectedTimes = (selectedTimes + normalized).distinct().sorted()
                                customTimeInput = ""
                                error = null
                            } else {
                                error = "Nhập giờ dạng HH:mm (ví dụ 08:30)."
                            }
                        }
                    ) {
                        Text("Thêm")
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú (tuỳ chọn)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = AlertRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Vui lòng nhập tên thuốc."
                        return@Button
                    }
                    if (selectedTimes.isEmpty()) {
                        error = "Chọn ít nhất 1 khung giờ."
                        return@Button
                    }
                    val saved = (initial ?: MedicationReminder(
                        profileId = profileId,
                        medicationName = name.trim(),
                        dosage = dosage.ifBlank { "Theo đơn" }
                    )).copy(
                        medicationName = name.trim(),
                        dosage = dosage.ifBlank { "Theo đơn" },
                        mealTiming = mealTiming,
                        times = selectedTimes.sorted(),
                        note = note.trim()
                    )
                    onSave(saved)
                }
            ) {
                Text("Lưu lịch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
