package com.medlens.ai.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicationReminder
import java.util.Calendar

class MedicationReminderScheduler(private val context: Context) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun rescheduleAll(reminders: List<MedicationReminder>) {
        reminders.forEach { reminder ->
            if (reminder.isActive) {
                scheduleReminder(reminder)
            } else {
                cancelReminder(reminder)
            }
        }
    }

    fun scheduleReminder(reminder: MedicationReminder) {
        if (!reminder.isActive) {
            cancelReminder(reminder)
            return
        }
        reminder.times.forEach { timeStr ->
            scheduleSingleTime(
                reminderId = reminder.id,
                medicationName = reminder.medicationName,
                dosage = reminder.dosage,
                mealTiming = reminder.mealTiming,
                timeStr = timeStr,
                forceNextDay = false
            )
        }
    }

    fun scheduleSingleTime(
        reminderId: String,
        medicationName: String,
        dosage: String,
        mealTiming: MealTiming,
        timeStr: String,
        forceNextDay: Boolean = false
    ) {
        val (hour, minute) = parseTime(timeStr) ?: return
        val triggerAt = nextTriggerMillis(hour, minute, forceNextDay)

        val pendingIntent = buildAlarmPendingIntent(
            reminderId = reminderId,
            medicationName = medicationName,
            dosage = dosage,
            mealTiming = mealTiming,
            timeStr = timeStr
        )

        scheduleExactOrFallback(triggerAt, pendingIntent)
    }

    fun scheduleSnooze(
        reminderId: String,
        medicationName: String,
        dosage: String,
        mealTiming: MealTiming,
        scheduledTime: String,
        delayMinutes: Int = 15
    ) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(MedicationNotificationHelper.EXTRA_MED_NAME, medicationName)
            putExtra(MedicationNotificationHelper.EXTRA_DOSAGE, dosage)
            putExtra(MedicationNotificationHelper.EXTRA_MEAL_TIMING, mealTiming.name)
            putExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val requestCode = ("snooze_${reminderId}_$scheduledTime").hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(triggerAt, pendingIntent)
    }

    fun cancelReminder(reminder: MedicationReminder) {
        reminder.times.forEach { timeStr ->
            val pendingIntent = buildAlarmPendingIntent(
                reminderId = reminder.id,
                medicationName = reminder.medicationName,
                dosage = reminder.dosage,
                mealTiming = reminder.mealTiming,
                timeStr = timeStr
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleExactOrFallback(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun buildAlarmPendingIntent(
        reminderId: String,
        medicationName: String,
        dosage: String,
        mealTiming: MealTiming,
        timeStr: String
    ): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(MedicationNotificationHelper.EXTRA_MED_NAME, medicationName)
            putExtra(MedicationNotificationHelper.EXTRA_DOSAGE, dosage)
            putExtra(MedicationNotificationHelper.EXTRA_MEAL_TIMING, mealTiming.name)
            putExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME, timeStr)
            putExtra(EXTRA_IS_SNOOZE, false)
        }
        val requestCode = ("alarm_${reminderId}_$timeStr").hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"

        fun parseTime(timeStr: String): Pair<Int, Int>? {
            val parts = timeStr.trim().split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }

        fun nextTriggerMillis(hour: Int, minute: Int, forceNextDay: Boolean = false): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (forceNextDay || !target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }
    }
}
