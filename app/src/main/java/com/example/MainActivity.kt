package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.engine.LockedActionTarget
import com.example.ui.components.AccountabilityLockDialog
import com.example.ui.components.AccountabilityRequiredDialog
import com.example.ui.components.NoInternetWarningDialog
import com.example.ui.components.RequestPartnerUnlockDialog
import com.example.ui.components.RequiredPermissionBarrier
import com.example.util.PermissionHelper
import com.example.ui.navigation.Screen
import com.example.ui.screens.AccountabilityScreen
import com.example.ui.screens.BlockingScreen
import com.example.ui.screens.BlocklistScreen
import com.example.ui.screens.CustomizeBlockScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HabitsScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.LevelsScreen
import com.example.ui.screens.OnboardingWizardScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.CardSurface
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.viewmodel.MainViewModel

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isForeground: Boolean = false
            private set

        @Volatile
        var isUninstallPopupVisible: Boolean = false
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        isForeground = true
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            isForeground = true
        }
    }

    override fun onPause() {
        isForeground = false
        isUninstallPopupVisible = false
        super.onPause()
    }

    override fun onStop() {
        isForeground = false
        isUninstallPopupVisible = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isForeground = true
        enableEdgeToEdge()
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()

                val lifecycleOwner = LocalLifecycleOwner.current
                var isAccessibilityGranted by remember {
                    mutableStateOf(PermissionHelper.isAccessibilityEnabled(this@MainActivity))
                }
                var isUsageStatsGranted by remember {
                    mutableStateOf(PermissionHelper.isUsageStatsGranted(this@MainActivity))
                }
                var isNotificationGranted by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else true
                    )
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(this@MainActivity)
                            isUsageStatsGranted = PermissionHelper.isUsageStatsGranted(this@MainActivity)
                            isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            } else true
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Request POST_NOTIFICATIONS permission on Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val bottomNavItems = listOf(
                    BottomNavItem(Screen.Dashboard, Icons.Default.Home, "Home"),
                    BottomNavItem(Screen.Blocking, Icons.Default.Shield, "Block"),
                    BottomNavItem(Screen.Progress, Icons.Default.BarChart, "Progress"),
                    BottomNavItem(Screen.More, Icons.Default.MoreHoriz, "More")
                )

                val showBottomBar = onboardingCompleted &&
                        isAccessibilityGranted &&
                        (currentRoute in bottomNavItems.map { it.screen.route })

                val accountabilityLockDialog by viewModel.accountabilityLockDialogState.collectAsState()
                val accountabilityRequiredAction by viewModel.accountabilityRequiredDialogState.collectAsState()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = CardSurface,
                                tonalElevation = 0.dp,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                bottomNavItems.forEach { item ->
                                    val isSelected = currentRoute == item.screen.route
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            navController.navigate(item.screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = item.label,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryGreen,
                                            selectedTextColor = PrimaryGreen,
                                            indicatorColor = PrimaryGreenLight,
                                            unselectedIconColor = SubtleText,
                                            unselectedTextColor = SubtleText
                                        ),
                                        modifier = Modifier.testTag("nav_item_${item.screen.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (onboardingCompleted && !isAccessibilityGranted) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                            RequiredPermissionBarrier(
                                isAccessibilityGranted = isAccessibilityGranted,
                                isUsageStatsGranted = isUsageStatsGranted,
                                isNotificationGranted = isNotificationGranted,
                                onOpenAccessibility = { PermissionHelper.openAccessibilitySettings(this@MainActivity) },
                                onOpenUsageAccess = { PermissionHelper.openUsageAccessSettings(this@MainActivity) },
                                onRecheck = {
                                    isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(this@MainActivity)
                                    isUsageStatsGranted = PermissionHelper.isUsageStatsGranted(this@MainActivity)
                                }
                            )
                        }
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = if (onboardingCompleted) Screen.Dashboard.route else Screen.Onboarding.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                        composable(Screen.Onboarding.route) {
                            OnboardingWizardScreen(
                                viewModel = viewModel,
                                onComplete = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToFocus = {
                                    navController.navigate(Screen.Focus.route)
                                },
                                onNavigateToLevels = {
                                    navController.navigate(Screen.Levels.route)
                                },
                                onNavigateToHabits = {
                                    navController.navigate(Screen.Habits.route)
                                },
                                onNavigateToJournal = {
                                    navController.navigate(Screen.Journal.route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.More.route)
                                }
                            )
                        }

                        composable(Screen.Blocking.route) {
                            BlockingScreen(
                                viewModel = viewModel,
                                onNavigateToBlocklist = {
                                    navController.navigate(Screen.Blocklist.route)
                                },
                                onNavigateToAccountability = {
                                    navController.navigate(Screen.Accountability.route)
                                },
                                onNavigateToCustomizeBlockScreen = {
                                    navController.navigate(Screen.CustomizeBlockScreen.route)
                                }
                            )
                        }

                        composable(Screen.Blocklist.route) {
                            BlocklistScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Progress.route) {
                            ProgressScreen(
                                viewModel = viewModel
                            )
                        }

                        composable(Screen.More.route) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToAccountability = {
                                    navController.navigate(Screen.Accountability.route)
                                },
                                onNavigateToCustomizeBlockScreen = {
                                    navController.navigate(Screen.CustomizeBlockScreen.route)
                                }
                            )
                        }

                        composable(Screen.CustomizeBlockScreen.route) {
                            CustomizeBlockScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Focus.route) {
                            FocusScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Levels.route) {
                            LevelsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Habits.route) {
                            HabitsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Journal.route) {
                            JournalScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Accountability.route) {
                            AccountabilityScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                    // Global Accountability Lock Dialog & 1-Item Partner Email / Code Dialog
                    val partnerEmail by viewModel.accountabilityPartnerEmail.collectAsState()
                    val partnerName by viewModel.accountabilityPartnerName.collectAsState()
                    val activePartnerRequests by viewModel.activePartnerRequests.collectAsState()
                    var activeLockedTargetForPartner by remember { mutableStateOf<LockedActionTarget?>(null) }

                    accountabilityLockDialog?.let { status ->
                        val target = status.lockedTarget ?: LockedActionTarget(
                            actionKey = "SINGLE_ACTION",
                            actionTitle = "Modify Protected Setting"
                        )
                        val alreadySent = activePartnerRequests.containsKey(target.actionKey)

                        AccountabilityLockDialog(
                            status = status,
                            hasAlreadySentEmailForThisItem = alreadySent,
                            onRequestPartnerUnlock = if (partnerEmail.isNotBlank()) {
                                {
                                    viewModel.dismissAccountabilityLockDialog()
                                    activeLockedTargetForPartner = target
                                }
                            } else null,
                            onConfigurePartnerEmail = if (partnerEmail.isBlank()) {
                                {
                                    viewModel.dismissAccountabilityLockDialog()
                                    navController.navigate(Screen.Accountability.route)
                                }
                            } else null,
                            onDismiss = { viewModel.dismissAccountabilityLockDialog() }
                        )
                    }

                    activeLockedTargetForPartner?.let { target ->
                        val existingReq = activePartnerRequests[target.actionKey]
                        RequestPartnerUnlockDialog(
                            actionKey = target.actionKey,
                            targetType = target.targetType,
                            targetDetail = target.actionTitle,
                            partnerEmail = existingReq?.partnerEmail?.ifBlank { partnerEmail } ?: partnerEmail,
                            partnerName = partnerName,
                            ruleId = target.ruleId,
                            payload = target.payload,
                            existingRequest = existingReq,
                            onSendRequest = { reason, onResult ->
                                viewModel.sendAccountabilityPartnerRequest(
                                    actionKey = target.actionKey,
                                    targetType = target.targetType,
                                    targetDetail = target.actionTitle,
                                    ruleId = target.ruleId,
                                    payload = target.payload,
                                    reason = reason,
                                    onResult = { result ->
                                        if (result.isSuccess) {
                                            onResult(true, result.getOrNull()?.responseNote)
                                        } else {
                                            onResult(false, result.exceptionOrNull()?.message)
                                        }
                                    }
                                )
                            },
                            onVerifyCode = { code, onResult ->
                                viewModel.verifyPartnerUnlockCode(
                                    actionKey = target.actionKey,
                                    enteredCode = code,
                                    onResult = onResult
                                )
                            },
                            onDismiss = { activeLockedTargetForPartner = null }
                        )
                    }

                    // Global Accountability Required Dialog (when attempting to enable blocks while Accountability is off)
                    accountabilityRequiredAction?.let { actionName ->
                        AccountabilityRequiredDialog(
                            actionName = actionName,
                            onNavigateToAccountability = {
                                viewModel.dismissAccountabilityRequiredDialog()
                                navController.navigate(Screen.Accountability.route)
                            },
                            onDismiss = { viewModel.dismissAccountabilityRequiredDialog() }
                        )
                    }

                    // Global No Internet Warning Dialog for must-internet features
                    val noInternetWarning by viewModel.noInternetWarning.collectAsState()
                    noInternetWarning?.let { warningMsg ->
                        NoInternetWarningDialog(
                            message = warningMsg,
                            onDismiss = { viewModel.dismissNoInternetWarning() }
                        )
                    }
                }
            }
        }
    }
}
