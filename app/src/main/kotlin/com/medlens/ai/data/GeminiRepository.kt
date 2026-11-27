package com.medlens.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ScanAttachment(
    val uri: Uri,
    val mimeType: String, // "image/jpeg", "image/png", "application/pdf"
    val displayName: String
)

class GeminiRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun analyzeMedicalAttachments(
        attachments: List<ScanAttachment>,
        profileId: String,
        profileContext: FamilyProfile?,
        apiKey: String,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        hintType: DocumentType = DocumentType.LAB_RESULT,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<MedicalRecord> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.replace(Regex("\\s+"), "")
        if (attachments.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng chụp ảnh hoặc chọn ít nhất 1 ảnh / file PDF."))
        }
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Chưa có Gemini API Key. Vui lòng vào tab Cài đặt để nhập khóa API Gemini, hoặc bấm nút Xem dữ liệu mẫu."
                )
            )
        }

        runCatching {
            val preparedList = attachments.map { prepareAttachmentAndSaveCopy(it) }
            val requestJson = buildGenerateContentPayload(
                preparedParts = preparedList,
                hintType = hintType,
                profileContext = profileContext
            )

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                apiKey = cleanKey,
                requestJson = requestJson,
                onModelAutoSwitched = onModelAutoSwitched
            )

            val textPayload = extractCandidateText(responseBody)
            val parsed = parseStructuredAnalysis(textPayload)
            val firstImagePath = preparedList.firstOrNull { it.mimeType.startsWith("image/") }?.savedFile?.absolutePath
            parsed.toMedicalRecord(
                profileId = profileId,
                localImagePath = firstImagePath,
                localAttachmentPaths = preparedList.map { it.savedFile.absolutePath }
            )
        }
    }

    suspend fun askContextualQuestion(
        record: MedicalRecord,
        profile: FamilyProfile?,
        question: String,
        apiKey: String,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập hoặc nói câu hỏi."))
        }

        val cleanKey = apiKey.replace(Regex("\\s+"), "")
        if (cleanKey.isEmpty()) {
            return@withContext Result.success(buildOfflineHelpfulReply(record, profile, cleanQuestion))
        }

        runCatching {
            val recordSnapshot = json.encodeToString(
                record.copy(chatHistory = emptyList(), localImagePath = null, localAttachmentPaths = emptyList())
            )
            val profileNote = profile?.let {
                "Thông tin thành viên: ${it.name} (${it.relationship}), dị ứng thuốc: ${it.drugAllergies.ifBlank { "chưa ghi nhận" }}, bệnh nền: ${it.chronicConditions.ifBlank { "chưa ghi nhận" }}."
            }.orEmpty()

            val systemText = """
                Bạn là trợ lý y khoa tiếng Việt thân thiện của ứng dụng "Hiểu Đơn" (MedRead VN).
                Người dùng đang hỏi sâu hơn về chính phiếu khám/đơn thuốc bên dưới.
                Nguyên tắc bắt buộc:
                1. Trả lời bằng tiếng Việt đời thường, ấm áp, dễ hiểu (ngắn gọn 3–6 câu hoặc gạch đầu dòng).
                2. Chỉ dựa vào dữ liệu phiếu bên dưới và kiến thức y khoa an toàn phổ thông; không bịa thông tin.
                3. Không khẳng định chẩn đoán bệnh và không tự ý bảo người dùng đổi liều hoặc bỏ thuốc bác sĩ kê.
                4. Nếu câu hỏi liên quan dị ứng, tương tác thuốc, đau dạ dày hoặc triệu chứng bất thường, nhắc họ hỏi lại bác sĩ/dược sĩ.
                
                $profileNote
                Dữ liệu phiếu hiện tại (JSON):
                $recordSnapshot
            """.trimIndent()

            val root = buildJsonObject {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
                put("contents", buildJsonArray {
                    record.chatHistory.takeLast(8).forEach { msg ->
                        add(buildJsonObject {
                            put("role", if (msg.isUser) "user" else "model")
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", msg.text) })
                            })
                        })
                    }
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", cleanQuestion) })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.3)
                })
            }

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                apiKey = cleanKey,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            extractCandidateText(responseBody)
        }
    }

    private fun executeGenerateContentWithAutoFallback(
        preferredModel: String,
        apiKey: String,
        requestJson: String,
        onModelAutoSwitched: ((String) -> Unit)?
    ): String {
        val primary = normalizeModelId(preferredModel).ifEmpty { AppSettings.DEFAULT_GEMINI_MODEL }
        val firstAttempt = postGenerateContent(primary, apiKey, requestJson)
        if (firstAttempt.isSuccess) return firstAttempt.body

        // If 404 (model retired / unavailable for this API key), discover active models dynamically!
        if (firstAttempt.code == 404) {
            val discovered = discoverAvailableGeminiModels(apiKey)
            val fallbackCandidates = (discovered + listOf(
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
                "gemini-flash-latest",
                "gemini-2.5-pro"
            ))
                .map { normalizeModelId(it) }
                .distinct()
                .filter { it.isNotEmpty() && it != primary }

            var lastError = firstAttempt
            for (candidate in fallbackCandidates.take(4)) {
                val retry = postGenerateContent(candidate, apiKey, requestJson)
                if (retry.isSuccess) {
                    onModelAutoSwitched?.invoke(candidate)
                    return retry.body
                }
                lastError = retry
                if (retry.code != 404) break
            }
            throw IllegalStateException(parseGeminiError(lastError.code, lastError.body, lastError.model))
        }

        throw IllegalStateException(parseGeminiError(firstAttempt.code, firstAttempt.body, primary))
    }

    private data class RawApiAttempt(
        val model: String,
        val code: Int,
        val isSuccess: Boolean,
        val body: String
    )

    private fun postGenerateContent(modelId: String, apiKey: String, requestJson: String): RawApiAttempt {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        val request = Request.Builder()
            .url(endpoint)
            .header("x-goog-api-key", apiKey)
            .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            RawApiAttempt(
                model = modelId,
                code = response.code,
                isSuccess = response.isSuccessful,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun discoverAvailableGeminiModels(apiKey: String): List<String> {
        return runCatching {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100")
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val models = root["models"]?.jsonArray ?: return@runCatching emptyList()

                val usable = models.mapNotNull { el ->
                    val obj = el.jsonObject
                    val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val id = normalizeModelId(rawName)
                    val methods = obj["supportedGenerationMethods"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        .orEmpty()
                    val supportsGenerate = methods.contains("generateContent")
                    val isGeminiTextVision = id.startsWith("gemini-") &&
                        !id.contains("embedding") &&
                        !id.contains("tts") &&
                        !id.contains("audio") &&
                        !id.contains("imagen") &&
                        !id.contains("veo")
                    if (supportsGenerate && isGeminiTextVision) id else null
                }

                usable.sortedBy { modelRank(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun normalizeModelId(raw: String): String {
        return raw.trim().removePrefix("models/")
    }

    private fun modelRank(id: String): Int = when {
        id == "gemini-2.5-flash" -> 0
        id == "gemini-2.5-flash-lite" -> 1
        id == "gemini-flash-latest" -> 2
        id.startsWith("gemini-3") && id.contains("flash") -> 3
        id == "gemini-2.5-pro" -> 4
        id.contains("flash") -> 5
        else -> 10
    }

    private fun buildOfflineHelpfulReply(
        record: MedicalRecord,
        profile: FamilyProfile?,
        question: String
    ): String {
        val lower = question.lowercase()
        val allergyHint = profile?.drugAllergies?.takeIf { it.isNotBlank() }
            ?.let { "\n• Lưu ý hồ sơ có ghi nhận tiền sử dị ứng: $it." }.orEmpty()

        return when {
            lower.contains("sữa") || lower.contains("uống chung") -> {
                "Để an toàn, bạn nên uống thuốc với **nước lọc đun sôi để nguội**, tránh uống chung với sữa, trà đặc hoặc cà phê vì canxi trong sữa có thể làm giảm hấp thu một số thuốc (đặc biệt là kháng sinh và thuốc dạ dày). Hãy uống cách bữa sữa ít nhất 1–2 giờ và xác nhận lại với dược sĩ nhé.$allergyHint\n\n*(Mẹo: Nhập Gemini API Key trong tab Cài đặt để AI trả lời chi tiết theo từng thuốc trong phiếu này.)*"
            }
            lower.contains("dạ dày") || lower.contains("đau bụng") -> {
                "Nếu người uống có tiền sử đau dạ dày, nên ưu tiên uống các thuốc dễ kích ứng **ngay sau khi ăn no** (trừ thuốc giảm tiết axit như Esomeprazole/Omeprazole cần uống trước ăn 30 phút theo đúng đơn). Tuyệt đối không tự ý ngưng thuốc; nếu thấy cồn cào hoặc đau bụng tăng lên, hãy báo bác sĩ điều trị ngay.$allergyHint"
            }
            else -> {
                val medsSummary = if (record.medications.isNotEmpty()) {
                    "Đơn hiện có ${record.medications.size} thuốc: ${record.medications.joinToString { it.name }}."
                } else {
                    "Phiếu hiện có ${record.labIndicators.size} chỉ số xét nghiệm."
                }
                "Mình đã ghi nhận câu hỏi của bạn về \"${record.title}\". $medsSummary Hiện chưa có Gemini API Key nên mình chỉ đưa ra hướng dẫn an toàn cơ bản: hãy tuân thủ đúng liều và thời điểm trước/sau ăn theo đơn, mang các câu hỏi gợi ý trong phiếu đi trao đổi trực tiếp với bác sĩ.$allergyHint\n\n*(Nhập Gemini API Key ở tab Cài đặt để kích hoạt Trợ lý Hỏi-Đáp AI đầy đủ.)*"
            }
        }
    }

    private data class PreparedPart(
        val mimeType: String,
        val base64Data: String,
        val savedFile: File
    )

    private fun prepareAttachmentAndSaveCopy(attachment: ScanAttachment): PreparedPart {
        val rawBytes = context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Không thể đọc tệp: ${attachment.displayName}")

        val scansDir = File(context.filesDir, "scans").apply { mkdirs() }
        val isPdf = attachment.mimeType.equals("application/pdf", ignoreCase = true) ||
            attachment.displayName.endsWith(".pdf", ignoreCase = true)

        if (isPdf) {
            if (rawBytes.size > 15 * 1024 * 1024) {
                throw IllegalArgumentException("File PDF vượt quá 15MB, vui lòng chọn file nhỏ hơn.")
            }
            val savedPdf = File(scansDir, "scan_${UUID.randomUUID()}.pdf")
            FileOutputStream(savedPdf).use { it.write(rawBytes) }
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            return PreparedPart("application/pdf", b64, savedPdf)
        }

        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: throw IllegalArgumentException("Tệp '${attachment.displayName}' không phải hình ảnh hợp lệ.")

        val resized = resizeKeepingAspect(decoded, maxDimension = 1600)
        val outStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 82, outStream)
        val compressedBytes = outStream.toByteArray()

        if (resized !== decoded) resized.recycle()
        decoded.recycle()

        val savedFile = File(scansDir, "scan_${UUID.randomUUID()}.jpg")
        FileOutputStream(savedFile).use { it.write(compressedBytes) }
        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        return PreparedPart("image/jpeg", base64, savedFile)
    }

    private fun resizeKeepingAspect(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun buildGenerateContentPayload(
        preparedParts: List<PreparedPart>,
        hintType: DocumentType,
        profileContext: FamilyProfile?
    ): String {
        val modeInstruction = when (hintType) {
            DocumentType.LAB_RESULT -> "Chế độ người dùng chọn: PHIẾU XÉT NGHIỆM (máu, nước tiểu, sinh hóa...)."
            DocumentType.PRESCRIPTION -> "Chế độ người dùng chọn: ĐƠN THUỐC hoặc ẢNH VỎ HỘP / CHAI / LỌ / VỈ THUỐC CÓ NHÃN HƯỚNG DẪN UỐNG (chữ in hoặc chữ viết tay bác sĩ/nhà thuốc)."
            DocumentType.MIXED -> "Hãy tự nhận diện đây là phiếu xét nghiệm, đơn thuốc, vỏ hộp/chai thuốc, hay cả hai."
        }
        val profileInfo = profileContext?.let {
            "Hồ sơ người khám: ${it.name} (${it.relationship}), dị ứng thuốc: ${it.drugAllergies.ifBlank { "chưa có" }}, bệnh nền: ${it.chronicConditions.ifBlank { "chưa có" }}."
        }.orEmpty()

        val systemPrompt = """
            Bạn là một trợ lý y khoa tiếng Việt chuyên giải thích phiếu xét nghiệm và đơn thuốc cho người bình thường. Hãy phân tích hình ảnh/tài liệu PDF được cung cấp và trả về đúng định dạng JSON yêu cầu.

            Nguyên tắc bắt buộc:
            1. Dùng ngôn ngữ đời thường, dễ hiểu, ấm áp, tránh lạm dụng thuật ngữ chuyên môn khó hiểu.
            2. Nếu chữ viết tay bị mờ hoặc không chắc chắn 100% tên thuốc/chỉ số, TUYỆT ĐỐI không đoán bừa — hãy ghi rõ "[Chữ mờ, cần xác nhận lại với nhà thuốc/bác sĩ]".
            3. Không đưa ra chẩn đoán khẳng định bệnh và không tự ý thay đổi liều lượng bác sĩ đã kê.
            4. Luôn kèm lời khuyên ăn uống/sinh hoạt an toàn và sinh ra 2–3 câu hỏi thông minh để người dùng trao đổi lại với bác sĩ khi tái khám.
        """.trimIndent()

        val userPrompt = """
            $modeInstruction
            $profileInfo
            Nếu có nhiều trang, hãy gộp đầy đủ thông tin từ tất cả các trang không bỏ sót.

            Trả về DUY NHẤT một đối tượng JSON hợp lệ (không thêm markdown) đúng cấu trúc sau:
            {
              "documentType": "LAB_RESULT | PRESCRIPTION | MIXED",
              "title": "Tiêu đề ngắn gọn dễ hiểu",
              "facilityName": "Tên bệnh viện/phòng khám nếu có hoặc chuỗi rỗng",
              "recordDate": "dd/MM/yyyy nếu có hoặc chuỗi rỗng",
              "overallSummary": "Tóm tắt ngắn gọn 2 câu tổng quan",
              "summaryBullets": [
                "Gạch đầu dòng 1 dễ hiểu nhất về tình trạng chung",
                "Gạch đầu dòng 2 về chỉ số/thuốc cần lưu ý nhất",
                "Gạch đầu dòng 3 về hướng theo dõi an toàn"
              ],
              "labIndicators": [
                {
                  "name": "Tên chỉ số chuẩn hóa ngắn gọn (vd: ALT (GPT), AST (GOT), Triglyceride)",
                  "value": "Giá trị đo dạng chữ",
                  "numericValue": 58.0,
                  "unit": "Đơn vị",
                  "referenceRange": "Khoảng tham chiếu",
                  "status": "NORMAL | BORDERLINE | HIGH | LOW | CRITICAL",
                  "plainExplanation": "Giải thích đời thường dễ hiểu",
                  "dietAndLifestyleAdvice": "Gợi ý ăn uống, vận động cụ thể"
                }
              ],
              "medications": [
                {
                  "name": "Tên thuốc (hoặc ghi kèm [Chữ mờ, cần xác nhận lại với nhà thuốc/bác sĩ] nếu không rõ)",
                  "activeIngredient": "Hoạt chất chính",
                  "categoryShort": "Công dụng ngắn gọn (vd: Bảo vệ dạ dày, Hạ mỡ máu, Kháng sinh)",
                  "dosage": "Liều mỗi lần (vd: 1 viên)",
                  "frequency": "Tần suất (vd: Ngày 2 lần Sáng, Tối)",
                  "mealTiming": "BEFORE_MEAL | AFTER_MEAL | WITH_MEAL | ANYTIME",
                  "daySlots": ["MORNING", "EVENING"],
                  "durationDays": 7,
                  "purposePlainVietnamese": "Giải thích công dụng bằng ngôn ngữ đời thường",
                  "sideEffectsWarning": "Cảnh báo an toàn (buồn ngủ, tránh rượu bia, cách bữa ăn...)",
                  "suggestedReminderTimes": ["07:00", "19:30"]
                }
              ],
              "generalDietAdvice": ["Lời khuyên ăn uống/sinh hoạt an toàn 1", "Lời khuyên 2"],
              "suggestedDoctorQuestions": [
                "Câu hỏi gợi ý 1 thông minh để hỏi lại bác sĩ khi tái khám",
                "Câu hỏi gợi ý 2",
                "Câu hỏi gợi ý 3"
              ],
              "redFlags": ["Dấu hiệu bất thường cần đi khám ngay nếu có"],
              "medicalDisclaimer": "Thông tin giải thích chỉ mang tính tham khảo, không thay thế chẩn đoán hay chỉ định của bác sĩ."
            }
        """.trimIndent()

        val root = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemPrompt) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", userPrompt) })
                        preparedParts.forEach { part ->
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", part.mimeType)
                                    put("data", part.base64Data)
                                })
                            })
                        }
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }
        return root.toString()
    }

    private fun extractCandidateText(rawResponse: String): String {
        val root = json.parseToJsonElement(rawResponse).jsonObject
        val candidates = root["candidates"]?.jsonArray
            ?: throw IllegalStateException("Gemini không trả về kết quả phân tích. Hãy thử chụp ảnh rõ nét hơn.")
        val first = candidates.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("Danh sách kết quả từ Gemini rỗng.")
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray
            ?: throw IllegalStateException("Phản hồi Gemini không chứa nội dung văn bản.")
        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("\n")
            .trim()
        if (text.isEmpty()) throw IllegalStateException("Gemini trả về nội dung trống.")
        return stripMarkdownFence(text)
    }

    internal fun parseStructuredAnalysis(rawText: String): GeminiAnalysisDto {
        val cleaned = stripMarkdownFence(rawText)
        val jsonSlice = extractJsonObjectSlice(cleaned)
        return json.decodeFromString<GeminiAnalysisDto>(jsonSlice)
    }

    private fun stripMarkdownFence(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("```")) {
            val firstNewline = trimmed.indexOf('\n')
            val lastFence = trimmed.lastIndexOf("```")
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim()
            }
        }
        return trimmed
    }

    private fun extractJsonObjectSlice(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return text
    }

    private fun parseGeminiError(code: Int, body: String, modelId: String): String {
        val serverMsg = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()

        return when (code) {
            400, 401, 403 -> "Gemini API Key không hợp lệ hoặc chưa được cấp quyền ($code). ${serverMsg.orEmpty()}"
            404 -> "Model '$modelId' không khả dụng (404) kể cả sau khi tự thử đổi model. Chi tiết Google: ${serverMsg ?: body.take(180)}"
            429 -> "Gemini API đã vượt hạn mức tạm thời (429). Vui lòng thử lại sau ít phút."
            else -> "Lỗi gọi Gemini API ($code - $modelId): ${serverMsg ?: body.take(200)}"
        }
    }
}

@Serializable
internal data class GeminiAnalysisDto(
    val documentType: String = "LAB_RESULT",
    val title: String = "Kết quả phân tích y tế",
    val facilityName: String = "",
    val recordDate: String = "",
    val overallSummary: String = "",
    val summaryBullets: List<String> = emptyList(),
    val labIndicators: List<GeminiLabIndicatorDto> = emptyList(),
    val medications: List<GeminiMedicationDto> = emptyList(),
    val generalDietAdvice: List<String> = emptyList(),
    val suggestedDoctorQuestions: List<String> = emptyList(),
    val redFlags: List<String> = emptyList(),
    val medicalDisclaimer: String = MedicalRecord.DEFAULT_MEDICAL_DISCLAIMER
) {
    fun toMedicalRecord(
        profileId: String = FamilyProfile.DEFAULT_SELF_ID,
        localImagePath: String?,
        localAttachmentPaths: List<String> = emptyList()
    ): MedicalRecord {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val resolvedType = runCatching {
            DocumentType.valueOf(documentType.trim().uppercase())
        }.getOrElse {
            when {
                medications.isNotEmpty() && labIndicators.isNotEmpty() -> DocumentType.MIXED
                medications.isNotEmpty() -> DocumentType.PRESCRIPTION
                else -> DocumentType.LAB_RESULT
            }
        }

        return MedicalRecord(
            profileId = profileId,
            title = title.ifBlank { resolvedType.labelVi },
            documentType = resolvedType,
            facilityName = facilityName.trim(),
            recordDate = recordDate.ifBlank { today },
            overallSummary = overallSummary.trim(),
            summaryBullets = summaryBullets.filter { it.isNotBlank() },
            labIndicators = labIndicators.map { it.toDomain() },
            medications = medications.map { it.toDomain() },
            generalDietAdvice = generalDietAdvice.filter { it.isNotBlank() },
            suggestedDoctorQuestions = suggestedDoctorQuestions.filter { it.isNotBlank() },
            redFlags = redFlags.filter { it.isNotBlank() },
            medicalDisclaimer = medicalDisclaimer.ifBlank { MedicalRecord.DEFAULT_MEDICAL_DISCLAIMER },
            localImagePath = localImagePath,
            localAttachmentPaths = localAttachmentPaths
        )
    }
}

@Serializable
internal data class GeminiLabIndicatorDto(
    val name: String = "",
    val value: String = "",
    val numericValue: Double? = null,
    val unit: String = "",
    val referenceRange: String = "",
    val status: String = "NORMAL",
    val plainExplanation: String = "",
    val dietAndLifestyleAdvice: String = ""
) {
    fun toDomain(): LabIndicator {
        val parsedStatus = runCatching {
            IndicatorStatus.valueOf(status.trim().uppercase())
        }.getOrDefault(IndicatorStatus.NORMAL)
        return LabIndicator(
            name = name.trim(),
            value = value.trim(),
            numericValue = numericValue,
            unit = unit.trim(),
            referenceRange = referenceRange.trim(),
            status = parsedStatus,
            plainExplanation = plainExplanation.trim(),
            dietAndLifestyleAdvice = dietAndLifestyleAdvice.trim()
        )
    }
}

@Serializable
internal data class GeminiMedicationDto(
    val name: String = "",
    val activeIngredient: String = "",
    val categoryShort: String = "",
    val dosage: String = "",
    val frequency: String = "",
    val mealTiming: String = "AFTER_MEAL",
    val daySlots: List<String> = emptyList(),
    val durationDays: Int = 7,
    val purposePlainVietnamese: String = "",
    val sideEffectsWarning: String = "",
    val suggestedReminderTimes: List<String> = emptyList()
) {
    fun toDomain(): MedicationItem {
        val parsedMeal = runCatching {
            MealTiming.valueOf(mealTiming.trim().uppercase())
        }.getOrDefault(MealTiming.AFTER_MEAL)
        val parsedSlots = daySlots.mapNotNull { slot ->
            runCatching { DaySlot.valueOf(slot.trim().uppercase()) }.getOrNull()
        }.ifEmpty {
            inferSlotsFromTimes(suggestedReminderTimes)
        }
        val validTimes = suggestedReminderTimes
            .map { it.trim() }
            .filter { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it) }
            .ifEmpty { parsedSlots.map { it.defaultTime } }
        return MedicationItem(
            name = name.trim(),
            activeIngredient = activeIngredient.trim(),
            categoryShort = categoryShort.trim(),
            dosage = dosage.ifBlank { "Theo chỉ định" },
            frequency = frequency.trim(),
            mealTiming = parsedMeal,
            daySlots = parsedSlots,
            durationDays = durationDays.coerceIn(1, 365),
            purposePlainVietnamese = purposePlainVietnamese.trim(),
            sideEffectsWarning = sideEffectsWarning.trim(),
            suggestedReminderTimes = validTimes
        )
    }

    private fun inferSlotsFromTimes(times: List<String>): List<DaySlot> {
        val inferred = times.mapNotNull { t ->
            val hour = t.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            when {
                hour < 11 -> DaySlot.MORNING
                hour < 14 -> DaySlot.NOON
                hour < 18 -> DaySlot.AFTERNOON
                else -> DaySlot.EVENING
            }
        }.distinct()
        return inferred.ifEmpty { listOf(DaySlot.MORNING, DaySlot.EVENING) }
    }
}
