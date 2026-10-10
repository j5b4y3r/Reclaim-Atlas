package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.AccountabilityLockStatus
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.SubtleText

@Composable
fun AccountabilityLockDialog(
    status: AccountabilityLockStatus,
    hasAlreadySentEmailForThisItem: Boolean = false,
    onRequestPartnerUnlock: (() -> Unit)? = null,
    onConfigurePartnerEmail: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val actionTitle = status.lockedTarget?.actionTitle ?: "Modify Protected Setting"

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("accountability_lock_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (hasAlreadySentEmailForThisItem) Color(0xFFEFF6FF) else Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasAlreadySentEmailForThisItem) Icons.Default.MarkEmailRead else Icons.Default.Lock,
                    contentDescription = "Accountability Lock",
                    tint = if (hasAlreadySentEmailForThisItem) Color(0xFF2563EB) else Color(0xFFD97706),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accountability Lock Active",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(4.dp))
                InfoHelpIconButton(
                    title = "Why Is This Setting Locked?",
                    stepsOrBullets = listOf(
                        "Partner Accountability: When Accountability Mode is active, disabling or deleting blocks is protected 24/7.",
                        "1-Time Partner Unlock: To unlock \"$actionTitle\", send a 1-time verification email to your accountability partner.",
                        "1 Code = 1 Item Only: Your partner receives a 6-digit code that unlocks ONLY \"$actionTitle\" once — all other blocks stay active.",
                        "No Email Spam: Once an email is sent for an item, you cannot send another email for that item until it is unlocked and blocked again."
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Specific item being unlocked
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LOCKED ITEM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SubtleText
                        )
                        Text(
                            text = actionTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }
                }

                if (hasAlreadySentEmailForThisItem) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Email Already Sent for This Item",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = "Tap 'Enter 6-Digit Partner Code' below to unlock this item using the code sent to your partner.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1D4ED8),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Unlocking requires your partner to approve by sharing the 6-digit code sent to their email.",
                                fontSize = 12.sp,
                                color = DarkText,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onRequestPartnerUnlock != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onRequestPartnerUnlock()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_partner_unlock_button")
                    ) {
                        Icon(
                            imageVector = if (hasAlreadySentEmailForThisItem) Icons.Default.Key else Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF2563EB)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasAlreadySentEmailForThisItem) {
                                "Already Email Sent • Enter 6-Digit Code"
                            } else {
                                "Unlock This Item via Partner Email"
                            },
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                } else if (onConfigurePartnerEmail != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onConfigurePartnerEmail()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF2563EB)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set Partner Email for Emergency Unlock",
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dismiss_accountability_dialog_button")
                ) {
                    Text(
                        text = "Keep Locked • Stay Focused",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun AccountabilityRequiredDialog(
    actionName: String,
    onNavigateToAccountability: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("accountability_required_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Accountability Required",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Enable Accountability First",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Accountability Mode is currently OFF. To enable $actionName and protect yourself against impulsive overrides, please turn on Accountability and set your daily window first.",
                    fontSize = 14.sp,
                    color = MutedText,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Why Accountability?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            text = "Accountability binds your rules and prevents impulsive disabling outside your daily unlock window.",
                            fontSize = 12.sp,
                            color = MutedText,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onNavigateToAccountability()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("setup_accountability_button")
            ) {
                Text("Turn On Accountability", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = SubtleText)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun UninstallProtectionDeviceAdminDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("device_admin_info_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF2F2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Device Admin",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Device Admin Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Uninstall Protection requires Android Device Administrator permission so Reclaim cannot be uninstalled or removed during moments of impulse.",
                    fontSize = 14.sp,
                    color = MutedText,
                    lineHeight = 20.sp
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "What to do to turn on:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("1. ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryGreen)
                            Text("Tap 'Open Device Admin' below", fontSize = 12.sp, color = DarkText)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("2. ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryGreen)
                            Text("Select 'Reclaim' in Device admin apps", fontSize = 12.sp, color = DarkText)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("3. ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryGreen)
                            Text("Tap 'Activate this device admin app'", fontSize = 12.sp, color = DarkText)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("4. ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryGreen)
                            Text("Return here to turn on Uninstall Protection", fontSize = 12.sp, color = DarkText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("open_device_admin_settings_button")
            ) {
                Text("Open Device Admin Settings", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = SubtleText)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}
