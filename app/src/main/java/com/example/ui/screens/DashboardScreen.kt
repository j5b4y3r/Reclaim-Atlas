package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.firebase.SyncState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import com.example.ui.viewmodel.MainViewModel

data class CurrentStageData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val stageIndex: Int
)

fun resolveStage(streakDays: Int): CurrentStageData {
    return when {
        streakDays >= 90 -> CurrentStageData(
            title = "True Self",
            description = "You are free. You have fully reclaimed your life.",
            icon = Icons.Default.Shield,
            stageIndex = 5
        )
        streakDays >= 60 -> CurrentStageData(
            title = "GigaMind",
            description = "Deep focus, grounded strength, unbroken clarity.",
            icon = Icons.Default.Shield,
            stageIndex = 4
        )
        streakDays >= 30 -> CurrentStageData(
            title = "Unshakable",
            description = "The urge arrives, and calmly passes through you.",
            icon = Icons.Default.Shield,
            stageIndex = 3
        )
        streakDays >= 14 -> CurrentStageData(
            title = "Quiet Warrior",
            description = "You're not just resisting. You're rebuilding.",
            icon = Icons.Default.Shield,
            stageIndex = 2
        )
        streakDays >= 7 -> CurrentStageData(
            title = "Reclaimer",
            description = "Taking back your time, attention, and agency.",
            icon = Icons.Default.Terrain,
            stageIndex = 2
        )
        streakDays >= 3 -> CurrentStageData(
            title = "Awakening",
            description = "The mental fog clears. You notice the triggers.",
            icon = Icons.Default.Spa,
            stageIndex = 1
        )
        else -> CurrentStageData(
            title = "Doomer",
            description = "Aware of the cycle. Taking the courageous first step.",
            icon = Icons.Default.Home,
            stageIndex = 0
        )
    }
}

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToFocus: () -> Unit,
    onNavigateToLevels: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onNavigateToJournal: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val streak by viewModel.dailyStreak.collectAsState()
    val streakStartTime by viewModel.streakStartTimestamp.collectAsState()
    val currentLiveTime by viewModel.currentLiveTimeMs.collectAsState()
    val bestStreak by viewModel.bestStreakDays.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val totalMinutes by viewModel.totalTimeReclaimedMinutes.collectAsState()
    val habits by viewModel.allHabits.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var relapseReason by remember { mutableStateOf("") }
    var selectedRelapseMood by remember { mutableStateOf("Reflective") }

    val currentStage = resolveStage(streak)
    val streakDisplay = if (streak == 1) "1 day" else "$streak days"

    // Live precise streak duration breakdown (e.g. 0 days, 1h, 3m, 3s)
    val effectiveStart = if (streakStartTime > 0L) streakStartTime else currentLiveTime
    val elapsedMs = (currentLiveTime - effectiveStart).coerceAtLeast(0L)
    val elapsedTotalSeconds = elapsedMs / 1000L
    val elapsedDays = (elapsedTotalSeconds / (24 * 3600L)).toInt()
    val elapsedHours = ((elapsedTotalSeconds % (24 * 3600L)) / 3600L).toInt()
    val elapsedMinutes = ((elapsedTotalSeconds % 3600L) / 60L).toInt()
    val elapsedSeconds = (elapsedTotalSeconds % 60L).toInt()

    // Formatted real time reclaimed
    val timeReclaimedText = if (totalMinutes >= 60) {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    } else {
        "${totalMinutes}m"
    }

    val bestStreakValue = maxOf(bestStreak, streak)
    val bestStreakText = "$bestStreakValue ${if (bestStreakValue == 1) "day" else "days"}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("dashboard_screen")
    ) {
        // 1. Top Bar / Greeting with Reclaim Logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_app_logo),
                    contentDescription = "Reclaim Logo",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (currentUser != null) "Welcome back, ${currentUser?.displayName ?: "Reclaimer"}" else "Good day,",
                        fontSize = 13.sp,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (streak == 0) "Begin anew today." else "You're doing great.",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.3).sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (syncState is SyncState.Syncing) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Syncing with cloud",
                            tint = PrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardSurface)
                        .testTag("dashboard_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = DarkText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Hero Card: Calm Landscape + Real Streak
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .testTag("hero_streak_card"),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryGreen)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_streak_card_bg),
                    contentDescription = "Calm mountain view",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Atmospheric overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x15000000),
                                    Color(0x660F172A),
                                    Color(0xCC0B1220)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top: Single small subtle Reset Button on Top Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SINGLE SMALL RESET BUTTON
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable { showResetDialog = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("reset_streak_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Streak",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Reset",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Middle: Single inline row "1 days 0h 9m 3s" with small semi-transparent h m s
                    Column {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Dominant "X days" (or "1 day")
                            Text(
                                text = "$streakDisplay ",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-1.0).sp
                            )

                            // Inline small semi-transparent time counting: "0h 9m 3s" with no background and baseline bottom aligned
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                            ) {
                                Text(
                                    text = "$elapsedHours",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.70f)
                                )
                                Text(
                                    text = "h ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.50f)
                                )
                                Text(
                                    text = "$elapsedMinutes",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.70f)
                                )
                                Text(
                                    text = "m ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.50f)
                                )
                                Text(
                                    text = "$elapsedSeconds",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.70f)
                                )
                                Text(
                                    text = "s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.50f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (streak == 0) "Clean & building momentum" else "In control",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Bottom Motivational sentence
                    Text(
                        text = if (streak == 0) {
                            "Recovery is not linear. Standing back up immediately builds lasting strength."
                        } else {
                            "You're not just quitting what hurts you; you're building the life you want."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. "Your journey" section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your journey",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
                letterSpacing = (-0.2).sp
            )

            Row(
                modifier = Modifier
                    .clickable { onNavigateToLevels() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View all",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Journey Card (Dynamic Real Rank + real stage progression)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToLevels() }
                .testTag("journey_level_card"),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentStage.icon,
                            contentDescription = "Rank Emblem",
                            tint = PrimaryGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Current rank",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MutedText
                        )
                        Text(
                            text = currentStage.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            letterSpacing = (-0.2).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentStage.description,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MutedText
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View all ranks",
                        tint = MutedText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Real Progression Bar matching streak stage
                JourneyProgressionBar(activeStageIndex = currentStage.stageIndex)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Statistics: Real Data Unified Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Time Reclaimed (Real Focus + Urges saved)
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = timeReclaimedText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Time reclaimed",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }

                // Vertical divider line
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(1.dp)
                        .background(Color(0xFFF1F5F9))
                )

                // Column 2: Urges Resisted (Actual DB block attempts)
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${allAttempts.size}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Urges resisted",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }

                // Vertical divider line
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .width(1.dp)
                        .background(Color(0xFFF1F5F9))
                )

                // Column 3: Best Streak (Real stored best streak)
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = bestStreakText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Best streak",
                        fontSize = 11.sp,
                        color = MutedText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Navigation Grid
        Text(
            text = "Companion practices",
            fontSize = 14.sp,
            color = MutedText
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardFeatureShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Spa,
                title = "Daily Habits",
                onClick = onNavigateToHabits
            )
            DashboardFeatureShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Book,
                title = "Journal",
                onClick = onNavigateToJournal
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardFeatureShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Landscape,
                title = "Your Journey",
                onClick = onNavigateToLevels
            )
            DashboardFeatureShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.HourglassTop,
                title = "Focus Mode",
                onClick = onNavigateToFocus
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
    }

    // RELAPSE / STREAK RESET CONFIRMATION DIALOG
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Reset Streak",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = DarkText
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Acknowledge a slip without shame. Resetting starts your active counter from Day 0 while preserving your personal best record and reflection history.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = relapseReason,
                        onValueChange = { relapseReason = it },
                        label = { Text("What triggered this? (Optional)") },
                        placeholder = { Text("e.g. Late night scrolling, stress") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetStreak(relapseReason.trim(), selectedRelapseMood)
                        showResetDialog = false
                        relapseReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset to Day 0", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = MutedText)
                }
            }
        )
    }
}

@Composable
fun DashboardFeatureShortcut(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SubtleText,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun JourneyProgressionBar(activeStageIndex: Int) {
    val stages = listOf(
        Pair("Doomer", Icons.Default.Home),
        Pair("Awakening", Icons.Default.Spa),
        Pair("Reclaimer", Icons.Default.Terrain),
        Pair("Unshakable", Icons.Default.Lock),
        Pair("GigaMind", Icons.Default.Lock),
        Pair("True Self", Icons.Default.Lock)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, (name, icon) ->
            val isPast = index < activeStageIndex
            val isActive = index == activeStageIndex
            val isLocked = index > activeStageIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isActive) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> PrimaryGreen
                                isPast -> PrimaryGreenLight
                                else -> Color(0xFFF1F5F9)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = when {
                            isActive -> Color.White
                            isPast -> PrimaryGreen
                            else -> SubtleText
                        },
                        modifier = Modifier.size(if (isActive) 18.dp else 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = name,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) PrimaryGreen else MutedText,
                    maxLines = 1
                )
            }
        }
    }
}
