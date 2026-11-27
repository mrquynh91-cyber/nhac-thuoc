package com.medlens.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.medlens.ai.data.DocumentType
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.ScanAttachment
import com.medlens.ai.ui.components.MedicalDisclaimerCard
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg
import java.io.File

@Composable
fun ScanScreen(
    activeProfile: FamilyProfile,
    hasGeminiApiKey: Boolean,
    geminiModel: String,
    isAnalyzing: Boolean,
    analysisError: String?,
    onAnalyzeAttachments: (List<ScanAttachment>, DocumentType) -> Unit,
    onLoadSampleLabWithTrend: () -> Unit,
    onLoadSamplePrescription: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(DocumentType.PRESCRIPTION) }
    val attachments = remember { mutableStateListOf<ScanAttachment>() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun queryDisplayName(uri: Uri, fallback: String): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: fallback
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            attachments.add(
                ScanAttachment(
                    uri = uri,
                    mimeType = "image/jpeg",
                    displayName = "Trang ${attachments.size + 1}"
                )
            )
        }
    }

    fun launchCamera() {
        val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val photoFile = File(capturesDir, "page_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
    }

    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        uris.forEach { uri ->
            attachments.add(
                ScanAttachment(
                    uri = uri,
                    mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                    displayName = queryDisplayName(uri, "Ảnh ${attachments.size + 1}")
                )
            )
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            attachments.add(
                ScanAttachment(
                    uri = uri,
                    mimeType = "application/pdf",
                    displayName = queryDisplayName(uri, "KetQuaBenhVien.pdf")
                )
            )
        }
    }

    fun startCameraWithPermissionCheck() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quét AI • Hồ sơ ${activeProfile.name}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Hỗ trợ chụp nhiều trang liên tiếp, chọn ảnh thư viện hoặc tải file PDF kết quả từ bệnh viện ($geminiModel).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onGoToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt",
                        tint = TealPrimary
                    )
                }
            }
        }

        // Fast 2-mode switcher: [Đơn thuốc] | [Phiếu xét nghiệm]
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == DocumentType.PRESCRIPTION,
                    onClick = { selectedMode = DocumentType.PRESCRIPTION },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) {
                    Text("Đơn thuốc", fontWeight = FontWeight.Bold)
                }
                SegmentedButton(
                    selected = selectedMode == DocumentType.LAB_RESULT,
                    onClick = { selectedMode = DocumentType.LAB_RESULT },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp)) }
                ) {
                    Text("Phiếu xét nghiệm", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!hasGeminiApiKey) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmberBg),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WarningAmber)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chưa có Gemini API Key",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Nhập khóa API miễn phí ở Cài đặt để quét ảnh/PDF thật, hoặc thử dữ liệu mẫu bên dưới.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(onClick = onGoToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Paper Alignment Guide + Capture / Multi-page / PDF Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Visual Paper Frame Guide
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TealLightContainer.copy(alpha = 0.35f))
                            .border(2.dp, TealPrimary.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selectedMode == DocumentType.PRESCRIPTION) {
                                    Icons.Default.Medication
                                } else {
                                    Icons.Default.Science
                                },
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (selectedMode == DocumentType.PRESCRIPTION) {
                                    "Đặt tờ ĐƠN THUỐC phẳng, đủ sáng trong khung"
                                } else {
                                    "Đặt PHIẾU XÉT NGHIỆM thẳng hàng, rõ cột số liệu"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = TealPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Có thể chụp nhiều trang liên tiếp nếu phiếu dài",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedText
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = { startCameraWithPermissionCheck() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing
                    ) {
                        Icon(
                            if (attachments.isEmpty()) Icons.Default.CameraAlt else Icons.Default.AddAPhoto,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (attachments.isEmpty()) "Mở Camera chụp trang 1"
                            else "Chụp thêm trang ${attachments.size + 1}"
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                multiPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isAnalyzing
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chọn ảnh")
                        }
                        FilledTonalButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.weight(1f),
                            enabled = !isAnalyzing
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tải PDF")
                        }
                    }

                    // Selected Multi-page / PDF preview strip
                    if (attachments.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Đã chọn ${attachments.size} trang / tệp",
                                style = MaterialTheme.typography.titleSmall
                            )
                            OutlinedButton(
                                onClick = { attachments.clear() },
                                enabled = !isAnalyzing,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Xóa hết")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(attachments) { idx, item ->
                                Box(
                                    modifier = Modifier
                                        .width(108.dp)
                                        .height(126.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                ) {
                                    if (item.mimeType == "application/pdf") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(AlertRedBg.copy(alpha = 0.4f))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = AlertRed,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = item.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                    ) {
                                        IconButton(
                                            onClick = { attachments.removeAt(idx) },
                                            enabled = !isAnalyzing
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Xóa trang",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onAnalyzeAttachments(attachments.toList(), selectedMode) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAnalyzing
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Gemini ($geminiModel) đang giải mã...")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Giải mã bằng Gemini AI (${attachments.size} tệp)")
                            }
                        }
                    }

                    if (!analysisError.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AlertRedBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = analysisError,
                                color = AlertRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Demo Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Xem thử ngay với dữ liệu mẫu",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tạo ngay phiếu xét nghiệm 2 đợt (để xem Đèn giao thông + Biểu đồ xu hướng) hoặc Đơn thuốc mẫu (Sáng–Trưa–Chiều–Tối + Chat AI).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onLoadSampleLabWithTrend,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Xét nghiệm + Biểu đồ")
                        }
                        OutlinedButton(
                            onClick = onLoadSamplePrescription,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Đơn thuốc mẫu")
                        }
                    }
                }
            }
        }

        item {
            MedicalDisclaimerCard(MedicalRecord.DEFAULT_MEDICAL_DISCLAIMER)
        }
    }
}
