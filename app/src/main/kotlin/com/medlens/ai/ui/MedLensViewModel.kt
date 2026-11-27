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
import com.medlens.ai.data.IndicatorTrendSeries
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.data.LocalMedicalStore
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.MedicationIntakeLog
import com.medlens.ai.data.MedicationItem
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.data.RecordChatMessage
import com.medlens.ai.data.SampleMedicalData
import com.medlens.ai.data.ScanAttachment
import com.medlens.ai.data.TrendAnalyzer
import com.medlens.ai.ota.OtaReleaseInfo
import com.medlens.ai.ota.OtaUpdateState
import com.medlens.ai.reminder.MedicationNotificationHelper
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
    RECORDS("Sổ khám"),
    SETTINGS("Cài đặt")
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

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

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
                apiKey = cfg.geminiApiKey,
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
                apiKey = cfg.geminiApiKey,
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
        _snackbarMessage.value = "Đã tạo đơn thuốc mẫu — bấm 'Tạo lịch nhắc uống thuốc' để thử ngay!"
    }

    fun deleteRecord(recordId: String) {
        localStore.deleteRecord(recordId)
        if (_activeRecordDetailId.value == recordId) {
            _activeRecordDetailId.value = null
        }
        _snackbarMessage.value = "Đã xóa phiếu khám."
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
                note = med.categoryShort.ifBlank { med.purposePlainVietnamese.take(100) },
                sourceRecordId = record.id,
                isActive = true
            )
            localStore.upsertReminder(reminder)
            scheduler.scheduleReminder(reminder)
        }
        _snackbarMessage.value = "✅ Đã tạo thành công ${record.medications.size} lịch nhắc uống thuốc!"
        triggerAutoSyncIfReady()
    }

    fun saveReminder(reminder: MedicationReminder, notifyUser: Boolean = true) {
        localStore.upsertReminder(reminder)
        if (reminder.isActive) {
            scheduler.scheduleReminder(reminder)
        } else {
            scheduler.cancelReminder(reminder)
        }
        if (notifyUser) {
            _snackbarMessage.value =
                "✅ Đã lưu lịch nhắc uống thuốc: ${reminder.medicationName} (${reminder.times.joinToString(", ")})"
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
        scheduler.cancelReminder(reminder)
        localStore.deleteReminder(reminder.id)
        _snackbarMessage.value = "Đã xóa lịch nhắc ${reminder.medicationName}"
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
        triggerAutoSyncIfReady()
    }

    fun sendTestNotification() {
        val posted = MedicationNotificationHelper.showReminderNotification(
            context = app,
            reminderId = "demo_test_notification",
            medicationName = "Vitamin C 500mg (${activeProfile.value.name})",
            dosage = "1 viên",
            mealTiming = MealTiming.AFTER_MEAL,
            scheduledTime = settings.value.morningTime
        )
        _snackbarMessage.value = if (posted) {
            "Đã gửi thông báo nhắc thuốc thử! Kiểm tra thanh thông báo."
        } else {
            "Chưa có quyền gửi thông báo. Hãy cấp quyền Thông báo trước."
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

    fun saveGeminiConfig(apiKey: String, model: String) {
        localStore.updateSettings {
            it.copy(geminiApiKey = apiKey.trim(), geminiModel = model)
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
        if (cfg.autoSyncEnabled && sync.isFirebaseConfigured && sync.user != null && !sync.isSyncing) {
            viewModelScope.launch {
                firebaseRepo.syncNow()
            }
        }
    }
}
