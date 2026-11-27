package com.medlens.ai.data

import android.content.Context
import android.content.SharedPreferences
import com.medlens.ai.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LocalMedicalStore(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dbFile: File
        get() = File(context.filesDir, "medlens_local_db.json")

    private val prefs: SharedPreferences =
        context.getSharedPreferences("medlens_settings", Context.MODE_PRIVATE)

    private val lock = Any()

    private val _profiles = MutableStateFlow<List<FamilyProfile>>(FamilyProfile.defaultProfiles())
    val profiles: StateFlow<List<FamilyProfile>> = _profiles.asStateFlow()

    private val _records = MutableStateFlow<List<MedicalRecord>>(emptyList())
    val records: StateFlow<List<MedicalRecord>> = _records.asStateFlow()

    private val _reminders = MutableStateFlow<List<MedicationReminder>>(emptyList())
    val reminders: StateFlow<List<MedicationReminder>> = _reminders.asStateFlow()

    private val _intakeLogs = MutableStateFlow<List<MedicationIntakeLog>>(emptyList())
    val intakeLogs: StateFlow<List<MedicationIntakeLog>> = _intakeLogs.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            if (!dbFile.exists()) {
                _profiles.value = FamilyProfile.defaultProfiles()
                return
            }
            runCatching {
                val raw = dbFile.readText()
                val snapshot = json.decodeFromString<LocalDatabaseSnapshot>(raw)
                _profiles.value = snapshot.profiles.ifEmpty { FamilyProfile.defaultProfiles() }
                _records.value = snapshot.records.sortedByDescending { it.createdAt }
                _reminders.value = snapshot.reminders.sortedByDescending { it.createdAt }
                _intakeLogs.value = snapshot.intakeLogs.sortedByDescending { it.loggedAt }
            }
        }
    }

    private fun persistToDisk() {
        synchronized(lock) {
            val snapshot = LocalDatabaseSnapshot(
                profiles = _profiles.value,
                records = _records.value,
                reminders = _reminders.value,
                intakeLogs = _intakeLogs.value
            )
            val tmpFile = File(context.filesDir, "medlens_local_db.json.tmp")
            tmpFile.writeText(json.encodeToString(snapshot))
            if (!tmpFile.renameTo(dbFile)) {
                dbFile.writeText(tmpFile.readText())
                tmpFile.delete()
            }
        }
    }

    private fun loadSettings(): AppSettings {
        val savedGeminiKey = prefs.getString(KEY_GEMINI_API_KEY, "")?.trim().orEmpty()
        val savedGithubRepo = prefs.getString(KEY_GITHUB_OTA_REPO, "")?.trim().orEmpty()
        val savedGithubToken = prefs.getString(KEY_GITHUB_OTA_TOKEN, "")?.trim().orEmpty()
        return AppSettings(
            geminiApiKey = savedGeminiKey.ifEmpty { BuildConfig.GEMINI_API_KEY },
            geminiModel = prefs.getString(KEY_GEMINI_MODEL, AppSettings.DEFAULT_GEMINI_MODEL)
                ?: AppSettings.DEFAULT_GEMINI_MODEL,
            githubOtaRepo = savedGithubRepo.ifEmpty { BuildConfig.GITHUB_OTA_REPO },
            githubOtaToken = savedGithubToken.ifEmpty { BuildConfig.GITHUB_OTA_TOKEN },
            morningTime = prefs.getString(KEY_MORNING_TIME, "07:00") ?: "07:00",
            noonTime = prefs.getString(KEY_NOON_TIME, "12:00") ?: "12:00",
            afternoonTime = prefs.getString(KEY_AFTERNOON_TIME, "17:30") ?: "17:30",
            eveningTime = prefs.getString(KEY_EVENING_TIME, "19:30") ?: "19:30",
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, true),
            autoCheckOta = prefs.getBoolean(KEY_AUTO_CHECK_OTA, true),
            lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED_AT, 0L),
            activeProfileId = prefs.getString(KEY_ACTIVE_PROFILE_ID, FamilyProfile.DEFAULT_SELF_ID)
                ?: FamilyProfile.DEFAULT_SELF_ID
        )
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(_settings.value)
        prefs.edit()
            .putString(KEY_GEMINI_API_KEY, updated.geminiApiKey.trim())
            .putString(KEY_GEMINI_MODEL, updated.geminiModel)
            .putString(KEY_GITHUB_OTA_REPO, updated.githubOtaRepo.trim())
            .putString(KEY_GITHUB_OTA_TOKEN, updated.githubOtaToken.trim())
            .putString(KEY_MORNING_TIME, updated.morningTime)
            .putString(KEY_NOON_TIME, updated.noonTime)
            .putString(KEY_AFTERNOON_TIME, updated.afternoonTime)
            .putString(KEY_EVENING_TIME, updated.eveningTime)
            .putBoolean(KEY_AUTO_SYNC, updated.autoSyncEnabled)
            .putBoolean(KEY_AUTO_CHECK_OTA, updated.autoCheckOta)
            .putLong(KEY_LAST_SYNCED_AT, updated.lastSyncedAt)
            .putString(KEY_ACTIVE_PROFILE_ID, updated.activeProfileId)
            .apply()
        _settings.value = updated
        return updated
    }

    fun upsertProfile(profile: FamilyProfile) {
        synchronized(lock) {
            val stamped = profile.copy(updatedAt = maxOf(profile.updatedAt, System.currentTimeMillis()))
            val existingIndex = _profiles.value.indexOfFirst { it.id == stamped.id }
            val updated = _profiles.value.toMutableList()
            if (existingIndex >= 0) {
                updated[existingIndex] = stamped
            } else {
                updated.add(stamped)
            }
            _profiles.value = updated
            persistToDisk()
        }
    }

    fun deleteProfile(profileId: String) {
        synchronized(lock) {
            if (_profiles.value.size <= 1) return
            _profiles.value = _profiles.value.filterNot { it.id == profileId }
            if (_settings.value.activeProfileId == profileId) {
                updateSettings { it.copy(activeProfileId = _profiles.value.first().id) }
            }
            persistToDisk()
        }
    }

    fun upsertRecord(record: MedicalRecord) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val stamped = record.copy(updatedAt = maxOf(record.updatedAt, now))
            val mutable = _records.value.filterNot { it.id == stamped.id }.toMutableList()
            mutable.add(stamped)
            _records.value = mutable.sortedByDescending { it.createdAt }
            persistToDisk()
        }
    }

    fun appendChatMessages(recordId: String, vararg messages: RecordChatMessage): MedicalRecord? {
        synchronized(lock) {
            val current = _records.value.firstOrNull { it.id == recordId } ?: return null
            val updated = current.copy(
                chatHistory = current.chatHistory + messages,
                updatedAt = System.currentTimeMillis()
            )
            upsertRecord(updated)
            return updated
        }
    }

    fun deleteRecord(recordId: String) {
        synchronized(lock) {
            _records.value = _records.value.filterNot { it.id == recordId }
            persistToDisk()
        }
    }

    fun upsertReminder(reminder: MedicationReminder) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val stamped = reminder.copy(
                times = reminder.times.distinct().sorted(),
                updatedAt = maxOf(reminder.updatedAt, now)
            )
            val mutable = _reminders.value.filterNot { it.id == stamped.id }.toMutableList()
            mutable.add(stamped)
            _reminders.value = mutable.sortedByDescending { it.createdAt }
            persistToDisk()
        }
    }

    fun deleteReminder(reminderId: String) {
        synchronized(lock) {
            _reminders.value = _reminders.value.filterNot { it.id == reminderId }
            persistToDisk()
        }
    }

    fun logMedicationIntake(
        reminderId: String,
        medicationName: String,
        scheduledTime: String,
        status: IntakeStatus,
        profileId: String = _settings.value.activeProfileId,
        dateKey: String = todayDateKey()
    ): MedicationIntakeLog {
        synchronized(lock) {
            val existing = _intakeLogs.value.firstOrNull {
                it.reminderId == reminderId &&
                    it.dateKey == dateKey &&
                    it.scheduledTime == scheduledTime
            }
            val log = MedicationIntakeLog(
                id = existing?.id ?: "${reminderId}_${dateKey}_${scheduledTime}",
                profileId = profileId,
                reminderId = reminderId,
                medicationName = medicationName,
                dateKey = dateKey,
                scheduledTime = scheduledTime,
                status = status,
                loggedAt = System.currentTimeMillis()
            )
            val updated = _intakeLogs.value.filterNot { it.id == log.id } + log
            _intakeLogs.value = updated.sortedByDescending { it.loggedAt }
            persistToDisk()
            return log
        }
    }

    fun mergeRemoteData(
        remoteProfiles: List<FamilyProfile> = emptyList(),
        remoteRecords: List<MedicalRecord>,
        remoteReminders: List<MedicationReminder>,
        remoteLogs: List<MedicationIntakeLog>
    ): MergeStats {
        synchronized(lock) {
            val profileMap = _profiles.value.associateBy { it.id }.toMutableMap()
            for (remote in remoteProfiles) {
                val local = profileMap[remote.id]
                if (local == null || remote.updatedAt > local.updatedAt) {
                    profileMap[remote.id] = remote.copy(
                        bhytImagePath = local?.bhytImagePath ?: remote.bhytImagePath
                    )
                }
            }

            val recordMap = _records.value.associateBy { it.id }.toMutableMap()
            var addedOrUpdatedRecords = 0
            for (remote in remoteRecords) {
                val local = recordMap[remote.id]
                if (local == null || remote.updatedAt > local.updatedAt) {
                    recordMap[remote.id] = remote.copy(
                        localImagePath = local?.localImagePath ?: remote.localImagePath,
                        localAttachmentPaths = local?.localAttachmentPaths?.ifEmpty { remote.localAttachmentPaths }
                            ?: remote.localAttachmentPaths
                    )
                    addedOrUpdatedRecords++
                }
            }

            val reminderMap = _reminders.value.associateBy { it.id }.toMutableMap()
            var addedOrUpdatedReminders = 0
            for (remote in remoteReminders) {
                val local = reminderMap[remote.id]
                if (local == null || remote.updatedAt > local.updatedAt) {
                    reminderMap[remote.id] = remote
                    addedOrUpdatedReminders++
                }
            }

            val logMap = _intakeLogs.value.associateBy { it.id }.toMutableMap()
            var addedOrUpdatedLogs = 0
            for (remote in remoteLogs) {
                val local = logMap[remote.id]
                if (local == null || remote.loggedAt > local.loggedAt) {
                    logMap[remote.id] = remote
                    addedOrUpdatedLogs++
                }
            }

            _profiles.value = profileMap.values.toList().ifEmpty { FamilyProfile.defaultProfiles() }
            _records.value = recordMap.values.sortedByDescending { it.createdAt }
            _reminders.value = reminderMap.values.sortedByDescending { it.createdAt }
            _intakeLogs.value = logMap.values.sortedByDescending { it.loggedAt }
            persistToDisk()

            return MergeStats(
                recordsMerged = addedOrUpdatedRecords,
                remindersMerged = addedOrUpdatedReminders,
                logsMerged = addedOrUpdatedLogs
            )
        }
    }

    fun exportBackupJson(appVersion: String = BuildConfig.VERSION_NAME): String {
        val bundle = BackupBundle(
            schemaVersion = 2,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            profiles = _profiles.value,
            records = _records.value,
            reminders = _reminders.value,
            intakeLogs = _intakeLogs.value
        )
        return json.encodeToString(bundle)
    }

    fun importBackupJson(rawJson: String): MergeStats {
        val bundle = json.decodeFromString<BackupBundle>(rawJson)
        return mergeRemoteData(
            remoteProfiles = bundle.profiles,
            remoteRecords = bundle.records,
            remoteReminders = bundle.reminders,
            remoteLogs = bundle.intakeLogs
        )
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GITHUB_OTA_REPO = "github_ota_repo"
        private const val KEY_GITHUB_OTA_TOKEN = "github_ota_token"
        private const val KEY_MORNING_TIME = "slot_morning_time"
        private const val KEY_NOON_TIME = "slot_noon_time"
        private const val KEY_AFTERNOON_TIME = "slot_afternoon_time"
        private const val KEY_EVENING_TIME = "slot_evening_time"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_AUTO_CHECK_OTA = "auto_check_ota"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

        fun todayDateKey(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}

data class MergeStats(
    val recordsMerged: Int,
    val remindersMerged: Int,
    val logsMerged: Int
)
