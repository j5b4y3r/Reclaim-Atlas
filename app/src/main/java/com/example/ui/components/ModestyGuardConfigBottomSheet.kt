package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.vision.ModestySensitivity
import com.example.domain.vision.ModestyShieldStyle
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModestyGuardConfigBottomSheet(
    initialScanFps: Int = 3,
    initialShieldStyle: String = "TARGETED_BLUR",
    initialBlurIntensity: Int = 80,
    initialSensitivity: String = "STRICT_AWRAH",
    onConfirm: (scanFps: Int, shieldStyle: String, blurIntensity: Int, sensitivity: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        ModestyGuardConfigContent(
            initialScanFps = initialScanFps,
            initialShieldStyle = initialShieldStyle,
            initialBlurIntensity = initialBlurIntensity,
            initialSensitivity = initialSensitivity,
            onConfirm = onConfirm,
            onCancel = onDismiss
        )
    }
}

@Composable
fun ModestyGuardConfigContent(
    initialScanFps: Int,
    initialShieldStyle: String,
    initialBlurIntensity: Int,
    initialSensitivity: String,
    onConfirm: (scanFps: Int, shieldStyle: String, blurIntensity: Int, sensitivity: String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedShieldStyle by remember {
        mutableStateOf(ModestyShieldStyle.fromString(initialShieldStyle))
    }
    var blurIntensity by remember {
        mutableFloatStateOf(initialBlurIntensity.coerceIn(25, 100).toFloat())
    }
    var selectedSensitivity by remember {
        mutableStateOf(ModestySensitivity.fromString(initialSensitivity))
    }
    var selectedFps by remember {
        mutableIntStateOf(if (initialScanFps in listOf(1, 3, 5)) initialScanFps else 3)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("modesty_guard_config_sheet")
    ) {
        // Drag Indicator bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE2E8F0))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0FDF4)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "AI Modesty Guard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = "4th Layer on-device vision protection",
                    fontSize = 13.sp,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spiritual Reminder Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "“Tell the believing men to lower their gaze and protect their private parts; that is purer for them.”",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = DarkText,
                    lineHeight = 17.sp
                )
                Text(
                    text = "— Surah An-Nur 24:30",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 1: Shielding Style
        Text(
            text = "Protection Overlay Style",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Text(
            text = "Choose how detected revealing models and reels are concealed",
            fontSize = 12.sp,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Style Option 1: Targeted Blur
        StyleSelectionCard(
            title = "Targeted Blur over Offending Content",
            description = "Blurs out only the detected exposed body area on screen",
            isSelected = selectedShieldStyle == ModestyShieldStyle.TARGETED_BLUR,
            onClick = { selectedShieldStyle = ModestyShieldStyle.TARGETED_BLUR }
        )

        if (selectedShieldStyle == ModestyShieldStyle.TARGETED_BLUR) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blur Strength",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkText
                        )
                        Text(
                            text = "${blurIntensity.toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                    }
                    Slider(
                        value = blurIntensity,
                        onValueChange = { blurIntensity = it },
                        valueRange = 25f..100f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryGreen,
                            activeTrackColor = PrimaryGreen,
                            inactiveTrackColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.testTag("blur_intensity_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Style Option 2: Targeted Blackout
        StyleSelectionCard(
            title = "Targeted Solid Blackout Shield",
            description = "Draws an opaque black concealment box directly over the model's body",
            isSelected = selectedShieldStyle == ModestyShieldStyle.BLACK_OVERLAY,
            onClick = { selectedShieldStyle = ModestyShieldStyle.BLACK_OVERLAY }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Style Option 3: Full Screen Shield
        StyleSelectionCard(
            title = "Full-Screen 'Lower Your Gaze' Barrier",
            description = "Dims the entire screen with a spiritual prompt and one-tap exit home",
            isSelected = selectedShieldStyle == ModestyShieldStyle.FULL_SCREEN,
            onClick = { selectedShieldStyle = ModestyShieldStyle.FULL_SCREEN }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Sensitivity
        Text(
            text = "Detection Strictness",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Text(
            text = "Threshold for flagging revealing models, dancing reels, and attire",
            fontSize = 12.sp,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensitivityChoiceCard(
                title = "Strict Awrah",
                subtitle = "Cleavage, midriff, thighs, bikini, dance reels",
                isSelected = selectedSensitivity == ModestySensitivity.STRICT_AWRAH,
                modifier = Modifier.weight(1f),
                onClick = { selectedSensitivity = ModestySensitivity.STRICT_AWRAH }
            )

            SensitivityChoiceCard(
                title = "Moderate",
                subtitle = "Swimwear, lingerie, explicit exposure only",
                isSelected = selectedSensitivity == ModestySensitivity.MODERATE,
                modifier = Modifier.weight(1f),
                onClick = { selectedSensitivity = ModestySensitivity.MODERATE }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Scan Speed / FPS
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Media Scan Rate",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
        }
        Text(
            text = "Only active when video or images are on screen; 0 FPS when reading text",
            fontSize = 12.sp,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FpsOptionChip(
                label = "3 FPS (Balanced)",
                subtitle = "Recommended",
                isSelected = selectedFps == 3,
                modifier = Modifier.weight(1f),
                onClick = { selectedFps = 3 }
            )
            FpsOptionChip(
                label = "5 FPS (Fluid)",
                subtitle = "Fast scrolling",
                isSelected = selectedFps == 5,
                modifier = Modifier.weight(1f),
                onClick = { selectedFps = 5 }
            )
            FpsOptionChip(
                label = "1 FPS (Eco)",
                subtitle = "Battery saver",
                isSelected = selectedFps == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedFps = 1 }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // On-device transparency card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "100% on-device AI. Zero network upload. Zero battery drain when reading text.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF166534),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Button(
            onClick = {
                onConfirm(
                    selectedFps,
                    selectedShieldStyle.name,
                    blurIntensity.toInt(),
                    selectedSensitivity.name
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("activate_modesty_guard_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text(
                text = "Activate AI Modesty Guard",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cancel",
                fontSize = 14.sp,
                color = MutedText
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun StyleSelectionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) PrimaryGreen else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MutedText,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SensitivityChoiceCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) PrimaryGreen else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MutedText,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun FpsOptionChip(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
            .border(
                1.5.dp,
                if (isSelected) PrimaryGreen else Color(0xFFE2E8F0),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) PrimaryGreen else DarkText
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MutedText
            )
        }
    }
}
