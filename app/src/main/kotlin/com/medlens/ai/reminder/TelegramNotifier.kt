package com.medlens.ai.reminder

import com.medlens.ai.data.AppSettings
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MedicationReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TelegramDiscoveredChat(
    val chatId: String,
    val title: String,
    val type: String
)

object TelegramNotifier {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun discoverGroups(botToken: String): Result<List<TelegramDiscoveredChat>> =
        withContext(Dispatchers.IO) {
            val cleanToken = botToken.trim()
            if (cleanToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Vui lòng nhập Telegram Bot Token trước.")
                )
            }
            runCatching {
                val url =
                    "https://api.telegram.org/bot$cleanToken/getUpdates?limit=50&allowed_updates=%5B%22message%22,%22my_chat_member%22,%22channel_post%22,%22edited_message%22%5D"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    val root = JSONObject(raw.ifBlank { "{}" })
                    if (!response.isSuccessful || !root.optBoolean("ok", false)) {
                        val desc = root.optString("description", "HTTP ${response.code}")
                        throw IllegalStateException("Telegram báo lỗi: $desc")
                    }
                    val updates = root.optJSONArray("result")
                    val found = LinkedHashMap<String, TelegramDiscoveredChat>()

                    if (updates != null) {
                        for (i in 0 until updates.length()) {
                            val upd = updates.optJSONObject(i) ?: continue
                            val chatObj = upd.optJSONObject("message")?.optJSONObject("chat")
                                ?: upd.optJSONObject("my_chat_member")?.optJSONObject("chat")
                                ?: upd.optJSONObject("channel_post")?.optJSONObject("chat")
                                ?: upd.optJSONObject("edited_message")?.optJSONObject("chat")
                                ?: continue
                            val id = chatObj.optLong("id", 0L)
                            if (id == 0L) continue
                            val type = chatObj.optString("type", "unknown")
                            val title = chatObj.optString("title").ifBlank {
                                listOf(
                                    chatObj.optString("first_name"),
                                    chatObj.optString("last_name")
                                ).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
                                    chatObj.optString("username").ifBlank { "Chat $id" }
                                }
                            }
                            found[id.toString()] = TelegramDiscoveredChat(
                                chatId = id.toString(),
                                title = title,
                                type = type
                            )
                        }
                    }

                    val sorted = found.values.sortedBy { chat ->
                        when (chat.type) {
                            "supergroup", "group" -> 0
                            "channel" -> 1
                            else -> 2
                        }
                    }
                    if (sorted.isEmpty()) {
                        throw IllegalStateException(
                            "Chưa tìm thấy nhóm nào. Hãy thêm Bot vào nhóm Telegram gia đình rồi nhắn 1 tin bất kỳ (vd: /start) trong nhóm, sau đó bấm 'Tự động tìm nhóm' lại."
                        )
                    }
                    sorted
                }
            }
        }

    suspend fun sendTestMessage(
        botToken: String,
        chatId: String,
        groupName: String
    ): Result<String> {
        val label = groupName.ifBlank { chatId }
        val text = buildString {
            appendLine("✅ <b>Kết nối thành công Hiểu Đơn • MedRead VN!</b>")
            appendLine("Nhóm <b>${escapeHtml(label)}</b> sẽ tự động nhận thông báo:")
            appendLine("• ⏰ Khi đến giờ uống thuốc (kèm danh sách thuốc &amp; cách uống)")
            appendLine("• ✅ Khi đã xác nhận <b>Đã uống</b>")
            appendLine("• ⏳ Khi bấm <b>Nhắc lại sau 15 phút</b> hoặc <b>Bỏ qua</b>")
        }
        return sendRawHtmlMessage(botToken, chatId, text)
    }

    suspend fun sendDueMedicationReminder(
        settings: AppSettings,
        scheduledTime: String,
        reminders: List<MedicationReminder>,
        profileMap: Map<String, FamilyProfile>,
        isDemoTest: Boolean = false
    ): Result<String> {
        if (!settings.telegramEnabled || !settings.telegramNotifyOnDue || !settings.hasTelegramConfigured) {
            return Result.success("skipped")
        }
        val eligible = reminders.filter { it.notifyTelegram }
        if (eligible.isEmpty()) return Result.success("disabled_for_meds")

        val profileNames = eligible
            .mapNotNull { profileMap[it.profileId]?.name }
            .distinct()
            .joinToString(", ")

        val grouped = eligible
            .groupBy { it.mealTiming }
            .entries
            .sortedBy { MedicationNotificationHelper.mealOrder(it.key) }

        val text = buildString {
            if (isDemoTest) {
                appendLine("🔔 <b>[THỬ NGHIỆM] ĐẾN GIỜ UỐNG THUỐC • ${escapeHtml(scheduledTime)}</b>")
            } else {
                appendLine("⏰ <b>ĐẾN GIỜ UỐNG THUỐC • ${escapeHtml(scheduledTime)}</b>")
            }
            if (profileNames.isNotBlank()) {
                appendLine("👤 Hồ sơ: <b>${escapeHtml(profileNames)}</b> (${eligible.size} loại thuốc)")
            } else {
                appendLine("💊 Tổng cộng: <b>${eligible.size} loại thuốc</b>")
            }
            appendLine()

            grouped.forEachIndexed { idx, (timing, meds) ->
                appendLine("<b>${idx + 1}. ${escapeHtml(timing.labelVi.uppercase(Locale.getDefault()))}</b>")
                meds.forEach { med ->
                    val memberTag = if (eligible.map { it.profileId }.distinct().size > 1) {
                        profileMap[med.profileId]?.name?.let { " [${escapeHtml(it)}]" }.orEmpty()
                    } else {
                        ""
                    }
                    val freqTag = med.frequency.takeIf { it.isNotBlank() }?.let { " • ${escapeHtml(it)}" }.orEmpty()
                    appendLine("• <b>${escapeHtml(med.medicationName)}</b> — ${escapeHtml(med.dosage)}$freqTag$memberTag")
                    med.courseProgressSummary()?.let { progress ->
                        appendLine("  <i>📦 ${escapeHtml(progress)}</i>")
                    }
                    if (med.note.isNotBlank()) {
                        appendLine("  <i>↳ ${escapeHtml(med.note.take(140))}</i>")
                    }
                }
                appendLine()
            }
            append("📱 <i>Hiểu Đơn • MedRead VN</i>")
        }

        return sendRawHtmlMessage(settings.telegramBotToken, settings.telegramChatId, text)
    }

    suspend fun sendDoseTakenConfirmation(
        settings: AppSettings,
        scheduledTime: String,
        takenReminders: List<MedicationReminder>,
        profileMap: Map<String, FamilyProfile>
    ): Result<String> {
        if (!settings.telegramEnabled || !settings.telegramNotifyOnTaken || !settings.hasTelegramConfigured) {
            return Result.success("skipped")
        }
        val eligible = takenReminders.filter { it.notifyTelegram }
        if (eligible.isEmpty()) return Result.success("disabled_for_meds")

        val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val profileNames = eligible
            .mapNotNull { profileMap[it.profileId]?.name }
            .distinct()
            .joinToString(", ")

        val text = buildString {
            appendLine("✅ <b>ĐÃ UỐNG THUỐC • Cữ ${escapeHtml(scheduledTime)}</b>")
            if (profileNames.isNotBlank()) {
                appendLine("👤 Hồ sơ: <b>${escapeHtml(profileNames)}</b>")
            }
            appendLine("🕒 Xác nhận lúc: <b>$nowStr</b>")
            appendLine()
            eligible.forEach { med ->
                val progressTag = med.courseProgressSummary()?.let { " — ${escapeHtml(it)}" }.orEmpty()
                appendLine("• <b>${escapeHtml(med.medicationName)}</b> (${escapeHtml(med.dosage)} — ${escapeHtml(med.mealTiming.labelVi)})$progressTag")
            }
        }

        return sendRawHtmlMessage(settings.telegramBotToken, settings.telegramChatId, text)
    }

    suspend fun sendDoseSnoozedOrSkippedAlert(
        settings: AppSettings,
        scheduledTime: String,
        reminders: List<MedicationReminder>,
        profileMap: Map<String, FamilyProfile>,
        isSnooze: Boolean,
        delayMinutes: Int = 15
    ): Result<String> {
        if (!settings.telegramEnabled || !settings.telegramNotifyOnSnoozeOrSkip || !settings.hasTelegramConfigured) {
            return Result.success("skipped")
        }
        val eligible = reminders.filter { it.notifyTelegram }
        if (reminders.isNotEmpty() && eligible.isEmpty()) return Result.success("disabled_for_meds")

        val nowStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val profileNames = eligible
            .mapNotNull { profileMap[it.profileId]?.name }
            .distinct()
            .joinToString(", ")

        val text = buildString {
            if (isSnooze) {
                appendLine("⏳ <b>NHẮC LẠI SAU $delayMinutes PHÚT • Cữ ${escapeHtml(scheduledTime)}</b>")
            } else {
                appendLine("⚠️ <b>ĐÃ BỎ QUA CỮ THUỐC • Cữ ${escapeHtml(scheduledTime)}</b>")
            }
            if (profileNames.isNotBlank()) {
                appendLine("👤 Hồ sơ: <b>${escapeHtml(profileNames)}</b>")
            }
            appendLine("🕒 Thời điểm: $nowStr")
            if (eligible.isNotEmpty()) {
                appendLine(
                    if (isSnooze) "📌 Chưa uống các thuốc sau, cả nhà nhớ nhắc nhé:"
                    else "📌 Thuốc vừa bị bỏ qua:"
                )
                eligible.forEach { med ->
                    appendLine("• <b>${escapeHtml(med.medicationName)}</b> (${escapeHtml(med.dosage)} — ${escapeHtml(med.mealTiming.labelVi)})")
                }
            }
        }

        return sendRawHtmlMessage(settings.telegramBotToken, settings.telegramChatId, text)
    }

    suspend fun sendRawHtmlMessage(
        botToken: String,
        chatId: String,
        htmlText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        if (cleanToken.isBlank() || cleanChatId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Thiếu Bot Token hoặc Chat ID nhóm Telegram.")
            )
        }

        runCatching {
            val firstAttempt = postSendMessage(cleanToken, cleanChatId, htmlText)
            if (firstAttempt.ok) {
                cleanChatId
            } else if (!firstAttempt.migratedChatId.isNullOrBlank()) {
                // Group was upgraded to supergroup — retry automatically with new chat_id!
                val retry = postSendMessage(cleanToken, firstAttempt.migratedChatId, htmlText)
                if (retry.ok) {
                    firstAttempt.migratedChatId
                } else {
                    throw IllegalStateException("Telegram báo lỗi: ${retry.errorDescription}")
                }
            } else {
                throw IllegalStateException("Telegram báo lỗi: ${firstAttempt.errorDescription}")
            }
        }
    }

    private data class SendResponse(
        val ok: Boolean,
        val errorDescription: String = "",
        val migratedChatId: String? = null
    )

    private fun postSendMessage(botToken: String, chatId: String, htmlText: String): SendResponse {
        val payload = JSONObject().apply {
            put("chat_id", chatId)
            put("text", htmlText)
            put("parse_mode", "HTML")
            put("disable_web_page_preview", true)
        }
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMessage")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val root = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            val ok = response.isSuccessful && root.optBoolean("ok", false)
            val desc = root.optString("description", "HTTP ${response.code}")
            val migrated = root.optJSONObject("parameters")
                ?.optLong("migrate_to_chat_id", 0L)
                ?.takeIf { it != 0L }
                ?.toString()
            return SendResponse(ok = ok, errorDescription = desc, migratedChatId = migrated)
        }
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
