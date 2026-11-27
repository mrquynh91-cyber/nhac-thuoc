package com.medlens.ai.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medlens.ai.data.MedicationReminder
import java.util.Calendar

class MedicationReminderScheduler(private val context: Context) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val prefs = context.getSharedPreferences("medlens_alarm_slots", Context.MODE_PRIVATE)

    /**
     * Groups all active reminders by unique HH:mm time slot so multiple medications
     * taken at the same meal/hour fire ONE single grouped alarm & checklist popup!
     */
    fun rescheduleAll(reminders: List<MedicationReminder>) {
        val oldSlots = prefs.getStringSet(KEY_SCHEDULED_SLOTS, emptySet()).orEmpty()
        val activeSlots = reminders
            .filter { it.isActive }
            .flatMap { it.times }
            .map { it.trim() }
            .filter { parseTime(it) != null }
            .toSet()

        // Cancel slots that no longer have active medications
        (oldSlots - activeSlots).forEach { removedSlot ->
            cancelTimeSlot(removedSlot)
        }

        // Schedule 1 grouped daily alarm per unique active HH:mm slot
        activeSlots.forEach { slotTime ->
            scheduleTimeSlot(slotTime, forceNextDay = false)
        }

        prefs.edit().putStringSet(KEY_SCHEDULED_SLOTS, activeSlots).apply()
    }

    fun scheduleTimeSlot(timeStr: String, forceNextDay: Boolean = false) {
        val (hour, minute) = parseTime(timeStr) ?: return
        val triggerAt = nextTriggerMillis(hour, minute, forceNextDay)
        val pendingIntent = buildSlotPendingIntent(timeStr)
        scheduleExactOrFallback(triggerAt, pendingIntent)
    }

    fun cancelTimeSlot(timeStr: String) {
        alarmManager.cancel(buildSlotPendingIntent(timeStr))
    }

    fun scheduleGroupSnooze(
        scheduledTime: String,
        delayMinutes: Int = 15
    ) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val requestCode = ("snooze_slot_$scheduledTime").hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(triggerAt, pendingIntent)
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

    private fun buildSlotPendingIntent(timeStr: String): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationNotificationHelper.EXTRA_SCHEDULED_TIME, timeStr)
            putExtra(EXTRA_IS_SNOOZE, false)
        }
        val requestCode = ("alarm_slot_$timeStr").hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val KEY_SCHEDULED_SLOTS = "scheduled_time_slots"
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
