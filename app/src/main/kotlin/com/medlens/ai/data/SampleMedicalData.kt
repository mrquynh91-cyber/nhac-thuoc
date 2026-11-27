package com.medlens.ai.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SampleMedicalData {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Creates 2 chronological lab records (6 months ago & today) so the user can
     * immediately see both the Traffic-Light breakdown AND the Trend Comparison Chart!
     */
    fun createSampleLabPair(profileId: String): Pair<MedicalRecord, MedicalRecord> {
        val now = System.currentTimeMillis()
        val sixMonthsAgoDate = LocalDate.now().minusMonths(6).format(dateFormatter)
        val todayDate = LocalDate.now().format(dateFormatter)

        val olderRecord = MedicalRecord(
            profileId = profileId,
            title = "Xét Nghiệm Tổng Quát (6 tháng trước - Mẫu)",
            documentType = DocumentType.LAB_RESULT,
            facilityName = "Bệnh viện Đa khoa (Dữ liệu mẫu)",
            recordDate = sixMonthsAgoDate,
            overallSummary = "Đợt khám 6 tháng trước ghi nhận men gan ALT và mỡ máu Triglyceride tăng khá rõ, cần điều chỉnh ăn uống và tái khám kiểm tra lại.",
            summaryBullets = listOf(
                "Men gan ALT (74 U/L) và AST (46 U/L) đều cao hơn ngưỡng chuẩn.",
                "Mỡ máu Triglyceride (3.10 mmol/L) cao rõ rệt, cần giảm đồ chiên rán & đồ ngọt.",
                "Đường huyết đói (5.4 mmol/L) và chức năng thận Creatinine vẫn trong ngưỡng bình thường."
            ),
            labIndicators = listOf(
                LabIndicator(
                    name = "ALT (GPT)",
                    value = "74",
                    numericValue = 74.0,
                    unit = "U/L",
                    referenceRange = "< 40 U/L",
                    status = IndicatorStatus.HIGH,
                    plainExplanation = "Men gan ALT tăng khá rõ, thường gặp khi gan nhiễm mỡ hoặc ăn uống nhiều chất béo/rượu bia.",
                    dietAndLifestyleAdvice = "Kiêng rượu bia hoàn toàn, hạn chế đồ chiên rán và ngủ sớm trước 23h."
                ),
                LabIndicator(
                    name = "Triglyceride",
                    value = "3.10",
                    numericValue = 3.10,
                    unit = "mmol/L",
                    referenceRange = "< 1.7 mmol/L",
                    status = IndicatorStatus.HIGH,
                    plainExplanation = "Đây là chỉ số mỡ máu, đang cao hơn mức bình thường khá nhiều. Dễ làm tăng nguy cơ gan nhiễm mỡ và tim mạch nếu để kéo dài.",
                    dietAndLifestyleAdvice = "Giảm cơm trắng, bánh ngọt, nước ngọt, nội tạng động vật; đi bộ 30 phút mỗi ngày."
                ),
                LabIndicator(
                    name = "AST (GOT)",
                    value = "46",
                    numericValue = 46.0,
                    unit = "U/L",
                    referenceRange = "< 40 U/L",
                    status = IndicatorStatus.BORDERLINE,
                    plainExplanation = "Men gan AST hơi nhỉnh nhẹ trên ngưỡng bình thường, cần theo dõi cùng chỉ số ALT.",
                    dietAndLifestyleAdvice = "Ăn nhiều rau xanh đậm, uống đủ nước và tránh thức khuya."
                ),
                LabIndicator(
                    name = "Glucose (Đường huyết đói)",
                    value = "5.4",
                    numericValue = 5.4,
                    unit = "mmol/L",
                    referenceRange = "3.9 - 5.6 mmol/L",
                    status = IndicatorStatus.NORMAL,
                    plainExplanation = "Đường huyết lúc đói bình thường.",
                    dietAndLifestyleAdvice = "Duy trì ăn đúng bữa, hạn chế ăn vặt ban đêm."
                ),
                LabIndicator(
                    name = "Creatinine",
                    value = "80",
                    numericValue = 80.0,
                    unit = "µmol/L",
                    referenceRange = "62 - 106 µmol/L",
                    status = IndicatorStatus.NORMAL,
                    plainExplanation = "Chức năng lọc của thận đang hoạt động tốt.",
                    dietAndLifestyleAdvice = "Uống đủ 1.5–2 lít nước/ngày, ăn nhạt vừa phải."
                )
            ),
            generalDietAdvice = listOf(
                "Hạn chế đồ chiên xào nhiều dầu mỡ, mỡ động vật, nội tạng và trà sữa/nước ngọt.",
                "Tăng cường cá biển, rau xanh, yến mạch và vận động nhẹ 30 phút/ngày."
            ),
            suggestedDoctorQuestions = listOf(
                "Với mức ALT 74 và Triglyceride 3.10, tôi có cần siêu âm gan kiểm tra gan nhiễm mỡ không?",
                "Tôi có thể cải thiện bằng chế độ ăn uống trong 3 tháng rồi xét nghiệm lại hay cần dùng thuốc ngay?"
            ),
            createdAt = now - 180L * 24 * 60 * 60 * 1000L,
            updatedAt = now - 180L * 24 * 60 * 60 * 1000L,
            isSample = true
        )

        val latestRecord = MedicalRecord(
            profileId = profileId,
            title = "Phiếu Xét Nghiệm Sinh Hóa Máu (Mẫu)",
            documentType = DocumentType.LAB_RESULT,
            facilityName = "Phòng khám Đa khoa Tổng quát (Dữ liệu mẫu)",
            recordDate = todayDate,
            overallSummary = "Kết quả có tiến triển tốt so với đợt trước! Men gan AST, đường huyết và chức năng thận đã ở mức bình thường. Chỉ còn ALT và mỡ máu Triglyceride hơi cao nhẹ cần tiếp tục duy trì ăn uống lành mạnh.",
            summaryBullets = listOf(
                "🔴 2 chỉ số cao hơn chuẩn: Men gan ALT (58 U/L) và Mỡ máu Triglyceride (2.45 mmol/L) — nhưng đã giảm so với lần trước.",
                "🟡 1 chỉ số cận ngưỡng: Mỡ xấu LDL-Cholesterol (3.5 mmol/L), nên chú ý giảm mỡ động vật.",
                "🟢 3 chỉ số hoàn toàn bình thường: Men gan AST, Đường huyết đói Glucose và chức năng thận Creatinine.",
                "💡 Xu hướng cải thiện tích cực — hãy mang phiếu này trao đổi thêm với bác sĩ khi tái khám."
            ),
            labIndicators = listOf(
                LabIndicator(
                    name = "ALT (GPT)",
                    value = "58",
                    numericValue = 58.0,
                    unit = "U/L",
                    referenceRange = "< 40 U/L",
                    status = IndicatorStatus.HIGH,
                    plainExplanation = "Men gan ALT còn cao nhẹ hơn mức chuẩn một chút, thường do gan nhiễm mỡ nhẹ hoặc cơ thể mệt mỏi.",
                    dietAndLifestyleAdvice = "Hạn chế tối đa rượu bia, đồ chiên rán, nội tạng; tăng rau xanh đậm và uống đủ 2 lít nước/ngày."
                ),
                LabIndicator(
                    name = "Triglyceride",
                    value = "2.45",
                    numericValue = 2.45,
                    unit = "mmol/L",
                    referenceRange = "< 1.7 mmol/L",
                    status = IndicatorStatus.HIGH,
                    plainExplanation = "Đây là chỉ số mỡ máu, đang cao hơn mức bình thường một chút. Nên giảm đồ chiên rán, nội tạng, nước ngọt và đi bộ thêm.",
                    dietAndLifestyleAdvice = "Giảm tinh bột nhanh & đồ ngọt, ưu tiên cá béo (cá hồi, cá thu), yến mạch và đi bộ nhanh 30 phút mỗi ngày."
                ),
                LabIndicator(
                    name = "LDL-Cholesterol",
                    value = "3.5",
                    numericValue = 3.5,
                    unit = "mmol/L",
                    referenceRange = "< 3.4 mmol/L",
                    status = IndicatorStatus.BORDERLINE,
                    plainExplanation = "Mỡ máu xấu hơi chạm ngưỡng trên một chút. Cần theo dõi để tránh tích tụ lâu dài ở thành mạch máu.",
                    dietAndLifestyleAdvice = "Thay mỡ động vật bằng dầu ô liu/dầu đậu nành, ăn thêm đậu nành và chất xơ hòa tan."
                ),
                LabIndicator(
                    name = "AST (GOT)",
                    value = "32",
                    numericValue = 32.0,
                    unit = "U/L",
                    referenceRange = "< 40 U/L",
                    status = IndicatorStatus.NORMAL,
                    plainExplanation = "Men gan AST đã về ngưỡng bình thường, tín hiệu rất tốt cho thấy gan đang hồi phục ổn định.",
                    dietAndLifestyleAdvice = "Tiếp tục duy trì ngủ đủ giấc và hạn chế rượu bia."
                ),
                LabIndicator(
                    name = "Glucose (Đường huyết đói)",
                    value = "5.1",
                    numericValue = 5.1,
                    unit = "mmol/L",
                    referenceRange = "3.9 - 5.6 mmol/L",
                    status = IndicatorStatus.NORMAL,
                    plainExplanation = "Đường huyết lúc đói hoàn toàn bình thường, chưa có dấu hiệu tiền tiểu đường.",
                    dietAndLifestyleAdvice = "Giữ thói quen ăn đúng bữa và hạn chế ăn khuya."
                ),
                LabIndicator(
                    name = "Creatinine",
                    value = "78",
                    numericValue = 78.0,
                    unit = "µmol/L",
                    referenceRange = "62 - 106 µmol/L",
                    status = IndicatorStatus.NORMAL,
                    plainExplanation = "Khả năng lọc chất thải của thận đang hoạt động ổn định.",
                    dietAndLifestyleAdvice = "Ăn nhạt vừa phải, không tự ý dùng thuốc giảm đau kéo dài."
                )
            ),
            generalDietAdvice = listOf(
                "Hạn chế rượu bia, nước ngọt đóng chai, đồ chiên ngập dầu và thịt đỏ nhiều mỡ.",
                "Tăng cường rau lá xanh, trái cây ít ngọt (ổi, bưởi, táo) và cá biển 2–3 bữa/tuần.",
                "Vận động thể chất tối thiểu 150 phút/tuần (đi bộ nhanh, đạp xe, bơi lội)."
            ),
            suggestedDoctorQuestions = listOf(
                "Men gan ALT và Triglyceride của tôi đã giảm so với 6 tháng trước, tôi nên duy trì ăn uống hay cần dùng thêm thuốc hạ mỡ máu không?",
                "Bao lâu nữa tôi nên đi xét nghiệm lại men gan và bộ mỡ máu để theo dõi?",
                "Các thuốc hiện tại tôi đang uống (nếu có) có ảnh hưởng đến chỉ số men gan ALT không?"
            ),
            redFlags = listOf(
                "Nếu xuất hiện vàng da, vàng mắt, đau tức hạ sườn phải hoặc mệt mỏi kéo dài, cần đi khám bác sĩ sớm."
            ),
            createdAt = now,
            updatedAt = now,
            isSample = true
        )

        return olderRecord to latestRecord
    }

    fun createSampleLabRecord(profileId: String = FamilyProfile.DEFAULT_SELF_ID): MedicalRecord {
        return createSampleLabPair(profileId).second
    }

    fun createSamplePrescriptionRecord(profileId: String = FamilyProfile.DEFAULT_SELF_ID): MedicalRecord {
        val today = LocalDate.now().format(dateFormatter)
        return MedicalRecord(
            profileId = profileId,
            title = "Đơn Thuốc Dạ Dày & Mỡ Máu (Mẫu)",
            documentType = DocumentType.PRESCRIPTION,
            facilityName = "Khoa Nội Tổng Hợp (Dữ liệu mẫu)",
            recordDate = today,
            overallSummary = "Đơn thuốc gồm 3 loại hỗ trợ giảm axit dạ dày, làm dịu trào ngược và kiểm soát mỡ máu buổi tối. Lưu ý uống thuốc dạ dày trước bữa sáng 30 phút và thuốc mỡ máu sau ăn tối.",
            summaryBullets = listOf(
                "Tổng cộng 3 loại thuốc: 2 thuốc hỗ trợ dạ dày & 1 thuốc kiểm soát mỡ máu.",
                "Sáng: Esomeprazole uống TRƯỚC ăn sáng 30 phút.",
                "Trưa & Chiều/Tối: Gaviscon uống SAU ăn (cách thuốc khác ít nhất 2 giờ).",
                "Tối: Atorvastatin uống SAU ăn tối, hạn chế uống nước ép bưởi chùm."
            ),
            medications = listOf(
                MedicationItem(
                    name = "Esomeprazole 40mg",
                    activeIngredient = "Esomeprazole",
                    categoryShort = "Bảo vệ dạ dày / Giảm axit",
                    dosage = "1 viên/lần",
                    frequency = "Ngày 1 lần (Sáng)",
                    mealTiming = MealTiming.BEFORE_MEAL,
                    daySlots = listOf(DaySlot.MORNING),
                    durationDays = 14,
                    purposePlainVietnamese = "Giảm lượng axit tiết ra trong dạ dày, giúp niêm mạc dạ dày lành lại và giảm ợ chua, đau thượng vị.",
                    sideEffectsWarning = "Nuốt nguyên viên, không nhai hoặc nghiền. Có thể gây khô miệng hoặc đầy hơi nhẹ.",
                    suggestedReminderTimes = listOf("07:00")
                ),
                MedicationItem(
                    name = "Gaviscon dạng gói 10ml",
                    activeIngredient = "Sodium alginate + Sodium bicarbonate + Calcium carbonate",
                    categoryShort = "Chống trào ngược dạ dày",
                    dosage = "1 gói/lần",
                    frequency = "Ngày 2 lần (Trưa, Chiều)",
                    mealTiming = MealTiming.AFTER_MEAL,
                    daySlots = listOf(DaySlot.NOON, DaySlot.AFTERNOON),
                    durationDays = 10,
                    purposePlainVietnamese = "Tạo lớp màng bảo vệ phía trên dịch dạ dày, giúp dịu cảm giác nóng rát và ngăn trào ngược sau ăn.",
                    sideEffectsWarning = "Uống cách các thuốc khác ít nhất 2 giờ để không làm giảm hấp thu thuốc.",
                    suggestedReminderTimes = listOf("12:00", "17:30")
                ),
                MedicationItem(
                    name = "Atorvastatin 10mg",
                    activeIngredient = "Atorvastatin",
                    categoryShort = "Hạ mỡ máu",
                    dosage = "1 viên/lần",
                    frequency = "Ngày 1 lần (Tối)",
                    mealTiming = MealTiming.AFTER_MEAL,
                    daySlots = listOf(DaySlot.EVENING),
                    durationDays = 30,
                    purposePlainVietnamese = "Hỗ trợ hạ mỡ máu xấu (LDL) và Triglyceride, giảm nguy cơ xơ vữa mạch máu.",
                    sideEffectsWarning = "Tránh uống cùng rượu bia và hạn chế nước ép bưởi chùm lượng lớn. Nếu đau mỏi cơ bất thường hãy báo ngay bác sĩ.",
                    suggestedReminderTimes = listOf("19:30")
                )
            ),
            generalDietAdvice = listOf(
                "Không nằm ngay sau khi ăn (nên chờ ít nhất 2–3 tiếng sau bữa tối).",
                "Tránh cà phê đặc, đồ chua cay, rượu bia trong thời gian điều trị dạ dày."
            ),
            suggestedDoctorQuestions = listOf(
                "Tôi cần uống cách nhau bao lâu giữa Gaviscon và thuốc hạ mỡ máu buổi tối?",
                "Sau 14 ngày hết thuốc dạ dày, tôi có cần tái khám để điều chỉnh đơn không?",
                "Nếu có tiền sử đau cơ hoặc mệt mỏi khi dùng Atorvastatin, tôi nên xử lý thế nào?"
            ),
            redFlags = listOf(
                "Đau bụng dữ dội, nôn ra máu, đi ngoài phân đen hoặc đau mỏi cơ bất thường cần đi khám ngay."
            ),
            isSample = true
        )
    }
}
