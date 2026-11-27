package com.medlens.ai.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.medlens.ai.MedLensApplication
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.LocalMedicalStore
import com.medlens.ai.data.MealTiming

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(MedicationNotificationHelper.EXTRA_REMINDER_ID) ?: return
        val medName = intent.getStringExtra(MedicationNotificationHelper.EXTRA_MED_NAME) ?: "Thuốc theo đơn"
        val dosage = intent.getStringExtra(MedicationNotificationHelper.EXTRA_DOSAGE) ?: "Theo chỉ định"
        val mealName = intent.getStringExtra(MedicationNotificationHelper.EXTRA_MEAL_TIMING)
            ?: MealTiming.AFTER_MEAL.name
        val mealTiming = runCatching { MealTiming.valueOf(mealName) }.getOrDefault(MealTiming.AFTER_MEAL)
        val scheduledTime = intent.getStringExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME) ?: "08:00"
        val isSnooze = intent.getBooleanExtra(MedicationReminderScheduler.EXTRA_IS_SNOOZE, false)

        MedicationNotificationHelper.showReminderNotification(
            context = context,
            reminderId = reminderId,
            medicationName = medName,
            dosage = dosage,
            mealTiming = mealTiming,
            scheduledTime = scheduledTime
        )

        if (!isSnooze) {
            MedicationReminderScheduler(context).scheduleSingleTime(
                reminderId = reminderId,
                medicationName = medName,
                dosage = dosage,
                mealTiming = mealTiming,
                timeStr = scheduledTime,
                forceNextDay = true
            )
        }
    }
}

class MedicationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(MedicationNotificationHelper.EXTRA_REMINDER_ID) ?: return
        val medName = intent.getStringExtra(MedicationNotificationHelper.EXTRA_MED_NAME) ?: "Thuốc"
        val dosage = intent.getStringExtra(MedicationNotificationHelper.EXTRA_DOSAGE) ?: ""
        val mealName = intent.getStringExtra(MedicationNotificationHelper.EXTRA_MEAL_TIMING)
            ?: MealTiming.AFTER_MEAL.name
        val mealTiming = runCatching { MealTiming.valueOf(mealName) }.getOrDefault(MealTiming.AFTER_MEAL)
        val scheduledTime = intent.getStringExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME) ?: "08:00"
        val notificationId = intent.getIntExtra(MedicationNotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        when (intent.action) {
            MedicationNotificationHelper.ACTION_MARK_TAKEN -> {
                val store = (context.applicationContext as? MedLensApplication)?.localStore
                    ?: LocalMedicalStore(context.applicationContext)
                store.logMedicationIntake(
                    reminderId = reminderId,
                    medicationName = medName,
                    scheduledTime = scheduledTime,
                    status = IntakeStatus.TAKEN
                )
            }

            MedicationNotificationHelper.ACTION_SNOOZE_15 -> {
                MedicationReminderScheduler(context.applicationContext).scheduleSnooze(
                    reminderId = reminderId,
                    medicationName = medName,
                    dosage = dosage,
                    mealTiming = mealTiming,
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
