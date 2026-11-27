package com.medlens.ai.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class DocumentType(val labelVi: String) {
    LAB_RESULT("Phiếu xét nghiệm"),
    PRESCRIPTION("Đơn thuốc"),
    MIXED("Hồ sơ khám tổng hợp")
}

@Serializable
enum class IndicatorStatus(val labelVi: String, val sortPriority: Int) {
    CRITICAL("Cần khám sớm", 0),
    HIGH("Cao hơn chuẩn", 1),
    LOW("Thấp hơn chuẩn", 1),
    BORDERLINE("Cận ngưỡng", 2),
    NORMAL("Bình thường", 3)
}

@Serializable
enum class MealTiming(val labelVi: String) {
    BEFORE_MEAL("Trước ăn 30 phút"),
    AFTER_MEAL("Sau ăn no"),
    WITH_MEAL("Trong bữa ăn"),
    ANYTIME("Lúc nào cũng được")
}

@Serializable
enum class DaySlot(val labelVi: String, val defaultTime: String) {
    MORNING("Sáng", "07:00"),
    NOON("Trưa", "12:00"),
    AFTERNOON("Chiều", "17:30"),
    EVENING("Tối", "19:30")
}

@Serializable
enum class IntakeStatus(val labelVi: String) {
    TAKEN("Đã uống"),
    SKIPPED("Bỏ qua")
}

@Serializable
data class FamilyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relationship: String = "Bản thân",
    val birthYear: String = "",
    val bloodType: String = "",
    val drugAllergies: String = "",
    val chronicConditions: String = "",
    val bhytCardNumber: String = "",
    val bhytImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SELF_ID = "profile_self"
        const val DEFAULT_MOM_ID = "profile_mom"
        const val DEFAULT_DAD_ID = "profile_dad"
        const val DEFAULT_CHILD_ID = "profile_child"

        fun defaultProfiles(): List<FamilyProfile> = listOf(
            FamilyProfile(
                id = DEFAULT_SELF_ID,
                name = "Tôi",
                relationship = "Bản thân",
                bloodType = "Chưa cập nhật"
            ),
            FamilyProfile(
                id = DEFAULT_MOM_ID,
                name = "Mẹ",
                relationship = "Mẹ",
                bloodType = "Chưa cập nhật",
                chronicConditions = "Theo dõi mỡ máu & dạ dày"
            ),
            FamilyProfile(
                id = DEFAULT_DAD_ID,
                name = "Bố",
                relationship = "Bố",
                bloodType = "Chưa cập nhật"
            ),
            FamilyProfile(
                id = DEFAULT_CHILD_ID,
                name = "Con",
                relationship = "Con",
                bloodType = "Chưa cập nhật"
            )
        )
    }
}

@Serializable
data class LabIndicator(
    val name: String,
    val value: String,
    val numericValue: Double? = null,
    val unit: String = "",
    val referenceRange: String = "",
    val status: IndicatorStatus = IndicatorStatus.NORMAL,
    val plainExplanation: String = "",
    val dietAndLifestyleAdvice: String = ""
) {
    fun parsedNumericValue(): Double? {
        if (numericValue != null) return numericValue
        val cleaned = value.replace(",", ".").trim()
        return Regex("-?\\d+(\\.\\d+)?").find(cleaned)?.value?.toDoubleOrNull()
    }
}

@Serializable
data class MedicationItem(
    val name: String,
    val activeIngredient: String = "",
    val categoryShort: String = "", // vd: Giảm đau hạ sốt, Kháng sinh, Bảo vệ dạ dày
    val dosage: String = "",
    val frequency: String = "",
    val mealTiming: MealTiming = MealTiming.AFTER_MEAL,
    val daySlots: List<DaySlot> = listOf(DaySlot.MORNING, DaySlot.EVENING),
    val durationDays: Int = 7,
    val purposePlainVietnamese: String = "",
    val sideEffectsWarning: String = "",
    val suggestedReminderTimes: List<String> = listOf("07:00", "19:30")
)

@Serializable
data class RecordChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class MedicalRecord(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = FamilyProfile.DEFAULT_SELF_ID,
    val title: String,
    val documentType: DocumentType = DocumentType.LAB_RESULT,
    val facilityName: String = "",
    val recordDate: String = "",
    val overallSummary: String = "",
    val summaryBullets: List<String> = emptyList(),
    val labIndicators: List<LabIndicator> = emptyList(),
    val medications: List<MedicationItem> = emptyList(),
    val generalDietAdvice: List<String> = emptyList(),
    val suggestedDoctorQuestions: List<String> = emptyList(),
    val redFlags: List<String> = emptyList(),
    val medicalDisclaimer: String = DEFAULT_MEDICAL_DISCLAIMER,
    val chatHistory: List<RecordChatMessage> = emptyList(),
    val localImagePath: String? = null,
    val localAttachmentPaths: List<String> = emptyList(),
    val cloudImageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSample: Boolean = false
) {
    companion object {
        const val DEFAULT_MEDICAL_DISCLAIMER =
            "Lưu ý y khoa: Nội dung giải thích do AI tạo ra chỉ mang tính tham khảo giúp bạn dễ hiểu hơn, KHÔNG thay thế chẩn đoán hay chỉ định của bác sĩ và dược sĩ. Tuyệt đối không tự ý ngưng hoặc thay đổi liều thuốc khi chưa hỏi ý kiến bác sĩ."
    }
}

@Serializable
data class MedicationReminder(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = FamilyProfile.DEFAULT_SELF_ID,
    val medicationName: String,
    val dosage: String,
    val mealTiming: MealTiming = MealTiming.AFTER_MEAL,
    val times: List<String> = listOf("07:00", "19:30"),
    val note: String = "",
    val sourceRecordId: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class MedicationIntakeLog(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String = FamilyProfile.DEFAULT_SELF_ID,
    val reminderId: String,
    val medicationName: String,
    val dateKey: String, // yyyy-MM-dd
    val scheduledTime: String, // HH:mm
    val status: IntakeStatus,
    val loggedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val geminiApiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val githubOtaRepo: String = "",
    val githubOtaToken: String = "",
    val morningTime: String = "07:00",
    val noonTime: String = "12:00",
    val afternoonTime: String = "17:30",
    val eveningTime: String = "19:30",
    val autoSyncEnabled: Boolean = true,
    val autoCheckOta: Boolean = true,
    val lastSyncedAt: Long = 0L,
    val activeProfileId: String = FamilyProfile.DEFAULT_SELF_ID
) {
    fun timeForSlot(slot: DaySlot): String = when (slot) {
        DaySlot.MORNING -> morningTime
        DaySlot.NOON -> noonTime
        DaySlot.AFTERNOON -> afternoonTime
        DaySlot.EVENING -> eveningTime
    }

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        val SUPPORTED_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro",
            "gemini-flash-latest"
        )
    }
}

@Serializable
data class LocalDatabaseSnapshot(
    val profiles: List<FamilyProfile> = emptyList(),
    val records: List<MedicalRecord> = emptyList(),
    val reminders: List<MedicationReminder> = emptyList(),
    val intakeLogs: List<MedicationIntakeLog> = emptyList()
)

@Serializable
data class BackupBundle(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val profiles: List<FamilyProfile> = emptyList(),
    val records: List<MedicalRecord> = emptyList(),
    val reminders: List<MedicationReminder> = emptyList(),
    val intakeLogs: List<MedicationIntakeLog> = emptyList()
)

data class IndicatorTrendPoint(
    val recordId: String,
    val dateLabel: String,
    val timestamp: Long,
    val value: Double,
    val rawValue: String,
    val unit: String,
    val status: IndicatorStatus
)

data class IndicatorTrendSeries(
    val indicatorName: String,
    val unit: String,
    val points: List<IndicatorTrendPoint>
) {
    val latestPoint: IndicatorTrendPoint get() = points.last()
    val previousPoint: IndicatorTrendPoint? get() = if (points.size >= 2) points[points.size - 2] else null

    fun deltaSummaryVi(): String {
        val prev = previousPoint ?: return "Cần ít nhất 2 lần xét nghiệm để so sánh xu hướng."
        val curr = latestPoint
        val diff = curr.value - prev.value
        val pct = if (prev.value != 0.0) (diff / prev.value) * 100.0 else 0.0
        val direction = when {
            diff > 0.001 -> "tăng"
            diff < -0.001 -> "giảm"
            else -> "giữ nguyên"
        }
        return if (direction == "giữ nguyên") {
            "Giữ nguyên ở mức ${curr.rawValue} ${curr.unit} so với ngày ${prev.dateLabel}."
        } else {
            val formattedPct = "%.1f".format(kotlin.math.abs(pct))
            "Đã $direction từ ${prev.rawValue} → ${curr.rawValue} ${curr.unit} ($formattedPct%) so với ngày ${prev.dateLabel}."
        }
    }
}
