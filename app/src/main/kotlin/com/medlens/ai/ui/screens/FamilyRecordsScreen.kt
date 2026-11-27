package com.medlens.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.IndicatorStatus
import com.medlens.ai.data.IndicatorTrendSeries
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.InfoBlue
import com.medlens.ai.ui.theme.InfoBlueBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.NormalGreen
import com.medlens.ai.ui.theme.NormalGreenBg
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import java.io.File

@Composable
fun FamilyRecordsScreen(
    profiles: List<FamilyProfile>,
    activeProfile: FamilyProfile,
    records: List<MedicalRecord>,
    trends: List<IndicatorTrendSeries>,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (FamilyProfile) -> Unit,
    onSaveBhytImage: (FamilyProfile, Uri) -> Unit,
    onOpenRecord: (MedicalRecord) -> Unit,
    onLoadSampleLabWithTrend: () -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Family Member Filter Row
        Column(modifier = Modifier.padding(fromHorizontal = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Text(
                text = "Sổ Khám Gia Đình",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(profiles, key = { it.id }) { prof ->
                    FilterChip(
                        selected = prof.id == activeProfile.id,
                        onClick = { onSelectProfile(prof.id) },
                        label = { Text(prof.name, fontWeight = FontWeight.SemiBold) }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showAddMemberDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thêm người")
                    }
                }
            }
        }

        // 3 Sub-tabs: Tất cả phiếu | Biểu đồ chỉ số | Thông tin cơ bản
        val subTabs = listOf("Tất cả phiếu", "Biểu đồ chỉ số", "Thông tin cơ bản")
        TabRow(selectedTabIndex = selectedSubTab) {
            subTabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }

        when (selectedSubTab) {
            0 -> AllRecordsSubTab(
                activeProfile = activeProfile,
                records = records,
                onOpenRecord = onOpenRecord,
                onLoadSampleLabWithTrend = onLoadSampleLabWithTrend
            )

            1 -> TrendChartSubTab(
                activeProfile = activeProfile,
                trends = trends,
                onLoadSampleLabWithTrend = onLoadSampleLabWithTrend
            )

            2 -> BasicProfileInfoSubTab(
                profile = activeProfile,
                onSaveProfile = onSaveProfile,
                onSaveBhytImage = onSaveBhytImage
            )
        }
    }

    if (showAddMemberDialog) {
        AddFamilyMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            onSave = { newMember ->
                onSaveProfile(newMember)
                showAddMemberDialog = false
            }
        )
    }
}

private fun Modifier.padding(
    fromHorizontal: androidx.compose.ui.unit.Dp,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp
): Modifier = this.padding(start = fromHorizontal, end = fromHorizontal, top = top, bottom = bottom)

@Composable
private fun AllRecordsSubTab(
    activeProfile: FamilyProfile,
    records: List<MedicalRecord>,
    onOpenRecord: (MedicalRecord) -> Unit,
    onLoadSampleLabWithTrend: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Hồ sơ '${activeProfile.name}' chưa có lịch sử khám",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Toàn bộ đơn thuốc, phiếu xét nghiệm và ảnh gốc sẽ được lưu theo dòng thời gian tại đây.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onLoadSampleLabWithTrend) {
                            Text("Tạo 2 phiếu xét nghiệm mẫu để xem thử")
                        }
                    }
                }
            }
        } else {
            items(records, key = { it.id }) { record ->
                MedicalRecordSummaryRow(record = record, onClick = { onOpenRecord(record) })
            }
        }
    }
}

@Composable
private fun TrendChartSubTab(
    activeProfile: FamilyProfile,
    trends: List<IndicatorTrendSeries>,
    onLoadSampleLabWithTrend: () -> Unit
) {
    var selectedSeriesIndex by remember(trends) { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (trends.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Chưa có dữ liệu biểu đồ cho ${activeProfile.name}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Khi quét phiếu xét nghiệm từ lần 2 trở đi, app sẽ tự so sánh xu hướng tăng/giảm các chỉ số như ALT, AST, Triglyceride, Glucose...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onLoadSampleLabWithTrend) {
                            Text("Tạo dữ liệu mẫu 2 đợt khám")
                        }
                    }
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trends.indices.toList()) { idx ->
                        val s = trends[idx]
                        FilterChip(
                            selected = selectedSeriesIndex == idx,
                            onClick = { selectedSeriesIndex = idx },
                            label = { Text("${s.indicatorName} (${s.points.size})") }
                        )
                    }
                }
            }

            val activeSeries = trends.getOrElse(selectedSeriesIndex) { trends.first() }
            item {
                IndicatorTrendCard(series = activeSeries)
            }

            item {
                Text(
                    text = "Tất cả chỉ số đang theo dõi",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(trends) { series ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = series.indicatorName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${series.latestPoint.rawValue} ${series.unit}".trim(),
                                style = MaterialTheme.typography.titleMedium,
                                color = TealPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = series.deltaSummaryVi(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndicatorTrendCard(series: IndicatorTrendSeries) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = series.indicatorName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${series.points.size} lần đo • Đơn vị: ${series.unit.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                Surface(color = InfoBlueBg, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = "${series.latestPoint.rawValue} ${series.unit}".trim(),
                        color = InfoBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = NormalGreenBg.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = series.deltaSummaryVi(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NormalGreen,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Line Chart Canvas
            val points = series.points
            val minVal = points.minOf { it.value }
            val maxVal = points.maxOf { it.value }
            val span = (maxVal - minVal).takeIf { it > 0.0001 } ?: maxOf(maxVal * 0.2, 1.0)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val w = size.width
                val h = size.height

                // Horizontal grid lines
                listOf(0f, 0.5f, 1f).forEach { frac ->
                    val y = h * frac
                    drawLine(
                        color = Color(0xFFDDE8E8),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 2f
                    )
                }

                val coords = points.mapIndexed { idx, p ->
                    val x = if (points.size == 1) w / 2f else (idx.toFloat() / (points.size - 1)) * w
                    val normalized = ((p.value - minVal) / span).toFloat().coerceIn(0f, 1f)
                    val y = h - (normalized * (h * 0.8f) + h * 0.1f)
                    Offset(x, y)
                }

                if (coords.size >= 2) {
                    val path = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        coords.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
                    }
                    drawPath(
                        path = path,
                        color = TealPrimary,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                }

                coords.forEachIndexed { idx, pt ->
                    val dotColor = when (points[idx].status) {
                        IndicatorStatus.NORMAL -> NormalGreen
                        IndicatorStatus.BORDERLINE -> WarningAmber
                        else -> AlertRed
                    }
                    drawCircle(color = Color.White, radius = 14f, center = pt)
                    drawCircle(color = dotColor, radius = 10f, center = pt)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = p.rawValue,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = p.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicProfileInfoSubTab(
    profile: FamilyProfile,
    onSaveProfile: (FamilyProfile) -> Unit,
    onSaveBhytImage: (FamilyProfile, Uri) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var birthYear by remember(profile.id) { mutableStateOf(profile.birthYear) }
    var bloodType by remember(profile.id) { mutableStateOf(profile.bloodType) }
    var drugAllergies by remember(profile.id) { mutableStateOf(profile.drugAllergies) }
    var chronicConditions by remember(profile.id) { mutableStateOf(profile.chronicConditions) }
    var bhytNumber by remember(profile.id) { mutableStateOf(profile.bhytCardNumber) }

    val bhytPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSaveBhytImage(profile, uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Thông tin y tế cơ bản (${profile.relationship})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tên hiển thị") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = birthYear,
                            onValueChange = { birthYear = it },
                            label = { Text("Năm sinh") },
                            placeholder = { Text("1965") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bloodType,
                            onValueChange = { bloodType = it },
                            label = { Text("Nhóm máu") },
                            placeholder = { Text("O+, A+, B+...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = drugAllergies,
                        onValueChange = { drugAllergies = it },
                        label = { Text("Tiền sử dị ứng thuốc / thực phẩm") },
                        placeholder = { Text("Vd: Dị ứng Penicillin, hải sản...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chronicConditions,
                        onValueChange = { chronicConditions = it },
                        label = { Text("Bệnh nền đang theo dõi") },
                        placeholder = { Text("Vd: Huyết áp cao, đau dạ dày, tiểu đường...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bhytNumber,
                        onValueChange = { bhytNumber = it },
                        label = { Text("Mã số thẻ BHYT") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSaveProfile(
                                profile.copy(
                                    name = name.ifBlank { profile.name },
                                    birthYear = birthYear.trim(),
                                    bloodType = bloodType.trim(),
                                    drugAllergies = drugAllergies.trim(),
                                    chronicConditions = chronicConditions.trim(),
                                    bhytCardNumber = bhytNumber.trim()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lưu thông tin hồ sơ")
                    }
                }
            }
        }

        // BHYT Card Image Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = TealPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Ảnh thẻ BHYT",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val bhytPath = profile.bhytImagePath?.takeIf { File(it).exists() }
                    if (bhytPath != null) {
                        AsyncImage(
                            model = File(bhytPath),
                            contentDescription = "Ảnh thẻ BHYT",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Text(
                            text = "Lưu ảnh chụp thẻ BHYT để mở nhanh mỗi khi đi khám bệnh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    FilledTonalButton(
                        onClick = {
                            bhytPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (bhytPath == null) "Chọn ảnh thẻ BHYT" else "Đổi ảnh thẻ BHYT")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onSave: (FamilyProfile) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Người thân") }
    var allergies by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm thành viên gia đình") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên gọi *") },
                    placeholder = { Text("Ví dụ: Bà Nội, Bé Bi...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Mối quan hệ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Dị ứng thuốc (nếu có)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            FamilyProfile(
                                name = name.trim(),
                                relationship = relationship.ifBlank { "Người thân" },
                                drugAllergies = allergies.trim()
                            )
                        )
                    }
                }
            ) {
                Text("Tạo hồ sơ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
