package com.medlens.ai.ui.screens

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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medlens.ai.data.DietPlanCatalog
import com.medlens.ai.data.FamilyProfile
import com.medlens.ai.data.MedicalRecord
import com.medlens.ai.data.MedicationReminder
import com.medlens.ai.data.PatientDietPlan
import com.medlens.ai.ui.components.MedicalDisclaimerCard
import com.medlens.ai.ui.theme.AlertRed
import com.medlens.ai.ui.theme.AlertRedBg
import com.medlens.ai.ui.theme.InfoBlue
import com.medlens.ai.ui.theme.InfoBlueBg
import com.medlens.ai.ui.theme.MutedText
import com.medlens.ai.ui.theme.NormalGreen
import com.medlens.ai.ui.theme.NormalGreenBg
import com.medlens.ai.ui.theme.TealDark
import com.medlens.ai.ui.theme.TealLightContainer
import com.medlens.ai.ui.theme.TealPrimary
import com.medlens.ai.ui.theme.WarningAmber
import com.medlens.ai.ui.theme.WarningAmberBg

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreAndDietScreen(
    activeProfile: FamilyProfile,
    profileRecords: List<MedicalRecord>,
    activeReminders: List<MedicationReminder>,
    savedAiDietPlan: PatientDietPlan?,
    isGeneratingDiet: Boolean,
    hasAiReady: Boolean,
    onGenerateAiDiet: (conditionKey: String, conditionLabel: String, extraPreference: String) -> Unit,
    onOpenMedicationManager: () -> Unit,
    onOpenProfileInfo: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val autoKey = remember(activeProfile.id, activeProfile.chronicConditions, profileRecords.size, activeReminders.size) {
        DietPlanCatalog.detectSuggestedKey(activeProfile, profileRecords, activeReminders)
    }
    var selectedConditionKey by remember(activeProfile.id, autoKey) { mutableStateOf(autoKey) }
    var extraPreference by remember(activeProfile.id) { mutableStateOf("") }

    val selectedPreset = remember(selectedConditionKey) {
        DietPlanCatalog.presetForKey(selectedConditionKey)
    }

    // Show saved AI plan if it matches the selected condition, otherwise show curated offline medical diet immediately
    val displayedPlan = remember(savedAiDietPlan, selectedPreset) {
        if (savedAiDietPlan != null && savedAiDietPlan.conditionKey == selectedPreset.key) {
            savedAiDietPlan
        } else {
            selectedPreset.defaultPlan
        }
    }

    val bmi = activeProfile.calculatedBmi()
    val age = activeProfile.calculatedAge()
    val hasBasicVitals = age != null || activeProfile.heightCm.isNotBlank() || activeProfile.weightKg.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header with Settings button on top-right
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thêm • Ăn kiêng người bệnh",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Hồ sơ đang chọn: ${activeProfile.name} (${activeProfile.relationship})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt",
                        tint = TealPrimary
                    )
                }
            }
        }

        // Quick Tools Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenMedicationManager() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Lịch thuốc", style = MaterialTheme.typography.titleSmall)
                            Text("${activeReminders.size} thuốc", style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                    }
                }
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenSettings() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Cài đặt", style = MaterialTheme.typography.titleSmall)
                            Text("Tele, Cloud, OTA", style = MaterialTheme.typography.bodySmall, color = MutedText)
                        }
                    }
                }
            }
        }

        // Hero Banner: Diet & Nutrition for Patients + Profile Vitals Snapshot
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(TealPrimary, TealDark)))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.RestaurantMenu,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Chế độ ăn kiêng cho người bệnh",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (hasBasicVitals) {
                                buildString {
                                    append("Thể trạng ${activeProfile.name}: ")
                                    val stats = listOfNotNull(
                                        age?.let { "$it tuổi" },
                                        activeProfile.gender.takeIf { it.isNotBlank() },
                                        activeProfile.heightCm.takeIf { it.isNotBlank() }?.let { "${it}cm" },
                                        activeProfile.weightKg.takeIf { it.isNotBlank() }?.let { "${it}kg" },
                                        bmi?.let { "BMI %.1f (%s)".format(it, activeProfile.bmiCategoryVi().orEmpty()) }
                                    )
                                    append(stats.joinToString(" • "))
                                }
                            } else {
                                "Gợi ý thực phẩm nên ăn, cần kiêng và thực đơn thuần Việt theo bệnh lý & đơn thuốc đang uống."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.93f)
                        )
                        if (!hasBasicVitals) {
                            FilledTonalButton(
                                onClick = onOpenProfileInfo,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cập nhật chiều cao, cân nặng, ngày sinh")
                            }
                        }
                    }
                }
            }
        }

        // Condition Selector Chips
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Chọn nhóm bệnh lý / mục tiêu ăn kiêng",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Hệ thống tự gợi ý theo đơn thuốc & bệnh nền của ${activeProfile.name}, hoặc bạn chọn thêm bên dưới:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DietPlanCatalog.presets.forEach { preset ->
                            FilterChip(
                                selected = selectedConditionKey == preset.key,
                                onClick = { selectedConditionKey = preset.key },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = extraPreference,
                        onValueChange = { extraPreference = it },
                        label = { Text("Khẩu vị / lưu ý thêm (tùy chọn)") },
                        placeholder = { Text("Vd: Không ăn hải sản, ăn nhạt, thích canh rau...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onGenerateAiDiet(
                                selectedPreset.key,
                                selectedPreset.label,
                                extraPreference
                            )
                        },
                        enabled = !isGeneratingDiet && hasAiReady,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (isGeneratingDiet) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Gemini AI đang lập thực đơn riêng...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tạo thực đơn AI riêng cho ${activeProfile.name}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Diet Plan Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TealLightContainer.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayedPlan.conditionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = TealDark,
                            modifier = Modifier.weight(1f)
                        )
                        if (displayedPlan.generatedByAi) {
                            Surface(color = TealPrimary, shape = RoundedCornerShape(50)) {
                                Text(
                                    text = "AI Cá nhân hóa",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (displayedPlan.summary.isNotBlank()) {
                        Text(
                            text = displayedPlan.summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Should Eat Card (Green)
        if (displayedPlan.shouldEat.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NormalGreenBg.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, NormalGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NormalGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Nên ăn & Ưu tiên mỗi ngày",
                                style = MaterialTheme.typography.titleMedium,
                                color = NormalGreen
                            )
                        }
                        displayedPlan.shouldEat.forEach { itemText ->
                            Text(text = "• $itemText", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Should Avoid Card (Red)
        if (displayedPlan.shouldAvoid.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertRedBg.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = AlertRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Cần kiêng / Hạn chế tối đa",
                                style = MaterialTheme.typography.titleMedium,
                                color = AlertRed
                            )
                        }
                        displayedPlan.shouldAvoid.forEach { itemText ->
                            Text(
                                text = "• $itemText",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
            }
        }

        // Medication-Food Interaction Notes (Amber)
        if (displayedPlan.medicationFoodNotes.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningAmberBg.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Lưu ý ăn uống với thuốc đang dùng",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF92400E)
                            )
                        }
                        displayedPlan.medicationFoodNotes.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }
            }
        }

        // Sample 1-Day Menu (Blue/Surface)
        if (displayedPlan.sampleDayMenu.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, InfoBlue.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Gợi ý thực đơn 1 ngày dễ nấu",
                                style = MaterialTheme.typography.titleMedium,
                                color = InfoBlue
                            )
                        }
                        displayedPlan.sampleDayMenu.forEach { meal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(InfoBlueBg.copy(alpha = 0.45f))
                                    .padding(10.dp)
                            ) {
                                Text(text = meal, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            MedicalDisclaimerCard(
                "Lưu ý dinh dưỡng: Chế độ ăn kiêng trên mang tính tham khảo hỗ trợ điều trị. Người bệnh suy thận nặng, tiểu đường tiêm Insulin hoặc có bệnh lý đặc biệt cần tuân thủ thực đơn chi tiết của bác sĩ dinh dưỡng."
            )
        }
    }
}
