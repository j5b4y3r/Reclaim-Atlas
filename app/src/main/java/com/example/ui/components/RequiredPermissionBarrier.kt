package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.AppBackground
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.SubtleText

@Composable
fun RequiredPermissionBarrier(
    isAccessibilityGranted: Boolean,
    isUsageStatsGranted: Boolean,
    isNotificationGranted: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onRecheck: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 24.dp)
            .testTag("required_permission_barrier"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(44.dp)
                )
            }

            // Titles
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Permission Required",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Reclaim requires Accessibility Service to be enabled in order to function. Without this permission, the app cannot block distractions or protect your device.",
                    fontSize = 14.sp,
                    color = MutedText,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Permissions Status List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PermissionStatusRow(
                        title = "Accessibility Service",
                        badge = "REQUIRED",
                        description = "Enforces app & keyword blocks and prevents bypasses",
                        isGranted = isAccessibilityGranted,
                        icon = Icons.Default.AccessibilityNew,
                        isMandatory = true
                    )

                    PermissionStatusRow(
                        title = "Usage Access",
                        badge = "RECOMMENDED",
                        description = "Measures screen time and focus statistics",
                        isGranted = isUsageStatsGranted,
                        icon = Icons.Default.QueryStats,
                        isMandatory = false
                    )

                    PermissionStatusRow(
                        title = "Notifications",
                        badge = "RECOMMENDED",
                        description = "Delivers urge reminders and daily check-ins",
                        isGranted = isNotificationGranted,
                        icon = Icons.Default.Notifications,
                        isMandatory = false
                    )
                }
            }

            // Step-by-step guidance
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "How to enable Accessibility:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text("1. Tap 'Enable Accessibility Service' below.", fontSize = 12.sp, color = MutedText)
                    Text("2. Find 'Reclaim' in Downloaded Services.", fontSize = 12.sp, color = MutedText)
                    Text("3. Switch the toggle to ON and tap Allow.", fontSize = 12.sp, color = MutedText)
                    Text("4. Return here to start using the app.", fontSize = 12.sp, color = MutedText)
                }
            }

            // Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenAccessibility,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("enable_accessibility_button")
                ) {
                    Icon(imageVector = Icons.Default.AccessibilityNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Accessibility Service",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isUsageStatsGranted) {
                    OutlinedButton(
                        onClick = onOpenUsageAccess,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("enable_usage_access_button")
                    ) {
                        Text(
                            text = "Grant Usage Access (Optional)",
                            fontSize = 13.sp,
                            color = DarkText
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRecheck,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("recheck_permissions_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = DarkText)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check Permission Status",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkText
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    badge: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    isMandatory: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isGranted) Color(0xFFDCFCE7) else if (isMandatory) Color(0xFFFEE2E2) else Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) PrimaryGreen else if (isMandatory) Color(0xFFDC2626) else SubtleText,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isMandatory) Color(0xFFFEE2E2) else Color(0xFFF3F4F6))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMandatory) Color(0xFFDC2626) else SubtleText
                    )
                }
            }
            Text(
                text = description,
                fontSize = 11.sp,
                color = MutedText,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = if (isGranted) "Granted" else "Missing",
            tint = if (isGranted) PrimaryGreen else if (isMandatory) Color(0xFFDC2626) else SubtleText,
            modifier = Modifier.size(20.dp)
        )
    }
}
