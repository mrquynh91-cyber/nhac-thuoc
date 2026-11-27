package com.medlens.ai

import android.app.Application
import com.medlens.ai.data.FirebaseSyncRepository
import com.medlens.ai.data.GeminiRepository
import com.medlens.ai.data.LocalMedicalStore
import com.medlens.ai.ota.GitHubOtaUpdater
import com.medlens.ai.reminder.MedicationNotificationHelper
import com.medlens.ai.reminder.MedicationReminderScheduler

class MedLensApplication : Application() {
    lateinit var localStore: LocalMedicalStore
        private set
    lateinit var geminiRepository: GeminiRepository
        private set
    lateinit var firebaseSyncRepository: FirebaseSyncRepository
        private set
    lateinit var reminderScheduler: MedicationReminderScheduler
        private set
    lateinit var otaUpdater: GitHubOtaUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        MedicationNotificationHelper.ensureChannel(this)
        localStore = LocalMedicalStore(this)
        geminiRepository = GeminiRepository(this)
        firebaseSyncRepository = FirebaseSyncRepository(this, localStore)
        reminderScheduler = MedicationReminderScheduler(this)
        otaUpdater = GitHubOtaUpdater(this)

        // Ensure all active medication alarms stay scheduled
        reminderScheduler.rescheduleAll(localStore.reminders.value)
    }
}
