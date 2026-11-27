package com.medlens.ai

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.ui.MainTab
import com.medlens.ai.ui.MedLensViewModel
import com.medlens.ai.ui.components.RecordDetailScreen
import com.medlens.ai.ui.screens.AiChatScreen
import com.medlens.ai.ui.screens.FamilyRecordsScreen
import com.medlens.ai.ui.screens.HomeScreen
import com.medlens.ai.ui.screens.ScanScreen
import com.medlens.ai.ui.screens.SyncAndOtaScreen
import com.medlens.ai.ui.theme.MedLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            MedLensTheme {
                MedLensApp()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedLensApp(vm: MedLensViewModel = viewModel()) {
    val selectedTab by vm.selectedTab.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val activeRecords by vm.activeProfileRecords.collectAsState()
    val activeReminders by vm.activeProfileReminders.collectAsState()
    val activeTodayDoses by vm.activeProfileTodayDoses.collectAsState()
    val activeTrends by vm.activeProfileTrends.collectAsState()
    val activeAssistantChat by vm.activeProfileAssistantChat.collectAsState()
    val settings by vm.settings.collectAsState()
    val cloudSyncState by vm.cloudSyncState.collectAsState()
    val otaState by vm.otaState.collectAsState()
    val activeDetail by vm.activeRecordDetail.collectAsState()
    val isAnalyzing by vm.isAnalyzing.collectAsState()
    val analysisError by vm.analysisError.collectAsState()
    val isChatReplying by vm.isChatReplying.collectAsState()
    val isAssistantReplying by vm.isAssistantReplying.collectAsState()
    val snackbarMsg by vm.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    if (activeDetail != null) {
        val record = activeDetail!!
        RecordDetailScreen(
            record = record,
            isChatReplying = isChatReplying,
            snackbarHostState = snackbarHostState,
            onBack = { vm.closeRecordDetail() },
            onDelete = { vm.deleteRecord(record.id) },
            onAddMedicationReminder = { med ->
                vm.addReminderFromMedication(med, record)
            },
            onAddAllReminders = {
                vm.addAllRemindersFromRecord(record)
            },
            onSendChatQuestion = { q ->
                vm.askRecordFollowUp(record, q)
            },
            onShowMessage = { vm.showMessage(it) }
        )
        return
    }

    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!imeVisible) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            MainTab.HOME -> Icons.Default.Home
                            MainTab.SCAN -> Icons.Default.CameraAlt
                            MainTab.RECORDS -> Icons.Default.FolderShared
                            MainTab.AI_CHAT -> Icons.Default.AutoAwesome
                            MainTab.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { vm.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    records = activeRecords,
                    todayDoses = activeTodayDoses,
                    otaState = otaState,
                    onSelectProfile = { vm.selectProfile(it) },
                    onNavigateToScan = { vm.selectTab(MainTab.SCAN) },
                    onNavigateToRecords = { vm.selectTab(MainTab.RECORDS) },
                    onNavigateToSettings = { vm.selectTab(MainTab.SETTINGS) },
                    onOpenRecord = { vm.openRecordDetail(it) },
                    onMarkDoseTaken = { dose -> vm.markDoseStatus(dose, IntakeStatus.TAKEN) },
                    onLoadSampleLab = { vm.loadSampleLabWithTrend() },
                    onLoadSamplePrescription = { vm.loadSamplePrescription() }
                )

                MainTab.SCAN -> ScanScreen(
                    activeProfile = activeProfile,
                    hasGeminiApiKey = settings.geminiApiKey.isNotBlank(),
                    geminiModel = settings.geminiModel,
                    isAnalyzing = isAnalyzing,
                    analysisError = analysisError,
                    onAnalyzeAttachments = { attachments, mode -> vm.analyzeAttachments(attachments, mode) },
                    onLoadSampleLabWithTrend = { vm.loadSampleLabWithTrend() },
                    onLoadSamplePrescription = { vm.loadSamplePrescription() },
                    onGoToSettings = { vm.selectTab(MainTab.SETTINGS) }
                )

                MainTab.RECORDS -> FamilyRecordsScreen(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    records = activeRecords,
                    trends = activeTrends,
                    onSelectProfile = { vm.selectProfile(it) },
                    onSaveProfile = { vm.saveProfile(it) },
                    onSaveBhytImage = { prof, uri -> vm.saveBhytImage(prof, uri) },
                    onOpenRecord = { vm.openRecordDetail(it) },
                    onLoadSampleLabWithTrend = { vm.loadSampleLabWithTrend() }
                )

                MainTab.AI_CHAT -> AiChatScreen(
                    activeProfile = activeProfile,
                    profileRecords = activeRecords,
                    messages = activeAssistantChat,
                    geminiModel = settings.geminiModel,
                    hasGeminiApiKey = settings.geminiApiKey.isNotBlank(),
                    isReplying = isAssistantReplying,
                    onSendMessage = { q, recId -> vm.sendDirectAssistantMessage(q, recId) },
                    onSendVoice = { file, recId -> vm.sendDirectAssistantVoice(file, recId) },
                    onClearChat = { vm.clearDirectAssistantChat() },
                    onGoToSettings = { vm.selectTab(MainTab.SETTINGS) },
                    onShowMessage = { vm.showMessage(it) }
                )

                MainTab.SETTINGS -> SyncAndOtaScreen(
                    activeProfile = activeProfile,
                    reminders = activeReminders,
                    settings = settings,
                    cloudSyncState = cloudSyncState,
                    otaState = otaState,
                    otaUpdater = vm.otaUpdater,
                    onSaveDefaultSlotTimes = { m, n, a, e -> vm.saveDefaultSlotTimes(m, n, a, e) },
                    onSaveReminder = { vm.saveReminder(it) },
                    onToggleReminder = { vm.toggleReminderActive(it) },
                    onDeleteReminder = { vm.deleteReminder(it) },
                    onSendTestNotification = { vm.sendTestNotification() },
                    onSaveGeminiConfig = { key, model -> vm.saveGeminiConfig(key, model) },
                    onSaveOtaConfig = { repo, token, auto -> vm.saveOtaConfig(repo, token, auto) },
                    onToggleAutoSync = { vm.toggleAutoSync(it) },
                    onSignInFirebase = { email, pass -> vm.signInFirebase(email, pass) },
                    onRegisterFirebase = { email, pass -> vm.registerFirebase(email, pass) },
                    onSignInAnonymously = { vm.signInAnonymouslyFirebase() },
                    onSignOutFirebase = { vm.signOutFirebase() },
                    onSyncFirebaseNow = { vm.syncFirebaseNow() },
                    onBuildBackupJson = { vm.buildBackupJson() },
                    onRestoreBackupJson = { vm.restoreBackupJson(it) },
                    onCheckOtaUpdate = { vm.checkForOtaUpdate() },
                    onDownloadOtaApk = { vm.downloadOtaApk(it) },
                    onShowMessage = { vm.showMessage(it) }
                )
            }
        }
    }
}
