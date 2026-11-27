package com.medlens.ai.data

data class DietConditionPreset(
    val key: String,
    val label: String,
    val keywords: List<String>,
    val defaultPlan: PatientDietPlan
)

object DietPlanCatalog {
    val presets: List<DietConditionPreset> = listOf(
        DietConditionPreset(
            key = "liver",
            label = "Viêm gan B / Men gan cao",
            keywords = listOf("gan", "viêm gan", "getinomide", "tenofovir", "alt", "ast", "ggt"),
            defaultPlan = PatientDietPlan(
                conditionKey = "liver",
                conditionTitle = "Chế độ ăn bảo vệ gan (Viêm gan B / Men gan cao)",
                summary = "Ưu tiên thực phẩm thanh đạm, giàu chất xơ và đạm dễ tiêu để giảm tải cho gan; tuyệt đối không dùng rượu bia hoặc thảo dược không rõ nguồn gốc.",
                shouldEat = listOf(
                    "Rau xanh đậm (bông cải xanh, cải bó xôi, rau ngót, mồng tơi) và trái cây tươi ít ngọt",
                    "Đạm lành mạnh dễ tiêu: cá hấp/luộc, ức gà bỏ da, đậu phụ, trứng luộc (3–4 quả/tuần)",
                    "Tinh bột tốt: cơm gạo lứt/gạo xát dối, yến mạch, khoai lang",
                    "Uống đủ 1.5–2 lít nước lọc ấm mỗi ngày, có thể dùng trà xanh loãng sau ăn"
                ),
                shouldAvoid = listOf(
                    "Tuyệt đối kiêng rượu, bia, đồ uống có cồn (gây tổn thương tế bào gan nặng hơn)",
                    "Đồ chiên rán nhiều dầu mỡ, mỡ động vật, nội tạng (gan, lòng, óc), đồ ăn nhanh",
                    "Thực phẩm mốc (lạc mốc, ngũ cốc để lâu) — chứa độc tố Aflatoxin rất hại gan",
                    "Không tự ý uống thuốc nam, lá cây, thực phẩm chức năng giải độc gan khi chưa hỏi bác sĩ"
                ),
                medicationFoodNotes = listOf(
                    "Nếu đang uống thuốc kháng virus viêm gan B (như Tenofovir / Getinomide), hãy uống đúng 1 khung giờ cố định mỗi ngày và không bỏ liều.",
                    "Tránh uống thuốc chung với trà đặc, cà phê hoặc rượu bia."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Cháo yến mạch thịt bằm hoặc bánh mì nguyên cám + 1 quả trứng luộc + rau xà lách",
                    "Trưa: 1–1.5 bát cơm + cá diêu hồng hấp gừng + canh cải xanh nấu tôm + 1 miếng đu đủ",
                    "Chiều nhẹ: 1 hũ sữa chua ít đường hoặc 1 quả táo/ổi",
                    "Tối: 1 bát cơm + đậu phụ sốt cà chua nhạt + bông cải xanh luộc + ức gà áp chảo ít dầu"
                )
            )
        ),
        DietConditionPreset(
            key = "diabetes",
            label = "Tiểu đường / Đường huyết cao",
            keywords = listOf("tiểu đường", "đường huyết", "glucose", "hba1c", "metformin", "insulin"),
            defaultPlan = PatientDietPlan(
                conditionKey = "diabetes",
                conditionTitle = "Chế độ ăn kiểm soát đường huyết (Tiểu đường)",
                summary = "Áp dụng nguyên tắc ăn rau trước → ăn đạm → ăn tinh bột sau cùng để đường huyết sau ăn không tăng vọt; chia đều bữa trong ngày.",
                shouldEat = listOf(
                    "Rau lá xanh ăn trước mỗi bữa (chiếm 1/2 đĩa ăn): rau muống, cải thìa, bầu, bí xanh, mướp đắng",
                    "Tinh bột hấp thu chậm: gạo lứt, gạo mầm, yến mạch, khoai lang luộc lượng vừa phải",
                    "Đạm ít béo: cá biển, cá đồng, ức gà, đậu hũ, các loại hạt (óc chó, hạnh nhân không muối)",
                    "Trái cây chỉ số đường huyết thấp (GI thấp): ổi, bưởi, táo xanh, thanh long, dâu tây"
                ),
                shouldAvoid = listOf(
                    "Nước ngọt có gas, trà sữa, nước ép trái cây đóng hộp, bánh kẹo ngọt, mứt",
                    "Cơm trắng quá nhiều, xôi, bánh mì trắng, phở/bún nhiều bánh ít rau",
                    "Trái cây quá ngọt: sầu riêng, nhãn, vải, xoài chín kỹ, mít, nho ngọt",
                    "Không bỏ bữa (dễ tụt đường huyết nguy hiểm nếu đang dùng thuốc hạ đường)"
                ),
                medicationFoodNotes = listOf(
                    "Luôn mang theo 1–2 viên kẹo nhỏ phòng trường hợp tụt đường huyết (run tay, vã mồ hôi).",
                    "Uống thuốc tiểu đường đúng thời điểm trước/sau bữa ăn theo chỉ định bác sĩ."
                ),
                sampleDayMenu = listOf(
                    "Sáng: 1 củ khoai lang nhỏ + 1 quả trứng luộc + 1 ly sữa hạt không đường",
                    "Trưa: 1 đĩa rau cải luộc ăn trước + 2/3 bát cơm gạo lứt + cá nục kho nhạt + canh bí đao",
                    "Chiều nhẹ: 2 múi bưởi hoặc nửa quả ổi",
                    "Tối: Canh mướp đắng nhồi thịt + 2/3 bát cơm + đậu phụ luộc + rau muống luộc"
                )
            )
        ),
        DietConditionPreset(
            key = "lipid",
            label = "Mỡ máu cao / Gan nhiễm mỡ",
            keywords = listOf("mỡ máu", "cholesterol", "triglyceride", "ldl", "gan nhiễm mỡ", "atorvastatin", "rosuvastatin"),
            defaultPlan = PatientDietPlan(
                conditionKey = "lipid",
                conditionTitle = "Chế độ ăn hạ mỡ máu & giảm gan nhiễm mỡ",
                summary = "Cắt giảm chất béo bão hòa, tinh bột tinh chế và đường; tăng cường chất xơ hòa tan và cá giàu Omega-3 kết hợp đi bộ nhanh 30 phút/ngày.",
                shouldEat = listOf(
                    "Cá giàu Omega-3 ít nhất 2–3 bữa/tuần: cá hồi, cá thu, cá trích, cá nục hấp/nướng",
                    "Chất xơ hòa tan giúp cuốn cholesterol: yến mạch, đậu lăng, đậu đen, táo, cà rốt, rau nhớt (mồng tơi, đậu bắp)",
                    "Chất béo tốt: dầu ô-liu, dầu đậu nành, quả bơ, hạt chia, hạt lanh lượng nhỏ",
                    "Chế biến luộc, hấp, áp chảo không dầu thay vì chiên ngập dầu"
                ),
                shouldAvoid = listOf(
                    "Mỡ heo, da gà/vịt, thịt ba chỉ nhiều mỡ, xúc xích, lạp xưởng, nội tạng động vật",
                    "Bánh ngọt công nghiệp, đồ chiên rán, bơ thực vật (chất béo trans)",
                    "Đường tinh luyện, nước ngọt, rượu bia (làm tăng mạnh Triglyceride)",
                    "Hạn chế lòng đỏ trứng (tối đa 2–3 lòng đỏ/tuần)"
                ),
                medicationFoodNotes = listOf(
                    "Nếu đang uống nhóm thuốc hạ mỡ máu Statin (Atorvastatin, Rosuvastatin...), tránh ăn bưởi chùm/uống nước ép bưởi sát giờ uống thuốc."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Cháo yến mạch nấm hương + 1 quả táo xanh",
                    "Trưa: 1 bát cơm gạo lứt + cá thu sốt cà chua ít dầu + đậu bắp luộc + canh mồng tơi",
                    "Chiều nhẹ: 1 nắm nhỏ hạnh nhân rang không muối",
                    "Tối: Salad ức gà dầu ô-liu + nửa bát cơm + canh rau củ thập cẩm"
                )
            )
        ),
        DietConditionPreset(
            key = "hypertension",
            label = "Huyết áp cao / Tim mạch",
            keywords = listOf("huyết áp", "tim mạch", "amlodipine", "losartan", "valsartan", "bisoprolol"),
            defaultPlan = PatientDietPlan(
                conditionKey = "hypertension",
                conditionTitle = "Chế độ ăn giảm muối DASH (Huyết áp cao / Tim mạch)",
                summary = "Giảm lượng muối xuống dưới 5g/ngày (khoảng 1 thìa cà phê gạt gồm cả nước mắm, hạt nêm), tăng rau củ giàu Kali và Magie.",
                shouldEat = listOf(
                    "Rau củ và trái cây giàu Kali giúp hạ áp tự nhiên: chuối, cam, rau ngót, cải bó xôi, khoai lang, cà chua",
                    "Ngũ cốc nguyên hạt, đậu hạt, cá hấp, thịt nạc trắng, sữa tách béo ít đường",
                    "Dùng gia vị tự nhiên tạo hương vị thay muối: gừng, tỏi, sả, chanh, rau thơm, tiêu"
                ),
                shouldAvoid = listOf(
                    "Dưa muối, cà muối, kim chi, mắm ruốc, cá khô, mắm nêm, nước tương đậm đặc",
                    "Mì ăn liền, giò chả, thịt nguội, đồ hộp, snack mặn (chứa lượng Natri rất cao)",
                    "Rượu bia, thuốc lá, cà phê đậm đặc gây co mạch tăng nhịp tim"
                ),
                medicationFoodNotes = listOf(
                    "Tránh ăn bưởi khi đang dùng một số thuốc chẹn kênh canxi (như Amlodipine, Nifedipine).",
                    "Uống thuốc huyết áp đều đặn hằng ngày, không tự ngưng khi thấy huyết áp đã đẹp."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Phở gà nấu nhạt (không húp hết nước dùng) hoặc bánh mì đen + 1 quả chuối",
                    "Trưa: Cơm + cá lóc hấp bầu + rau muống luộc chấm ít nước mắm chanh tỏi loãng",
                    "Chiều nhẹ: 1 ly sữa tách béo không đường hoặc 1 quả cam",
                    "Tối: Cơm + thịt thăn luộc + canh cà chua nấu đậu phụ + bí xanh luộc"
                )
            )
        ),
        DietConditionPreset(
            key = "stomach",
            label = "Đau dạ dày / Trào ngược",
            keywords = listOf("dạ dày", "trào ngược", "hp", "esomeprazole", "omeprazole", "pantoprazole", "gaviscon"),
            defaultPlan = PatientDietPlan(
                conditionKey = "stomach",
                conditionTitle = "Chế độ ăn êm dịu niêm mạc (Đau dạ dày / Trào ngược)",
                summary = "Ăn chín, mềm, nhai kỹ, chia 4–5 bữa nhỏ trong ngày; tuyệt đối không để bụng quá đói hoặc ăn quá no rồi nằm ngay.",
                shouldEat = listOf(
                    "Thực phẩm mềm, dễ thấm axit: cháo, súp, cơm mềm, bánh mì khô, khoai tây/khoai lang hấp",
                    "Rau củ ít xơ cứng: bí đỏ, cà rốt hầm mềm, bắp cải luộc (giàu vitamin U tốt cho niêm mạc)",
                    "Chuối chín, đu đủ chín, táo hấp, sữa chua ăn sau bữa chính, tinh bột nghệ mật ong ấm"
                ),
                shouldAvoid = listOf(
                    "Đồ chua gắt (chanh, giấm, xoài xanh, cóc, dưa muối) và đồ cay nóng (ớt, tiêu nhiều, sa tế)",
                    "Cà phê, trà đặc lúc đói, nước ngọt có gas, bia rượu",
                    "Đồ chiên rán ngập dầu, thức ăn quá cứng/dai, gân sụn",
                    "Không ăn khuya trong vòng 2–3 giờ trước khi đi ngủ"
                ),
                medicationFoodNotes = listOf(
                    "Thuốc giảm tiết axit (Esomeprazole, Omeprazole...) cần uống trước bữa ăn sáng 30 phút để đạt hiệu quả tốt nhất.",
                    "Tránh tự ý uống thuốc giảm đau kháng viêm (Ibuprofen, Aspirin...) lúc bụng đói."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Cháo thịt bằm bí đỏ ấm hoặc bánh mì mềm + trứng hấp",
                    "Trưa: Cơm mềm + thịt nạc băm hấp nấm + bắp cải luộc mềm + canh khoai tây cà rốt",
                    "Chiều nhẹ: 1 quả chuối chín hoặc 2 lát bánh quy lạt",
                    "Tối (trước 19h): Súp gà ngô non mềm + nửa bát cơm + cá hấp gừng"
                )
            )
        ),
        DietConditionPreset(
            key = "gout",
            label = "Gout (Axit uric cao)",
            keywords = listOf("gout", "gút", "acid uric", "axit uric", "allopurinol", "febuxostat", "colchicine"),
            defaultPlan = PatientDietPlan(
                conditionKey = "gout",
                conditionTitle = "Chế độ ăn ít nhân Purin (Gout / Axit uric cao)",
                summary = "Giảm mạnh thực phẩm giàu Purin (nội tạng, hải sản vỏ, thịt đỏ, bia), uống nhiều nước lọc mỗi ngày để thận đào thải Axit uric.",
                shouldEat = listOf(
                    "Uống 2–2.5 lít nước lọc/ngày (có thể thêm nước khoáng kiềm nhẹ)",
                    "Sữa ít béo, sữa chua không đường, trứng gà, đậu phụ lượng vừa phải",
                    "Rau xanh giàu vitamin C và chất xơ: dưa leo, cải bẹ xanh, bí đao, ổi, cam, anh đào (cherry)"
                ),
                shouldAvoid = listOf(
                    "Nội tạng động vật (gan, thận, tim, lòng), tiết canh, nước lẩu hầm xương đặc",
                    "Hải sản giàu purin: tôm, cua, ghẹ, mực, sò, cá mòi, cá cơm",
                    "Thịt đỏ (bò, dê, chó, thịt thú rừng) và các loại bia, rượu mạnh",
                    "Đồ uống nhiều đường Fructose (nước ngọt, siro)"
                ),
                medicationFoodNotes = listOf(
                    "Không tự ý ngưng thuốc hạ axit uric (Allopurinol, Febuxostat) ngay cả khi khớp đã hết đau.",
                    "Tránh tuyệt đối bia rượu trong và sau đợt đau khớp cấp."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Bánh mì trứng ốp la ít dầu + 1 ly sữa tươi ít béo không đường",
                    "Trưa: Cơm + đậu phụ sốt cà chua + trứng đúc thịt nạc ít + canh bí đao",
                    "Chiều nhẹ: 1 quả ổi hoặc 1 ly nước lọc ấm pha vài lát chanh loãng",
                    "Tối: Cơm + ức gà luộc (lượng vừa ~80g) + rau cải luộc + dưa leo"
                )
            )
        ),
        DietConditionPreset(
            key = "kidney",
            label = "Thận yếu / Ăn nhạt",
            keywords = listOf("thận", "creatinin", "ure", "egfr", "protein niệu", "suy thận"),
            defaultPlan = PatientDietPlan(
                conditionKey = "kidney",
                conditionTitle = "Chế độ ăn giảm gánh nặng cho thận",
                summary = "Ăn nhạt tuyệt đối, kiểm soát lượng đạm vừa đủ theo cân nặng, hạn chế thực phẩm giàu Phốt-pho và Kali cao nếu có chỉ định bác sĩ.",
                shouldEat = listOf(
                    "Tinh bột ít đạm: miến dong, bột sắn dây, khoai sọ, cơm trắng lượng vừa đủ năng lượng",
                    "Đạm chất lượng cao với khẩu phần nhỏ theo hướng dẫn bác sĩ: lòng trắng trứng, cá nạc, thịt nạc trắng",
                    "Rau củ ít Kali: bầu, bí xanh, su su, bắp cải, dưa leo (nên thái nhỏ luộc bỏ nước đầu)"
                ),
                shouldAvoid = listOf(
                    "Muối chấm, nước mắm mặn, mì gói, đồ hộp, dưa muối, xúc xích",
                    "Nước hầm xương đặc, nội tạng động vật, nước ngọt có gas màu sẫm (giàu phốt-pho)",
                    "Khế chua (độc cho người bệnh thận) và hạn chế trái cây quá giàu Kali (chuối, cam đặc) khi Kali máu cao"
                ),
                medicationFoodNotes = listOf(
                    "Tuyệt đối không tự ý dùng thuốc giảm đau NSAID hoặc thuốc nam/rễ cây không rõ nguồn gốc vì rất dễ gây suy thận cấp."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Miến gà xé nhạt (ít thịt, không dùng nước hầm xương đặc)",
                    "Trưa: Cơm + cá lóc hấp nhạt (~70g) + su su luộc bỏ nước đầu",
                    "Chiều nhẹ: 1 bát chè bột sắn dây ít đường hoặc nửa quả táo",
                    "Tối: Cơm + lòng trắng trứng hấp + bầu luộc"
                )
            )
        ),
        DietConditionPreset(
            key = "recovery",
            label = "Bồi bổ / Hồi phục sức khỏe",
            keywords = listOf("hồi phục", "mệt", "thiếu máu", "hậu phẫu", "suy nhược", "vitamin"),
            defaultPlan = PatientDietPlan(
                conditionKey = "recovery",
                conditionTitle = "Chế độ ăn bồi bổ & phục hồi thể lực",
                summary = "Cung cấp đủ đạm dễ hấp thu, vitamin C/nhóm B và chia bữa hợp lý giúp cơ thể nhanh lấy lại sức mà không gây đầy bụng.",
                shouldEat = listOf(
                    "Đạm dễ tiêu: cháo cá, súp gà nấm, thịt bò nạc hầm mềm, trứng, sữa dinh dưỡng ấm",
                    "Rau củ nhiều màu sắc (cà rốt, bí đỏ, rau dền, cải xanh) và trái cây giàu Vitamin C (cam, ổi, kiwi)",
                    "Uống đủ nước ấm, canh rau, nước điện giải nếu có sốt hoặc mệt nhiều"
                ),
                shouldAvoid = listOf(
                    "Đồ ăn sống lạnh (gỏi, tiết canh, rau sống chưa rửa kỹ), hải sản dễ dị ứng",
                    "Đồ chiên ngập dầu, thức ăn nhanh khó tiêu, rượu bia, cà phê quá nhiều"
                ),
                medicationFoodNotes = listOf(
                    "Nếu đang uống bổ sung sắt, uống cùng chút nước cam/vitamin C giúp hấp thu tốt hơn và tránh uống chung với sữa hoặc trà."
                ),
                sampleDayMenu = listOf(
                    "Sáng: Súp gà nấm hương ấm + 1 ly sữa tươi ấm",
                    "Trưa: Cơm + cá hồi áp chảo + rau cải xanh luộc + canh bí đỏ thịt bằm",
                    "Chiều nhẹ: 1 hũ sữa chua + 1 quả cam tươi",
                    "Tối: Cháo thịt bò hầm cà rốt mềm + trái cây tươi"
                )
            )
        )
    )

    fun detectSuggestedKey(
        profile: FamilyProfile,
        records: List<MedicalRecord>,
        reminders: List<MedicationReminder>
    ): String {
        val corpus = buildString {
            append(profile.chronicConditions).append(' ')
            records.take(5).forEach { rec ->
                append(rec.title).append(' ')
                append(rec.overallSummary).append(' ')
                rec.medications.forEach { append(it.name).append(' ').append(it.categoryShort).append(' ') }
                rec.labIndicators.filter { it.status != IndicatorStatus.NORMAL }
                    .forEach { append(it.name).append(' ') }
            }
            reminders.forEach { append(it.medicationName).append(' ').append(it.note).append(' ') }
        }.lowercase()

        for (preset in presets) {
            if (preset.keywords.any { corpus.contains(it) }) {
                return preset.key
            }
        }
        return presets.first().key
    }

    fun presetForKey(key: String): DietConditionPreset =
        presets.firstOrNull { it.key == key } ?: presets.first()
}
