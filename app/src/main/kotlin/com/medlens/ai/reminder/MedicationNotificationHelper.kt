package com.medlens.ai.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicationReminder

object MedicationNotificationHelper {
    const val CHANNEL_ID = "medlens_medication_reminders"
    private const val CHANNEL_NAME = "Nhắc uống thuốc"
    private const val CHANNEL_DESC = "Thông báo nhắc nhở uống thuốc theo cữ kèm danh sách cách uống"

    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_IS_DEMO_TEST = "extra_is_demo_test"

    const val ACTION_MARK_GROUP_TAKEN = "com.medlens.ai.action.MARK_GROUP_TAKEN"
    const val ACTION_SNOOZE_GROUP_15 = "com.medlens.ai.action.SNOOZE_GROUP_15"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun createPopupIntent(
        context: Context,
        scheduledTime: String,
        notificationId: Int,
        isDemoTest: Boolean = false
    ): Intent {
        return Intent(context, MedicationAlarmPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_IS_DEMO_TEST, isDemoTest)
        }
    }

    fun showGroupedReminderNotification(
        context: Context,
        scheduledTime: String,
        remindersInSlot: List<MedicationReminder>,
        isDemoTest: Boolean = false
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false
        if (remindersInSlot.isEmpty()) return false

        val sorted = remindersInSlot.sortedBy { mealOrder(it.mealTiming) }
        val notificationId = ("group_slot_$scheduledTime").hashCode()

        val popupIntent = createPopupIntent(
            context = context,
            scheduledTime = scheduledTime,
            notificationId = notificationId,
            isDemoTest = isDemoTest
        )
        val popupPending = PendingIntent.getActivity(
            context,
            notificationId,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takenIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_MARK_GROUP_TAKEN
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val takenPending = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_GROUP_15
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (sorted.size == 1) {
            "⏰ $scheduledTime • Uống ${sorted.first().medicationName}"
        } else {
            "⏰ $scheduledTime • Đến giờ uống ${sorted.size} loại thuốc"
        }

        val summaryLine = sorted.joinToString(", ") { it.medicationName }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("Chạm để mở bảng hướng dẫn uống chi tiết")

        sorted.forEachIndexed { idx, r ->
            val notePart = r.note.takeIf { it.isNotBlank() }?.let { " — ${it.take(40)}" }.orEmpty()
            inboxStyle.addLine("${idx + 1}. ${r.medicationName} (${r.dosage}) • ${r.mealTiming.labelVi}$notePart")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(popupPending)
            .setFullScreenIntent(popupPending, true)
            .addAction(0, if (sorted.size > 1) "Đã uống tất cả ✓" else "Đã uống ✓", takenPending)
            .addAction(0, "Nhắc sau 15p", snoozePending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    fun mealOrder(timing: MealTiming): Int = when (timing) {
        MealTiming.BEFORE_MEAL -> 0
        MealTiming.WITH_MEAL -> 1
        MealTiming.AFTER_MEAL -> 2
        MealTiming.ANYTIME -> 3
    }
}
