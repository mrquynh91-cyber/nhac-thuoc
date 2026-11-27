package com.medlens.ai.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class FirebaseUserInfo(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean
)

data class CloudSyncUiState(
    val isFirebaseConfigured: Boolean = false,
    val user: FirebaseUserInfo? = null,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String? = null,
    val errorMessage: String? = null
)

class FirebaseSyncRepository(
    private val context: Context,
    private val localStore: LocalMedicalStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(CloudSyncUiState())
    val state: StateFlow<CloudSyncUiState> = _state.asStateFlow()

    init {
        refreshConfigurationStatus()
    }

    fun refreshConfigurationStatus() {
        val configured = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) != null
            } else {
                true
            }
        }.getOrDefault(false)

        if (!configured) {
            _state.value = CloudSyncUiState(
                isFirebaseConfigured = false,
                lastSyncMessage = "Chưa gắn file app/google-services.json. App vẫn hoạt động offline & sao lưu JSON bình thường."
            )
            return
        }

        runCatching {
            val firestore = FirebaseFirestore.getInstance()
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder().build()
        }

        val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        _state.value = _state.value.copy(
            isFirebaseConfigured = true,
            user = currentUser?.let {
                FirebaseUserInfo(
                    uid = it.uid,
                    email = it.email,
                    isAnonymous = it.isAnonymous
                )
            },
            errorMessage = null
        )
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUserInfo> =
        withContext(Dispatchers.IO) {
            if (!_state.value.isFirebaseConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("Firebase chưa được cấu hình (thiếu app/google-services.json).")
                )
            }
            runCatching {
                val result = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email.trim(), password)
                    .await()
                val user = result.user ?: throw IllegalStateException("Đăng nhập không thành công.")
                val info = FirebaseUserInfo(user.uid, user.email, user.isAnonymous)
                _state.value = _state.value.copy(
                    user = info,
                    errorMessage = null,
                    lastSyncMessage = "Đã đăng nhập ${info.email ?: info.uid}"
                )
                info
            }.onFailure { err ->
                _state.value = _state.value.copy(errorMessage = err.localizedMessage)
            }
        }

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUserInfo> =
        withContext(Dispatchers.IO) {
            if (!_state.value.isFirebaseConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("Firebase chưa được cấu hình (thiếu app/google-services.json).")
                )
            }
            runCatching {
                val result = FirebaseAuth.getInstance()
                    .createUserWithEmailAndPassword(email.trim(), password)
                    .await()
                val user = result.user ?: throw IllegalStateException("Tạo tài khoản không thành công.")
                val info = FirebaseUserInfo(user.uid, user.email, user.isAnonymous)
                _state.value = _state.value.copy(
                    user = info,
                    errorMessage = null,
                    lastSyncMessage = "Đã tạo tài khoản ${info.email}"
                )
                info
            }.onFailure { err ->
                _state.value = _state.value.copy(errorMessage = err.localizedMessage)
            }
        }

    suspend fun signInAnonymously(): Result<FirebaseUserInfo> = withContext(Dispatchers.IO) {
        if (!_state.value.isFirebaseConfigured) {
            return@withContext Result.failure(
                IllegalStateException("Firebase chưa được cấu hình (thiếu app/google-services.json).")
            )
        }
        runCatching {
            val result = FirebaseAuth.getInstance().signInAnonymously().await()
            val user = result.user ?: throw IllegalStateException("Đăng nhập nhanh thất bại.")
            val info = FirebaseUserInfo(user.uid, user.email, user.isAnonymous)
            _state.value = _state.value.copy(
                user = info,
                errorMessage = null,
                lastSyncMessage = "Đã bật đồng bộ đám mây (tài khoản khách)"
            )
            info
        }.onFailure { err ->
            _state.value = _state.value.copy(errorMessage = err.localizedMessage)
        }
    }

    fun signOut() {
        if (_state.value.isFirebaseConfigured) {
            runCatching { FirebaseAuth.getInstance().signOut() }
        }
        _state.value = _state.value.copy(
            user = null,
            lastSyncMessage = "Đã đăng xuất Firebase."
        )
    }

    suspend fun syncNow(): Result<String> = withContext(Dispatchers.IO) {
        if (!_state.value.isFirebaseConfigured) {
            return@withContext Result.failure(
                IllegalStateException("Chưa gắn app/google-services.json nên chưa thể đồng bộ Firebase.")
            )
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.failure(
                IllegalStateException("Vui lòng đăng nhập tài khoản Firebase trước khi đồng bộ.")
            )

        _state.value = _state.value.copy(isSyncing = true, errorMessage = null)

        runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val storage = FirebaseStorage.getInstance()
            val userDoc = firestore.collection("users").document(uid)

            val remoteProfiles = userDoc.collection("profiles").get().await().documents.mapNotNull { doc ->
                doc.getString("payloadJson")?.let { payload ->
                    runCatching { json.decodeFromString<FamilyProfile>(payload) }.getOrNull()
                }
            }
            val remoteRecords = userDoc.collection("records").get().await().documents.mapNotNull { doc ->
                doc.getString("payloadJson")?.let { payload ->
                    runCatching { json.decodeFromString<MedicalRecord>(payload) }.getOrNull()
                }
            }
            val remoteReminders = userDoc.collection("reminders").get().await().documents.mapNotNull { doc ->
                doc.getString("payloadJson")?.let { payload ->
                    runCatching { json.decodeFromString<MedicationReminder>(payload) }.getOrNull()
                }
            }
            val remoteLogs = userDoc.collection("intake_logs").get().await().documents.mapNotNull { doc ->
                doc.getString("payloadJson")?.let { payload ->
                    runCatching { json.decodeFromString<MedicationIntakeLog>(payload) }.getOrNull()
                }
            }

            val mergeStats = localStore.mergeRemoteData(
                remoteProfiles = remoteProfiles,
                remoteRecords = remoteRecords,
                remoteReminders = remoteReminders,
                remoteLogs = remoteLogs
            )

            for (record in localStore.records.value) {
                val localPath = record.localImagePath
                if (localPath != null && record.cloudImageUrl.isNullOrBlank()) {
                    val file = File(localPath)
                    if (file.exists()) {
                        val downloadUrl = runCatching {
                            val ref = storage.reference.child("users/$uid/scans/${record.id}.jpg")
                            ref.putFile(Uri.fromFile(file)).await()
                            ref.downloadUrl.await().toString()
                        }.getOrNull()
                        if (!downloadUrl.isNullOrBlank()) {
                            localStore.upsertRecord(record.copy(cloudImageUrl = downloadUrl))
                        }
                    }
                }
            }

            val batch = firestore.batch()
            localStore.profiles.value.forEach { profile ->
                val ref = userDoc.collection("profiles").document(profile.id)
                batch.set(
                    ref,
                    mapOf(
                        "id" to profile.id,
                        "updatedAt" to profile.updatedAt,
                        "payloadJson" to json.encodeToString(profile)
                    ),
                    SetOptions.merge()
                )
            }
            localStore.records.value.forEach { record ->
                val ref = userDoc.collection("records").document(record.id)
                batch.set(
                    ref,
                    mapOf(
                        "id" to record.id,
                        "updatedAt" to record.updatedAt,
                        "payloadJson" to json.encodeToString(record)
                    ),
                    SetOptions.merge()
                )
            }
            localStore.reminders.value.forEach { reminder ->
                val ref = userDoc.collection("reminders").document(reminder.id)
                batch.set(
                    ref,
                    mapOf(
                        "id" to reminder.id,
                        "updatedAt" to reminder.updatedAt,
                        "payloadJson" to json.encodeToString(reminder)
                    ),
                    SetOptions.merge()
                )
            }
            localStore.intakeLogs.value.take(200).forEach { log ->
                val ref = userDoc.collection("intake_logs").document(log.id)
                batch.set(
                    ref,
                    mapOf(
                        "id" to log.id,
                        "loggedAt" to log.loggedAt,
                        "payloadJson" to json.encodeToString(log)
                    ),
                    SetOptions.merge()
                )
            }
            batch.commit().await()

            val syncedAt = System.currentTimeMillis()
            localStore.updateSettings { it.copy(lastSyncedAt = syncedAt) }

            val summary = "Đồng bộ thành công (${localStore.profiles.value.size} hồ sơ gia đình, " +
                "${localStore.records.value.size} phiếu khám, ${localStore.reminders.value.size} lịch thuốc; " +
                "nhận mới ${mergeStats.recordsMerged} phiếu)."
            _state.value = _state.value.copy(
                isSyncing = false,
                lastSyncMessage = summary,
                errorMessage = null
            )
            summary
        }.onFailure { err ->
            _state.value = _state.value.copy(
                isSyncing = false,
                errorMessage = "Lỗi đồng bộ Firebase: ${err.localizedMessage}"
            )
        }
    }
}
