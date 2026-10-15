package com.example.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.FocusSchedule
import com.example.domain.engine.LockedActionTarget
import com.example.service.focus.FocusModeService
import com.example.ui.components.EmailAlreadySentBadge
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
import com.example.util.PermissionHelper
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val schedules by viewModel.allFocusSchedules.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val activePartnerRequests by viewModel.activePartnerRequests.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<FocusSchedule?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isAccessibilityGranted by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityGranted = PermissionHelper.isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var timeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            timeTick++
        }
    }

    val activeSchedule = remember(schedules, timeTick) {
        schedules.firstOrNull { isScheduleCurrentlyActive(it) }
    }
    val isFocusActive = FocusModeService.isFocusActive || activeSession != null || activeSchedule != null

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkText
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Mode",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.4).sp
                    )
                }
                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = MutedText
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSchedule = null
                    showAddSheet = true
                },
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Schedule")
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Accessibility Service warning if not enabled
                if (!isAccessibilityGranted) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { PermissionHelper.openAccessibilitySettings(context) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.5.dp, Color(0xFFEF4444))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFEE2E2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Accessibility Service Required",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = "Tap to enable SelfControl so Focus Mode can detect and block unallowed apps.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { PermissionHelper.openAccessibilitySettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Active Focus Mode Banner
                if (isFocusActive) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight),
                            border = BorderStroke(1.5.dp, PrimaryGreen)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
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
                                            .clip(CircleShape)
                                            .background(PrimaryGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Focus Mode Active",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = DarkText
                                        )
                                        Text(
                                            text = if (activeSchedule != null) {
                                                "Active: \"${activeSchedule.title.ifBlank { "Focus Session" }}\" (${activeSchedule.startTime} - ${activeSchedule.endTime})"
                                            } else {
                                                "Only allowed apps, calls & SMS are permitted"
                                            },
                                            fontSize = 12.sp,
                                            color = MutedText
                                        )
                                    }
                                }
                                if (activeSession != null) {
                                    val hasPendingStop = activePartnerRequests.containsKey("FOCUS_SESSION_STOP")
                                    Column(horizontalAlignment = Alignment.End) {
                                        Button(
                                            onClick = { viewModel.stopInstantFocus(context) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (hasPendingStop) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            EmailAlreadySentBadge(onClick = {
                                                viewModel.triggerLockedItemDialog(
                                                    LockedActionTarget("FOCUS_SESSION_STOP", "Stop Active Focus Mode")
                                                )
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }



                // Schedules Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Scheduled Sessions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            text = "${schedules.size} created",
                            fontSize = 13.sp,
                            color = SubtleText
                        )
                    }
                }

                if (schedules.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardSurface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryGreen.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No focus schedules yet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = DarkText
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap + below to create a schedule with your preferred time, days, and allowed apps.",
                                        color = SubtleText,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(schedules, key = { it.id }) { schedule ->
                        val disableKey = "FOCUS_SCHEDULE_DISABLE_${schedule.id}"
                        val deleteKey = "FOCUS_SCHEDULE_DELETE_${schedule.id}"
                        val editKey = "FOCUS_SCHEDULE_EDIT_${schedule.id}"
                        val scheduleTitle = schedule.title.ifBlank { "Schedule #${schedule.id}" }
                        val hasPendingDisable = activePartnerRequests.containsKey(disableKey)
                        val hasPendingDelete = activePartnerRequests.containsKey(deleteKey)
                        val hasPendingEdit = activePartnerRequests.containsKey(editKey)
                        val hasPendingEmailCode = hasPendingDisable || hasPendingDelete || hasPendingEdit

                        FocusScheduleItem(
                            schedule = schedule,
                            hasPendingEmailCode = hasPendingEmailCode,
                            onOpenPendingCode = {
                                when {
                                    hasPendingDelete -> viewModel.triggerLockedItemDialog(
                                        LockedActionTarget(
                                            actionKey = deleteKey,
                                            actionTitle = "Delete Focus Schedule: $scheduleTitle",
                                            targetType = "DELETE_FOCUS_SCHEDULE",
                                            ruleId = schedule.id
                                        )
                                    )
                                    hasPendingEdit -> viewModel.triggerLockedItemDialog(
                                        LockedActionTarget(
                                            actionKey = editKey,
                                            actionTitle = "Edit Focus Schedule: $scheduleTitle",
                                            targetType = "EDIT_FOCUS_SCHEDULE",
                                            ruleId = schedule.id
                                        )
                                    )
                                    else -> viewModel.triggerLockedItemDialog(
                                        LockedActionTarget(
                                            actionKey = disableKey,
                                            actionTitle = "Disable Focus Schedule: $scheduleTitle",
                                            targetType = "DISABLE_FOCUS_SCHEDULE",
                                            ruleId = schedule.id
                                        )
                                    )
                                }
                            },
                            onToggle = { enabled ->
                                viewModel.toggleFocusSchedule(schedule.id, enabled)
                            },
                            onEdit = {
                                val lockStatus = viewModel.checkAccountabilityLock(
                                    LockedActionTarget(
                                        actionKey = editKey,
                                        actionTitle = "Edit Focus Schedule: $scheduleTitle",
                                        targetType = "EDIT_FOCUS_SCHEDULE",
                                        ruleId = schedule.id
                                    )
                                )
                                if (lockStatus.isLocked) {
                                    viewModel.triggerLockedItemDialog(
                                        LockedActionTarget(
                                            actionKey = editKey,
                                            actionTitle = "Edit Focus Schedule: $scheduleTitle",
                                            targetType = "EDIT_FOCUS_SCHEDULE",
                                            ruleId = schedule.id
                                        )
                                    )
                                } else {
                                    editingSchedule = schedule
                                    showAddSheet = true
                                }
                            },
                            onDelete = {
                                viewModel.deleteFocusSchedule(schedule)
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAddSheet = false
                editingSchedule = null
            },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            AddFocusScheduleContent(
                initialSchedule = editingSchedule,
                onSchedule = { title, start, end, isDaily, days, allowedApps ->
                    if (editingSchedule != null) {
                        val updated = editingSchedule!!.copy(
                            title = title,
                            startTime = start,
                            endTime = end,
                            isDaily = isDaily,
                            selectedDays = days,
                            allowedApps = allowedApps
                        )
                        val success = viewModel.updateFocusSchedule(updated)
                        if (success) {
                            showAddSheet = false
                            editingSchedule = null
                        }
                    } else {
                        viewModel.addFocusSchedule(
                            FocusSchedule(
                                title = title,
                                startTime = start,
                                endTime = end,
                                isDaily = isDaily,
                                selectedDays = days,
                                allowedApps = allowedApps,
                                isEnabled = true
                            )
                        )
                        showAddSheet = false
                        editingSchedule = null
                    }
                },
                onCancel = {
                    showAddSheet = false
                    editingSchedule = null
                }
            )
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text("How Focus Mode Works", fontWeight = FontWeight.Bold, color = DarkText)
            },
            text = {
                Text(
                    "During an active Focus Session or scheduled focus time, Reclaim keeps you in deep work.\n\n" +
                            "• Only calls, SMS, emergency dialer, and your explicitly selected allowed apps will open.\n" +
                            "• All other distracting apps will be blocked by the accessibility guardian.\n" +
                            "• You can configure custom recurrence days (Sat, Sun, Mon...) and choose exactly which apps remain accessible.",
                    fontSize = 14.sp,
                    color = DarkText,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Got It")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun FocusScheduleItem(
    schedule: FocusSchedule,
    hasPendingEmailCode: Boolean = false,
    onOpenPendingCode: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val durationText = calculateDurationText(schedule.startTime, schedule.endTime)
    val allowedAppsCount = if (schedule.allowedApps.isBlank()) 0 else schedule.allowedApps.split(",").size
    val isActiveNow = isScheduleCurrentlyActive(schedule)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveNow) PrimaryGreenLight.copy(alpha = 0.5f) else CardSurface
        ),
        border = if (isActiveNow) BorderStroke(1.5.dp, PrimaryGreen) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.title.takeIf { it.isNotBlank() } ?: "Focus Session",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (schedule.isEnabled) DarkText else SubtleText
                        )
                        if (isActiveNow) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryGreen)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE NOW",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${schedule.startTime} - ${schedule.endTime}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (schedule.isEnabled) PrimaryGreen else SubtleText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = durationText,
                            fontSize = 12.sp,
                            color = MutedText
                        )
                    }
                    if (hasPendingEmailCode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        EmailAlreadySentBadge(onClick = onOpenPendingCode)
                    }
                }

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryGreen,
                        uncheckedThumbColor = SubtleText,
                        uncheckedTrackColor = PrimaryGreenPill
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details pills & actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Recurrence Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (schedule.isDaily) "Daily" else schedule.selectedDays.ifBlank { "Custom" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkText
                        )
                    }

                    // Allowed apps Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (allowedAppsCount > 0) PrimaryGreen.copy(alpha = 0.1f) else Color(0xFFF3F4F6))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (allowedAppsCount > 0) "$allowedAppsCount allowed apps" else "Calls & SMS only",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (allowedAppsCount > 0) PrimaryGreen else MutedText
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Schedule",
                            tint = MutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddFocusScheduleContent(
    initialSchedule: FocusSchedule? = null,
    onSchedule: (title: String, start: String, end: String, isDaily: Boolean, days: String, allowedApps: String) -> Unit,
    onCancel: () -> Unit
) {
    val now = remember { Calendar.getInstance() }
    val defaultStart = remember { formatCalendarTime(now) }
    val defaultEnd = remember {
        val endCal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        formatCalendarTime(endCal)
    }
    val defaultToday = remember {
        when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "Sat"
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            else -> "Mon"
        }
    }

    var title by remember { mutableStateOf(initialSchedule?.title ?: "") }
    var startTime by remember { mutableStateOf(initialSchedule?.startTime ?: defaultStart) }
    var endTime by remember { mutableStateOf(initialSchedule?.endTime ?: defaultEnd) }
    var isDaily by remember { mutableStateOf(initialSchedule?.isDaily ?: true) }
    
    var selectedDays by remember {
        mutableStateOf(
            if (initialSchedule != null && initialSchedule.selectedDays.isNotBlank()) {
                initialSchedule.selectedDays.split(",").map { it.trim() }.toSet()
            } else {
                setOf(defaultToday, "Sat", "Sun")
            }
        )
    }

    var selectedAllowedApps by remember {
        mutableStateOf(
            if (initialSchedule != null && initialSchedule.allowedApps.isNotBlank()) {
                initialSchedule.allowedApps.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
        )
    }

    var acceptedRules by remember { mutableStateOf(true) }

    // Dialog state for Clock UI
    var showStartTimeClock by remember { mutableStateOf(false) }
    var showEndTimeClock by remember { mutableStateOf(false) }

    // Bottom sheet for choosing allowed apps
    var showAllowedAppsSheet by remember { mutableStateOf(false) }
    var showAllowedAppsInfoDialog by remember { mutableStateOf(false) }

    val durationText = calculateDurationText(startTime, endTime)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = if (initialSchedule == null) "New Focus Schedule" else "Edit Focus Schedule",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Title (e.g. Deep Work, Study)", color = MutedText) },
            label = { Text("Schedule Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = reclaimTextFieldColors(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Duration Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Duration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = durationText,
                        fontSize = 13.sp,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time Pickers (CLOCK UI - Clickable Cards)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClockTimeCard(
                        label = "Start Time",
                        timeValue = startTime,
                        onClick = { showStartTimeClock = true },
                        modifier = Modifier.weight(1f)
                    )
                    ClockTimeCard(
                        label = "End Time",
                        timeValue = endTime,
                        onClick = { showEndTimeClock = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Radio Options: Daily vs Custom
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { isDaily = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isDaily) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Daily",
                            tint = if (isDaily) PrimaryGreen else SubtleText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Daily",
                            color = DarkText,
                            fontSize = 14.sp,
                            fontWeight = if (isDaily) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { isDaily = false }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (!isDaily) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Custom",
                            tint = if (!isDaily) PrimaryGreen else SubtleText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Custom",
                            color = DarkText,
                            fontSize = 14.sp,
                            fontWeight = if (!isDaily) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                // 7 Days Selection UI (displayed when Custom is selected)
                if (!isDaily) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Select active days:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val daysList = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        daysList.forEach { day ->
                            val isSelected = selectedDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryGreen else Color(0xFFF3F4F6))
                                    .then(
                                        if (!isSelected) Modifier.border(1.dp, Color(0xFFE2E8F0), CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        selectedDays = if (isSelected) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else DarkText
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Allowed Apps Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppBackground),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Allowed Apps",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showAllowedAppsInfoDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Allowed Apps Info",
                            tint = MutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Select Button Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PrimaryGreen.copy(alpha = 0.12f))
                        .clickable { showAllowedAppsSheet = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Select (${selectedAllowedApps.size})",
                            color = PrimaryGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Rules Checkbox
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = acceptedRules,
                onCheckedChange = { acceptedRules = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
            )
            Text(
                text = "I understand that only calls, SMS, and selected allowed apps will be accessible during focus time. All other apps will be blocked.",
                fontSize = 13.sp,
                color = DarkText,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                border = BorderStroke(1.dp, PrimaryGreen)
            ) {
                Text("CANCEL", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    if (acceptedRules) {
                        onSchedule(
                            title,
                            startTime,
                            endTime,
                            isDaily,
                            selectedDays.joinToString(","),
                            selectedAllowedApps.joinToString(",")
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen,
                    disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
                ),
                enabled = acceptedRules
            ) {
                Text(
                    if (initialSchedule == null) "SCHEDULE" else "UPDATE",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // CLOCK UI DIALOGS
    if (showStartTimeClock) {
        ClockTimePickerDialog(
            title = "Select Start Time",
            initialTime = startTime,
            onTimeSelected = { startTime = it },
            onDismiss = { showStartTimeClock = false }
        )
    }

    if (showEndTimeClock) {
        ClockTimePickerDialog(
            title = "Select End Time",
            initialTime = endTime,
            onTimeSelected = { endTime = it },
            onDismiss = { showEndTimeClock = false }
        )
    }

    // ALLOWED APPS BOTTOM SHEET
    if (showAllowedAppsSheet) {
        SelectAllowedAppsBottomSheet(
            selectedPackages = selectedAllowedApps,
            onTogglePackage = { pkg ->
                selectedAllowedApps = if (selectedAllowedApps.contains(pkg)) {
                    selectedAllowedApps - pkg
                } else {
                    selectedAllowedApps + pkg
                }
            },
            onDismiss = { showAllowedAppsSheet = false }
        )
    }

    // ALLOWED APPS INFO POPUP
    if (showAllowedAppsInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAllowedAppsInfoDialog = false },
            title = {
                Text(
                    text = "Allowed Apps",
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            },
            text = {
                Text(
                    text = "Allowed apps remain accessible while this Focus schedule is active. All other apps on your device will be blocked by the guardian.\n\nPhone calls, SMS messages, and emergency dialer are always permitted.",
                    fontSize = 14.sp,
                    color = DarkText,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAllowedAppsInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("Got It")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * ClockTimeCard: Modern interactive time selector card.
 * Clicking opens the Clock dial UI instead of typing text.
 * Displays a pen edit icon so users immediately know it's editable.
 */
@Composable
fun ClockTimeCard(
    label: String,
    timeValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SubtleText,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeValue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Time",
                tint = PrimaryGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * ClockTimePickerDialog: Displays Material 3 clock UI dial to pick time visually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockTimePickerDialog(
    title: String,
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMinutes = parseTimeToMinutes(initialTime) ?: (13 * 60 + 22)
    val initHour = initialMinutes / 60
    val initMinute = initialMinutes % 60

    val timePickerState = rememberTimePickerState(
        initialHour = initHour,
        initialMinute = initMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Color(0xFFF3F4F6),
                        selectorColor = PrimaryGreen,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = DarkText,
                        timeSelectorSelectedContainerColor = PrimaryGreen.copy(alpha = 0.15f),
                        timeSelectorSelectedContentColor = PrimaryGreen,
                        timeSelectorUnselectedContainerColor = Color(0xFFF3F4F6),
                        timeSelectorUnselectedContentColor = DarkText,
                        periodSelectorSelectedContainerColor = PrimaryGreen,
                        periodSelectorSelectedContentColor = Color.White,
                        periodSelectorUnselectedContainerColor = Color(0xFFF3F4F6),
                        periodSelectorUnselectedContentColor = MutedText
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hour = timePickerState.hour
                    val minute = timePickerState.minute
                    val isPm = hour >= 12
                    val displayHour = when {
                        hour == 0 -> 12
                        hour > 12 -> hour - 12
                        else -> hour
                    }
                    val amPm = if (isPm) "PM" else "AM"
                    val formatted = String.format(Locale.US, "%02d:%02d %s", displayHour, minute, amPm)
                    onTimeSelected(formatted)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * Data holder for installed app UI representation
 */
private data class InstalledAppInfo(
    val packageName: String,
    val name: String,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap
)

/**
 * SelectAllowedAppsBottomSheet:
 * Visually shows all apps with icons, title, and package.
 * Users toggle apps to add/remove from allowed list just like app block!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAllowedAppsBottomSheet(
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val installedApps = remember {
        val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        list.filter { app ->
            pm.getLaunchIntentForPackage(app.packageName) != null && app.packageName != context.packageName
        }.map { app ->
            val label = app.loadLabel(pm).toString()
            val icon = app.loadIcon(pm).toBitmap(width = 96, height = 96).asImageBitmap()
            InstalledAppInfo(app.packageName, label, icon)
        }.sortedBy { it.name.lowercase() }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, installedApps) {
        val query = searchQuery.trim()
        if (query.isBlank()) installedApps
        else installedApps.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Allowed Apps (${selectedPackages.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = "Toggle apps that stay accessible during focus",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by visible name") },
                placeholder = { Text("Search by visible app name...", color = SubtleText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedText) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = reclaimTextFieldColors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isAllowed = selectedPackages.contains(app.packageName)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePackage(app.packageName) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAllowed) PrimaryGreenLight.copy(alpha = 0.5f) else Color(0xFFF9FAFB)
                        ),
                        border = BorderStroke(1.dp, if (isAllowed) PrimaryGreen.copy(alpha = 0.4f) else Color(0xFFE5E7EB))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    bitmap = app.iconBitmap,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = app.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = SubtleText,
                                        maxLines = 1
                                    )
                                }
                            }

                            Switch(
                                checked = isAllowed,
                                onCheckedChange = { onTogglePackage(app.packageName) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryGreen,
                                    uncheckedThumbColor = SubtleText,
                                    uncheckedTrackColor = PrimaryGreenPill
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helpers for time parsing & dynamic duration calculation
 */
fun parseTimeToMinutes(timeStr: String): Int? {
    if (timeStr.isBlank()) return null
    val clean = timeStr.trim().lowercase()
        .replace(".", "")
        .replace("\u00a0", " ")
        .replace("\u202f", " ")
    val isPm = clean.contains("pm")
    val isAm = clean.contains("am")
    val digitsOnly = clean.replace("am", "").replace("pm", "").trim()
    val parts = digitsOnly.split(":").map { it.trim() }
    if (parts.size < 2) return null
    var hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].take(2).toIntOrNull() ?: return null
    if (isPm && hour < 12) hour += 12
    if (isAm && hour == 12) hour = 0
    return (hour * 60 + minute) % (24 * 60)
}

fun formatCalendarTime(cal: Calendar): String {
    val hour = cal.get(Calendar.HOUR)
    val displayHour = if (hour == 0) 12 else hour
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"
    return String.format(Locale.US, "%02d:%02d %s", displayHour, minute, amPm)
}

fun isScheduleCurrentlyActive(schedule: FocusSchedule): Boolean {
    if (!schedule.isEnabled) return false
    val now = Calendar.getInstance()
    val currentDayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)
    val currentTotalMinutes = currentHour * 60 + currentMinute

    val dayName = when (currentDayOfWeek) {
        Calendar.SATURDAY -> "Sat"
        Calendar.SUNDAY -> "Sun"
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        else -> ""
    }

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
    val yesterdayDayName = when (yesterdayCal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> "Sat"
        Calendar.SUNDAY -> "Sun"
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        else -> ""
    }

    val startMin = parseTimeToMinutes(schedule.startTime) ?: return false
    val endMin = parseTimeToMinutes(schedule.endTime) ?: return false

    val isOvernight = startMin > endMin
    val isInRange = if (!isOvernight) {
        currentTotalMinutes in startMin..endMin
    } else {
        currentTotalMinutes >= startMin || currentTotalMinutes <= endMin
    }

    if (!isInRange) return false

    if (!schedule.isDaily && schedule.selectedDays.isNotBlank()) {
        val allowedDays = schedule.selectedDays.split(",").map { it.trim().lowercase() }
        val effectiveDay = if (isOvernight && currentTotalMinutes <= endMin) yesterdayDayName.lowercase() else dayName.lowercase()
        if (!allowedDays.contains(effectiveDay)) return false
    }

    return true
}

private fun calculateDurationText(startTime: String, endTime: String): String {
    val startMin = parseTimeToMinutes(startTime) ?: return "(0 hr 10 min)"
    val endMin = parseTimeToMinutes(endTime) ?: return "(0 hr 10 min)"
    val diffMinutes = if (endMin >= startMin) {
        endMin - startMin
    } else {
        (24 * 60 - startMin) + endMin
    }
    val hours = diffMinutes / 60
    val mins = diffMinutes % 60
    return "($hours hr $mins min)"
}
