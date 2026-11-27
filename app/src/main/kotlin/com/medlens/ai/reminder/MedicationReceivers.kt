package com.medlens.ai.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.medlens.ai.MedLensApplication
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.LocalMedicalStore

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
    }
}

class MedicationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledTime = intent.getStringExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME)
            ?: return
        val notificationId = intent.getIntExtra(MedicationNotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        when (intent.action) {
            MedicationNotificationHelper.ACTION_MARK_GROUP_TAKEN -> {
                val store = (context.applicationContext as? MedLensApplication)?.localStore
                    ?: LocalMedicalStore(context.applicationContext)
                val matching = store.reminders.value.filter { it.isActive && it.times.contains(scheduledTime) }
                matching.forEach { reminder ->
                    store.logMedicationIntake(
                        reminderId = reminder.id,
                        medicationName = reminder.medicationName,
                        scheduledTime = scheduledTime,
                        status = IntakeStatus.TAKEN,
                        profileId = reminder.profileId
                    )
                }
            }

            MedicationNotificationHelper.ACTION_SNOOZE_GROUP_15 -> {
                MedicationReminderScheduler(context.applicationContext).scheduleGroupSnooze(
                    scheduledTime = scheduledTime,
                    delayMinutes = 15
                )
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
