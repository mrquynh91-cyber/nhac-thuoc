package com.medlens.ai.data

import com.medlens.ai.reminder.MedicationReminderScheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedLensCoreLogicTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun geminiAnalysisDto_convertsTrafficLightBulletsQuestionsAndDaySlots() {
        val raw = """
            {
              "documentType": "MIXED",
              "title": "Phiếu xét nghiệm & Đơn thuốc tổng quát",
              "overallSummary": "Men gan ALT tăng nhẹ, cần kiêng bia rượu.",
              "summaryBullets": [
                "Men gan ALT tăng nhẹ (56 U/L).",
                "Mỡ máu cần theo dõi thêm."
              ],
              "labIndicators": [
                {
                  "name": "ALT (GPT)",
                  "value": "56",
                  "numericValue": 56.0,
                  "unit": "U/L",
                  "referenceRange": "< 40 U/L",
                  "status": "HIGH",
                  "plainExplanation": "Men gan cao hơn chuẩn một chút.",
                  "dietAndLifestyleAdvice": "Hạn chế rượu bia và đồ chiên."
                }
              ],
              "medications": [
                {
                  "name": "Atorvastatin 10mg",
                  "categoryShort": "Hạ mỡ máu",
                  "dosage": "1 viên",
                  "mealTiming": "AFTER_MEAL",
                  "daySlots": ["EVENING"],
                  "durationDays": 30,
                  "purposePlainVietnamese": "Hạ mỡ máu xấu",
                  "suggestedReminderTimes": ["19:30", "invalid"]
                }
              ],
              "suggestedDoctorQuestions": [
                "Tôi có cần siêu âm gan không?",
                "Bao lâu nên xét nghiệm lại men gan?"
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<GeminiAnalysisDto>(raw)
        val record = dto.toMedicalRecord(profileId = FamilyProfile.DEFAULT_MOM_ID, localImagePath = null)

        assertEquals(FamilyProfile.DEFAULT_MOM_ID, record.profileId)
        assertEquals(DocumentType.MIXED, record.documentType)
        assertEquals(2, record.summaryBullets.size)
        assertEquals(2, record.suggestedDoctorQuestions.size)
        assertEquals(IndicatorStatus.HIGH, record.labIndicators.first().status)
        assertEquals(56.0, record.labIndicators.first().parsedNumericValue()!!, 0.001)
        assertEquals(listOf(DaySlot.EVENING), record.medications.first().daySlots)
        assertEquals("Hạ mỡ máu", record.medications.first().categoryShort)
        assertEquals(listOf("19:30"), record.medications.first().suggestedReminderTimes)
        assertTrue(record.medicalDisclaimer.isNotBlank())
    }

    @Test
    fun trendAnalyzer_computesMultiVisitDeltaForSameIndicator() {
        val (older, latest) = SampleMedicalData.createSampleLabPair(FamilyProfile.DEFAULT_MOM_ID)
        val trends = TrendAnalyzer.buildTrendSeries(listOf(latest, older))

        val altSeries = trends.firstOrNull { it.indicatorName == "ALT (GPT)" }
        assertNotNull(altSeries)
        assertEquals(2, altSeries!!.points.size)
        assertEquals(74.0, altSeries.points[0].value, 0.01)
        assertEquals(58.0, altSeries.points[1].value, 0.01)
        assertTrue(altSeries.deltaSummaryVi().contains("giảm"))
    }

    @Test
    fun reminderScheduler_parsesValidAndInvalidTimes() {
        assertEquals(7 to 30, MedicationReminderScheduler.parseTime("07:30"))
        assertEquals(23 to 59, MedicationReminderScheduler.parseTime("23:59"))
        assertNull(MedicationReminderScheduler.parseTime("25:00"))
        assertNull(MedicationReminderScheduler.parseTime("abc"))
    }

    @Test
    fun backupBundle_roundTripSerializationPreservesFamilyProfilesAndRecords() {
        val profiles = FamilyProfile.defaultProfiles()
        val (older, latest) = SampleMedicalData.createSampleLabPair(FamilyProfile.DEFAULT_MOM_ID)
        val sampleRx = SampleMedicalData.createSamplePrescriptionRecord(FamilyProfile.DEFAULT_MOM_ID)
        val reminder = MedicationReminder(
            profileId = FamilyProfile.DEFAULT_MOM_ID,
            medicationName = "Esomeprazole 40mg",
            dosage = "1 viên",
            mealTiming = MealTiming.BEFORE_MEAL,
            times = listOf("07:00")
        )
        val bundle = BackupBundle(
            profiles = profiles,
            records = listOf(older, latest, sampleRx),
            reminders = listOf(reminder)
        )

        val encoded = json.encodeToString(bundle)
        val decoded = json.decodeFromString<BackupBundle>(encoded)

        assertEquals(4, decoded.profiles.size)
        assertEquals(3, decoded.records.size)
        assertEquals(1, decoded.reminders.size)
        assertEquals("Esomeprazole 40mg", decoded.reminders.first().medicationName)
    }
}
