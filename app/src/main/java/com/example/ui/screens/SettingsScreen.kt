package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.SyncState
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.TermsAndConditionsDialog
import com.example.ui.theme.reclaimTextFieldColors
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGoldLight
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToAccountability: () -> Unit = {},
    onNavigateToCustomizeBlockScreen: () -> Unit = {}
) {
    val customMessage by viewModel.customBlockedMessage.collectAsState()
    val redirectUrl by viewModel.redirectUrl.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMessageDialog by remember { mutableStateOf(false) }
    var editMessage by remember { mutableStateOf(customMessage) }

    var showRedirectDialog by remember { mutableStateOf(false) }
    var editRedirect by remember { mutableStateOf(redirectUrl) }

    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var authErrorText by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("settings_screen")
    ) {
        // Header
        Text(
            text = "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Section: "Account & Cloud Sync" (Firebase)
        Text(
            text = "Cloud Sync & Account",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            letterSpacing = (-0.2).sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (currentUser != null) PrimaryGreenLight else Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentUser != null) Icons.Default.Person else Icons.Default.CloudSync,
                            contentDescription = "User",
                            tint = if (currentUser != null) PrimaryGreen else SubtleText,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentUser != null) {
                                    currentUser?.displayName ?: (currentUser?.email ?: "Reclaimer")
                                } else {
                                    "Offline Mode (Local Storage)"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            if (currentUser != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (currentUser?.authProvider == "google") Color(0xFFEBF5FF) else PrimaryGreenLight
                                ) {
                                    Text(
                                        text = if (currentUser?.authProvider == "google") "Google" else if (currentUser?.isAnonymous == true) "Guest" else "Firebase",
                                        color = if (currentUser?.authProvider == "google") Color(0xFF2563EB) else PrimaryGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentUser != null) {
                                currentUser?.email ?: "Synced with Firebase Firestore"
                            } else {
                                "Sign in to backup streaks and recovery data to cloud"
                            },
                            fontSize = 12.sp,
                            color = MutedText
                        )
                    }
                }

                // Sync status indicator
                when (syncState) {
                    is SyncState.Syncing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Syncing with Firestore...",
                                fontSize = 12.sp,
                                color = PrimaryGreen
                            )
                        }
                    }
                    is SyncState.Success -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = PrimaryGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (syncState as SyncState.Success).lastSyncedTimeText,
                                fontSize = 12.sp,
                                color = PrimaryGreen
                            )
                        }
                    }
                    is SyncState.Error -> {
                        Text(
                            text = (syncState as SyncState.Error).message,
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                    else -> {}
                }

                // Action buttons
                if (currentUser == null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Prominent "Continue with Google" (Google Auth only)
                        Surface(
                            onClick = {
                                isAuthLoading = true
                                authErrorText = null
                                viewModel.signInWithGoogle(context) { success, err ->
                                    isAuthLoading = false
                                    if (!success) {
                                        authErrorText = err
                                    }
                                }
                            },
                            enabled = !isAuthLoading,
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isAuthLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = PrimaryGreen
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Signing in with Google...", fontSize = 13.sp, color = DarkText)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4285F4)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("G", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Sign in with Google",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                        }

                        if (authErrorText != null) {
                            Text(
                                text = authErrorText ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = "Authenticate with your Google Account to back up block settings, accountability, and streak data to cloud.",
                            fontSize = 11.sp,
                            color = MutedText,
                            lineHeight = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncDataToCloud() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Cloud", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.restoreDataFromCloud() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore", fontSize = 13.sp, color = DarkText)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = { viewModel.signOut() }
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = DarkText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", color = DarkText, fontSize = 13.sp)
                        }

                        TextButton(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier.testTag("delete_account_button")
                        ) {
                            Text("Delete Account & Data", color = Color(0xFFDC2626), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Section: "App Configuration"
        Text(
            text = "App Configuration",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            letterSpacing = (-0.2).sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val accountabilityEnabled by viewModel.accountabilityEnabled.collectAsState()

                SettingsListItem(
                    icon = Icons.Default.Lock,
                    title = "Accountability Options",
                    subtitle = if (accountabilityEnabled) {
                        "Protected by partner • Unlock via 6-digit email code"
                    } else {
                        "Require partner email verification code to unblock"
                    },
                    onClick = onNavigateToAccountability
                )
                val bgType by viewModel.blockScreenBgType.collectAsState()
                SettingsListItem(
                    icon = Icons.Default.Movie,
                    title = "Customize Block Screen & Motivation",
                    subtitle = when (bgType) {
                        "VIDEO" -> "Motivation Video active (looping with sound) • Tap to edit"
                        "IMAGE" -> "Custom motivation image active • Tap to edit"
                        else -> "Set motivation video, background image, clean texts & layout"
                    },
                    onClick = onNavigateToCustomizeBlockScreen
                )
                SettingsListItem(
                    icon = Icons.Default.Lock,
                    title = "Intervention message",
                    subtitle = customMessage,
                    onClick = {
                        editMessage = customMessage
                        showMessageDialog = true
                    }
                )
                SettingsListItem(
                    icon = Icons.Default.Security,
                    title = "Productive redirect URL",
                    subtitle = if (redirectUrl.isBlank()) "Default (Browser initial / Home page)" else redirectUrl,
                    onClick = {
                        editRedirect = redirectUrl
                        showRedirectDialog = true
                    }
                )
                SettingsListItem(
                    icon = Icons.Default.Security,
                    title = "Appear on top (Overlay)",
                    subtitle = if (com.example.util.PermissionHelper.canDrawOverlays(context)) {
                        "Permission active • Ensures block screen stays on top"
                    } else {
                        "Tap to allow • Prevents block screen from auto-minimizing"
                    },
                    onClick = {
                        com.example.util.PermissionHelper.openOverlaySettings(context)
                    }
                )
                SettingsListItem(
                    icon = Icons.Default.CloudSync,
                    title = "Check for Updates",
                    subtitle = "v${com.example.BuildConfig.VERSION_NAME} • Google Play Store",
                    onClick = {
                        try {
                            val marketIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=${context.packageName}")
                            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(marketIntent)
                        } catch (_: Exception) {
                            try {
                                val webIntent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(webIntent)
                            } catch (_: Exception) {}
                        }
                    }
                )
                SettingsListItem(
                    icon = Icons.Default.Info,
                    title = "About Reclaim",
                    subtitle = "Version ${com.example.BuildConfig.VERSION_NAME} • Digital Self-Control",
                    onClick = { showAboutDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Section: "Privacy & Legal Policies"
        Text(
            text = "Privacy & Legal Policies",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            letterSpacing = (-0.2).sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsListItem(
                    icon = Icons.Default.Shield,
                    title = "Privacy Policy",
                    subtitle = "100% on-device processing • Permissions & data safety",
                    onClick = { showPrivacyPolicyDialog = true }
                )
                SettingsListItem(
                    icon = Icons.Default.Gavel,
                    title = "Terms & Conditions",
                    subtitle = "Terms of service, user agreement & liability guidelines",
                    onClick = { showTermsDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Reclaim", fontWeight = FontWeight.Bold, color = DarkText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reclaim — Digital Self-Control (v${com.example.BuildConfig.VERSION_NAME})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryGreen
                    )
                    Text(
                        text = "Reclaim helps you break impulsive digital habits with on-device app & website blocking, short-form video guards, scheduled focus sessions, and time-locked accountability.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MutedText
                    )
                    Text(
                        text = "Support: support@healinglab.app",
                        fontSize = 12.sp,
                        color = SubtleText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Close")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account & Cloud Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete your cloud account, synced backups, and local session history. This action cannot be undone.",
                    fontSize = 14.sp,
                    color = MutedText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccountAndData {
                            showDeleteAccountDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            }
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }

    if (showTermsDialog) {
        TermsAndConditionsDialog(
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showMessageDialog) {
        AlertDialog(
            onDismissRequest = { showMessageDialog = false },
            title = { Text("Blocked Screen Message", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editMessage,
                    onValueChange = { editMessage = it },
                    label = { Text("Encouraging text") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomBlockedMessage(editMessage.trim())
                        showMessageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMessageDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            }
        )
    }

    if (showRedirectDialog) {
        AlertDialog(
            onDismissRequest = { showRedirectDialog = false },
            title = { Text("Redirect Destination", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editRedirect,
                    onValueChange = { editRedirect = it },
                    label = { Text("Productive website URL (optional)") },
                    placeholder = { Text("Leave blank for browser initial page", color = SubtleText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setRedirectUrl(editRedirect.trim())
                        showRedirectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRedirectDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            }
        )
    }
}

@Composable
fun SettingsListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DarkText,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MutedText,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = SubtleText,
            modifier = Modifier.size(18.dp)
        )
    }
}
