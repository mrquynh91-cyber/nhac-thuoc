package com.medlens.ai.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.medicationName ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "1 viên") }
    var mealTiming by remember { mutableStateOf(initial?.mealTiming ?: MealTiming.AFTER_MEAL) }
    var selectedTimes by remember {
        mutableStateOf(
            initial?.times?.ifEmpty { listOf("09:00") }
                ?: listOf(defaultTimes.firstOrNull() ?: "09:00")
        )
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val quickPresetTimes = remember(defaultTimes, selectedTimes) {
        (listOf("07:00", "08:00", "09:00", "10:00", "12:00", "17:30", "19:30", "21:00") +
            defaultTimes + selectedTimes)
            .distinct()
            .sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null || initial.id.isBlank()) "Chọn giờ & Lưu lịch nhắc"
                else "Sửa lịch nhắc uống thuốc"
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên thuốc *") },
                    placeholder = { Text("Ví dụ: Getinomide 25mg") },
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

                Text("Cách uống (Trước / Sau ăn)", style = MaterialTheme.typography.labelMedium, color = MutedText)
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

                Text(
                    text = "Giờ uống thuốc (chọn nhanh 09:00, 10:00... hoặc mở đồng hồ):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresetTimes.forEach { t ->
                        val active = selectedTimes.contains(t)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedTimes = if (active) selectedTimes - t else (selectedTimes + t).sorted()
                                error = null
                            },
                            label = { Text(t) }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        val base = selectedTimes.lastOrNull()?.let { MedicationReminderScheduler.parseTime(it) }
                            ?: (9 to 0)
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val picked = "%02d:%02d".format(hourOfDay, minute)
                                selectedTimes = (selectedTimes + picked).distinct().sorted()
                                error = null
                            },
                            base.first,
                            base.second,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chọn giờ bất kỳ trên đồng hồ (24h)")
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Hướng dẫn / lưu ý cách uống") },
                    placeholder = { Text("Vd: Nuốt nguyên viên, uống cách sữa 2 giờ...") },
                    maxLines = 3,
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
                        error = "Vui lòng chọn ít nhất 1 khung giờ."
                        return@Button
                    }
                    val saved = if (initial != null && initial.id.isNotBlank()) {
                        initial.copy(
                            medicationName = name.trim(),
                            dosage = dosage.ifBlank { "Theo đơn" },
                            mealTiming = mealTiming,
                            times = selectedTimes.sorted(),
                            note = note.trim()
                        )
                    } else {
                        MedicationReminder(
                            profileId = profileId,
                            medicationName = name.trim(),
                            dosage = dosage.ifBlank { "Theo đơn" },
                            mealTiming = mealTiming,
                            times = selectedTimes.sorted(),
                            note = note.trim(),
                            sourceRecordId = initial?.sourceRecordId
                        )
                    }
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
