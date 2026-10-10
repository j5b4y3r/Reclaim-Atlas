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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.PartnerUnlockRequest
import com.example.domain.engine.PartnerEmailSender
import com.example.ui.components.EmailAlreadySentBadge
import com.example.ui.components.InfoHelpIconButton
import com.example.ui.components.PartnerGoogleLoginDialog
import com.example.ui.components.PartnerRequestStatusDialog
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.PrimaryGreenPill
import com.example.ui.theme.SubtleText
import com.example.ui.theme.reclaimTextFieldColors
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AccountabilityScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val accountabilityEnabled by viewModel.accountabilityEnabled.collectAsState()
    val partnerName by viewModel.accountabilityPartnerName.collectAsState()
    val partnerEmail by viewModel.accountabilityPartnerEmail.collectAsState()
    val partnerNote by viewModel.accountabilityNote.collectAsState()
    val activePartnerRequests by viewModel.activePartnerRequests.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showPartnerDialog by remember { mutableStateOf(false) }
    var showPartnerLoginDialog by remember { mutableStateOf(false) }
    var isPartnerAuthLoading by remember { mutableStateOf(false) }
    var partnerLoginError by remember { mutableStateOf<String?>(null) }
    var selectedPendingRequestForCode by remember { mutableStateOf<PartnerUnlockRequest?>(null) }

    val handleOpenPartnerEmail: () -> Unit = {
        if (currentUser == null) {
            partnerLoginError = null
            showPartnerLoginDialog = true
        } else if (viewModel.checkInternetForMustOnlineFeature()) {
            showPartnerDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .testTag("accountability_screen")
    ) {
        // App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("accountability_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DarkText,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Accountability",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "1-Time Partner Email Unlock Codes",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoHelpIconButton(
                    title = "How Accountability Works",
                    stepsOrBullets = listOf(
                        "24/7 Protection: When Accountability Mode is enabled, all active protections, filters, and block rules are locked around the clock.",
                        "No Time Window: You can only unlock or modify rules by entering a 1-time 6-digit code sent to your partner's email.",
                        "1 Code = 1 Item Only: If you need to turn off a specific block, Reclaim emails a 6-digit code to your partner that unlocks ONLY that 1 item. All other blocks stay locked.",
                        "Anti-Spam Protection: Once an email is sent for an item, duplicate emails are blocked until you unlock and re-block that item."
                    ),
                    footerTip = "Partner emails are sent in the background via ${PartnerEmailSender.getConfiguredSenderEmail()}."
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Snapshot Live Sync",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live Sync", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = DarkText)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Banner
            AccountabilityStatusBanner(
                enabled = accountabilityEnabled,
                partnerEmail = partnerEmail
            )

            // Active Per-Item Pending Partner Requests (if any email was already sent)
            if (activePartnerRequests.isNotEmpty()) {
                val requestsList = activePartnerRequests.values.sortedByDescending { it.requestedAt }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pending 1-Time Partner Codes (${requestsList.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                        Text(
                            text = "Tap to enter 6-digit code",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }

                    requestsList.forEach { req ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPendingRequestForCode = req }
                                .testTag("pending_partner_request_banner"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.targetDetail,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "Email already sent to ${req.partnerEmail} • Tap to enter 6-digit code",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main Accountability Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (accountabilityEnabled) PrimaryGreenLight else Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (accountabilityEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = if (accountabilityEnabled) PrimaryGreen else SubtleText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Enable Accountability",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkText
                                    )
                                    InfoHelpIconButton(
                                        title = "Accountability Mode",
                                        stepsOrBullets = listOf(
                                            "When enabled, turning off Accountability Mode itself requires a 1-time 6-digit code from your partner.",
                                            "Unlocking Accountability Mode only turns off Accountability Mode; unlocking a single filter only turns off that single filter."
                                        )
                                    )
                                }
                                Text(
                                    text = if (accountabilityEnabled) "Protections locked • Unlock via partner code" else "Require partner verification code to unblock",
                                    fontSize = 12.sp,
                                    color = MutedText
                                )
                                if (accountabilityEnabled && activePartnerRequests.containsKey("ACC_DISABLE_MODE")) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    EmailAlreadySentBadge(
                                        onClick = {
                                            selectedPendingRequestForCode = activePartnerRequests["ACC_DISABLE_MODE"]
                                        }
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = accountabilityEnabled,
                            onCheckedChange = { willEnable ->
                                if (willEnable) {
                                    if (partnerEmail.isBlank()) {
                                        handleOpenPartnerEmail()
                                    } else {
                                        viewModel.setAccountabilityEnabled(true)
                                    }
                                } else {
                                    viewModel.setAccountabilityEnabled(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryGreen,
                                uncheckedThumbColor = SubtleText,
                                uncheckedTrackColor = PrimaryGreenPill
                            ),
                            modifier = Modifier.testTag("accountability_master_toggle")
                        )
                    }
                }
            }

            // Section: Accountability Partner & 1-Time Email Code Verification
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accountability Partner",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                InfoHelpIconButton(
                    title = "Accountability Partner & Email Codes",
                    stepsOrBullets = listOf(
                        "Who Is Your Partner: A trusted friend, spouse, or mentor whose email receives your 1-time 6-digit unlock codes.",
                        "Individual 1-Time Codes: Whenever you need to unlock a specific block, Reclaim emails a 6-digit code to your partner. They give you the code to type in.",
                        "Anti-Spam: Only 1 email can be sent per locked item until you unlock and re-block that item.",
                        "Changing Partner Email: If you need to change your Partner Email while locked, Reclaim sends a verification code to your current partner email first to approve the change."
                    )
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (partnerName.isNotBlank()) partnerName else "Accountability Partner",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = if (partnerEmail.isNotBlank()) partnerEmail else "No partner email configured",
                                    fontSize = 12.sp,
                                    color = if (partnerEmail.isNotBlank()) PrimaryGreen else MutedText
                                )
                                if (activePartnerRequests.containsKey("ACC_CHANGE_PARTNER_EMAIL")) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    EmailAlreadySentBadge(
                                        onClick = {
                                            selectedPendingRequestForCode = activePartnerRequests["ACC_CHANGE_PARTNER_EMAIL"]
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { handleOpenPartnerEmail() },
                            modifier = Modifier.testTag("edit_partner_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Partner",
                                tint = PrimaryGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (partnerEmail.isNotBlank()) {
                        Text(
                            text = "1-time 6-digit unlock codes are sent in the background via ${PartnerEmailSender.getConfiguredSenderEmail()} to $partnerEmail. Each code unlocks only the single requested item.",
                            fontSize = 11.sp,
                            color = MutedText,
                            lineHeight = 16.sp
                        )

                        OutlinedButton(
                            onClick = { handleOpenPartnerEmail() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF2563EB))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (accountabilityEnabled) "Request Partner Email Change" else "Edit Partner Email",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    } else {
                        Button(
                            onClick = { handleOpenPartnerEmail() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("set_partner_email_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set Accountability Partner Email", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Clean Google Login Required Popup for Partner Email Adding (only Google login button + underlined Terms & Conditions)
    if (showPartnerLoginDialog) {
        PartnerGoogleLoginDialog(
            isLoading = isPartnerAuthLoading,
            errorMessage = partnerLoginError,
            onGoogleSignInClick = {
                isPartnerAuthLoading = true
                partnerLoginError = null
                viewModel.signInWithGoogle(context) { success, err ->
                    isPartnerAuthLoading = false
                    if (success) {
                        showPartnerLoginDialog = false
                        showPartnerDialog = true
                    } else {
                        partnerLoginError = err
                    }
                }
            },
            onDismiss = {
                if (!isPartnerAuthLoading) {
                    showPartnerLoginDialog = false
                }
            }
        )
    }

    // Partner Details Dialog (supports direct save when unlocked, or verification request when changing email while locked)
    if (showPartnerDialog) {
        PartnerDetailsDialog(
            initialName = partnerName,
            initialEmail = partnerEmail,
            initialNote = partnerNote,
            isLocked = accountabilityEnabled && partnerEmail.isNotBlank(),
            hasPendingEmailChangeRequest = activePartnerRequests.containsKey("ACC_CHANGE_PARTNER_EMAIL"),
            onOpenPendingEmailChangeCode = {
                showPartnerDialog = false
                selectedPendingRequestForCode = activePartnerRequests["ACC_CHANGE_PARTNER_EMAIL"]
            },
            onDismiss = { showPartnerDialog = false },
            onSave = { name, email, note ->
                showPartnerDialog = false
                viewModel.setAccountabilityPartner(name, email, note)
            }
        )
    }

    // Partner Request Status / 6-Digit Code Entry Dialog
    selectedPendingRequestForCode?.let { req ->
        PartnerRequestStatusDialog(
            request = req,
            onVerifyCode = { code, onResult ->
                viewModel.verifyPartnerUnlockCode(
                    actionKey = req.actionKey,
                    enteredCode = code,
                    onResult = onResult
                )
            },
            onDismiss = { selectedPendingRequestForCode = null }
        )
    }
}

@Composable
private fun AccountabilityStatusBanner(
    enabled: Boolean,
    partnerEmail: String
) {
    if (!enabled) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F4F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = SubtleText,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Accountability Mode is OFF",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = "Turn on Accountability to lock all content filters and prevent unblocking without your partner's email verification code.",
                        fontSize = 12.sp,
                        color = MutedText,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PARTNER ACCOUNTABILITY ACTIVE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = if (partnerEmail.isNotBlank()) {
                        "All filters and protections are locked 24/7. Unlocking requires a 1-time 6-digit code sent to $partnerEmail."
                    } else {
                        "All filters are locked. Configure your partner's email to receive 1-time 6-digit unlock codes."
                    },
                    fontSize = 13.sp,
                    color = DarkText,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun PartnerDetailsDialog(
    initialName: String,
    initialEmail: String,
    initialNote: String,
    isLocked: Boolean = false,
    hasPendingEmailChangeRequest: Boolean = false,
    onOpenPendingEmailChangeCode: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var note by remember { mutableStateOf(initialNote) }

    val isChangingEmailWhileLocked = isLocked &&
            initialEmail.isNotBlank() &&
            !initialEmail.trim().equals(email.trim(), ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Accountability Partner",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                InfoHelpIconButton(
                    title = "Changing Partner Email",
                    stepsOrBullets = listOf(
                        "First-Time Setup: If you haven't set a partner email yet, you can save it immediately.",
                        "Changing Partner Email: To prevent self-unlocking by swapping to your own email while locked, changing an existing partner email sends a 6-digit code to your CURRENT partner email ($initialEmail) first."
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasPendingEmailChangeRequest) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPendingEmailChangeCode() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email Change Code Already Sent",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = "Tap here to enter the 6-digit code sent to $initialEmail.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1D4ED8)
                                )
                            }
                        }
                    }
                }

                if (isLocked && initialEmail.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                    ) {
                        Text(
                            text = "Accountability Lock is active. If you change the Partner Email from $initialEmail, a 1-time 6-digit code will be emailed to $initialEmail to approve the change.",
                            fontSize = 11.sp,
                            color = Color(0xFF92400E),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Partner Name or Sponsor") },
                    placeholder = { Text("e.g. Sarah / Mentor", color = SubtleText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Partner Email (Receives 6-digit codes)") },
                    placeholder = { Text("e.g. partner@example.com", color = SubtleText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Personal Commitment Note") },
                    placeholder = { Text("I commit to staying focused and honoring my boundaries.", color = SubtleText) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = reclaimTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), email.trim(), note.trim()) },
                enabled = email.trim().contains("@"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isChangingEmailWhileLocked) Color(0xFF2563EB) else PrimaryGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isChangingEmailWhileLocked) "Request Email Change Code" else "Save Partner",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}
