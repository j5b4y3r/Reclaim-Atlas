package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.PrimaryGreenPill
import com.example.ui.theme.SubtleText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Modern bottom sheet to configure Uninstall & Tamper Protection timerange / lock duration
 * with 24 Hours vs Custom Days, agreement rules checkmark, and seamless Device Admin activation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallProtectionConfigBottomSheet(
    isDeviceAdminActive: Boolean,
    initialDays: Int = 1,
    onConfirm: (durationDays: Int) -> Unit,
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
        UninstallProtectionConfigContent(
            isDeviceAdminActive = isDeviceAdminActive,
            initialDays = initialDays,
            onConfirm = onConfirm,
            onCancel = onDismiss
        )
    }
}

@Composable
fun UninstallProtectionConfigContent(
    isDeviceAdminActive: Boolean,
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
            .padding(horizontal = 24.dp, vertical = 24.dp)
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

        Spacer(modifier = Modifier.height(20.dp))

        // Header with Shield Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Activate Uninstall Protection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Lock down protection against impulsive bypasses",
                    fontSize = 12.sp,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Duration / Time Range Selection Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Lock Duration",
                        fontSize = 17.sp,
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

                Spacer(modifier = Modifier.height(14.dp))

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
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText
                                )
                                Text(
                                    text = "Standard 1-day lock timerange",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                            .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "Choose multi-day lock commitment",
                                        fontSize = 12.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }

                        if (!isOneDaySelected) {
                            Spacer(modifier = Modifier.height(14.dp))

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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Stepper Control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF3F4F6))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                    fontSize = 16.sp,
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

                Spacer(modifier = Modifier.height(14.dp))

                // Calculated Date Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(20.dp)
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // What this protects info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = DarkText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How Uninstall Protection Works",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DarkText
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 12.sp)
                    Text(
                        text = "Enforces Android Device Administrator so Reclaim cannot be uninstalled or deleted.",
                        fontSize = 12.sp,
                        color = MutedText,
                        lineHeight = 16.sp
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 12.sp)
                    Text(
                        text = "Blocks access to system App Info uninstall and force stop pages.",
                        fontSize = 12.sp,
                        color = MutedText,
                        lineHeight = 16.sp
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 12.sp)
                    Text(
                        text = "Can only be turned off during your scheduled Accountability Unlock Window or when approved by your partner.",
                        fontSize = 12.sp,
                        color = MutedText,
                        lineHeight = 16.sp
                    )
                }

                if (isDeviceAdminActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDCFCE7))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓ Device Administrator permission is active. Tamper protection will lock immediately upon activation.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF166534),
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Note: Device Admin permission will be requested first before protection can be locked.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Agreement Checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { agreedToTerms = !agreedToTerms }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen),
                modifier = Modifier.testTag("agree_terms_checkbox")
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "I understand that once activated, Uninstall Protection is locked until the duration expires, during my scheduled Accountability Unlock Window, or via Partner Email Approval.",
                fontSize = 12.sp,
                color = DarkText,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkText)
            ) {
                Text("CANCEL", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                    .height(50.dp)
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
