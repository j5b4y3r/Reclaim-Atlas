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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.PartnerUnlockRequest
import com.example.domain.engine.PartnerEmailSender
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import com.example.ui.theme.reclaimTextFieldColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable (?) Help Icon button that opens a self-explanatory info modal so users never need a tutorial.
 */
@Composable
fun InfoHelpIconButton(
    title: String,
    stepsOrBullets: List<String>,
    footerTip: String? = null,
    tint: Color = SubtleText,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showInfo = true },
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "More info about $title",
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    stepsOrBullets.forEachIndexed { index, point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = point,
                                fontSize = 13.sp,
                                color = DarkText,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (!footerTip.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                        ) {
                            Text(
                                text = footerTip,
                                fontSize = 11.sp,
                                color = MutedText,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfo = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got It", fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

/**
 * Small clickable pill badge shown on any toggle or rule where an unlock email has already been sent.
 */
@Composable
fun EmailAlreadySentBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEFF6FF))
            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Email Sent • Enter Code",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D4ED8)
        )
    }
}

/**
 * Unified 1-Item Partner Email Unlock & 6-Digit Code Verification Dialog.
 * - If no email has been sent for [actionKey] yet: lets the user enter a reason and sends 1 background email.
 * - If an email was ALREADY sent for [actionKey]: blocks duplicate emails (anti-spam) and displays the 6-digit code input.
 */
@Composable
fun RequestPartnerUnlockDialog(
    actionKey: String = "SINGLE_ACTION",
    targetType: String,
    targetDetail: String,
    partnerEmail: String,
    partnerName: String,
    ruleId: Long? = null,
    payload: String = "",
    existingRequest: PartnerUnlockRequest? = null,
    onSendRequest: (reason: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onVerifyCode: (code: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var reasonText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var justSentLocal by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }

    val isEmailAlreadySent = (existingRequest != null && existingRequest.deliveredViaSmtp) || justSentLocal
    var codeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var unlockedSuccessMessage by remember { mutableStateOf<String?>(null) }

    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val sentTimeText = existingRequest?.requestedAt?.takeIf { it > 0L }?.let { sdf.format(Date(it)) } ?: "Just now"
    val activeSenderEmail = existingRequest?.senderEmail?.takeIf { it.isNotBlank() }
        ?: PartnerEmailSender.getConfiguredSenderEmail()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("request_partner_unlock_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            unlockedSuccessMessage != null -> PrimaryGreenLight
                            isEmailAlreadySent -> Color(0xFFFEF3C7)
                            else -> Color(0xFFEFF6FF)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        unlockedSuccessMessage != null -> Icons.Default.LockOpen
                        isEmailAlreadySent -> Icons.Default.MarkEmailRead
                        else -> Icons.Default.Email
                    },
                    contentDescription = "Partner Verification",
                    tint = when {
                        unlockedSuccessMessage != null -> PrimaryGreen
                        isEmailAlreadySent -> Color(0xFFD97706)
                        else -> Color(0xFF2563EB)
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            unlockedSuccessMessage != null -> "Item Unlocked!"
                            isEmailAlreadySent -> "Email Already Sent"
                            else -> "1-Time Partner Unlock"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    InfoHelpIconButton(
                        title = "How 1-Time Partner Unlock Works",
                        stepsOrBullets = listOf(
                            "1 Code = 1 Specific Item: Requesting an unlock only applies to \"$targetDetail\". All your other blocks remain locked.",
                            "Background Email Delivery: Reclaim sends a 6-digit code directly to your partner ($partnerEmail) via $activeSenderEmail. You never see the code on your phone.",
                            "Anti-Spam Protection: Only 1 email can be sent per locked item until you unlock it and block it again.",
                            "Enter Partner's Code: Ask your partner for the 6-digit code from their email and enter it here to unlock this item immediately."
                        ),
                        footerTip = "Tip: If you need to change your Partner Email while locked, requesting a partner email change sends a verification code to your current partner first."
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Visual 3-Step Progress Bar so user understands flow without any tutorial
                PartnerFlowStepIndicator(
                    currentStep = when {
                        unlockedSuccessMessage != null -> 3
                        isEmailAlreadySent -> 2
                        else -> 1
                    }
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Target Action Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SINGLE ITEM TO UNLOCK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SubtleText)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background( Color(0xFFDBEAFE))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("1 Item Only", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                            }
                        }
                        Text(
                            text = targetDetail,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Partner: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedText)
                            Text(
                                text = if (partnerName.isNotBlank()) "$partnerName ($partnerEmail)" else partnerEmail,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkText
                            )
                        }
                    }
                }

                if (unlockedSuccessMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$targetDetail has been unlocked! All other protections remain active.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkText,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else if (!isEmailAlreadySent) {
                    // STEP 1: Request Email for this specific item
                    Text(
                        text = "An automated email with a secret 6-digit code will be sent from $activeSenderEmail to $partnerEmail for this single item only.",
                        fontSize = 12.sp,
                        color = MutedText,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = {
                            reasonText = it
                            sendError = null
                        },
                        label = { Text("Reason for unlocking this item (required)") },
                        placeholder = { Text("Explain to your partner why you need to unlock this...", color = SubtleText) },
                        enabled = !isSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("partner_request_reason_input"),
                        minLines = 2,
                        maxLines = 3,
                        colors = reclaimTextFieldColors()
                    )

                    if (sendError != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                        ) {
                            Text(
                                text = sendError!!,
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Text(
                        text = "Note: You can only send 1 email per locked item until it is unlocked and blocked again.",
                        fontSize = 11.sp,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp
                    )
                } else {
                    // STEP 2 & 3: Email Already Sent -> Anti-Spam Notice + 6-Digit Code Entry
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Email Already Sent ($sentTimeText)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Text(
                                text = "1 email was sent from $activeSenderEmail to $partnerEmail for this item. To prevent spam, another email cannot be sent until this item is unlocked and blocked again.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309),
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Text(
                        text = "Enter the 6-digit verification code from your partner:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }.take(6)
                            codeInput = digitsOnly
                            codeError = null
                        },
                        label = { Text("6-Digit Partner Code") },
                        placeholder = { Text("000000", color = SubtleText) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("partner_verification_code_input"),
                        colors = reclaimTextFieldColors()
                    )

                    if (codeError != null) {
                        Text(
                            text = codeError!!,
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (unlockedSuccessMessage != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            } else if (!isEmailAlreadySent) {
                Button(
                    onClick = {
                        if (reasonText.isNotBlank() && !isSending) {
                            isSending = true
                            sendError = null
                            onSendRequest(reasonText.trim()) { success, errorOrNote ->
                                isSending = false
                                if (success) {
                                    justSentLocal = true
                                } else {
                                    sendError = errorOrNote ?: "Failed to send email. Please check internet connection and try again."
                                }
                            }
                        }
                    },
                    enabled = reasonText.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("send_partner_request_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sending Email to Partner...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send 1-Time Code to Partner", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (codeInput.length == 6) {
                            isVerifying = true
                            onVerifyCode(codeInput) { success, message ->
                                isVerifying = false
                                if (success) {
                                    unlockedSuccessMessage = message
                                } else {
                                    codeError = message
                                }
                            }
                        }
                    },
                    enabled = codeInput.length == 6 && !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_partner_code_button")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify Code & Unlock This Item", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (unlockedSuccessMessage == null) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSending && !isVerifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEmailAlreadySent) "Close (Enter Code Later)" else "Cancel", color = SubtleText)
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun PartnerFlowStepIndicator(currentStep: Int) {
    val steps = listOf("1. Request", "2. Email Sent", "3. Enter Code")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF1F5F9))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val stepNum = index + 1
            val isActive = stepNum == currentStep || (currentStep == 2 && stepNum == 3)
            val isCompleted = stepNum < currentStep
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isCompleted -> PrimaryGreenLight
                            isActive -> Color.White
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isCompleted -> PrimaryGreen
                        isActive -> DarkText
                        else -> SubtleText
                    }
                )
            }
        }
    }
}

/**
 * Dialog displaying an active / pending partner request and allowing 6-digit code entry.
 */
@Composable
fun PartnerRequestStatusDialog(
    request: PartnerUnlockRequest,
    onVerifyCode: (code: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onClearRequest: () -> Unit = {},
    onDismiss: () -> Unit
) {
    RequestPartnerUnlockDialog(
        actionKey = request.actionKey,
        targetType = request.targetType,
        targetDetail = request.targetDetail,
        partnerEmail = request.partnerEmail,
        partnerName = "",
        ruleId = request.ruleId,
        payload = request.payload,
        existingRequest = request,
        onSendRequest = { _, onResult -> onResult(true, null) },
        onVerifyCode = onVerifyCode,
        onDismiss = onDismiss
    )
}
