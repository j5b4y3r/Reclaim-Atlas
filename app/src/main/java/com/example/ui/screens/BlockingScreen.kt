package com.example.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import com.example.service.vpn.SelfControlVpnService
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.LockedActionTarget
import com.example.ui.components.EmailAlreadySentBadge
import com.example.ui.components.InfoHelpIconButton
import com.example.ui.components.UninstallProtectionConfigBottomSheet
import com.example.ui.components.UninstallProtectionDeviceAdminDialog
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.PrimaryGreenPill
import com.example.ui.theme.SubtleText
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.viewmodel.MainViewModel
import com.example.util.PermissionHelper

@Composable
fun BlockingScreen(
    viewModel: MainViewModel,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToAccountability: () -> Unit = {},
    onNavigateToCustomizeBlockScreen: () -> Unit = {}
) {
    val adultBlocked by viewModel.adultContentBlocked.collectAsState()
    val imageVideoSearchBlocked by viewModel.imageVideoSearchBlocked.collectAsState()
    val googleLensBlocked by viewModel.googleLensBlocked.collectAsState()
    val shortsBlocked by viewModel.youtubeShortsBlocked.collectAsState()
    val whatsappBlocked by viewModel.whatsappChannelsBlocked.collectAsState()
    val instaReelsBlocked by viewModel.instagramReelsBlocked.collectAsState()
    val fbReelsBlocked by viewModel.facebookReelsBlocked.collectAsState()

    val uninstallProtection by viewModel.uninstallProtection.collectAsState()
    val uninstallProtectionUntil by viewModel.uninstallProtectionUntil.collectAsState()
    val uninstallProtectionDurationDays by viewModel.uninstallProtectionDurationDays.collectAsState()
    val currentLiveTimeMs by viewModel.currentLiveTimeMs.collectAsState()
    val newAppsBlocked by viewModel.newAppsBlocked.collectAsState()
    val unsupportedBrowsersBlocked by viewModel.unsupportedBrowsersBlocked.collectAsState()
    val accountabilityEnabled by viewModel.accountabilityEnabled.collectAsState()
    val partnerEmail by viewModel.accountabilityPartnerEmail.collectAsState()
    val partnerName by viewModel.accountabilityPartnerName.collectAsState()
    val activePartnerRequests by viewModel.activePartnerRequests.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDeviceAdminActive by remember { mutableStateOf(PermissionHelper.isDeviceAdminActive(context)) }
    var isAccessibilityGranted by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var showDeviceAdminPopup by remember { mutableStateOf(false) }
    var showUninstallConfigSheet by remember { mutableStateOf(false) }
    var pendingAdminDays by remember { mutableIntStateOf(1) }
    var waitingForAdminGrant by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            SelfControlVpnService.startVpn(context)
            viewModel.setAdultContentBlocked(true)
            Toast.makeText(context, "CleanBrowsing DNS activated via Reclaim!", Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(context)
                val active = PermissionHelper.isDeviceAdminActive(context)
                isDeviceAdminActive = active
                if (active && waitingForAdminGrant) {
                    waitingForAdminGrant = false
                    com.example.MainActivity.isUninstallPopupVisible = true
                    showUninstallConfigSheet = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            com.example.MainActivity.isUninstallPopupVisible = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showUninstallConfigSheet, showDeviceAdminPopup) {
        com.example.MainActivity.isUninstallPopupVisible = showUninstallConfigSheet || showDeviceAdminPopup
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .testTag("blocking_screen")
        ) {
        // Header with (?) Help
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Blocking",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                letterSpacing = (-0.5).sp
            )
            InfoHelpIconButton(
                title = "How Blocking & Locks Work",
                stepsOrBullets = listOf(
                    "Turn On Anytime: You can turn on any filter or add any app/website block at any time.",
                    "Locked 24/7 with Accountability: When Accountability Mode is ON, active blocks cannot be turned off without your partner's 6-digit code.",
                    "1-Time Partner Unlock: If you try to turn off a locked filter, Reclaim sends 1 email with a 6-digit code to your partner that unlocks ONLY that 1 item.",
                    "Anti-Spam Protection: Once an email is sent for a filter, it displays 'Email Sent • Enter Code' and blocks duplicate emails until you unlock and re-block it."
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Quiet protection against digital triggers",
            fontSize = 14.sp,
            color = MutedText
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!accountabilityEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clickable { onNavigateToAccountability() }
                    .testTag("accountability_disabled_warning_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accountability Mode is OFF",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Content filters can be toggled freely. To lock app/website blocks and require your partner's email code to unblock, enable Accountability. Tap to configure.",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        BlockingCategoryCard(
            icon = Icons.Default.List,
            title = "Manage Blocklist",
            subtitle = "Custom websites, apps, and keywords",
            onClick = onNavigateToBlocklist
        )

        Spacer(modifier = Modifier.height(14.dp))

        BlockingCategoryCard(
            icon = Icons.Default.Lock,
            title = "Accountability Options",
            subtitle = if (accountabilityEnabled) {
                "Protected by partner • Unlock via 6-digit email code"
            } else {
                "Require partner email verification code to unblock"
            },
            onClick = onNavigateToAccountability
        )

        Spacer(modifier = Modifier.height(14.dp))

        BlockingCategoryCard(
            icon = Icons.Default.Movie,
            title = "Customize Block Screen",
            subtitle = "Set motivation video, background image & clean texts",
            onClick = onNavigateToCustomizeBlockScreen
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Content Filters section header with action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Content Filters",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    letterSpacing = (-0.2).sp
                )
                InfoHelpIconButton(
                    title = "Content Filters",
                    stepsOrBullets = listOf(
                        "Each filter targets specific high-dopamine or adult triggers inside browsers and social apps.",
                        "When Accountability Mode is locked, turning off any filter requires either waiting for your daily window or entering a 1-time 6-digit code from your partner for that single filter."
                    )
                )
            }
            val anyFilterOn = adultBlocked || imageVideoSearchBlocked || googleLensBlocked || shortsBlocked || instaReelsBlocked || fbReelsBlocked || whatsappBlocked
            TextButton(
                onClick = { viewModel.setAllContentFilters(!anyFilterOn) }
            ) {
                Text(
                    text = if (anyFilterOn) "Turn Off All" else "Turn On All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AdvancedToggleItem(
                    title = "Block Adult Content",
                    description = "CleanBrowsing Adult Filter DNS (adult-filter-dns.cleanbrowsing.org) blocks adult websites dynamically at the network level (no block screen).",
                    checked = adultBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_ADULT_CONTENT"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_ADULT_CONTENT", "Turn Off 'Block Adult Content'")
                        )
                    },
                    onCheckedChange = { willBlock ->
                        if (willBlock) {
                            PermissionHelper.activateCleanBrowsingDnsViaReclaim(
                                context = context,
                                onRequireSystemConsent = { vpnLauncher.launch(it) },
                                onActivated = {
                                    viewModel.setAdultContentBlocked(true)
                                    Toast.makeText(context, "CleanBrowsing DNS activated via Reclaim!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            SelfControlVpnService.stopVpn(context)
                            viewModel.setAdultContentBlocked(false)
                        }
                    }
                )

                // In-App CleanBrowsing DNS Card (Directly managed by Reclaim - zero manual setup)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cleanbrowsing_in_app_dns_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (adultBlocked) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, if (adultBlocked) Color(0xFFBBF7D0) else Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (adultBlocked) PrimaryGreen else Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CleanBrowsing DNS",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (adultBlocked) Color(0xFF166534) else DarkText
                                )
                            }
                            // Status pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (adultBlocked) Color(0xFFDCFCE7) else Color(0xFFE2E8F0))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (adultBlocked) "Active in Reclaim" else "Inactive",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (adultBlocked) Color(0xFF15803D) else Color(0xFF64748B)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (adultBlocked) {
                                "Adult domains resolve to NXDOMAIN via CleanBrowsing DNS (185.228.168.10 / adult-filter-dns.cleanbrowsing.org) with strict SafeSearch enforced. Directly managed by Reclaim."
                            } else {
                                "CleanBrowsing Adult Filter DNS dynamically blocks adult websites and enforces SafeSearch directly through the Reclaim app without any manual network setup."
                            },
                            fontSize = 11.sp,
                            color = if (adultBlocked) Color(0xFF15803D) else Color(0xFF64748B),
                            lineHeight = 15.sp
                        )
                        if (!adultBlocked) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    PermissionHelper.activateCleanBrowsingDnsViaReclaim(
                                        context = context,
                                        onRequireSystemConsent = { vpnLauncher.launch(it) },
                                        onActivated = {
                                            viewModel.setAdultContentBlocked(true)
                                            Toast.makeText(context, "CleanBrowsing DNS added and activated via Reclaim!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("activate_dns_in_app_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add CleanBrowsing DNS via Reclaim",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                AdvancedToggleItem(
                    title = "Block Image & Video Search",
                    description = "Blocks image & video tab searching across supported engines (Google, Bing, DuckDuckGo, Yahoo, Yandex, Ecosia, Brave) & blocks unsupported search engines",
                    checked = imageVideoSearchBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_IMAGE_VIDEO_SEARCH"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_IMAGE_VIDEO_SEARCH", "Turn Off 'Block Image & Video Search'")
                        )
                    },
                    onCheckedChange = { viewModel.setImageVideoSearchBlocked(it) }
                )

                AdvancedToggleItem(
                    title = "Block YouTube Shorts",
                    description = "Intercepts Shorts player in official & modded YouTube (ReVanced, NewPipe, RVX)",
                    checked = shortsBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_YOUTUBE_SHORTS"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_YOUTUBE_SHORTS", "Turn Off 'Block YouTube Shorts'")
                        )
                    },
                    onCheckedChange = { viewModel.setYoutubeShortsBlocked(it) }
                )

                AdvancedToggleItem(
                    title = "Block Instagram Reels",
                    description = "Blocks fullscreen Reels viewer & tab (feed videos allowed)",
                    checked = instaReelsBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_INSTAGRAM_REELS"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_INSTAGRAM_REELS", "Turn Off 'Block Instagram Reels'")
                        )
                    },
                    onCheckedChange = { viewModel.setInstagramReelsBlocked(it) }
                )

                AdvancedToggleItem(
                    title = "Block Facebook Reels",
                    description = "Blocks fullscreen Reels viewer & tab (feed videos allowed)",
                    checked = fbReelsBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_FACEBOOK_REELS"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_FACEBOOK_REELS", "Turn Off 'Block Facebook Reels'")
                        )
                    },
                    onCheckedChange = { viewModel.setFacebookReelsBlocked(it) }
                )

                AdvancedToggleItem(
                    title = "Block Google Scan & Lens",
                    description = "Blocks Google Lens, Visual Search, Picture Scan & Circle to Search in Google & Chrome",
                    checked = googleLensBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_GOOGLE_LENS"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_GOOGLE_LENS", "Turn Off 'Block Google Scan & Lens'")
                        )
                    },
                    onCheckedChange = { viewModel.setGoogleLensBlocked(it) }
                )

                AdvancedToggleItem(
                    title = "Block WhatsApp Channels",
                    description = "Restricts updates & channel tab",
                    checked = whatsappBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("FILTER_WHATSAPP_CHANNELS"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("FILTER_WHATSAPP_CHANNELS", "Turn Off 'Block WhatsApp Channels'")
                        )
                    },
                    onCheckedChange = { viewModel.setWhatsappChannelsBlocked(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Advanced Protection section
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced Protection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                letterSpacing = (-0.2).sp
            )
            InfoHelpIconButton(
                title = "Advanced Protection",
                stepsOrBullets = listOf(
                    "Prevent Uninstall: Locks Reclaim against uninstallation and accessibility tampering for your chosen number of days (auto-unblocks when timer finishes).",
                    "Block New App Installs: Automatically blocks any newly installed apps downloaded after enabling.",
                    "Block Unsupported Browsers: Blocks browsers that do not expose URL bars to accessibility filters."
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val remainingMs = (uninstallProtectionUntil - currentLiveTimeMs).coerceAtLeast(0L)
                val isUninstallActive = uninstallProtection && (uninstallProtectionUntil == 0L || remainingMs > 0L)
                val uninstallDesc = if (isUninstallActive && uninstallProtectionUntil > 0L) {
                    val days = (remainingMs / (24 * 60 * 60 * 1000L)).toInt()
                    val hours = ((remainingMs % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L)).toInt()
                    val minutes = ((remainingMs % (60 * 60 * 1000L)) / (60 * 1000L)).toInt()
                    if (days > 0) {
                        "Active lock • ${days}d ${hours}h remaining (auto-unblocks when finished)"
                    } else if (hours > 0) {
                        "Active lock • ${hours}h ${minutes}m remaining (auto-unblocks when finished)"
                    } else {
                        "Active lock • ${minutes.coerceAtLeast(1)}m remaining (auto-unblocks when finished)"
                    }
                } else {
                    "Prevents app removal & settings tampering for set days"
                }

                AdvancedToggleItem(
                    title = "Prevent Uninstall",
                    description = uninstallDesc,
                    checked = isUninstallActive,
                    hasPendingEmailCode = activePartnerRequests.containsKey("ADV_UNINSTALL_PROTECTION"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("ADV_UNINSTALL_PROTECTION", "Turn Off 'Prevent Uninstall'")
                        )
                    },
                    onCheckedChange = { willEnable ->
                        if (willEnable) {
                            val active = PermissionHelper.isDeviceAdminActive(context)
                            isDeviceAdminActive = active
                            com.example.MainActivity.isUninstallPopupVisible = true
                            if (!active) {
                                // Ask user permission first if Device Admin is not granted!
                                showDeviceAdminPopup = true
                            } else {
                                showUninstallConfigSheet = true
                            }
                        } else {
                            val success = viewModel.tryDisableUninstallProtection()
                            if (success) {
                                PermissionHelper.removeDeviceAdmin(context)
                                isDeviceAdminActive = PermissionHelper.isDeviceAdminActive(context)
                            }
                        }
                    }
                )

                AdvancedToggleItem(
                    title = "Block New App Installs",
                    description = "Blocks newly installed apps & package installers",
                    checked = newAppsBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("ADV_NEW_APPS_BLOCKED"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("ADV_NEW_APPS_BLOCKED", "Turn Off 'Block New App Installs'")
                        )
                    },
                    onCheckedChange = { willEnable ->
                        viewModel.setNewAppsBlocked(willEnable)
                    }
                )

                AdvancedToggleItem(
                    title = "Block Unsupported Browsers",
                    description = "Blocks browsers that bypass accessibility filters",
                    checked = unsupportedBrowsersBlocked,
                    hasPendingEmailCode = activePartnerRequests.containsKey("ADV_UNSUPPORTED_BROWSERS"),
                    onOpenPendingCode = {
                        viewModel.triggerLockedItemDialog(
                            LockedActionTarget("ADV_UNSUPPORTED_BROWSERS", "Turn Off 'Block Unsupported Browsers'")
                        )
                    },
                    onCheckedChange = { willEnable ->
                        viewModel.setUnsupportedBrowsersBlocked(willEnable)
                    }
                )

            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        }

        if (showUninstallConfigSheet) {
            UninstallProtectionConfigBottomSheet(
                isDeviceAdminActive = isDeviceAdminActive,
                initialDays = uninstallProtectionDurationDays.coerceAtLeast(1),
                onConfirm = { durationDays ->
                    showUninstallConfigSheet = false
                    val active = PermissionHelper.isDeviceAdminActive(context)
                    isDeviceAdminActive = active
                    if (!active) {
                        pendingAdminDays = durationDays
                        com.example.MainActivity.isUninstallPopupVisible = true
                        showDeviceAdminPopup = true
                    } else {
                        com.example.MainActivity.isUninstallPopupVisible = false
                        PermissionHelper.clearAdminActivationRequest()
                        com.example.service.receiver.SelfControlDeviceAdminReceiver.lastAdminEnabledTimeMs = 0L
                        viewModel.activateUninstallProtection(durationDays)
                    }
                },
                onDismiss = {
                    com.example.MainActivity.isUninstallPopupVisible = false
                    showUninstallConfigSheet = false
                }
            )
        }

        if (showDeviceAdminPopup) {
            UninstallProtectionDeviceAdminDialog(
                onOpenSettings = {
                    com.example.MainActivity.isUninstallPopupVisible = false
                    showDeviceAdminPopup = false
                    waitingForAdminGrant = true
                    PermissionHelper.openDeviceAdminSettings(context)
                },
                onDismiss = {
                    com.example.MainActivity.isUninstallPopupVisible = false
                    showDeviceAdminPopup = false
                    waitingForAdminGrant = false
                }
            )
        }
    }
}

@Composable
fun BlockingCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(22.dp)
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
                    color = MutedText
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SubtleText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AdvancedToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    hasPendingEmailCode: Boolean = false,
    onOpenPendingCode: () -> Unit = {},
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )
                if (hasPendingEmailCode && checked) {
                    EmailAlreadySentBadge(onClick = onOpenPendingCode)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MutedText,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryGreen,
                uncheckedThumbColor = SubtleText,
                uncheckedTrackColor = PrimaryGreenPill
            )
        )
    }
}
