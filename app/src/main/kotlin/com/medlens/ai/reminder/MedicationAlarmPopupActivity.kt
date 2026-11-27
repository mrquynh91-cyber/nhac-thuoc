package com.medlens.ai.reminder

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.medlens.ai.MedLensApplication
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.LocalMedicalStore
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.ui.theme.InfoBlue
import com.medlens.ai.ui.theme.InfoBlueBg
import com.medlens.ai.ui.theme.MedLensTheme
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.NormalGreen
import com.medlens.ai.ui.theme.NormalGreenBg
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicationAlarmPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val scheduledTime = intent.getStringExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME) ?: "09:00"
        val notificationId = intent.getIntExtra(MedicationNotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val isDemoTest = intent.getBooleanExtra(MedicationNotificationHelper.EXTRA_IS_DEMO_TEST, false)

        val store = (applicationContext as? MedLensApplication)?.localStore
            ?: LocalMedicalStore(applicationContext)
        val profileMap = store.profiles.value.associateBy { it.id }

        val realReminders = store.reminders.value
            .filter { it.isActive && it.times.contains(scheduledTime) }

        val displayReminders = when {
            realReminders.isNotEmpty() -> realReminders
            isDemoTest -> buildDemoMealReminders(store.settings.value.activeProfileId, scheduledTime)
            else -> store.reminders.value.filter { it.isActive }.take(5)
        }

        setContent {
            MedLensTheme {
                MedicationAlarmPopupScreen(
                    scheduledTime = scheduledTime,
                    reminders = displayReminders,
                    profileMap = profileMap,
                    isDemoTest = isDemoTest && realReminders.isEmpty(),
                    onConfirmTaken = { checkedIds ->
                        if (!(isDemoTest && realReminders.isEmpty())) {
                            val takenList = displayReminders.filter { checkedIds.contains(it.id) }
                            takenList.forEach { reminder ->
                                store.logMedicationIntake(
                                    reminderId = reminder.id,
                                    medicationName = reminder.medicationName,
                                    scheduledTime = scheduledTime,
                                    status = IntakeStatus.TAKEN,
                                    profileId = reminder.profileId
                                )
                            }
                            val cfg = store.settings.value
                            if (takenList.isNotEmpty() && cfg.telegramEnabled && cfg.telegramNotifyOnTaken && cfg.hasTelegramConfigured) {
                                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                    TelegramNotifier.sendDoseTakenConfirmation(
                                        settings = cfg,
                                        scheduledTime = scheduledTime,
                                        takenReminders = takenList,
                                        profileMap = profileMap
                                    )
                                }
                            }
                        }
                        if (notificationId != 0) {
                            NotificationManagerCompat.from(this).cancel(notificationId)
                        }
                        finish()
                    },
                    onSnooze15 = {
                        if (!(isDemoTest && realReminders.isEmpty())) {
                            MedicationReminderScheduler(applicationContext).scheduleGroupSnooze(
                                scheduledTime = scheduledTime,
                                delayMinutes = 15
                            )
                            val cfg = store.settings.value
                            if (cfg.telegramEnabled && cfg.telegramNotifyOnSnoozeOrSkip && cfg.hasTelegramConfigured) {
                                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                    TelegramNotifier.sendDoseSnoozedOrSkippedAlert(
                                        settings = cfg,
                                        scheduledTime = scheduledTime,
                                        reminders = displayReminders,
                                        profileMap = profileMap,
                                        isSnooze = true,
                                        delayMinutes = 15
                                    )
                                }
                            }
                        }
                        if (notificationId != 0) {
                            NotificationManagerCompat.from(this).cancel(notificationId)
                        }
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun buildDemoMealReminders(profileId: String, time: String): List<MedicationReminder> = listOf(
        MedicationReminder(
            id = "demo_1",
            profileId = profileId,
            medicationName = "Esomeprazole 40mg",
            dosage = "1 viên",
            mealTiming = MealTiming.BEFORE_MEAL,
            times = listOf(time),
            note = "Nuốt nguyên viên với nước lọc trước bữa ăn 30 phút, không nhai."
        ),
        MedicationReminder(
            id = "demo_2",
            profileId = profileId,
            medicationName = "Getinomide 25mg (Tenofovir AF)",
            dosage = "1 viên",
            mealTiming = MealTiming.WITH_MEAL,
            times = listOf(time),
            note = "Uống cùng bữa ăn đúng giờ mỗi ngày, không tự ý bỏ liều."
        ),
        MedicationReminder(
            id = "demo_3",
            profileId = profileId,
            medicationName = "Vitamin B-Complex / Bổ gan",
            dosage = "1 viên",
            mealTiming = MealTiming.AFTER_MEAL,
            times = listOf(time),
            note = "Uống ngay sau khi ăn no với nhiều nước."
        )
    )
}

@Composable
private fun MedicationAlarmPopupScreen(
    scheduledTime: String,
    reminders: List<MedicationReminder>,
    profileMap: Map<String, FamilyProfile>,
    isDemoTest: Boolean,
    onConfirmTaken: (Set<String>) -> Unit,
    onSnooze15: () -> Unit,
    onDismiss: () -> Unit
) {
    val checkedMap = remember(reminders) {
        mutableStateMapOf<String, Boolean>().apply {
            reminders.forEach { put(it.id, true) }
        }
    }

    val groupedByMeal = remember(reminders) {
        reminders
            .groupBy { it.mealTiming }
            .entries
            .sortedBy { MedicationNotificationHelper.mealOrder(it.key) }
    }

    val checkedIds = checkedMap.filterValues { it }.keys

    Scaffold(
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onConfirmTaken(checkedIds) },
                        enabled = checkedIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (checkedIds.size == reminders.size) {
                                "Đã uống đủ tất cả (${checkedIds.size} thuốc)"
                            } else {
                                "Xác nhận đã uống (${checkedIds.size}/${reminders.size} thuốc)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onSnooze15,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nhắc lại sau 15 phút")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Bold High-Contrast Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(TealPrimary, TealDark)))
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Đến giờ uống thuốc • $scheduledTime",
                                style = MaterialTheme.typography.headlineSmall,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isDemoTest) {
                                "Xem trước popup mẫu khi có ${reminders.size} thuốc cùng 1 cữ"
                            } else {
                                "Cữ này có ${reminders.size} loại thuốc — sắp xếp theo thứ tự cách uống để dễ nhớ"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }

            // Grouped Checklist by Drinking Order
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedByMeal.forEachIndexed { groupIdx, (mealTiming, medsInGroup) ->
                    item(key = "header_${mealTiming.name}") {
                        val (bg, fg) = when (mealTiming) {
                            MealTiming.BEFORE_MEAL -> WarningAmberBg to WarningAmber
                            MealTiming.WITH_MEAL -> InfoBlueBg to InfoBlue
                            MealTiming.AFTER_MEAL -> NormalGreenBg to NormalGreen
                            MealTiming.ANYTIME -> MaterialTheme.colorScheme.surfaceVariant to TealDark
                        }
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Thứ tự ${groupIdx + 1}: ${mealTiming.labelVi.uppercase()} (${medsInGroup.size} thuốc)",
                                color = fg,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    items(medsInGroup, key = { it.id }) { reminder ->
                        val isChecked = checkedMap[reminder.id] == true
                        val memberName = profileMap[reminder.profileId]?.name
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkedMap[reminder.id] = !isChecked },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) {
                                    NormalGreenBg.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = 1.5.dp,
                                color = if (isChecked) NormalGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checkedMap[reminder.id] = it }
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = reminder.medicationName,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(color = InfoBlueBg, shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                text = reminder.dosage,
                                                color = InfoBlue,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = buildString {
                                            append("Cách uống: ${reminder.mealTiming.labelVi}")
                                            if (!memberName.isNullOrBlank()) {
                                                append(" • Hồ sơ: $memberName")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TealPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (reminder.note.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Lưu ý: ${reminder.note}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MutedText
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
}
