package com.medlens.ai.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medlens.ai.BuildConfig
import com.medlens.ai.data.AppSettings
import com.medlens.ai.data.CloudSyncUiState
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MealTiming
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.ota.GitHubOtaUpdater
import com.medlens.ai.ota.OtaReleaseInfo
import com.medlens.ai.ota.OtaUpdateState
import com.medlens.ai.reminder.MedicationNotificationHelper
import com.medlens.ai.reminder.MedicationReminderScheduler
import com.medlens.ai.ui.components.AddEditMedicationReminderDialog
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.InfoBlue
import com.medlens.ai.ui.theme.InfoBlueBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.NormalGreen
import com.medlens.ai.ui.theme.NormalGreenBg
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncAndOtaScreen(
    activeProfile: FamilyProfile,
    reminders: List<MedicationReminder>,
    settings: AppSettings,
    cloudSyncState: CloudSyncUiState,
    otaState: OtaUpdateState,
    otaUpdater: GitHubOtaUpdater,
    onSaveDefaultSlotTimes: (String, String, String, String) -> Unit,
    onSaveReminder: (MedicationReminder) -> Unit,
    onToggleReminder: (MedicationReminder) -> Unit,
    onDeleteReminder: (MedicationReminder) -> Unit,
    onSendTestNotification: () -> Unit,
    onSaveGeminiConfig: (apiKey: String, proxyUrl: String, proxyToken: String, model: String) -> Unit,
    onSaveOtaConfig: (String, String, Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onSignInFirebase: (String, String) -> Unit,
    onRegisterFirebase: (String, String) -> Unit,
    onSignInAnonymously: () -> Unit,
    onSignOutFirebase: () -> Unit,
    onSyncFirebaseNow: () -> Unit,
    onBuildBackupJson: () -> String,
    onRestoreBackupJson: (String) -> Unit,
    onCheckOtaUpdate: () -> Unit,
    onDownloadOtaApk: (OtaReleaseInfo) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current

    var hasNotifPermission by remember {
        mutableStateOf(MedicationNotificationHelper.hasNotificationPermission(context))
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    // Default meal/dose slot times
    var morningTime by remember(settings.morningTime) { mutableStateOf(settings.morningTime) }
    var noonTime by remember(settings.noonTime) { mutableStateOf(settings.noonTime) }
    var afternoonTime by remember(settings.afternoonTime) { mutableStateOf(settings.afternoonTime) }
    var eveningTime by remember(settings.eveningTime) { mutableStateOf(settings.eveningTime) }

    // Reminder Add/Edit dialog state
    var showCreateReminder by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<MedicationReminder?>(null) }

    // Gemini inputs (keep built-in proxy credentials hidden unless user expands advanced override)
    var apiKeyInput by remember(settings.geminiApiKey) { mutableStateOf(settings.geminiApiKey) }
    var proxyUrlInput by remember(settings.geminiProxyUrl) {
        mutableStateOf(if (settings.geminiProxyUrl == BuildConfig.GEMINI_PROXY_URL) "" else settings.geminiProxyUrl)
    }
    var proxyTokenInput by remember(settings.geminiProxyToken) {
        mutableStateOf(if (settings.geminiProxyToken == BuildConfig.GEMINI_PROXY_TOKEN) "" else settings.geminiProxyToken)
    }
    var selectedModel by remember(settings.geminiModel) { mutableStateOf(settings.geminiModel) }
    var showApiKey by remember { mutableStateOf(false) }
    var showAdvancedProxy by remember { mutableStateOf(false) }

    // Firebase auth inputs
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    // GitHub OTA inputs
    var repoInput by remember(settings.githubOtaRepo) { mutableStateOf(settings.githubOtaRepo) }
    var tokenInput by remember(settings.githubOtaToken) { mutableStateOf(settings.githubOtaToken) }
    var autoCheckOta by remember(settings.autoCheckOta) { mutableStateOf(settings.autoCheckOta) }

    // SAF Backup Export/Import Launchers
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val payload = onBuildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("Đã xuất file sao lưu JSON thành công!")
            }.onFailure { err ->
                onShowMessage("Lỗi xuất sao lưu: ${err.localizedMessage}")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Không thể đọc tệp đã chọn.")
                onRestoreBackupJson(raw)
            }.onFailure { err ->
                onShowMessage("Lỗi đọc tệp sao lưu: ${err.localizedMessage}")
            }
        }
    }

    // Collapsible sections state — all collapsed by default for a clean screen
    var expandedReminder by remember { mutableStateOf(false) }
    var expandedGemini by remember { mutableStateOf(false) }
    var expandedFirebase by remember { mutableStateOf(false) }
    var expandedBackup by remember { mutableStateOf(false) }
    var expandedOta by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Cài đặt, Nhắc thuốc & OTA",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Bấm vào từng mục bên dưới để mở rộng hoặc thu gọn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        // 1. Medication Reminders & Default Slot Times
        item {
            val reminderSubtitle = if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "Chưa cấp quyền thông báo • ${reminders.size} lịch thuốc"
            } else {
                "${reminders.size} lịch thuốc • Sáng ${settings.morningTime}, Tối ${settings.eveningTime}"
            }
            SectionCard(
                title = "Nhắc uống thuốc (${activeProfile.name})",
                subtitle = reminderSubtitle,
                subtitleColor = if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) WarningAmber else MutedText,
                icon = Icons.Default.Alarm,
                expanded = expandedReminder,
                onToggle = { expandedReminder = !expandedReminder }
            ) {
                if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Surface(
                        color = WarningAmberBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chưa cấp quyền Thông báo nhắc thuốc",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Cấp quyền")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    text = "Giờ uống thuốc mặc định (áp dụng khi tạo nhắc 1 chạm từ đơn):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = morningTime,
                        onValueChange = { morningTime = it },
                        label = { Text("Sáng") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = noonTime,
                        onValueChange = { noonTime = it },
                        label = { Text("Trưa") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = afternoonTime,
                        onValueChange = { afternoonTime = it },
                        label = { Text("Chiều") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = eveningTime,
                        onValueChange = { eveningTime = it },
                        label = { Text("Tối") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val ok = listOf(morningTime, noonTime, afternoonTime, eveningTime)
                                .all { MedicationReminderScheduler.parseTime(it) != null }
                            if (ok) {
                                onSaveDefaultSlotTimes(morningTime.trim(), noonTime.trim(), afternoonTime.trim(), eveningTime.trim())
                            } else {
                                onShowMessage("Nhập đúng định dạng HH:mm (ví dụ 07:00, 19:30).")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lưu khung giờ")
                    }
                    OutlinedButton(
                        onClick = onSendTestNotification,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thử báo thức")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lịch thuốc đang quản lý (${reminders.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FilledTonalButton(
                        onClick = { showCreateReminder = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thêm thuốc")
                    }
                }

                if (reminders.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Chưa có lịch thuốc cho hồ sơ ${activeProfile.name}. Hãy quét đơn thuốc hoặc bấm '+ Thêm thuốc'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminders.forEach { reminder ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = reminder.medicationName,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "${reminder.dosage} • ${reminder.mealTiming.labelVi} • ${reminder.times.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MutedText
                                            )
                                        }
                                        Switch(
                                            checked = reminder.isActive,
                                            onCheckedChange = { onToggleReminder(reminder) }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { editingReminder = reminder }) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Sửa")
                                        }
                                        TextButton(onClick = { onDeleteReminder(reminder) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Xóa", color = AlertRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Gemini AI Card
        item {
            val geminiSubtitle = when {
                settings.isUsingProxy -> "Máy chủ AI tích hợp sẵn • ${settings.geminiModel}"
                settings.geminiApiKey.isNotBlank() -> "Khóa API cá nhân • ${settings.geminiModel}"
                else -> "Chưa cấu hình AI • Bấm để cài đặt"
            }
            SectionCard(
                title = "Tích hợp AI Gemini",
                subtitle = geminiSubtitle,
                subtitleColor = if (settings.hasAiReady) NormalGreen else WarningAmber,
                icon = Icons.Default.AutoAwesome,
                expanded = expandedGemini,
                onToggle = { expandedGemini = !expandedGemini }
            ) {
                if (settings.isUsingProxy) {
                    Surface(
                        color = NormalGreenBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Đang kết nối máy chủ AI Cloudflare Proxy (không lộ khóa API, mọi người tải app là dùng ngay).",
                            color = NormalGreen,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    "Chọn model Gemini 4 / 3.8 (mặc định gemini-4-hydrogen nhanh & chính xác)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppSettings.SUPPORTED_MODELS.forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showAdvancedProxy = !showAdvancedProxy },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (showAdvancedProxy) "Ẩn cấu hình nâng cao ▲"
                        else "Dùng API Key / Proxy riêng (tuỳ chọn) ▼"
                    )
                }

                AnimatedVisibility(visible = showAdvancedProxy) {
                    Column {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Gemini API Key cá nhân (để trống = dùng máy chủ chung)") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Hiện/ẩn key"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = proxyUrlInput,
                            onValueChange = { proxyUrlInput = it },
                            label = { Text("Proxy URL riêng (để trống = mặc định)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = proxyTokenInput,
                            onValueChange = { proxyTokenInput = it },
                            label = { Text("Proxy Token riêng (để trống = mặc định)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Button(
                    onClick = {
                        val effectiveProxyUrl = proxyUrlInput.trim().ifEmpty { BuildConfig.GEMINI_PROXY_URL }
                        val effectiveProxyToken = proxyTokenInput.trim().ifEmpty { BuildConfig.GEMINI_PROXY_TOKEN }
                        onSaveGeminiConfig(apiKeyInput, effectiveProxyUrl, effectiveProxyToken, selectedModel)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lưu cấu hình Gemini")
                }
            }
        }

        // 3. Firebase Sync Card
        item {
            val firebaseSubtitle = when {
                cloudSyncState.user != null -> "Đã kết nối: ${cloudSyncState.user.email ?: "Tài khoản khách"}"
                cloudSyncState.isFirebaseConfigured -> "Sẵn sàng đăng nhập đồng bộ đám mây"
                else -> "Đang chạy Offline • Chưa gắn google-services.json"
            }
            SectionCard(
                title = "Đồng bộ Firebase",
                subtitle = firebaseSubtitle,
                subtitleColor = if (cloudSyncState.user != null) NormalGreen else MutedText,
                icon = Icons.Default.CloudSync,
                expanded = expandedFirebase,
                onToggle = { expandedFirebase = !expandedFirebase }
            ) {
                val statusBg = if (cloudSyncState.isFirebaseConfigured) NormalGreenBg else WarningAmberBg
                val statusFg = if (cloudSyncState.isFirebaseConfigured) NormalGreen else WarningAmber
                Surface(color = statusBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (cloudSyncState.isFirebaseConfigured) {
                            "Đã nhận diện cấu hình Firebase (google-services.json)"
                        } else {
                            "Chưa gắn app/google-services.json — đặt file vào thư mục app/ và build lại để kích hoạt Cloud Sync"
                        },
                        color = statusFg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (cloudSyncState.user != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cloudSyncState.user.email ?: "Tài khoản khách (${cloudSyncState.user.uid.take(8)}...)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (settings.lastSyncedAt > 0L) {
                                Text(
                                    text = "Lần đồng bộ cuối: ${formatTimestamp(settings.lastSyncedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                        }
                        OutlinedButton(onClick = onSignOutFirebase) {
                            Text("Đăng xuất")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tự động đồng bộ khi quét/sửa", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = settings.autoSyncEnabled, onCheckedChange = onToggleAutoSync)
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onSyncFirebaseNow,
                        enabled = !cloudSyncState.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (cloudSyncState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Đang đồng bộ 2 chiều...")
                        } else {
                            Icon(Icons.Default.CloudDone, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Đồng bộ Firebase ngay")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Firebase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Mật khẩu") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSignInFirebase(emailInput, passwordInput) },
                            enabled = cloudSyncState.isFirebaseConfigured && emailInput.isNotBlank() && passwordInput.length >= 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Đăng nhập")
                        }
                        FilledTonalButton(
                            onClick = { onRegisterFirebase(emailInput, passwordInput) },
                            enabled = cloudSyncState.isFirebaseConfigured && emailInput.isNotBlank() && passwordInput.length >= 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Đăng ký")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignInAnonymously,
                        enabled = cloudSyncState.isFirebaseConfigured,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Đồng bộ nhanh không cần mật khẩu (Anonymous)")
                    }
                }

                cloudSyncState.lastSyncMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = msg, style = MaterialTheme.typography.bodySmall, color = TealPrimary)
                }
                cloudSyncState.errorMessage?.let { err ->
                    Spacer(Modifier.height(6.dp))
                    Text(text = err, style = MaterialTheme.typography.bodySmall, color = AlertRed)
                }
            }
        }

        // 4. Local JSON Backup & Restore
        item {
            SectionCard(
                title = "Sao lưu & Khôi phục (JSON)",
                subtitle = "Xuất hoặc khôi phục toàn bộ hồ sơ & lịch thuốc",
                icon = Icons.Default.Backup,
                expanded = expandedBackup,
                onToggle = { expandedBackup = !expandedBackup }
            ) {
                Text(
                    text = "Xuất toàn bộ hồ sơ gia đình, phiếu khám, biểu đồ và lịch nhắc thuốc thành 1 file JSON.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportBackupLauncher.launch("hieudon_backup_$stamp.json")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Xuất sao lưu")
                    }
                    FilledTonalButton(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Khôi phục")
                    }
                }
            }
        }

        // 5. GitHub Releases OTA Updater
        item {
            val (otaSubtitle, otaSubtitleColor) = when (otaState) {
                is OtaUpdateState.UpdateAvailable ->
                    "v${BuildConfig.VERSION_NAME} → Có bản mới ${otaState.release.tagName}!" to WarningAmber
                is OtaUpdateState.Downloading ->
                    "Đang tải ${otaState.release.tagName} (${otaState.progressPercent}%)" to InfoBlue
                is OtaUpdateState.ReadyToInstall ->
                    "Đã tải xong ${otaState.release.tagName} • Bấm để cài" to NormalGreen
                is OtaUpdateState.UpToDate ->
                    "v${BuildConfig.VERSION_NAME} • Đang là bản mới nhất" to NormalGreen
                else ->
                    "Phiên bản v${BuildConfig.VERSION_NAME} • ${settings.githubOtaRepo.ifBlank { "Chưa cấu hình repo" }}" to MutedText
            }
            val isOtaExpanded = expandedOta ||
                otaState is OtaUpdateState.Downloading ||
                otaState is OtaUpdateState.ReadyToInstall
            SectionCard(
                title = "Cập nhật OTA qua GitHub",
                subtitle = otaSubtitle,
                subtitleColor = otaSubtitleColor,
                icon = Icons.Default.SystemUpdate,
                expanded = isOtaExpanded,
                onToggle = { expandedOta = !expandedOta }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phiên bản hiện tại",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                    Surface(color = InfoBlueBg, shape = RoundedCornerShape(50)) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                            color = InfoBlue,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repository (owner/repo)") },
                    placeholder = { Text("vd: nguyenvana/hieudon-android") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("GitHub Token (chỉ cần nếu repo Private)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tự kiểm tra cập nhật khi mở app", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoCheckOta, onCheckedChange = { autoCheckOta = it })
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSaveOtaConfig(repoInput, tokenInput, autoCheckOta) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lưu repo")
                    }
                    Button(
                        onClick = {
                            onSaveOtaConfig(repoInput, tokenInput, autoCheckOta)
                            onCheckOtaUpdate()
                        },
                        enabled = otaState !is OtaUpdateState.Checking && otaState !is OtaUpdateState.Downloading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kiểm tra OTA")
                    }
                }

                Spacer(Modifier.height(12.dp))
                OtaStatusBlock(
                    otaState = otaState,
                    onDownloadClick = onDownloadOtaApk,
                    onInstallClick = { apkFile ->
                        if (!otaUpdater.canRequestPackageInstalls()) {
                            onShowMessage("Vui lòng bật 'Cho phép cài đặt từ nguồn này', sau đó bấm cài lại.")
                            context.startActivity(otaUpdater.createUnknownSourcesSettingsIntent())
                        } else {
                            context.startActivity(otaUpdater.createInstallApkIntent(apkFile))
                        }
                    }
                )
            }
        }
    }

    if (showCreateReminder || editingReminder != null) {
        AddEditMedicationReminderDialog(
            profileId = activeProfile.id,
            initial = editingReminder,
            defaultTimes = listOf(settings.morningTime, settings.noonTime, settings.afternoonTime, settings.eveningTime),
            onDismiss = {
                showCreateReminder = false
                editingReminder = null
            },
            onSave = { saved ->
                onSaveReminder(saved)
                showCreateReminder = false
                editingReminder = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddEditReminderDialog(
    profileId: String,
    initial: MedicationReminder?,
    defaultTimes: List<String>,
    onDismiss: () -> Unit,
    onSave: (MedicationReminder) -> Unit
) {
    var name by remember { mutableStateOf(initial?.medicationName ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "1 viên") }
    var mealTiming by remember { mutableStateOf(initial?.mealTiming ?: MealTiming.AFTER_MEAL) }
    var selectedTimes by remember {
        mutableStateOf(
            initial?.times?.ifEmpty { listOf(defaultTimes.first(), defaultTimes.last()) }
                ?: listOf(defaultTimes.first(), defaultTimes.last())
        )
    }
    var customTimeInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Thêm lịch nhắc thuốc" else "Sửa lịch nhắc thuốc") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên thuốc *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Liều dùng") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MealTiming.entries.forEach { timing ->
                        FilterChip(
                            selected = mealTiming == timing,
                            onClick = { mealTiming = timing },
                            label = { Text(timing.labelVi) }
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (defaultTimes + selectedTimes).distinct().sorted().forEach { t ->
                        val active = selectedTimes.contains(t)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedTimes = if (active) selectedTimes - t else (selectedTimes + t).sorted()
                            },
                            label = { Text(t) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customTimeInput,
                        onValueChange = { customTimeInput = it },
                        label = { Text("Giờ khác (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            val parsed = MedicationReminderScheduler.parseTime(customTimeInput)
                            if (parsed != null) {
                                val normalized = "%02d:%02d".format(parsed.first, parsed.second)
                                selectedTimes = (selectedTimes + normalized).distinct().sorted()
                                customTimeInput = ""
                                error = null
                            } else {
                                error = "Nhập giờ dạng HH:mm (ví dụ 08:30)."
                            }
                        }
                    ) {
                        Text("Thêm")
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = AlertRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Vui lòng nhập tên thuốc."
                        return@Button
                    }
                    if (selectedTimes.isEmpty()) {
                        error = "Chọn ít nhất 1 khung giờ."
                        return@Button
                    }
                    val saved = (initial ?: MedicationReminder(
                        profileId = profileId,
                        medicationName = name.trim(),
                        dosage = dosage.ifBlank { "Theo đơn" }
                    )).copy(
                        medicationName = name.trim(),
                        dosage = dosage.ifBlank { "Theo đơn" },
                        mealTiming = mealTiming,
                        times = selectedTimes.sorted(),
                        note = note.trim()
                    )
                    onSave(saved)
                }
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun OtaStatusBlock(
    otaState: OtaUpdateState,
    onDownloadClick: (OtaReleaseInfo) -> Unit,
    onInstallClick: (java.io.File) -> Unit
) {
    when (otaState) {
        OtaUpdateState.Idle -> {
            Text(
                text = "App sẽ kiểm tra bản phát hành mới nhất từ GitHub Releases và tải trực tiếp file .apk để cài đặt OTA.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }

        OtaUpdateState.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Đang kiểm tra GitHub Releases...", style = MaterialTheme.typography.bodyMedium)
            }
        }

        is OtaUpdateState.UpToDate -> {
            Surface(color = NormalGreenBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bạn đang dùng phiên bản mới nhất (${otaState.currentVersion} / release ${otaState.latestTag}).",
                    color = NormalGreen,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        is OtaUpdateState.UpdateAvailable -> {
            val release = otaState.release
            Card(
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Phiên bản mới: ${release.releaseName} (${release.tagName})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = InfoBlue
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = release.releaseNotes, style = MaterialTheme.typography.bodySmall)
                    if (release.apkSizeBytes > 0) {
                        Spacer(Modifier.height(4.dp))
                        val sizeMb = "%.1f MB".format(release.apkSizeBytes / (1024f * 1024f))
                        Text(
                            text = "Tệp APK: ${release.apkFileName ?: "app.apk"} ($sizeMb)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onDownloadClick(release) },
                        enabled = release.apkUrl != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Tải bản cập nhật APK")
                    }
                }
            }
        }

        is OtaUpdateState.Downloading -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                val downloadedMb = "%.1f".format(otaState.downloadedBytes / (1024f * 1024f))
                val totalMb = if (otaState.totalBytes > 0) "%.1f MB".format(otaState.totalBytes / (1024f * 1024f)) else "..."
                Text(
                    text = "Đang tải ${otaState.release.tagName}: ${otaState.progressPercent}% ($downloadedMb / $totalMb)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { otaState.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is OtaUpdateState.ReadyToInstall -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = NormalGreenBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Đã tải xong ${otaState.release.tagName}!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NormalGreen
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onInstallClick(otaState.apkFile) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cài đặt bản cập nhật ngay")
                    }
                }
            }
        }

        is OtaUpdateState.Error -> {
            Surface(color = AlertRedBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = otaState.message,
                    color = AlertRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    subtitleColor: Color = MutedText,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) TealPrimary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TealLightContainer.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = TealPrimary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Thu gọn" else "Mở rộng",
                    tint = MutedText
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    return SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN")).format(Date(epochMillis))
}
