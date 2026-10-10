package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    PolicyViewerDialog(
        title = "Privacy Policy",
        subtitle = "Last updated: September 2026 • Version ${com.example.BuildConfig.VERSION_NAME}",
        icon = Icons.Default.Shield,
        iconTint = PrimaryGreen,
        iconBg = PrimaryGreenLight,
        onDismiss = onDismiss,
        testTag = "privacy_policy_dialog"
    ) {
        PolicySection(
            title = "1. Our Core Privacy Philosophy",
            content = "Reclaim is built with a strict privacy-by-design architecture. We believe your digital wellness and personal recovery journey belong exclusively to you. The app operates with maximum on-device processing to ensure your private habits, search history, and blocklists never leave your phone without your explicit consent."
        )

        PolicySection(
            title = "2. Data Processed on Your Device",
            content = "All sensitive app operations are performed locally on your device:\n" +
                    "• Blocked Apps & Custom Domain Lists: Stored strictly in your local encrypted database.\n" +
                    "• Streak Counters & Milestones: Calculated and stored on-device.\n" +
                    "• Reflection & Craving Logs: Stored locally in your app sandbox.\n" +
                    "• Time Reclaimed Metrics: Aggregated locally from on-device usage stats."
        )

        PolicySection(
            title = "3. Android Permissions & How They Are Used",
            content = "To provide effective self-control interventions, Reclaim requires specific Android system permissions:\n\n" +
                    "• Accessibility Service (BIND_ACCESSIBILITY_SERVICE):\n" +
                    "Used solely to detect foreground application package names and browser URL domains in real-time to trigger configured blocks. We NEVER record keystrokes, personal messages, banking details, or screen contents.\n\n" +
                    "• Device Administrator (BIND_DEVICE_ADMIN):\n" +
                    "Used exclusively for the optional 'Uninstall Protection' feature to prevent impulsive app uninstallation during active recovery streaks.\n\n" +
                    "• Usage Access (PACKAGE_USAGE_STATS):\n" +
                    "Used to calculate accurate daily screen time and hours reclaimed from blocked apps.\n\n" +
                    "• Notifications (POST_NOTIFICATIONS):\n" +
                    "Used to deliver streak milestone badges, motivational check-ins, and accountability window notifications."
        )

        PolicySection(
            title = "4. Optional Cloud Synchronization",
            content = "If you choose to register an account with Firebase Authentication (Email or Google Sign-In), only non-sensitive account profile metadata (your email address, display name, and streak numbers) is securely backed up. Your web browsing history is NEVER uploaded."
        )

        PolicySection(
            title = "5. No Third-Party Trackers or Data Selling",
            content = "We do NOT sell, rent, monetize, or share your personal data, logs, or analytics with any third-party advertisers, data brokers, or marketing networks. Reclaim is 100% ad-free."
        )

        PolicySection(
            title = "6. Data Retention & Deletion",
            content = "You have full control over your data. You may clear all local records at any time through the app settings or by clearing Android application data. If you have created a cloud account, you can request full account deletion directly within the settings."
        )

        PolicySection(
            title = "7. Contact & Inquiries",
            content = "If you have questions about our privacy practices, please contact us at privacy@healinglab.app."
        )
    }
}

@Composable
fun TermsAndConditionsDialog(
    onDismiss: () -> Unit
) {
    PolicyViewerDialog(
        title = "Terms & Conditions",
        subtitle = "Effective date: September 2026 • Version ${com.example.BuildConfig.VERSION_NAME}",
        icon = Icons.Default.Gavel,
        iconTint = Color(0xFF2563EB),
        iconBg = Color(0xFFEFF6FF),
        onDismiss = onDismiss,
        testTag = "terms_conditions_dialog"
    ) {
        PolicySection(
            title = "1. Acceptance of Terms",
            content = "By downloading, installing, or using Reclaim ('the Application'), you agree to be bound by these Terms and Conditions. If you do not agree to these terms, please do not install or use the Application."
        )

        PolicySection(
            title = "2. Description of Service",
            content = "Reclaim is a self-regulation, digital wellness, and habit-formation tool designed to assist users in managing application usage, resisting impulsive screen time, and tracking personal sobriety and focus milestones."
        )

        PolicySection(
            title = "3. User Responsibility & System Permissions",
            content = "You acknowledge and agree that:\n" +
                    "• You explicitly authorize the Application to monitor active foreground applications and browser URLs based on your configured blocklists.\n" +
                    "• When enabling 'Strict Mode', 'Accountability Time Windows', or 'Uninstall Protection', the Application will intentionally enforce friction and restrict configuration changes according to your specified parameters.\n" +
                    "• You are responsible for ensuring that critical emergency apps (e.g. Phone, Emergency SOS, Medical apps) remain excluded from your blocklists."
        )

        PolicySection(
            title = "4. Not Medical or Mental Health Advice",
            content = "The Application is a productivity and habit-building utility. It is NOT a medical device, mental health clinic, or psychological crisis service. If you are experiencing acute psychological distress or addiction crises, please consult licensed healthcare professionals."
        )

        PolicySection(
            title = "5. Intellectual Property",
            content = "All logos, user interface designs, custom graphics, algorithms, and source code are the intellectual property of Healing Lab / Reclaim. You may not reverse engineer, redistribute, or create unauthorized derivative works from the Application."
        )

        PolicySection(
            title = "6. Disclaimer of Warranties & Limitation of Liability",
            content = "The Application is provided on an 'AS IS' and 'AS AVAILABLE' basis without warranties of any kind. Healing Lab shall not be liable for any indirect, incidental, special, or consequential damages resulting from device configuration, third-party browser updates, or OS battery optimization behaviors."
        )

        PolicySection(
            title = "7. Modifications to Terms",
            content = "We reserve the right to modify these Terms at any time. Continued use of the Application following any updates signifies your acceptance of the revised Terms."
        )

        PolicySection(
            title = "8. Contact Information",
            content = "For support, feedback, or legal inquiries regarding these Terms, contact support@healinglab.app."
        )
    }
}

@Composable
private fun PolicyViewerDialog(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onDismiss: () -> Unit,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag(testTag),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                letterSpacing = (-0.3).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = MutedText
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = DarkText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // Footer Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text(
                            text = "I Understand & Agree",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    content: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 13.sp,
            color = Color(0xFF334155),
            lineHeight = 19.sp
        )
    }
}

/**
 * Clean, minimal Google Login required popup for Partner Email adding.
 * Shows ONLY the Google Login button and underlined Terms & Conditions label (no extra message).
 */
@Composable
fun PartnerGoogleLoginDialog(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onGoogleSignInClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var showTermsDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("partner_google_login_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            tonalElevation = 4.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    onClick = onGoogleSignInClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("partner_google_login_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryGreen
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Signing in...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4285F4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "Terms and Conditions",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MutedText,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showTermsDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("partner_login_terms_link")
                )
            }
        }
    }

    if (showTermsDialog) {
        TermsAndConditionsDialog(
            onDismiss = { showTermsDialog = false }
        )
    }
}

@Composable
fun NoInternetWarningDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("no_internet_warning_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF2F2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "No Internet",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "No Internet Available",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = MutedText,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dismiss_no_internet_button")
            ) {
                Text("OK", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

