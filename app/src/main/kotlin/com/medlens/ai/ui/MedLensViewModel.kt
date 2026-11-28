package com.medlens.ai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medlens.ai.BuildConfig
import com.medlens.ai.MedLensApplication
import com.medlens.ai.data.AppSettings
import com.medlens.ai.data.CloudSyncUiState
import com.medlens.ai.data.DaySlot
import com.medlens.ai.data.DocumentType
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.GeminiEndpointConfig
import com.medlens.ai.data.IndicatorTrendSeries
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.LocalMedicalStore
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.MedicationIntakeLog
import com.medlens.ai.data.MedicationItem
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.data.PatientDietPlan
import com.medlens.ai.data.RecordChatMessage
import com.medlens.ai.data.SampleMedicalData
import com.medlens.ai.data.SavedAiChatSession
import com.medlens.ai.data.ScanAttachment
import com.medlens.ai.data.TrendAnalyzer
import com.medlens.ai.ota.OtaReleaseInfo
import com.medlens.ai.ota.OtaUpdateState
import com.medlens.ai.reminder.MedicationNotificationHelper
import com.medlens.ai.reminder.TelegramDiscoveredChat
import com.medlens.ai.reminder.TelegramNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class MainTab(val label: String) {
    HOME("Trang chủ"),
    SCAN("Quét AI"),
    AI_CHAT("Gemini AI"),
    RECORDS("Sổ khám"),
    MORE("Thêm")
}

data class ScheduledDoseItem(
    val reminder: MedicationReminder,
    val scheduledTime: String,
    val todayStatus: IntakeStatus?
)

class MedLensViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MedLensApplication
    private val localStore = app.localStore
    private val geminiRepo = app.geminiRepository
    private val firebaseRepo = app.firebaseSyncRepository
    private val scheduler = app.reminderScheduler
    val otaUpdater = app.otaUpdater

    val profiles: StateFlow<List<FamilyProfile>> = localStore.profiles
    val records: StateFlow<List<MedicalRecord>> = localStore.records
    val reminders: StateFlow<List<MedicationReminder>> = localStore.reminders
    val intakeLogs: StateFlow<List<MedicationIntakeLog>> = localStore.intakeLogs
    val settings: StateFlow<AppSettings> = localStore.settings
    val cloudSyncState: StateFlow<CloudSyncUiState> = firebaseRepo.state
    val otaState: StateFlow<OtaUpdateState> = otaUpdater.state

    val activeProfile: StateFlow<FamilyProfile> = combine(profiles, settings) { list, cfg ->
        list.firstOrNull { it.id == cfg.activeProfileId } ?: list.first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FamilyProfile.defaultProfiles().first())

    val activeProfileRecords: StateFlow<List<MedicalRecord>> = combine(records, activeProfile) { all, prof ->
        all.filter { it.profileId == prof.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileReminders: StateFlow<List<MedicationReminder>> = combine(reminders, activeProfile) { all, prof ->
        all.filter { it.profileId == prof.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileTodayDoses: StateFlow<List<ScheduledDoseItem>> = combine(
        activeProfileReminders,
        intakeLogs
    ) { reminderList, logs ->
        val today = LocalMedicalStore.todayDateKey()
        val todayMap = logs.filter { it.dateKey == today }
            .associateBy { "${it.reminderId}_${it.scheduledTime}" }

        reminderList
            .filter { it.isActive }
            .flatMap { reminder ->
                reminder.times.map { time ->
                    ScheduledDoseItem(
                        reminder = reminder,
                        scheduledTime = time,
                        todayStatus = todayMap["${reminder.id}_$time"]?.status
                    )
                }
            }
            .sortedBy { it.scheduledTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileTrends: StateFlow<List<IndicatorTrendSeries>> = combine(
        activeProfileRecords,
        activeProfile
    ) { profileRecords, _ ->
        TrendAnalyzer.buildTrendSeries(profileRecords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileAssistantChat: StateFlow<List<RecordChatMessage>> = combine(
        localStore.assistantChats,
        activeProfile
    ) { map, prof ->
        map[prof.id].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileSavedChats: StateFlow<List<SavedAiChatSession>> = combine(
        localStore.savedAiChats,
        activeProfile
    ) { all, prof ->
        all.filter { it.profileId == prof.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileDietPlan: StateFlow<PatientDietPlan?> = combine(
        localStore.dietPlans,
        activeProfile
    ) { map, prof ->
        map[prof.id]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isGeneratingDiet = MutableStateFlow(false)
    val isGeneratingDiet: StateFlow<Boolean> = _isGeneratingDiet.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _activeRecordDetailId = MutableStateFlow<String?>(null)
    val activeRecordDetail: StateFlow<MedicalRecord?> = combine(
        records,
        _activeRecordDetailId
    ) { all, id ->
        if (id == null) null else all.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _isChatReplying = MutableStateFlow(false)
    val isChatReplying: StateFlow<Boolean> = _isChatReplying.asStateFlow()

    private val _isAssistantReplying = MutableStateFlow(false)
    val isAssistantReplying: StateFlow<Boolean> = _isAssistantReplying.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isTelegramBusy = MutableStateFlow(false)
    val isTelegramBusy: StateFlow<Boolean> = _isTelegramBusy.asStateFlow()

    private val _discoveredTelegramChats = MutableStateFlow<List<TelegramDiscoveredChat>>(emptyList())
    val discoveredTelegramChats: StateFlow<List<TelegramDiscoveredChat>> = _discoveredTelegramChats.asStateFlow()

    init {
        val cfg = settings.value
        if (cfg.autoCheckOta && cfg.githubOtaRepo.isNotBlank()) {
            viewModelScope.launch {
                otaUpdater.checkForUpdate(
                    repoSlug = cfg.githubOtaRepo,
                    currentVersion = BuildConfig.VERSION_NAME,
                    githubToken = cfg.githubOtaToken
                )
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun selectProfile(profileId: String) {
        localStore.updateSettings { it.copy(activeProfileId = profileId) }
    }

    fun saveProfile(profile: FamilyProfile) {
        localStore.upsertProfile(profile)
        localStore.updateSettings { it.copy(activeProfileId = profile.id) }
        _snackbarMessage.value = "Đã lưu hồ sơ ${profile.name}."
        triggerAutoSyncIfReady()
    }

    fun saveBhytImage(profile: FamilyProfile, uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(app.filesDir, "bhyt").apply { mkdirs() }
                    val dest = File(dir, "bhyt_${profile.id}.jpg")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    dest.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) {
                localStore.upsertProfile(profile.copy(bhytImagePath = savedPath))
                _snackbarMessage.value = "Đã lưu ảnh thẻ BHYT cho hồ sơ ${profile.name}."
            } else {
                _snackbarMessage.value = "Không thể lưu ảnh thẻ BHYT."
            }
        }
    }

    fun openRecordDetail(record: MedicalRecord) {
        _activeRecordDetailId.value = record.id
    }

    fun closeRecordDetail() {
        _activeRecordDetailId.value = null
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    private fun currentGeminiEndpoint(): GeminiEndpointConfig {
        val cfg = settings.value
        return GeminiEndpointConfig(
            apiKey = cfg.geminiApiKey,
            proxyUrl = cfg.geminiProxyUrl,
            proxyToken = cfg.geminiProxyToken
        )
    }

    fun analyzeAttachments(attachments: List<ScanAttachment>, mode: DocumentType) {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            val cfg = settings.value
            val currentProfile = activeProfile.value
            val result = geminiRepo.analyzeMedicalAttachments(
                attachments = attachments,
                profileId = currentProfile.id,
                profileContext = currentProfile,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                hintType = mode,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isAnalyzing.value = false
            result.onSuccess { record ->
                localStore.upsertRecord(record)
                _activeRecordDetailId.value = record.id
                _snackbarMessage.value = "Đã giải mã xong: ${record.title}"
                triggerAutoSyncIfReady()
            }.onFailure { err ->
                _analysisError.value = err.localizedMessage ?: "Không thể phân tích tài liệu."
            }
        }
    }

    fun askRecordFollowUp(record: MedicalRecord, question: String) {
        val clean = question.trim()
        if (clean.isEmpty() || _isChatReplying.value) return

        val userMsg = RecordChatMessage(isUser = true, text = clean)
        val updatedWithUser = localStore.appendChatMessages(record.id, userMsg) ?: record

        viewModelScope.launch {
            _isChatReplying.value = true
            val cfg = settings.value
            val profile = profiles.value.firstOrNull { it.id == record.profileId }
            val result = geminiRepo.askContextualQuestion(
                record = updatedWithUser,
                profile = profile,
                question = clean,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isChatReplying.value = false
            result.onSuccess { answer ->
                localStore.appendChatMessages(
                    record.id,
                    RecordChatMessage(isUser = false, text = answer)
                )
                triggerAutoSyncIfReady()
            }.onFailure { err ->
                localStore.appendChatMessages(
                    record.id,
                    RecordChatMessage(
                        isUser = false,
                        text = "Xin lỗi, mình chưa trả lời được lúc này: ${err.localizedMessage}"
                    )
                )
            }
        }
    }

    fun askRecordFollowUpVoice(record: MedicalRecord, audioFile: File) {
        if (_isChatReplying.value) {
            audioFile.delete()
            return
        }
        viewModelScope.launch {
            _isChatReplying.value = true
            val cfg = settings.value
            val profile = profiles.value.firstOrNull { it.id == record.profileId }
            val result = geminiRepo.askContextualVoiceQuestion(
                record = record,
                profile = profile,
                audioFile = audioFile,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isChatReplying.value = false
            result.onSuccess { voiceRes ->
                localStore.appendChatMessages(
                    record.id,
                    RecordChatMessage(isUser = true, text = "🎤 ${voiceRes.transcript}"),
                    RecordChatMessage(isUser = false, text = voiceRes.reply)
                )
                triggerAutoSyncIfReady()
            }.onFailure { err ->
                localStore.appendChatMessages(
                    record.id,
                    RecordChatMessage(
                        isUser = false,
                        text = "Xin lỗi, chưa xử lý được ghi âm lúc này: ${err.localizedMessage}"
                    )
                )
            }
        }
    }

    fun sendDirectAssistantMessage(question: String, focusedRecordId: String?) {
        val clean = question.trim()
        if (clean.isEmpty() || _isAssistantReplying.value) return

        val profile = activeProfile.value
        val previousHistory = activeProfileAssistantChat.value
        val focusedRecord = focusedRecordId?.let { id -> activeProfileRecords.value.firstOrNull { it.id == id } }

        localStore.appendAssistantMessages(profile.id, RecordChatMessage(isUser = true, text = clean))

        viewModelScope.launch {
            _isAssistantReplying.value = true
            val cfg = settings.value
            val result = geminiRepo.askDirectAssistantChat(
                profile = profile,
                recentRecords = activeProfileRecords.value,
                activeReminders = activeProfileReminders.value.filter { it.isActive },
                focusedRecord = focusedRecord,
                chatHistory = previousHistory,
                question = clean,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isAssistantReplying.value = false
            result.onSuccess { reply ->
                localStore.appendAssistantMessages(profile.id, RecordChatMessage(isUser = false, text = reply))
            }.onFailure { err ->
                localStore.appendAssistantMessages(
                    profile.id,
                    RecordChatMessage(
                        isUser = false,
                        text = "Xin lỗi, mình chưa thể trả lời ngay lúc này: ${err.localizedMessage}"
                    )
                )
            }
        }
    }

    fun sendDirectAssistantImage(imageUri: Uri, question: String, focusedRecordId: String?) {
        if (_isAssistantReplying.value) return
        val profile = activeProfile.value
        val previousHistory = activeProfileAssistantChat.value
        val focusedRecord = focusedRecordId?.let { id -> activeProfileRecords.value.firstOrNull { it.id == id } }
        val cleanQuestion = question.trim()
        val displayQuestion = cleanQuestion.ifBlank { "📷 Xem và giải thích giúp mình hình ảnh này" }

        viewModelScope.launch {
            _isAssistantReplying.value = true
            val savedFileResult = geminiRepo.saveChatImageCopy(imageUri)
            val savedFile = savedFileResult.getOrElse { err ->
                _isAssistantReplying.value = false
                _snackbarMessage.value = err.localizedMessage ?: "Không thể đọc hình ảnh đã chọn."
                return@launch
            }

            localStore.appendAssistantMessages(
                profile.id,
                RecordChatMessage(
                    isUser = true,
                    text = displayQuestion,
                    imagePath = savedFile.absolutePath
                )
            )

            val cfg = settings.value
            val result = geminiRepo.askDirectAssistantWithSavedImage(
                imageFile = savedFile,
                question = cleanQuestion,
                profile = profile,
                recentRecords = activeProfileRecords.value,
                activeReminders = activeProfileReminders.value.filter { it.isActive },
                focusedRecord = focusedRecord,
                chatHistory = previousHistory,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isAssistantReplying.value = false
            result.onSuccess { reply ->
                localStore.appendAssistantMessages(profile.id, RecordChatMessage(isUser = false, text = reply))
            }.onFailure { err ->
                localStore.appendAssistantMessages(
                    profile.id,
                    RecordChatMessage(
                        isUser = false,
                        text = "Xin lỗi, mình chưa thể phân tích hình ảnh lúc này: ${err.localizedMessage}"
                    )
                )
            }
        }
    }

    fun sendDirectAssistantVoice(audioFile: File, focusedRecordId: String?) {
        if (_isAssistantReplying.value) {
            audioFile.delete()
            return
        }
        val profile = activeProfile.value
        val previousHistory = activeProfileAssistantChat.value
        val focusedRecord = focusedRecordId?.let { id -> activeProfileRecords.value.firstOrNull { it.id == id } }

        viewModelScope.launch {
            _isAssistantReplying.value = true
            val cfg = settings.value
            val result = geminiRepo.askDirectAssistantVoice(
                audioFile = audioFile,
                profile = profile,
                recentRecords = activeProfileRecords.value,
                activeReminders = activeProfileReminders.value.filter { it.isActive },
                focusedRecord = focusedRecord,
                chatHistory = previousHistory,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isAssistantReplying.value = false
            result.onSuccess { voiceRes ->
                localStore.appendAssistantMessages(
                    profile.id,
                    RecordChatMessage(isUser = true, text = "🎤 ${voiceRes.transcript}"),
                    RecordChatMessage(isUser = false, text = voiceRes.reply)
                )
            }.onFailure { err ->
                localStore.appendAssistantMessages(
                    profile.id,
                    RecordChatMessage(
                        isUser = false,
                        text = "Xin lỗi, chưa xử lý được ghi âm lúc này: ${err.localizedMessage}"
                    )
                )
            }
        }
    }

    fun clearDirectAssistantChat() {
        val profile = activeProfile.value
        localStore.clearAssistantChat(profile.id)
        _snackbarMessage.value = "Đã bắt đầu cuộc trò chuyện mới cho ${profile.name}."
    }

    fun transcribeAudioIntoInput(audioFile: File, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val cfg = settings.value
            val result = geminiRepo.transcribeAudioToText(
                audioFile = audioFile,
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            result.onSuccess { text ->
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onResult("")
                    _snackbarMessage.value = "Chưa nghe rõ lời nói, bạn hãy thử nói lại nhé."
                }
            }.onFailure { err ->
                onResult("")
                _snackbarMessage.value = "Không thể chuyển giọng nói thành chữ: ${err.localizedMessage}"
            }
        }
    }

    fun saveCurrentAssistantChat(customTitle: String, focusedRecordTitle: String = "") {
        val profile = activeProfile.value
        val currentMessages = activeProfileAssistantChat.value
        if (currentMessages.isEmpty()) {
            _snackbarMessage.value = "Chưa có tin nhắn nào để lưu."
            return
        }
        val fallbackTitle = currentMessages.firstOrNull { it.isUser }?.text
            ?.removePrefix("🎤 ")
            ?.trim()
            ?.take(60)
            ?.ifBlank { "Hỏi đáp AI • ${profile.name}" }
            ?: "Hỏi đáp AI • ${profile.name}"
        val resolvedTitle = customTitle.trim().ifBlank { fallbackTitle }
        val session = SavedAiChatSession(
            profileId = profile.id,
            title = resolvedTitle,
            focusedRecordTitle = focusedRecordTitle.trim(),
            messages = currentMessages,
            savedAt = System.currentTimeMillis()
        )
        localStore.saveAiChatSession(session)
        _snackbarMessage.value = "✅ Đã lưu lịch sử trò chuyện: \"$resolvedTitle\"!"
    }

    fun loadSavedAssistantChat(session: SavedAiChatSession) {
        val profile = activeProfile.value
        localStore.replaceAssistantChat(profile.id, session.messages)
        _snackbarMessage.value = "Đã mở lại cuộc trò chuyện \"${session.title}\"."
    }

    fun deleteSavedAssistantChat(sessionId: String) {
        localStore.deleteSavedAiChatSession(sessionId)
        _snackbarMessage.value = "Đã xóa bản lưu cuộc trò chuyện."
    }

    fun generatePersonalizedDietPlan(
        conditionKey: String,
        conditionLabel: String,
        extraPreference: String
    ) {
        if (_isGeneratingDiet.value) return
        val profile = activeProfile.value
        val cfg = settings.value
        viewModelScope.launch {
            _isGeneratingDiet.value = true
            val result = geminiRepo.generatePersonalizedDietPlan(
                profile = profile,
                conditionKey = conditionKey,
                conditionLabel = conditionLabel,
                extraPreference = extraPreference,
                recentRecords = activeProfileRecords.value,
                activeReminders = activeProfileReminders.value.filter { it.isActive },
                endpoint = currentGeminiEndpoint(),
                modelName = cfg.geminiModel,
                onModelAutoSwitched = { workingModel ->
                    localStore.updateSettings { it.copy(geminiModel = workingModel) }
                }
            )
            _isGeneratingDiet.value = false
            result.onSuccess { plan ->
                localStore.saveDietPlan(profile.id, plan)
                _snackbarMessage.value = "✅ Đã tạo thực đơn ăn kiêng AI riêng cho ${profile.name}!"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Không thể tạo thực đơn AI lúc này."
            }
        }
    }

    fun loadSampleLabWithTrend() {
        val profileId = activeProfile.value.id
        val (older, latest) = SampleMedicalData.createSampleLabPair(profileId)
        localStore.upsertRecord(older)
        localStore.upsertRecord(latest)
        _activeRecordDetailId.value = latest.id
        _snackbarMessage.value = "Đã tạo 2 phiếu xét nghiệm mẫu (6 tháng trước & hôm nay) để xem cả Biểu đồ xu hướng!"
    }

    fun loadSamplePrescription() {
        val profileId = activeProfile.value.id
        val sample = SampleMedicalData.createSamplePrescriptionRecord(profileId)
        localStore.upsertRecord(sample)
        _activeRecordDetailId.value = sample.id
        _snackbarMessage.value = "Đã tạo đơn thuốc mẫu — bấm 'Chọn giờ & Nhắc' ở từng thuốc để đặt lịch!"
    }

    fun addReminderFromMedication(med: MedicationItem, record: MedicalRecord) {
        val cfg = settings.value
        val mappedTimes = med.daySlots.map { cfg.timeForSlot(it) }
            .ifEmpty { med.suggestedReminderTimes }
            .ifEmpty { listOf(cfg.morningTime, cfg.eveningTime) }

        val reminder = MedicationReminder(
            profileId = record.profileId,
            medicationName = med.name,
            dosage = med.dosage.ifBlank { "Theo đơn" },
            mealTiming = med.mealTiming,
            times = mappedTimes.distinct().sorted(),
            note = med.categoryShort.ifBlank { med.purposePlainVietnamese.take(100) },
            sourceRecordId = record.id,
            isActive = true
        )
        saveReminder(reminder, notifyUser = false)
        _snackbarMessage.value = "✅ Đã thêm lịch nhắc uống thuốc: ${med.name} (${reminder.times.joinToString(", ")})"
    }

    fun addAllRemindersFromRecord(record: MedicalRecord) {
        if (record.medications.isEmpty()) return
        val cfg = settings.value
        record.medications.forEach { med ->
            val mappedTimes = med.daySlots.map { cfg.timeForSlot(it) }
                .ifEmpty { med.suggestedReminderTimes }
                .ifEmpty { listOf(cfg.morningTime, cfg.eveningTime) }

            val reminder = MedicationReminder(
                profileId = record.profileId,
                medicationName = med.name,
                dosage = med.dosage.ifBlank { "Theo đơn" },
                mealTiming = med.mealTiming,
                times = mappedTimes.distinct().sorted(),
                note = listOf(med.sideEffectsWarning, med.categoryShort)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                    .ifBlank { med.purposePlainVietnamese.take(100) },
                sourceRecordId = record.id,
                isActive = true
            )
            localStore.upsertReminder(reminder)
        }
        scheduler.rescheduleAll(localStore.reminders.value)
        _snackbarMessage.value = "✅ Đã tạo thành công ${record.medications.size} lịch nhắc uống thuốc!"
        triggerAutoSyncIfReady()
    }

    fun saveReminder(reminder: MedicationReminder, notifyUser: Boolean = true) {
        val targetKey = LocalMedicalStore.normalizedMedicationKey(reminder.profileId, reminder.medicationName)
        val alreadyExists = localStore.reminders.value.any {
            (reminder.id.isNotBlank() && it.id == reminder.id) ||
                LocalMedicalStore.normalizedMedicationKey(it.profileId, it.medicationName) == targetKey
        }
        localStore.upsertReminder(reminder)
        scheduler.rescheduleAll(localStore.reminders.value)
        if (notifyUser) {
            val action = if (alreadyExists) "Đã cập nhật lịch nhắc" else "Đã lưu lịch nhắc"
            _snackbarMessage.value =
                "✅ $action: ${reminder.medicationName.trim()} (${reminder.times.distinct().sorted().joinToString(", ")})"
        }
        triggerAutoSyncIfReady()
    }

    fun toggleReminderActive(reminder: MedicationReminder) {
        val updated = reminder.copy(isActive = !reminder.isActive)
        saveReminder(updated, notifyUser = false)
        _snackbarMessage.value = if (updated.isActive) {
            "Đã bật lịch nhắc ${updated.medicationName}"
        } else {
            "Đã tạm tắt lịch nhắc ${updated.medicationName}"
        }
    }

    fun deleteReminder(reminder: MedicationReminder) {
        localStore.deleteReminder(reminder.id)
        scheduler.rescheduleAll(localStore.reminders.value)
        _snackbarMessage.value = "Đã xóa lịch nhắc ${reminder.medicationName}"
        triggerAutoSyncIfReady()
    }

    fun markDoseStatus(dose: ScheduledDoseItem, status: IntakeStatus) {
        localStore.logMedicationIntake(
            reminderId = dose.reminder.id,
            medicationName = dose.reminder.medicationName,
            scheduledTime = dose.scheduledTime,
            status = status,
            profileId = dose.reminder.profileId
        )
        _snackbarMessage.value = when (status) {
            IntakeStatus.TAKEN -> "Đã đánh dấu uống ${dose.reminder.medicationName} lúc ${dose.scheduledTime}"
            IntakeStatus.SKIPPED -> "Đã đánh dấu bỏ qua cữ ${dose.scheduledTime}"
        }
        val cfg = settings.value
        if (cfg.telegramEnabled && cfg.hasTelegramConfigured) {
            val profileMap = profiles.value.associateBy { it.id }
            viewModelScope.launch {
                when (status) {
                    IntakeStatus.TAKEN -> if (cfg.telegramNotifyOnTaken) {
                        TelegramNotifier.sendDoseTakenConfirmation(
                            settings = cfg,
                            scheduledTime = dose.scheduledTime,
                            takenReminders = listOf(dose.reminder),
                            profileMap = profileMap
                        )
                    }
                    IntakeStatus.SKIPPED -> if (cfg.telegramNotifyOnSnoozeOrSkip) {
                        TelegramNotifier.sendDoseSnoozedOrSkippedAlert(
                            settings = cfg,
                            scheduledTime = dose.scheduledTime,
                            reminders = listOf(dose.reminder),
                            profileMap = profileMap,
                            isSnooze = false
                        )
                    }
                }
            }
        }
        triggerAutoSyncIfReady()
    }

    fun sendTestNotification() {
        val activeList = localStore.reminders.value.filter { it.isActive }
        val targetSlot = activeList
            .flatMap { it.times }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "09:00"

        val inSlot = activeList.filter { it.times.contains(targetSlot) }
        val demoFallback = listOf(
            MedicationReminder(
                id = "demo_1",
                profileId = activeProfile.value.id,
                medicationName = "Esomeprazole 40mg",
                dosage = "1 viên",
                mealTiming = MealTiming.BEFORE_MEAL,
                times = listOf(targetSlot),
                note = "Nuốt nguyên viên trước ăn 30 phút"
            ),
            MedicationReminder(
                id = "demo_2",
                profileId = activeProfile.value.id,
                medicationName = "Getinomide 25mg",
                dosage = "1 viên",
                mealTiming = MealTiming.WITH_MEAL,
                times = listOf(targetSlot),
                note = "Uống đúng giờ mỗi ngày, không bỏ liều"
            ),
            MedicationReminder(
                id = "demo_3",
                profileId = activeProfile.value.id,
                medicationName = "Bổ gan / Vitamin",
                dosage = "1 viên",
                mealTiming = MealTiming.AFTER_MEAL,
                times = listOf(targetSlot),
                note = "Uống ngay sau ăn no"
            )
        )

        val medsToShow = inSlot.ifEmpty { demoFallback }
        val isDemo = inSlot.isEmpty()

        val posted = MedicationNotificationHelper.showGroupedReminderNotification(
            context = app,
            scheduledTime = targetSlot,
            remindersInSlot = medsToShow,
            isDemoTest = isDemo
        )

        // Immediately launch the full-screen grouped checklist popup so user can preview it right now!
        val notifId = ("group_slot_$targetSlot").hashCode()
        val popupIntent = MedicationNotificationHelper.createPopupIntent(
            context = app,
            scheduledTime = targetSlot,
            notificationId = notifId,
            isDemoTest = isDemo
        )
        runCatching { app.startActivity(popupIntent) }

        val cfg = settings.value
        if (cfg.telegramEnabled && cfg.telegramNotifyOnDue && cfg.hasTelegramConfigured) {
            val profileMap = profiles.value.associateBy { it.id }
            viewModelScope.launch {
                TelegramNotifier.sendDueMedicationReminder(
                    settings = cfg,
                    scheduledTime = targetSlot,
                    reminders = medsToShow,
                    profileMap = profileMap,
                    isDemoTest = isDemo
                )
            }
        }

        _snackbarMessage.value = if (posted) {
            "Đã mở Popup & gửi thông báo gom nhóm cữ $targetSlot (${medsToShow.size} thuốc)!"
        } else {
            "Đã mở Popup xem trước! Hãy cấp quyền Thông báo để nhận chuông khi tắt màn hình."
        }
    }

    fun saveTelegramConfig(
        enabled: Boolean,
        botToken: String,
        chatId: String,
        groupName: String,
        notifyOnDue: Boolean,
        notifyOnTaken: Boolean,
        notifyOnSnoozeOrSkip: Boolean
    ) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        if (enabled && (cleanToken.isBlank() || cleanChatId.isBlank())) {
            _snackbarMessage.value = "Vui lòng nhập Bot Token và Chat ID nhóm (hoặc bấm 'Tự động tìm nhóm') trước khi bật."
            return
        }
        localStore.updateSettings {
            it.copy(
                telegramEnabled = enabled,
                telegramBotToken = cleanToken,
                telegramChatId = cleanChatId,
                telegramGroupName = groupName.trim(),
                telegramNotifyOnDue = notifyOnDue,
                telegramNotifyOnTaken = notifyOnTaken,
                telegramNotifyOnSnoozeOrSkip = notifyOnSnoozeOrSkip
            )
        }
        _snackbarMessage.value = if (enabled) {
            "✅ Đã lưu & bật gửi nhắc nhở về nhóm Telegram (${groupName.ifBlank { cleanChatId }})!"
        } else {
            "Đã lưu cấu hình Telegram (đang tạm tắt)."
        }
    }

    fun discoverTelegramGroups(botToken: String) {
        if (_isTelegramBusy.value) return
        viewModelScope.launch {
            _isTelegramBusy.value = true
            val result = TelegramNotifier.discoverGroups(botToken)
            _isTelegramBusy.value = false
            result.onSuccess { chats ->
                _discoveredTelegramChats.value = chats
                if (chats.size == 1) {
                    val only = chats.first()
                    _snackbarMessage.value = "✅ Đã tìm thấy nhóm: ${only.title} (${only.chatId})!"
                } else {
                    _snackbarMessage.value = "✅ Tìm thấy ${chats.size} nhóm/cuộc trò chuyện — hãy bấm chọn nhóm muốn gửi nhắc nhở!"
                }
            }.onFailure { err ->
                _discoveredTelegramChats.value = emptyList()
                _snackbarMessage.value = err.localizedMessage ?: "Không tìm thấy nhóm Telegram."
            }
        }
    }

    fun sendTelegramTestMessage(botToken: String, chatId: String, groupName: String) {
        if (_isTelegramBusy.value) return
        viewModelScope.launch {
            _isTelegramBusy.value = true
            val result = TelegramNotifier.sendTestMessage(botToken, chatId, groupName)
            _isTelegramBusy.value = false
            result.onSuccess { resolvedChatId ->
                if (resolvedChatId != chatId.trim() && resolvedChatId.isNotBlank()) {
                    localStore.updateSettings { it.copy(telegramChatId = resolvedChatId) }
                }
                _snackbarMessage.value = "✅ Đã gửi tin nhắn thử về nhóm Telegram thành công!"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Gửi tin thử Telegram thất bại."
            }
        }
    }

    fun saveDefaultSlotTimes(morning: String, noon: String, afternoon: String, evening: String) {
        localStore.updateSettings {
            it.copy(
                morningTime = morning,
                noonTime = noon,
                afternoonTime = afternoon,
                eveningTime = evening
            )
        }
        _snackbarMessage.value = "Đã lưu giờ uống thuốc mặc định (Sáng $morning • Trưa $noon • Chiều $afternoon • Tối $evening)."
    }

    fun saveGeminiConfig(apiKey: String, proxyUrl: String, proxyToken: String, model: String) {
        localStore.updateSettings {
            it.copy(
                geminiApiKey = apiKey.trim(),
                geminiProxyUrl = proxyUrl.trim(),
                geminiProxyToken = proxyToken.trim(),
                geminiModel = model
            )
        }
        _snackbarMessage.value = "Đã lưu cấu hình Gemini AI ($model)."
    }

    fun saveOtaConfig(repoSlug: String, token: String, autoCheck: Boolean) {
        localStore.updateSettings {
            it.copy(
                githubOtaRepo = repoSlug.trim(),
                githubOtaToken = token.trim(),
                autoCheckOta = autoCheck
            )
        }
        _snackbarMessage.value = "Đã lưu cấu hình GitHub OTA."
    }

    fun toggleAutoSync(enabled: Boolean) {
        localStore.updateSettings { it.copy(autoSyncEnabled = enabled) }
    }

    fun signInFirebase(email: String, password: String) {
        viewModelScope.launch {
            firebaseRepo.signInWithEmail(email, password)
                .onSuccess {
                    _snackbarMessage.value = "Đăng nhập Firebase thành công!"
                    syncFirebaseNow()
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Đăng nhập thất bại."
                }
        }
    }

    fun registerFirebase(email: String, password: String) {
        viewModelScope.launch {
            firebaseRepo.registerWithEmail(email, password)
                .onSuccess {
                    _snackbarMessage.value = "Tạo tài khoản Firebase thành công!"
                    syncFirebaseNow()
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Đăng ký thất bại."
                }
        }
    }

    fun signInAnonymouslyFirebase() {
        viewModelScope.launch {
            firebaseRepo.signInAnonymously()
                .onSuccess {
                    _snackbarMessage.value = "Đã kết nối đồng bộ Firebase!"
                    syncFirebaseNow()
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Không thể kết nối Firebase."
                }
        }
    }

    fun signOutFirebase() {
        firebaseRepo.signOut()
        _snackbarMessage.value = "Đã đăng xuất tài khoản Firebase."
    }

    fun syncFirebaseNow() {
        viewModelScope.launch {
            firebaseRepo.syncNow()
                .onSuccess { summary ->
                    scheduler.rescheduleAll(localStore.reminders.value)
                    _snackbarMessage.value = summary
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Đồng bộ thất bại."
                }
        }
    }

    fun buildBackupJson(): String = localStore.exportBackupJson(BuildConfig.VERSION_NAME)

    fun restoreBackupJson(rawJson: String) {
        runCatching {
            val stats = localStore.importBackupJson(rawJson)
            scheduler.rescheduleAll(localStore.reminders.value)
            _snackbarMessage.value =
                "Khôi phục xong: +${stats.recordsMerged} phiếu khám, +${stats.remindersMerged} lịch thuốc."
        }.onFailure { err ->
            _snackbarMessage.value = "File sao lưu không hợp lệ: ${err.localizedMessage}"
        }
    }

    fun checkForOtaUpdate() {
        val cfg = settings.value
        viewModelScope.launch {
            otaUpdater.checkForUpdate(
                repoSlug = cfg.githubOtaRepo,
                currentVersion = BuildConfig.VERSION_NAME,
                githubToken = cfg.githubOtaToken
            )
        }
    }

    fun downloadOtaApk(release: OtaReleaseInfo) {
        val cfg = settings.value
        viewModelScope.launch {
            otaUpdater.downloadApk(release = release, githubToken = cfg.githubOtaToken)
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Tải bản cập nhật thất bại."
                }
        }
    }

    private fun triggerAutoSyncIfReady() {
        val cfg = settings.value
        val sync = cloudSyncState.value
        if (cfg.autoSyncEnabled && sync.isFirebaseConfigured && sync.user != null) {
            if (sync.isSyncing) {
                firebaseRepo.requestFollowUpSync()
            } else {
                viewModelScope.launch {
                    firebaseRepo.syncNow()
                }
            }
        }
    }
}
