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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medlens.ai.data.IntakeStatus
import com.medlens.ai.ui.MainTab
import com.medlens.ai.ui.MedLensViewModel
import com.medlens.ai.ui.components.RecordDetailScreen
import com.medlens.ai.ui.screens.AiChatScreen
import com.medlens.ai.ui.screens.FamilyRecordsScreen
import com.medlens.ai.ui.screens.HomeScreen
import com.medlens.ai.ui.screens.MedicationManagerScreen
import com.medlens.ai.ui.screens.MoreAndDietScreen
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
    val activeDietPlan by vm.activeProfileDietPlan.collectAsState()
    val isGeneratingDiet by vm.isGeneratingDiet.collectAsState()
    val settings by vm.settings.collectAsState()
    val cloudSyncState by vm.cloudSyncState.collectAsState()
    val otaState by vm.otaState.collectAsState()
    val activeDetail by vm.activeRecordDetail.collectAsState()
    val isAnalyzing by vm.isAnalyzing.collectAsState()
    val analysisError by vm.analysisError.collectAsState()
    val isChatReplying by vm.isChatReplying.collectAsState()
    val isAssistantReplying by vm.isAssistantReplying.collectAsState()
    val snackbarMsg by vm.snackbarMessage.collectAsState()
    val isTelegramBusy by vm.isTelegramBusy.collectAsState()
    val discoveredTelegramChats by vm.discoveredTelegramChats.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showMedicationManager by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var recordsInitialSubTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
            vm.consumeSnackbar()
        }
    }

    if (showSettingsScreen) {
        SyncAndOtaScreen(
            activeProfile = activeProfile,
            reminders = activeReminders,
            settings = settings,
            cloudSyncState = cloudSyncState,
            otaState = otaState,
            otaUpdater = vm.otaUpdater,
            snackbarHostState = snackbarHostState,
            isTelegramBusy = isTelegramBusy,
            discoveredTelegramChats = discoveredTelegramChats,
            onBack = { showSettingsScreen = false },
            onSaveDefaultSlotTimes = { m, n, a, e -> vm.saveDefaultSlotTimes(m, n, a, e) },
            onSaveReminder = { vm.saveReminder(it) },
            onToggleReminder = { vm.toggleReminderActive(it) },
            onDeleteReminder = { vm.deleteReminder(it) },
            onSendTestNotification = { vm.sendTestNotification() },
            onSaveTelegramConfig = { enabled, botToken, chatId, groupName, onDue, onTaken, onSnoozeSkip ->
                vm.saveTelegramConfig(enabled, botToken, chatId, groupName, onDue, onTaken, onSnoozeSkip)
            },
            onDiscoverTelegramGroups = { vm.discoverTelegramGroups(it) },
            onSendTelegramTest = { botToken, chatId, groupName ->
                vm.sendTelegramTestMessage(botToken, chatId, groupName)
            },
            onSaveGeminiConfig = { key, proxy, token, model ->
                vm.saveGeminiConfig(key, proxy, token, model)
            },
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
        return
    }

    if (showMedicationManager) {
        MedicationManagerScreen(
            activeProfile = activeProfile,
            reminders = activeReminders,
            settings = settings,
            snackbarHostState = snackbarHostState,
            onBack = { showMedicationManager = false },
            onSaveReminder = { vm.saveReminder(it) },
            onToggleReminder = { vm.toggleReminderActive(it) },
            onDeleteReminder = { vm.deleteReminder(it) },
            onSendTestNotification = { vm.sendTestNotification() }
        )
        return
    }

    if (activeDetail != null) {
        val record = activeDetail!!
        RecordDetailScreen(
            record = record,
            existingReminders = activeReminders,
            settings = settings,
            isChatReplying = isChatReplying,
            snackbarHostState = snackbarHostState,
            onBack = { vm.closeRecordDetail() },
            onSaveCustomReminder = { reminder ->
                vm.saveReminder(reminder)
            },
            onSendChatQuestion = { q ->
                vm.askRecordFollowUp(record, q)
            },
            onSendChatVoice = { file ->
                vm.askRecordFollowUpVoice(record, file)
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
                            MainTab.MORE -> Icons.Default.Apps
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                if (tab == MainTab.RECORDS) {
                                    recordsInitialSubTab = 0
                                }
                                vm.selectTab(tab)
                            },
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
                    settings = settings,
                    otaState = otaState,
                    onSelectProfile = { vm.selectProfile(it) },
                    onNavigateToScan = { vm.selectTab(MainTab.SCAN) },
                    onNavigateToRecords = {
                        recordsInitialSubTab = 0
                        vm.selectTab(MainTab.RECORDS)
                    },
                    onNavigateToSettings = { showSettingsScreen = true },
                    onOpenMedicationManager = { showMedicationManager = true },
                    onSaveReminder = { vm.saveReminder(it) },
                    onOpenRecord = { vm.openRecordDetail(it) },
                    onMarkDoseTaken = { dose -> vm.markDoseStatus(dose, IntakeStatus.TAKEN) },
                    onLoadSampleLab = { vm.loadSampleLabWithTrend() },
                    onLoadSamplePrescription = { vm.loadSamplePrescription() }
                )

                MainTab.SCAN -> ScanScreen(
                    activeProfile = activeProfile,
                    hasGeminiApiKey = settings.hasAiReady,
                    geminiModel = settings.geminiModel,
                    isAnalyzing = isAnalyzing,
                    analysisError = analysisError,
                    onAnalyzeAttachments = { attachments, mode -> vm.analyzeAttachments(attachments, mode) },
                    onLoadSampleLabWithTrend = { vm.loadSampleLabWithTrend() },
                    onLoadSamplePrescription = { vm.loadSamplePrescription() },
                    onGoToSettings = { showSettingsScreen = true }
                )

                MainTab.RECORDS -> FamilyRecordsScreen(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    records = activeRecords,
                    trends = activeTrends,
                    initialSubTab = recordsInitialSubTab,
                    onSelectProfile = { vm.selectProfile(it) },
                    onSaveProfile = { vm.saveProfile(it) },
                    onSaveBhytImage = { prof, uri -> vm.saveBhytImage(prof, uri) },
                    onOpenRecord = { vm.openRecordDetail(it) },
                    onLoadSampleLabWithTrend = { vm.loadSampleLabWithTrend() },
                    onOpenSettings = { showSettingsScreen = true }
                )

                MainTab.AI_CHAT -> AiChatScreen(
                    activeProfile = activeProfile,
                    profileRecords = activeRecords,
                    messages = activeAssistantChat,
                    geminiModel = settings.geminiModel,
                    hasGeminiApiKey = settings.hasAiReady,
                    isReplying = isAssistantReplying,
                    onSendMessage = { q, recId -> vm.sendDirectAssistantMessage(q, recId) },
                    onSendVoice = { file, recId -> vm.sendDirectAssistantVoice(file, recId) },
                    onClearChat = { vm.clearDirectAssistantChat() },
                    onGoToSettings = { showSettingsScreen = true },
                    onShowMessage = { vm.showMessage(it) }
                )

                MainTab.MORE -> MoreAndDietScreen(
                    activeProfile = activeProfile,
                    profileRecords = activeRecords,
                    activeReminders = activeReminders,
                    savedAiDietPlan = activeDietPlan,
                    isGeneratingDiet = isGeneratingDiet,
                    hasAiReady = settings.hasAiReady,
                    onGenerateAiDiet = { key, label, pref ->
                        vm.generatePersonalizedDietPlan(key, label, pref)
                    },
                    onOpenMedicationManager = { showMedicationManager = true },
                    onOpenProfileInfo = {
                        recordsInitialSubTab = 2
                        vm.selectTab(MainTab.RECORDS)
                    },
                    onOpenSettings = { showSettingsScreen = true }
                )
            }
        }
    }
}
