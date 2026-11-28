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

data class VoiceChatResult(
    val transcript: String,
    val reply: String
)

data class GeminiEndpointConfig(
    val apiKey: String = "",
    val proxyUrl: String = "",
    val proxyToken: String = ""
) {
    val cleanApiKey: String get() = apiKey.replace(Regex("\\s+"), "")
    val cleanProxyUrl: String get() = proxyUrl.trim().trimEnd('/')
    val cleanProxyToken: String get() = proxyToken.trim()

    val useProxy: Boolean get() = cleanApiKey.isEmpty() && cleanProxyUrl.isNotEmpty()
    val isReady: Boolean get() = cleanApiKey.isNotEmpty() || cleanProxyUrl.isNotEmpty()
}

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
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        hintType: DocumentType = DocumentType.LAB_RESULT,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<MedicalRecord> = withContext(Dispatchers.IO) {
        if (attachments.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng chụp ảnh hoặc chọn ít nhất 1 ảnh / file PDF."))
        }
        if (!endpoint.isReady) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Chưa cấu hình máy chủ AI hoặc Gemini API Key. Vui lòng vào tab Cài đặt hoặc bấm Xem dữ liệu mẫu."
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
                endpoint = endpoint,
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
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập hoặc nói câu hỏi."))
        }

        if (!endpoint.isReady) {
            return@withContext Result.success(buildOfflineHelpfulReply(record, profile, cleanQuestion))
        }

        runCatching {
            val recordSnapshot = json.encodeToString(
                record.copy(chatHistory = emptyList(), localImagePath = null, localAttachmentPaths = emptyList())
            )
            val profileNote = profile?.let {
                "Thông tin thành viên: ${it.aiContextSummary()}."
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
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            extractCandidateText(responseBody)
        }
    }

    suspend fun askContextualVoiceQuestion(
        record: MedicalRecord,
        profile: FamilyProfile?,
        audioFile: File,
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<VoiceChatResult> = withContext(Dispatchers.IO) {
        if (!endpoint.isReady) {
            audioFile.delete()
            return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình máy chủ AI hoặc Gemini API Key trong Cài đặt.")
            )
        }

        runCatching {
            val audioB64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val recordSnapshot = json.encodeToString(
                record.copy(chatHistory = emptyList(), localImagePath = null, localAttachmentPaths = emptyList())
            )
            val profileNote = profile?.let {
                "Thông tin thành viên: ${it.aiContextSummary()}."
            }.orEmpty()

            val systemText = """
                Bạn là trợ lý y khoa tiếng Việt thân thiện của ứng dụng "Hiểu Đơn" (MedRead VN).
                Người dùng vừa ghi âm câu hỏi bằng giọng nói về chính phiếu khám/đơn thuốc bên dưới.
                Hãy:
                1. Chép lại chính xác nội dung câu hỏi tiếng Việt người dùng vừa nói (trường "transcript").
                2. Trả lời rõ ràng, ngắn gọn, dễ hiểu bằng tiếng Việt (trường "reply") dựa trên dữ liệu phiếu bên dưới và kiến thức y khoa an toàn.
                
                $profileNote
                Dữ liệu phiếu hiện tại (JSON):
                $recordSnapshot

                Trả về DUY NHẤT JSON hợp lệ:
                {
                  "transcript": "Câu hỏi người dùng vừa nói",
                  "reply": "Câu trả lời y khoa tiếng Việt rõ ràng, dễ hiểu"
                }
            """.trimIndent()

            val root = buildJsonObject {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
                put("contents", buildJsonArray {
                    record.chatHistory.takeLast(6).forEach { msg ->
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
                            add(buildJsonObject {
                                put("text", "Nghe đoạn ghi âm câu hỏi tiếng Việt về phiếu này, chép lại và trả lời bằng JSON.")
                            })
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", "audio/mp4")
                                    put("data", audioB64)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                })
            }

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            val text = extractCandidateText(responseBody)
            val slice = extractJsonObjectSlice(stripMarkdownFence(text))
            val obj = json.parseToJsonElement(slice).jsonObject
            val transcript = obj["transcript"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.ifBlank { "Câu hỏi bằng giọng nói" } ?: "Câu hỏi bằng giọng nói"
            val reply = obj["reply"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.ifBlank { text } ?: text
            VoiceChatResult(transcript = transcript, reply = reply)
        }.also {
            audioFile.delete()
        }
    }

    suspend fun askDirectAssistantChat(
        profile: FamilyProfile,
        recentRecords: List<MedicalRecord>,
        activeReminders: List<MedicationReminder>,
        focusedRecord: MedicalRecord?,
        chatHistory: List<RecordChatMessage>,
        question: String,
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng nhập hoặc nói câu hỏi."))
        }

        if (!endpoint.isReady) {
            val fallback = if (focusedRecord != null) {
                buildOfflineHelpfulReply(focusedRecord, profile, cleanQuestion)
            } else {
                "Mình đã nhận câu hỏi của bạn cho hồ sơ **${profile.name}**. Hiện chưa có kết nối máy chủ AI hoặc Gemini API Key trong tab Cài đặt.\n\nBạn hãy vào **Cài đặt → Tích hợp AI Gemini** để kết nối, sau đó quay lại đây hỏi chi tiết nhé!"
            }
            return@withContext Result.success(fallback)
        }

        runCatching {
            val profileSummary = buildString {
                appendLine("Hồ sơ đang trò chuyện: ${profile.name} (${profile.relationship})")
                if (profile.birthYear.isNotBlank()) appendLine("- Năm sinh: ${profile.birthYear}")
                if (profile.bloodType.isNotBlank() && profile.bloodType != "Chưa cập nhật") {
                    appendLine("- Nhóm máu: ${profile.bloodType}")
                }
                appendLine("- Tiền sử dị ứng thuốc: ${profile.drugAllergies.ifBlank { "chưa ghi nhận" }}")
                appendLine("- Bệnh nền: ${profile.chronicConditions.ifBlank { "chưa ghi nhận" }}")
            }

            val remindersText = if (activeReminders.isEmpty()) {
                "Chưa có lịch thuốc đang uống."
            } else {
                activeReminders.joinToString("\n") { r ->
                    "- ${r.medicationName} (${r.dosage}, ${r.mealTiming.labelVi}, giờ nhắc: ${r.times.joinToString(", ")})"
                }
            }

            val contextData = if (focusedRecord != null) {
                "Phiếu khám người dùng đang ghim để hỏi kỹ:\n" + json.encodeToString(
                    focusedRecord.copy(chatHistory = emptyList(), localImagePath = null, localAttachmentPaths = emptyList())
                )
            } else {
                val briefRecords = recentRecords.take(3).joinToString("\n") { rec ->
                    val labs = rec.labIndicators.take(8).joinToString("; ") { "${it.name}: ${it.value} ${it.unit} (${it.status.labelVi})" }
                    val meds = rec.medications.joinToString("; ") { "${it.name} ${it.dosage} (${it.mealTiming.labelVi})" }
                    "- [${rec.recordDate}] ${rec.title}: ${rec.overallSummary.take(180)}" +
                        (if (labs.isNotBlank()) " | Chỉ số: $labs" else "") +
                        (if (meds.isNotBlank()) " | Thuốc: $meds" else "")
                }
                if (briefRecords.isBlank()) "Chưa có phiếu khám nào được quét." else "Các phiếu khám gần nhất:\n$briefRecords"
            }

            val systemText = """
                Bạn là Trợ lý Chuyên môn Y khoa & Dược lý tiếng Việt trong ứng dụng "Hiểu Đơn" (MedRead VN).
                Nhiệm vụ của bạn là giải đáp TOÀN DIỆN, SÂU SẮC và DỄ HIỂU mọi câu hỏi thuộc chuyên ngành y học — bao gồm:
                - Bệnh lý các chuyên khoa (Nội tổng quát, Gan mật - Truyền nhiễm như viêm gan B/C, Tim mạch, Nội tiết - Tiểu đường, Tiêu hóa - Dạ dày, Hô hấp, Thần kinh, Cơ xương khớp, Da liễu, Nhi khoa, Sản phụ khoa...).
                - Dược lý & điều trị theo y văn chuẩn (giải thích rõ các nhóm thuốc / hoạt chất phổ biến bác sĩ thường chỉ định, cơ chế tác dụng, tiêu chí xét nghiệm để bắt đầu dùng thuốc, tương tác thuốc và tác dụng phụ thường gặp).
                - Đọc hiểu mọi xét nghiệm cận lâm sàng, siêu âm, X-quang, CT/MRI, vắc-xin, chế độ dinh dưỡng bệnh lý và hướng dẫn chăm sóc / sơ cứu an toàn.

                Nguyên tắc trả lời bắt buộc:
                1. Luôn trả lời trực diện, đầy đủ kiến thức chuyên môn nhưng diễn đạt bằng tiếng Việt đời thường, rõ ràng theo từng ý gạch đầu dòng ngắn gọn (tránh trả lời chung chung kiểu né tránh).
                2. Ví dụ khi người dùng hỏi "bị viêm gan B thì uống thuốc nào", hãy giải thích cụ thể: không phải ai mang virus cũng cần uống ngay; nêu rõ các thuốc kháng virus chuẩn hiện nay bác sĩ thường kê (như Tenofovir TDF/TAF, Entecavir), các xét nghiệm cần làm để quyết định điều trị (HBeAg, HBV-DNA, ALT, siêu âm/đo độ đàn hồi gan), và lưu ý uống đều không tự ngưng.
                3. Kết hợp thông tin hồ sơ sức khỏe, thuốc đang uống và phiếu khám bên dưới nếu câu hỏi có liên quan trực tiếp đến người bệnh.
                4. Đảm bảo an toàn y khoa: nêu rõ thông tin mang tính tham khảo y khoa, không thay thế đơn kê trực tiếp của bác sĩ chuyên khoa (đặc biệt với thuốc kê đơn như kháng sinh, kháng virus, tim mạch) và cảnh báo dấu hiệu cấp cứu cần đi viện ngay nếu có.

                $profileSummary
                Thuốc đang đặt lịch nhắc:
                $remindersText

                $contextData
            """.trimIndent()

            val root = buildJsonObject {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
                put("contents", buildJsonArray {
                    chatHistory.takeLast(12).forEach { msg ->
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
                    put("temperature", 0.35)
                })
            }

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            extractCandidateText(responseBody)
        }
    }

    suspend fun saveChatImageCopy(imageUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Không thể đọc hình ảnh đã chọn.")
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: throw IllegalArgumentException("Tệp đã chọn không phải hình ảnh hợp lệ.")
            val resized = resizeKeepingAspect(decoded, maxDimension = 1280)
            val outStream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 82, outStream)
            val compressedBytes = outStream.toByteArray()
            if (resized !== decoded) resized.recycle()
            decoded.recycle()

            val chatImgDir = File(context.filesDir, "chat_images").apply { mkdirs() }
            val savedFile = File(chatImgDir, "chat_${UUID.randomUUID()}.jpg")
            FileOutputStream(savedFile).use { it.write(compressedBytes) }
            savedFile
        }
    }

    suspend fun askDirectAssistantWithSavedImage(
        imageFile: File,
        question: String,
        profile: FamilyProfile,
        recentRecords: List<MedicalRecord>,
        activeReminders: List<MedicationReminder>,
        focusedRecord: MedicalRecord?,
        chatHistory: List<RecordChatMessage>,
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!endpoint.isReady) {
            return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình máy chủ AI hoặc Gemini API Key trong Cài đặt.")
            )
        }

        runCatching {
            val imageBytes = imageFile.readBytes()
            val imageB64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val cleanQuestion = question.trim().ifBlank {
                "Hãy quan sát kỹ hình ảnh này (thuốc, phiếu khám, chỉ số máy đo, món ăn hoặc dấu hiệu sức khỏe...) và giải thích rõ ràng, dễ hiểu cho mình nhé."
            }

            val profileSummary = "Hồ sơ người đang hỏi: ${profile.aiContextSummary()}"
            val remindersText = if (activeReminders.isEmpty()) {
                "Chưa có lịch thuốc đang uống."
            } else {
                activeReminders.joinToString("\n") { r ->
                    "- ${r.medicationName} (${r.dosage}, ${r.mealTiming.labelVi})"
                }
            }
            val contextData = if (focusedRecord != null) {
                "Phiếu khám đang ghim:\n" + json.encodeToString(
                    focusedRecord.copy(chatHistory = emptyList(), localImagePath = null, localAttachmentPaths = emptyList())
                )
            } else {
                recentRecords.take(3).joinToString("\n") { rec ->
                    "- [${rec.recordDate}] ${rec.title}: ${rec.overallSummary.take(160)}"
                }.ifBlank { "Chưa có phiếu khám." }
            }

            val systemText = """
                Bạn là Trợ lý Chuyên môn Y khoa & Dược lý tiếng Việt trong ứng dụng "Hiểu Đơn" (MedRead VN).
                Người dùng vừa gửi kèm một HÌNH ẢNH để hỏi đáp trực tiếp (có thể là vỏ hộp/lọ thuốc, vỉ thuốc, phiếu xét nghiệm, màn hình máy đo huyết áp/đường huyết, thực phẩm ăn uống, hoặc tổn thương ngoài da...).
                Nguyên tắc trả lời bắt buộc:
                1. Quan sát thật kỹ mọi chữ viết, hàm lượng, hoạt chất, số đo hoặc dấu hiệu trong ảnh; giải thích trực diện, rõ ràng, dễ hiểu bằng gạch đầu dòng tiếng Việt đời thường.
                2. Nếu ảnh mờ không đọc chắc chắn tên thuốc/con số, nói rõ phần nào bị mờ và nhắc xác nhận lại với bác sĩ/dược sĩ — tuyệt đối không bịa số liệu.
                3. Đối chiếu với hồ sơ thể trạng, bệnh nền, dị ứng và danh sách thuốc đang uống bên dưới (cảnh báo ngay nếu thấy trùng hoạt chất, tương tác thuốc hoặc thực phẩm cần kiêng).
                4. Đảm bảo an toàn y khoa: nêu rõ thông tin mang tính tham khảo, không thay thế chẩn đoán trực tiếp của bác sĩ và nhắc dấu hiệu cần đi khám ngay nếu có.

                $profileSummary
                Thuốc đang đặt lịch nhắc:
                $remindersText

                $contextData
            """.trimIndent()

            val root = buildJsonObject {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
                put("contents", buildJsonArray {
                    chatHistory.takeLast(10).forEach { msg ->
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
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", "image/jpeg")
                                    put("data", imageB64)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.3)
                })
            }

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            extractCandidateText(responseBody)
        }
    }

    suspend fun askDirectAssistantVoice(
        audioFile: File,
        profile: FamilyProfile,
        recentRecords: List<MedicalRecord>,
        activeReminders: List<MedicationReminder>,
        focusedRecord: MedicalRecord?,
        chatHistory: List<RecordChatMessage>,
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<VoiceChatResult> = withContext(Dispatchers.IO) {
        if (!endpoint.isReady) {
            return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình máy chủ AI hoặc Gemini API Key trong tab Cài đặt.")
            )
        }

        runCatching {
            val audioBytes = audioFile.readBytes()
            val audioB64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val profileSummary = buildString {
                appendLine("Hồ sơ đang trò chuyện: ${profile.name} (${profile.relationship})")
                if (profile.birthYear.isNotBlank()) appendLine("- Năm sinh: ${profile.birthYear}")
                appendLine("- Tiền sử dị ứng thuốc: ${profile.drugAllergies.ifBlank { "chưa ghi nhận" }}")
                appendLine("- Bệnh nền: ${profile.chronicConditions.ifBlank { "chưa ghi nhận" }}")
            }

            val remindersText = if (activeReminders.isEmpty()) {
                "Chưa có lịch thuốc đang uống."
            } else {
                activeReminders.joinToString("\n") { r ->
                    "- ${r.medicationName} (${r.dosage}, ${r.mealTiming.labelVi})"
                }
            }

            val contextData = if (focusedRecord != null) {
                "Phiếu khám đang ghim:\n" + json.encodeToString(
                    focusedRecord.copy(chatHistory = emptyList(), localImagePath = null, localAttachmentPaths = emptyList())
                )
            } else {
                recentRecords.take(3).joinToString("\n") { rec ->
                    "- [${rec.recordDate}] ${rec.title}: ${rec.overallSummary.take(160)}"
                }.ifBlank { "Chưa có phiếu khám." }
            }

            val systemText = """
                Bạn là Trợ lý Chuyên môn Y khoa & Dược lý tiếng Việt trong ứng dụng "Hiểu Đơn" (MedRead VN).
                Người dùng vừa ghi âm câu hỏi bằng giọng nói tiếng Việt.
                Hãy:
                1. Chép lại chính xác nội dung câu hỏi tiếng Việt người dùng vừa nói (trường "transcript").
                2. Trả lời đầy đủ, chuyên sâu, dễ hiểu mọi câu hỏi thuộc chuyên ngành y học (bệnh lý, thuốc theo y văn chuẩn, xét nghiệm, tương tác thuốc, dinh dưỡng) trình bày gọn gàng theo gạch đầu dòng (trường "reply").

                $profileSummary
                Thuốc đang đặt lịch nhắc:
                $remindersText
                $contextData

                Trả về DUY NHẤT JSON hợp lệ:
                {
                  "transcript": "Nội dung câu hỏi người dùng vừa nói",
                  "reply": "Câu trả lời y khoa tiếng Việt rõ ràng, đầy đủ, dễ hiểu"
                }
            """.trimIndent()

            val root = buildJsonObject {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                })
                put("contents", buildJsonArray {
                    chatHistory.takeLast(8).forEach { msg ->
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
                            add(buildJsonObject {
                                put("text", "Hãy nghe đoạn ghi âm câu hỏi y khoa tiếng Việt sau, chép lại câu hỏi và trả lời chi tiết bằng JSON.")
                            })
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", "audio/mp4")
                                    put("data", audioB64)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                })
            }

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            val text = extractCandidateText(responseBody)
            val slice = extractJsonObjectSlice(stripMarkdownFence(text))
            val obj = json.parseToJsonElement(slice).jsonObject
            val transcript = obj["transcript"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.ifBlank { "🎤 Câu hỏi bằng giọng nói" } ?: "🎤 Câu hỏi bằng giọng nói"
            val reply = obj["reply"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.ifBlank { text } ?: text
            VoiceChatResult(transcript = transcript, reply = reply)
        }.also {
            audioFile.delete()
        }
    }

    suspend fun transcribeAudioToText(
        audioFile: File,
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!endpoint.isReady) {
            audioFile.delete()
            return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình máy chủ AI để chuyển giọng nói thành chữ.")
            )
        }
        runCatching {
            val audioB64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val root = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", "Hãy chép lại CHÍNH XÁC nội dung lời nói tiếng Việt trong đoạn âm thanh sau thành văn bản thuần túy, không thêm lời giải thích.")
                            })
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", "audio/mp4")
                                    put("data", audioB64)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.1)
                })
            }
            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            extractCandidateText(responseBody).trim()
        }.also {
            audioFile.delete()
        }
    }

    suspend fun generatePersonalizedDietPlan(
        profile: FamilyProfile,
        conditionKey: String,
        conditionLabel: String,
        extraPreference: String,
        recentRecords: List<MedicalRecord>,
        activeReminders: List<MedicationReminder>,
        endpoint: GeminiEndpointConfig,
        modelName: String = AppSettings.DEFAULT_GEMINI_MODEL,
        onModelAutoSwitched: ((String) -> Unit)? = null
    ): Result<PatientDietPlan> = withContext(Dispatchers.IO) {
        if (!endpoint.isReady) {
            return@withContext Result.failure(
                IllegalStateException("Chưa cấu hình máy chủ AI hoặc Gemini API Key.")
            )
        }

        runCatching {
            val medsSummary = activeReminders.take(8).joinToString("; ") { med ->
                "${med.medicationName} (${med.dosage}, ${med.mealTiming.labelVi})"
            }.ifBlank { "Chưa ghi nhận thuốc đang uống" }

            val abnormalLabs = recentRecords.take(3)
                .flatMap { it.labIndicators }
                .filter { it.status != IndicatorStatus.NORMAL }
                .take(8)
                .joinToString("; ") { "${it.name}: ${it.value} ${it.unit} (${it.status.labelVi})" }
                .ifBlank { "Chưa có chỉ số bất thường" }

            val systemPrompt = """
                Bạn là chuyên gia dinh dưỡng lâm sàng tiếng Việt của ứng dụng "Hiểu Đơn" (MedRead VN).
                Hãy thiết kế chế độ ăn kiêng & thực đơn 1 ngày thuần Việt, dễ nấu, an toàn y khoa, cá nhân hóa sát theo hồ sơ thể trạng (tuổi, giới tính, chiều cao, cân nặng, BMI), bệnh lý, chỉ số xét nghiệm và thuốc đang uống của người bệnh.
                Không kê đơn thuốc, không khẳng định chữa khỏi bệnh bằng ăn uống; luôn chú ý tương tác giữa thức ăn và thuốc đang dùng.
            """.trimIndent()

            val userPrompt = """
                Thông tin người bệnh: ${profile.aiContextSummary()}
                Nhóm bệnh lý / mục tiêu ăn kiêng: $conditionLabel
                Thuốc đang dùng: $medsSummary
                Chỉ số xét nghiệm bất thường gần đây: $abnormalLabs
                Ghi chú khẩu vị / dị ứng riêng: ${extraPreference.ifBlank { "Không có" }}

                Trả về DUY NHẤT 1 đối tượng JSON hợp lệ (không markdown) đúng mẫu:
                {
                  "conditionTitle": "Tiêu đề chế độ ăn cá nhân hóa cho ${profile.name}",
                  "summary": "Tóm tắt 2-3 câu về mục tiêu dinh dưỡng phù hợp thể trạng/BMI và bệnh lý",
                  "shouldEat": ["Nhóm thực phẩm/món Việt nên ăn 1", "Mục 2", "Mục 3", "Mục 4"],
                  "shouldAvoid": ["Thực phẩm/đồ uống cần kiêng tuyệt đối 1", "Mục 2", "Mục 3", "Mục 4"],
                  "medicationFoodNotes": ["Lưu ý tương tác thực phẩm với thuốc đang uống 1", "Mục 2"],
                  "sampleDayMenu": [
                    "Sáng: ...",
                    "Trưa: ...",
                    "Chiều nhẹ: ...",
                    "Tối: ..."
                  ]
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
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                })
            }

            val responseBody = executeGenerateContentWithAutoFallback(
                preferredModel = modelName,
                endpoint = endpoint,
                requestJson = root.toString(),
                onModelAutoSwitched = onModelAutoSwitched
            )
            val text = extractCandidateText(responseBody)
            val slice = extractJsonObjectSlice(stripMarkdownFence(text))
            val obj = json.parseToJsonElement(slice).jsonObject

            fun strList(key: String): List<String> =
                obj[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()

            PatientDietPlan(
                conditionKey = conditionKey,
                conditionTitle = obj["conditionTitle"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.ifBlank { "Thực đơn cho ${profile.name} ($conditionLabel)" }
                    ?: "Thực đơn cho ${profile.name} ($conditionLabel)",
                summary = obj["summary"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                shouldEat = strList("shouldEat"),
                shouldAvoid = strList("shouldAvoid"),
                medicationFoodNotes = strList("medicationFoodNotes"),
                sampleDayMenu = strList("sampleDayMenu"),
                generatedByAi = true,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun executeGenerateContentWithAutoFallback(
        preferredModel: String,
        endpoint: GeminiEndpointConfig,
        requestJson: String,
        onModelAutoSwitched: ((String) -> Unit)?
    ): String {
        val primary = normalizeModelId(preferredModel).ifEmpty { AppSettings.DEFAULT_GEMINI_MODEL }
        val firstAttempt = postGenerateContent(primary, endpoint, requestJson)
        if (firstAttempt.isSuccess) return firstAttempt.body

        val isRetriableOverload = firstAttempt.code == 503 || firstAttempt.code == 429 || firstAttempt.code == 500
        val isModelNotFound = firstAttempt.code == 404

        if (isModelNotFound || isRetriableOverload) {
            if (isRetriableOverload) {
                runCatching { Thread.sleep(500) }
                val quickRetry = postGenerateContent(primary, endpoint, requestJson)
                if (quickRetry.isSuccess) return quickRetry.body
            }

            val discovered = if (isModelNotFound) discoverAvailableGeminiModels(endpoint) else emptyList()
            val fallbackCandidates = (discovered + listOf(
                "gemini-4-hydrogen",
                "gemini-3.8-flash",
                "gemini-4-neon",
                "gemini-3.5-flash-lite",
                "gemini-4-argon",
                "gemini-flash-latest"
            ))
                .map { normalizeModelId(it) }
                .distinct()
                .filter { it.isNotEmpty() && it != primary }

            var lastError = firstAttempt
            for (candidate in fallbackCandidates.take(5)) {
                val retry = postGenerateContent(candidate, endpoint, requestJson)
                if (retry.isSuccess) {
                    if (isModelNotFound) {
                        onModelAutoSwitched?.invoke(candidate)
                    }
                    return retry.body
                }
                lastError = retry
                if (retry.code != 404 && retry.code != 503 && retry.code != 429) break
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

    private fun postGenerateContent(
        modelId: String,
        endpoint: GeminiEndpointConfig,
        requestJson: String
    ): RawApiAttempt {
        val baseUrl = if (endpoint.useProxy) {
            endpoint.cleanProxyUrl
        } else {
            "https://generativelanguage.googleapis.com"
        }
        val url = "$baseUrl/v1beta/models/$modelId:generateContent"
        val reqBuilder = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (endpoint.useProxy) {
            if (endpoint.cleanProxyToken.isNotEmpty()) {
                reqBuilder.header("x-hieudon-token", endpoint.cleanProxyToken)
            }
        } else {
            reqBuilder.header("x-goog-api-key", endpoint.cleanApiKey)
        }

        return httpClient.newCall(reqBuilder.build()).execute().use { response ->
            RawApiAttempt(
                model = modelId,
                code = response.code,
                isSuccess = response.isSuccessful,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun discoverAvailableGeminiModels(endpoint: GeminiEndpointConfig): List<String> {
        return runCatching {
            val baseUrl = if (endpoint.useProxy) {
                endpoint.cleanProxyUrl
            } else {
                "https://generativelanguage.googleapis.com"
            }
            val reqBuilder = Request.Builder()
                .url("$baseUrl/v1beta/models?pageSize=100")
                .get()

            if (endpoint.useProxy) {
                if (endpoint.cleanProxyToken.isNotEmpty()) {
                    reqBuilder.header("x-hieudon-token", endpoint.cleanProxyToken)
                }
            } else {
                reqBuilder.header("x-goog-api-key", endpoint.cleanApiKey)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
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
        id == "gemini-4-hydrogen" -> 0
        id == "gemini-4-neon" -> 1
        id == "gemini-3.8-flash" -> 2
        id == "gemini-3.5-flash-lite" -> 3
        id == "gemini-4-argon" -> 4
        id == "gemini-4-carbon" -> 5
        id.startsWith("gemini-4") -> 6
        id.startsWith("gemini-3") && id.contains("flash") -> 7
        id == "gemini-flash-latest" -> 8
        id.contains("flash") -> 9
        else -> 12
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

        val resized = resizeKeepingAspect(decoded, maxDimension = 1280)
        val outStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
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
            "Hồ sơ người khám: ${it.aiContextSummary()}."
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
                  "categoryShort": "Công dụng ngắn gọn (vd: Kháng virus viêm gan B, Bảo vệ dạ dày, Hạ mỡ máu)",
                  "dosage": "Liều mỗi lần chính xác theo đơn/nhãn (vd: 1 viên)",
                  "frequency": "Tần suất chỉ định chính xác (vd: 1 ngày 1 viên, hoặc Ngày 2 lần Sáng-Tối)",
                  "mealTiming": "BEFORE_MEAL | AFTER_MEAL | WITH_MEAL | ANYTIME",
                  "daySlots": ["MORNING", "EVENING"],
                  "durationDays": 30,
                  "totalQuantity": "Tổng số viên/gói trong lọ hoặc đơn nếu thấy trên nhãn (vd: 30 viên / 1 lọ — hết lọ sau 30 ngày), hoặc chuỗi rỗng",
                  "purposePlainVietnamese": "Giải thích công dụng bằng ngôn ngữ đời thường",
                  "sideEffectsWarning": "Cảnh báo an toàn (buồn ngủ, tránh rượu bia, uống đúng giờ, không bỏ liều...)",
                  "suggestedReminderTimes": ["08:00"]
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
            503 -> "Máy chủ Gemini đang quá tải tạm thời (503) sau khi đã tự động chuyển model dự phòng. Bạn vui lòng bấm gửi lại sau vài giây nhé."
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
    val totalQuantity: String = "",
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
            totalQuantity = totalQuantity.trim(),
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
