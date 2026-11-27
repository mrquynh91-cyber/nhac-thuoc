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

    private val _assistantChats = MutableStateFlow<Map<String, List<RecordChatMessage>>>(emptyMap())
    val assistantChats: StateFlow<Map<String, List<RecordChatMessage>>> = _assistantChats.asStateFlow()

    private val _savedAiChats = MutableStateFlow<List<SavedAiChatSession>>(emptyList())
    val savedAiChats: StateFlow<List<SavedAiChatSession>> = _savedAiChats.asStateFlow()

    private val _dietPlans = MutableStateFlow<Map<String, PatientDietPlan>>(emptyMap())
    val dietPlans: StateFlow<Map<String, PatientDietPlan>> = _dietPlans.asStateFlow()

    private val deletedProfileIds = mutableMapOf<String, Long>()
    private val deletedRecordIds = mutableMapOf<String, Long>()
    private val deletedReminderIds = mutableMapOf<String, Long>()

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
                deletedProfileIds.clear()
                deletedProfileIds.putAll(snapshot.deletedProfileIds)
                deletedRecordIds.clear()
                deletedRecordIds.putAll(snapshot.deletedRecordIds)
                deletedReminderIds.clear()
                deletedReminderIds.putAll(snapshot.deletedReminderIds)

                _profiles.value = snapshot.profiles.ifEmpty { FamilyProfile.defaultProfiles() }
                _records.value = snapshot.records.sortedByDescending { it.createdAt }
                val (dedupedReminders, droppedIds) = deduplicateReminders(snapshot.reminders)
                val now = System.currentTimeMillis()
                droppedIds.forEach { deletedReminderIds[it] = now }
                _reminders.value = dedupedReminders
                _intakeLogs.value = snapshot.intakeLogs.sortedByDescending { it.loggedAt }
                _assistantChats.value = snapshot.assistantChats
                _savedAiChats.value = snapshot.savedAiChats.sortedByDescending { it.savedAt }
                _dietPlans.value = snapshot.dietPlans
                if (droppedIds.isNotEmpty()) {
                    persistToDisk()
                }
            }
        }
    }

    private fun persistToDisk() {
        synchronized(lock) {
            val snapshot = LocalDatabaseSnapshot(
                profiles = _profiles.value,
                records = _records.value,
                reminders = _reminders.value,
                intakeLogs = _intakeLogs.value,
                assistantChats = _assistantChats.value,
                savedAiChats = _savedAiChats.value,
                dietPlans = _dietPlans.value,
                deletedProfileIds = deletedProfileIds.toMap(),
                deletedRecordIds = deletedRecordIds.toMap(),
                deletedReminderIds = deletedReminderIds.toMap()
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
        val savedProxyUrl = prefs.getString(KEY_GEMINI_PROXY_URL, "")?.trim().orEmpty()
        val savedProxyToken = prefs.getString(KEY_GEMINI_PROXY_TOKEN, "")?.trim().orEmpty()
        val savedGithubRepo = prefs.getString(KEY_GITHUB_OTA_REPO, "")?.trim().orEmpty()
        val savedGithubToken = prefs.getString(KEY_GITHUB_OTA_TOKEN, "")?.trim().orEmpty()
        val savedModel = prefs.getString(KEY_GEMINI_MODEL, AppSettings.DEFAULT_GEMINI_MODEL)
            ?.trim()
            .orEmpty()
        val resolvedModel = when {
            savedModel.isEmpty() -> AppSettings.DEFAULT_GEMINI_MODEL
            savedModel == "gemini-2.5-flash" || savedModel == "gemini-2.0-flash" -> AppSettings.DEFAULT_GEMINI_MODEL
            else -> savedModel
        }
        return AppSettings(
            geminiApiKey = savedGeminiKey.ifEmpty { BuildConfig.GEMINI_API_KEY },
            geminiProxyUrl = savedProxyUrl.ifEmpty { BuildConfig.GEMINI_PROXY_URL },
            geminiProxyToken = savedProxyToken.ifEmpty { BuildConfig.GEMINI_PROXY_TOKEN },
            geminiModel = resolvedModel,
            githubOtaRepo = savedGithubRepo.ifEmpty { BuildConfig.GITHUB_OTA_REPO },
            githubOtaToken = savedGithubToken.ifEmpty { BuildConfig.GITHUB_OTA_TOKEN },
            morningTime = prefs.getString(KEY_MORNING_TIME, "07:00") ?: "07:00",
            noonTime = prefs.getString(KEY_NOON_TIME, "12:00") ?: "12:00",
            afternoonTime = prefs.getString(KEY_AFTERNOON_TIME, "17:30") ?: "17:30",
            eveningTime = prefs.getString(KEY_EVENING_TIME, "19:30") ?: "19:30",
            telegramEnabled = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false),
            telegramBotToken = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "")?.trim().orEmpty(),
            telegramChatId = prefs.getString(KEY_TELEGRAM_CHAT_ID, "")?.trim().orEmpty(),
            telegramGroupName = prefs.getString(KEY_TELEGRAM_GROUP_NAME, "")?.trim().orEmpty(),
            telegramNotifyOnDue = prefs.getBoolean(KEY_TELEGRAM_NOTIFY_DUE, true),
            telegramNotifyOnTaken = prefs.getBoolean(KEY_TELEGRAM_NOTIFY_TAKEN, true),
            telegramNotifyOnSnoozeOrSkip = prefs.getBoolean(KEY_TELEGRAM_NOTIFY_SNOOZE_SKIP, true),
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
            .putString(KEY_GEMINI_PROXY_URL, updated.geminiProxyUrl.trim())
            .putString(KEY_GEMINI_PROXY_TOKEN, updated.geminiProxyToken.trim())
            .putString(KEY_GEMINI_MODEL, updated.geminiModel)
            .putString(KEY_GITHUB_OTA_REPO, updated.githubOtaRepo.trim())
            .putString(KEY_GITHUB_OTA_TOKEN, updated.githubOtaToken.trim())
            .putString(KEY_MORNING_TIME, updated.morningTime)
            .putString(KEY_NOON_TIME, updated.noonTime)
            .putString(KEY_AFTERNOON_TIME, updated.afternoonTime)
            .putString(KEY_EVENING_TIME, updated.eveningTime)
            .putBoolean(KEY_TELEGRAM_ENABLED, updated.telegramEnabled)
            .putString(KEY_TELEGRAM_BOT_TOKEN, updated.telegramBotToken.trim())
            .putString(KEY_TELEGRAM_CHAT_ID, updated.telegramChatId.trim())
            .putString(KEY_TELEGRAM_GROUP_NAME, updated.telegramGroupName.trim())
            .putBoolean(KEY_TELEGRAM_NOTIFY_DUE, updated.telegramNotifyOnDue)
            .putBoolean(KEY_TELEGRAM_NOTIFY_TAKEN, updated.telegramNotifyOnTaken)
            .putBoolean(KEY_TELEGRAM_NOTIFY_SNOOZE_SKIP, updated.telegramNotifyOnSnoozeOrSkip)
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
            deletedProfileIds.remove(profile.id)
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
            deletedProfileIds[profileId] = System.currentTimeMillis()
            _profiles.value = _profiles.value.filterNot { it.id == profileId }
            if (_settings.value.activeProfileId == profileId) {
                updateSettings { it.copy(activeProfileId = _profiles.value.first().id) }
            }
            persistToDisk()
        }
    }

    fun upsertRecord(record: MedicalRecord) {
        synchronized(lock) {
            deletedRecordIds.remove(record.id)
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

    fun appendAssistantMessages(profileId: String, vararg messages: RecordChatMessage) {
        synchronized(lock) {
            val currentMap = _assistantChats.value.toMutableMap()
            val updated = (currentMap[profileId].orEmpty() + messages).takeLast(60)
            currentMap[profileId] = updated
            _assistantChats.value = currentMap
            persistToDisk()
        }
    }

    fun clearAssistantChat(profileId: String) {
        synchronized(lock) {
            val currentMap = _assistantChats.value.toMutableMap()
            currentMap.remove(profileId)
            _assistantChats.value = currentMap
            persistToDisk()
        }
    }

    fun replaceAssistantChat(profileId: String, messages: List<RecordChatMessage>) {
        synchronized(lock) {
            val currentMap = _assistantChats.value.toMutableMap()
            currentMap[profileId] = messages.takeLast(60)
            _assistantChats.value = currentMap
            persistToDisk()
        }
    }

    fun saveAiChatSession(session: SavedAiChatSession) {
        synchronized(lock) {
            val updated = (_savedAiChats.value.filterNot { it.id == session.id } + session)
                .sortedByDescending { it.savedAt }
            _savedAiChats.value = updated
            persistToDisk()
        }
    }

    fun deleteSavedAiChatSession(sessionId: String) {
        synchronized(lock) {
            _savedAiChats.value = _savedAiChats.value.filterNot { it.id == sessionId }
            persistToDisk()
        }
    }

    fun saveDietPlan(profileId: String, plan: PatientDietPlan) {
        synchronized(lock) {
            val currentMap = _dietPlans.value.toMutableMap()
            currentMap[profileId] = plan.copy(updatedAt = System.currentTimeMillis())
            _dietPlans.value = currentMap
            persistToDisk()
        }
    }

    fun deleteRecord(recordId: String) {
        synchronized(lock) {
            deletedRecordIds[recordId] = System.currentTimeMillis()
            _records.value = _records.value.filterNot { it.id == recordId }
            persistToDisk()
        }
    }

    fun upsertReminder(reminder: MedicationReminder) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val cleanName = reminder.medicationName.trim()
            val targetKey = normalizedMedicationKey(reminder.profileId, cleanName)
            val existingById = _reminders.value.firstOrNull { it.id == reminder.id && reminder.id.isNotBlank() }
            val existingByName = _reminders.value.firstOrNull {
                normalizedMedicationKey(it.profileId, it.medicationName) == targetKey
            }
            val matched = existingById ?: existingByName

            val resolvedId = when {
                matched != null -> matched.id
                reminder.id.isNotBlank() -> reminder.id
                else -> java.util.UUID.randomUUID().toString()
            }
            deletedReminderIds.remove(resolvedId)

            val stamped = reminder.copy(
                id = resolvedId,
                medicationName = cleanName,
                times = reminder.times.distinct().sorted(),
                createdAt = matched?.createdAt ?: reminder.createdAt,
                updatedAt = maxOf(reminder.updatedAt, now)
            )
            // Record any duplicate reminder IDs that are collapsed by this upsert so cloud deletes them too
            _reminders.value.forEach { old ->
                if (old.id != stamped.id && normalizedMedicationKey(old.profileId, old.medicationName) == targetKey) {
                    deletedReminderIds[old.id] = now
                }
            }
            val mutable = _reminders.value
                .filterNot {
                    it.id == stamped.id ||
                        normalizedMedicationKey(it.profileId, it.medicationName) == targetKey
                }
                .toMutableList()
            mutable.add(stamped)
            _reminders.value = mutable.sortedByDescending { it.createdAt }
            persistToDisk()
        }
    }

    private fun deduplicateReminders(list: List<MedicationReminder>): Pair<List<MedicationReminder>, List<String>> {
        val kept = mutableListOf<MedicationReminder>()
        val droppedIds = mutableListOf<String>()
        list.groupBy { normalizedMedicationKey(it.profileId, it.medicationName) }
            .values
            .forEach { group ->
                val winner = group.maxByOrNull { maxOf(it.updatedAt, it.createdAt) }
                if (winner != null) {
                    kept.add(
                        winner.copy(
                            medicationName = winner.medicationName.trim(),
                            times = winner.times.distinct().sorted()
                        )
                    )
                    group.forEach { item ->
                        if (item.id != winner.id) {
                            droppedIds.add(item.id)
                        }
                    }
                }
            }
        return kept.sortedByDescending { it.createdAt } to droppedIds
    }

    fun deleteReminder(reminderId: String) {
        synchronized(lock) {
            deletedReminderIds[reminderId] = System.currentTimeMillis()
            _reminders.value = _reminders.value.filterNot { it.id == reminderId }
            persistToDisk()
        }
    }

    fun getPendingDeletes(): PendingDeletes = synchronized(lock) {
        PendingDeletes(
            profileIds = deletedProfileIds.toMap(),
            recordIds = deletedRecordIds.toMap(),
            reminderIds = deletedReminderIds.toMap()
        )
    }

    fun confirmRemoteDeletes(confirmed: PendingDeletes) {
        synchronized(lock) {
            var changed = false
            confirmed.profileIds.forEach { (id, ts) ->
                val current = deletedProfileIds[id]
                if (current != null && current <= ts) {
                    deletedProfileIds.remove(id)
                    changed = true
                }
            }
            confirmed.recordIds.forEach { (id, ts) ->
                val current = deletedRecordIds[id]
                if (current != null && current <= ts) {
                    deletedRecordIds.remove(id)
                    changed = true
                }
            }
            confirmed.reminderIds.forEach { (id, ts) ->
                val current = deletedReminderIds[id]
                if (current != null && current <= ts) {
                    deletedReminderIds.remove(id)
                    changed = true
                }
            }
            if (changed) persistToDisk()
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
        remoteLogs: List<MedicationIntakeLog>,
        clearMatchingTombstones: Boolean = false
    ): MergeStats {
        synchronized(lock) {
            if (clearMatchingTombstones) {
                remoteProfiles.forEach { deletedProfileIds.remove(it.id) }
                remoteRecords.forEach { deletedRecordIds.remove(it.id) }
                remoteReminders.forEach { deletedReminderIds.remove(it.id) }
            }

            val profileMap = _profiles.value.associateBy { it.id }.toMutableMap()
            for (remote in remoteProfiles) {
                if (deletedProfileIds.containsKey(remote.id)) continue
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
                if (deletedRecordIds.containsKey(remote.id)) continue
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
                if (deletedReminderIds.containsKey(remote.id)) continue
                val local = reminderMap[remote.id]
                if (local == null || remote.updatedAt > local.updatedAt) {
                    reminderMap[remote.id] = remote
                    addedOrUpdatedReminders++
                }
            }

            val (dedupedReminders, droppedDuplicateIds) = deduplicateReminders(reminderMap.values.toList())
            val now = System.currentTimeMillis()
            droppedDuplicateIds.forEach { deletedReminderIds[it] = now }

            val logMap = _intakeLogs.value.associateBy { it.id }.toMutableMap()
            var addedOrUpdatedLogs = 0
            for (remote in remoteLogs) {
                if (deletedReminderIds.containsKey(remote.reminderId)) continue
                val local = logMap[remote.id]
                if (local == null || remote.loggedAt > local.loggedAt) {
                    logMap[remote.id] = remote
                    addedOrUpdatedLogs++
                }
            }

            _profiles.value = profileMap.values.toList().ifEmpty { FamilyProfile.defaultProfiles() }
            _records.value = recordMap.values.sortedByDescending { it.createdAt }
            _reminders.value = dedupedReminders
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
            remoteLogs = bundle.intakeLogs,
            clearMatchingTombstones = true
        )
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_PROXY_URL = "gemini_proxy_url"
        private const val KEY_GEMINI_PROXY_TOKEN = "gemini_proxy_token"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GITHUB_OTA_REPO = "github_ota_repo"
        private const val KEY_GITHUB_OTA_TOKEN = "github_ota_token"
        private const val KEY_MORNING_TIME = "slot_morning_time"
        private const val KEY_NOON_TIME = "slot_noon_time"
        private const val KEY_AFTERNOON_TIME = "slot_afternoon_time"
        private const val KEY_EVENING_TIME = "slot_evening_time"
        private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        private const val KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        private const val KEY_TELEGRAM_GROUP_NAME = "telegram_group_name"
        private const val KEY_TELEGRAM_NOTIFY_DUE = "telegram_notify_due"
        private const val KEY_TELEGRAM_NOTIFY_TAKEN = "telegram_notify_taken"
        private const val KEY_TELEGRAM_NOTIFY_SNOOZE_SKIP = "telegram_notify_snooze_skip"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_AUTO_CHECK_OTA = "auto_check_ota"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

        fun todayDateKey(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        fun normalizedMedicationKey(profileId: String, medicationName: String): String {
            val cleanName = medicationName.trim().lowercase().replace(Regex("\\s+"), " ")
            return "$profileId::$cleanName"
        }
    }
}

data class PendingDeletes(
    val profileIds: Map<String, Long> = emptyMap(),
    val recordIds: Map<String, Long> = emptyMap(),
    val reminderIds: Map<String, Long> = emptyMap()
) {
    val isEmpty: Boolean
        get() = profileIds.isEmpty() && recordIds.isEmpty() && reminderIds.isEmpty()
}

data class MergeStats(
    val recordsMerged: Int,
    val remindersMerged: Int,
    val logsMerged: Int
)
