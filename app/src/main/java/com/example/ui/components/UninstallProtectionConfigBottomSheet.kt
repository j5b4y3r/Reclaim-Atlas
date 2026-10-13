package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.ui.theme.AppBackground
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * In-window bottom sheet overlay to configure Lock Duration (24 Hours vs Custom Days).
 * Rendered directly inside MainActivity's Compose hierarchy (without spawning a separate OS DialogWindow)
 * and stripped of bulky texts so Samsung One UI / Android 13 Accessibility never confuses it with Settings.
 */
@Composable
fun UninstallProtectionConfigBottomSheet(
    isDeviceAdminActive: Boolean,
    initialDays: Int = 1,
    onConfirm: (durationDays: Int) -> Unit,
    onDismiss: () -> Unit
) {
    DisposableEffect(Unit) {
        MainActivity.isUninstallPopupVisible = true
        onDispose {
            MainActivity.isUninstallPopupVisible = false
        }
    }

    BackHandler(onBack = onDismiss)

    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = sheetInteraction,
                    indication = null,
                    onClick = {}
                ),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 16.dp
        ) {
            UninstallProtectionConfigContent(
                isDeviceAdminActive = isDeviceAdminActive,
                initialDays = initialDays,
                onConfirm = onConfirm,
                onCancel = onDismiss
            )
        }
    }
}

@Composable
fun UninstallProtectionConfigContent(
    @Suppress("UNUSED_PARAMETER") isDeviceAdminActive: Boolean,
    initialDays: Int = 1,
    onConfirm: (durationDays: Int) -> Unit,
    onCancel: () -> Unit
) {
    var isOneDaySelected by remember { mutableStateOf(initialDays <= 1) }
    var customDays by remember { mutableIntStateOf(if (initialDays > 1) initialDays else 7) }
    var agreedToTerms by remember { mutableStateOf(false) }

    val effectiveDays = if (isOneDaySelected) 1 else customDays
    val expiryDate = remember(effectiveDays) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, effectiveDays)
        cal.time
    }

    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy 'at' hh:mm a", Locale.getDefault()) }
    val formattedExpiry = remember(expiryDate) { dateFormat.format(expiryDate) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("uninstall_protection_config_sheet")
    ) {
        // Drag Indicator bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE2E8F0))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Compact Header with Shield Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Configure Lock Timer",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Select how long this commitment stays active",
                    fontSize = 12.sp,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Duration / Time Range Selection Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Lock Duration",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isOneDaySelected) "24 Hours" else "$customDays Days",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: 24 Hours (1 Day)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isOneDaySelected = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOneDaySelected) Color.White else Color(0xFFF9FAFB)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (isOneDaySelected) PrimaryGreen else Color(0xFFE5E7EB)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOneDaySelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isOneDaySelected) PrimaryGreen else SubtleText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "24 Hours (1 Day)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Standard 1-day commitment",
                                    fontSize = 11.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Custom Days
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isOneDaySelected = false },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isOneDaySelected) Color.White else Color(0xFFF9FAFB)
                    ),
                    border = BorderStroke(
                        1.5.dp,
                        if (!isOneDaySelected) PrimaryGreen else Color(0xFFE5E7EB)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (!isOneDaySelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (!isOneDaySelected) PrimaryGreen else SubtleText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Custom Duration (in Days)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "Choose multi-day commitment",
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }

                        if (!isOneDaySelected) {
                            Spacer(modifier = Modifier.height(12.dp))

                            // Preset Pills
                            val presets = listOf(3, 7, 14, 30)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presets.forEach { days ->
                                    val isCurrentPreset = customDays == days
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCurrentPreset) PrimaryGreen else Color(0xFFF3F4F6))
                                            .clickable { customDays = days }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$days d",
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrentPreset) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrentPreset) Color.White else DarkText
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Stepper Control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { if (customDays > 1) customDays-- },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease Days",
                                        tint = if (customDays > 1) DarkText else SubtleText
                                    )
                                }

                                Text(
                                    text = "$customDays Days",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )

                                IconButton(
                                    onClick = { if (customDays < 90) customDays++ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase Days",
                                        tint = if (customDays < 90) DarkText else SubtleText
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Calculated Date Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Active Lock Until:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryGreen
                            )
                            Text(
                                text = formattedExpiry,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Concise Agreement Checkbox (zero trigger keywords)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { agreedToTerms = !agreedToTerms }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen),
                modifier = Modifier.testTag("agree_terms_checkbox")
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "I understand this lock stays active until the timer finishes or is unlocked during my scheduled window.",
                fontSize = 12.sp,
                color = DarkText,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkText)
            ) {
                Text("CLOSE", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Button(
                onClick = {
                    if (agreedToTerms) {
                        onConfirm(effectiveDays)
                    }
                },
                enabled = agreedToTerms,
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .testTag("confirm_activate_uninstall_protection_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    disabledContainerColor = PrimaryGreen.copy(alpha = 0.4f)
                )
            ) {
                Text("ACTIVATE ($effectiveDays ${if (effectiveDays == 1) "DAY" else "DAYS"})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
