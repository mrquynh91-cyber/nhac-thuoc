package com.medlens.ai.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.medlens.ai.MedLensApplication
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.LocalMedicalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledTime = intent.getStringExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME)
            ?: return
        val isSnooze = intent.getBooleanExtra(MedicationReminderScheduler.EXTRA_IS_SNOOZE, false)

        val store = (context.applicationContext as? MedLensApplication)?.localStore
            ?: LocalMedicalStore(context.applicationContext)

        val matchingReminders = store.reminders.value
            .filter { it.isActive && it.times.contains(scheduledTime) }

        if (matchingReminders.isNotEmpty()) {
            MedicationNotificationHelper.showGroupedReminderNotification(
                context = context,
                scheduledTime = scheduledTime,
                remindersInSlot = matchingReminders,
                isDemoTest = false
            )
        }

        if (!isSnooze && matchingReminders.isNotEmpty()) {
            MedicationReminderScheduler(context).scheduleTimeSlot(
                timeStr = scheduledTime,
                forceNextDay = true
            )
        }

        val settings = store.settings.value
        if (matchingReminders.isNotEmpty() &&
            settings.telegramEnabled &&
            settings.telegramNotifyOnDue &&
            settings.hasTelegramConfigured
        ) {
            val profileMap = store.profiles.value.associateBy { it.id }
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    TelegramNotifier.sendDueMedicationReminder(
                        settings = settings,
                        scheduledTime = scheduledTime,
                        reminders = matchingReminders,
                        profileMap = profileMap,
                        isDemoTest = false
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class MedicationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledTime = intent.getStringExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME)
            ?: return
        val notificationId = intent.getIntExtra(MedicationNotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        val store = (context.applicationContext as? MedLensApplication)?.localStore
            ?: LocalMedicalStore(context.applicationContext)
        val settings = store.settings.value
        val profileMap = store.profiles.value.associateBy { it.id }
        val matching = store.reminders.value.filter { it.isActive && it.times.contains(scheduledTime) }

        when (intent.action) {
            MedicationNotificationHelper.ACTION_MARK_GROUP_TAKEN -> {
                matching.forEach { reminder ->
                    store.logMedicationIntake(
                        reminderId = reminder.id,
                        medicationName = reminder.medicationName,
                        scheduledTime = scheduledTime,
                        status = IntakeStatus.TAKEN,
                        profileId = reminder.profileId
                    )
                }
                if (matching.isNotEmpty() &&
                    settings.telegramEnabled &&
                    settings.telegramNotifyOnTaken &&
                    settings.hasTelegramConfigured
                ) {
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            TelegramNotifier.sendDoseTakenConfirmation(
                                settings = settings,
                                scheduledTime = scheduledTime,
                                takenReminders = matching,
                                profileMap = profileMap
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }

            MedicationNotificationHelper.ACTION_SNOOZE_GROUP_15 -> {
                MedicationReminderScheduler(context.applicationContext).scheduleGroupSnooze(
                    scheduledTime = scheduledTime,
                    delayMinutes = 15
                )
                if (settings.telegramEnabled &&
                    settings.telegramNotifyOnSnoozeOrSkip &&
                    settings.hasTelegramConfigured
                ) {
                    val pendingResult = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            TelegramNotifier.sendDoseSnoozedOrSkippedAlert(
                                settings = settings,
                                scheduledTime = scheduledTime,
                                reminders = matching,
                                profileMap = profileMap,
                                isSnooze = true,
                                delayMinutes = 15
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }

        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val store = (context.applicationContext as? MedLensApplication)?.localStore
            ?: LocalMedicalStore(context.applicationContext)
        MedicationReminderScheduler(context.applicationContext).rescheduleAll(store.reminders.value)
    }
}
