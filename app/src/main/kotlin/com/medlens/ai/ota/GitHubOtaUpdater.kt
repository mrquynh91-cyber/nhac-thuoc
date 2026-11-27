package com.medlens.ai.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class OtaReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val publishedAt: String,
    val htmlUrl: String
)

sealed interface OtaUpdateState {
    data object Idle : OtaUpdateState
    data object Checking : OtaUpdateState
    data class UpToDate(val currentVersion: String, val latestTag: String) : OtaUpdateState
    data class UpdateAvailable(val release: OtaReleaseInfo) : OtaUpdateState
    data class Downloading(
        val release: OtaReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : OtaUpdateState

    data class ReadyToInstall(val release: OtaReleaseInfo, val apkFile: File) : OtaUpdateState
    data class Error(val message: String) : OtaUpdateState
}

class GitHubOtaUpdater(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<OtaUpdateState>(OtaUpdateState.Idle)
    val state: StateFlow<OtaUpdateState> = _state.asStateFlow()

    suspend fun checkForUpdate(
        repoSlug: String,
        currentVersion: String,
        githubToken: String = ""
    ): Result<OtaUpdateState> = withContext(Dispatchers.IO) {
        val cleanRepo = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        if (!cleanRepo.contains('/') || cleanRepo.split('/').size != 2) {
            val err = "Vui lòng nhập đúng định dạng GitHub Repo dạng owner/repo (ví dụ: username/medlens-ai)."
            _state.value = OtaUpdateState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _state.value = OtaUpdateState.Checking

        runCatching {
            val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MedLensAI-Android-OTA")
            if (githubToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${githubToken.trim()}")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 404) {
                    throw IllegalStateException("Không tìm thấy GitHub Release nào tại repo '$cleanRepo' (404). Hãy đảm bảo repo đã tạo Release kèm file .apk.")
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException("Lỗi kết nối GitHub API (${response.code}).")
                }

                val release = parseReleaseJson(body)
                val nextState = if (SemVerComparator.isNewerVersion(release.tagName, currentVersion)) {
                    OtaUpdateState.UpdateAvailable(release)
                } else {
                    OtaUpdateState.UpToDate(
                        currentVersion = currentVersion,
                        latestTag = release.tagName
                    )
                }
                _state.value = nextState
                nextState
            }
        }.onFailure { err ->
            _state.value = OtaUpdateState.Error(err.localizedMessage ?: "Không thể kiểm tra cập nhật OTA.")
        }
    }

    suspend fun downloadApk(
        release: OtaReleaseInfo,
        githubToken: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        val apkUrl = release.apkUrl
            ?: return@withContext Result.failure(
                IllegalStateException("Bản Release ${release.tagName} không đính kèm file .apk.")
            )

        _state.value = OtaUpdateState.Downloading(
            release = release,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = release.apkSizeBytes
        )

        runCatching {
            val reqBuilder = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "MedLensAI-Android-OTA")
            if (githubToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${githubToken.trim()}")
                reqBuilder.header("Accept", "application/octet-stream")
            }

            val updatesDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "updates"
            ).apply { mkdirs() }

            // Clean old APK files to save storage
            updatesDir.listFiles()?.forEach { old ->
                if (old.name.endsWith(".apk")) old.delete()
            }

            val safeName = (release.apkFileName ?: "MedLensAI-${release.versionName}.apk")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(updatesDir, safeName)

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Tải file APK thất bại (HTTP ${response.code}).")
                }
                val body = response.body ?: throw IllegalStateException("Phản hồi tải APK rỗng.")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (totalBytes > 0) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = OtaUpdateState.Downloading(
                                    release = release,
                                    progressPercent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                    }
                }
            }

            _state.value = OtaUpdateState.ReadyToInstall(release = release, apkFile = targetFile)
            targetFile
        }.onFailure { err ->
            _state.value = OtaUpdateState.Error(err.localizedMessage ?: "Lỗi tải bản cập nhật APK.")
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createInstallApkIntent(apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun parseReleaseJson(rawJson: String): OtaReleaseInfo {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        val tagName = obj["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val releaseName = obj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { tagName } ?: tagName
        val notes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val apkAsset = obj["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { asset ->
                asset["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true
            }

        return OtaReleaseInfo(
            tagName = tagName,
            versionName = SemVerComparator.normalize(tagName),
            releaseName = releaseName,
            releaseNotes = notes.ifBlank { "Cải thiện độ ổn định và trải nghiệm người dùng." },
            apkUrl = apkAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
            apkFileName = apkAsset?.get("name")?.jsonPrimitive?.contentOrNull,
            apkSizeBytes = apkAsset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl
        )
    }
}
