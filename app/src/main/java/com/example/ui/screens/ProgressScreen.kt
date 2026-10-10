package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentGoldLight
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.PrimaryGreen
import com.example.ui.theme.PrimaryGreenLight
import com.example.ui.theme.SubtleText
import com.example.ui.theme.DarkText
import com.example.ui.theme.MutedText
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ProgressScreen(
    viewModel: MainViewModel
) {
    val streak by viewModel.dailyStreak.collectAsState()
    val streakStartTime by viewModel.streakStartTimestamp.collectAsState()
    val currentLiveTime by viewModel.currentLiveTimeMs.collectAsState()
    val bestStreak by viewModel.bestStreakDays.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    var relapseReason by remember { mutableStateOf("") }

    val effectiveStart = if (streakStartTime > 0L) streakStartTime else currentLiveTime
    val elapsedMs = (currentLiveTime - effectiveStart).coerceAtLeast(0L)
    val elapsedTotalSeconds = elapsedMs / 1000L
    val elapsedDays = (elapsedTotalSeconds / (24 * 3600L)).toInt()
    val elapsedHours = ((elapsedTotalSeconds % (24 * 3600L)) / 3600L).toInt()
    val elapsedMinutes = ((elapsedTotalSeconds % 3600L) / 60L).toInt()
    val elapsedSeconds = (elapsedTotalSeconds % 60L).toInt()

    val bestStreakDisplay = maxOf(bestStreak, streak)
    val completedSessions = allSessions.filter { it.isCompleted }

    // Compute real last 7 calendar days (oldest to today) based on actual streak start time
    val last7Days = remember(currentLiveTime / 60_000L, effectiveStart) {
        val dayLabels = arrayOf("S", "M", "T", "W", "T", "F", "S")
        (6 downTo 0).map { daysAgo ->
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = currentLiveTime
                add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val label = dayLabels[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
            val endOfThatDay = (cal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
            }.timeInMillis
            val isCompletedDay = streakStartTime > 0L && endOfThatDay >= streakStartTime
            Pair(label, isCompletedDay)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("progress_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Progress",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Small steps. Unbroken consistency.",
                    fontSize = 14.sp,
                    color = MutedText
                )
            }

            IconButton(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7))
                    .testTag("progress_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Streak",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Streak Card with Tasteful Gold Flame Badge
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showResetDialog = true }
                .testTag("streak_progress_card"),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AccentGoldLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak Flame",
                            tint = AccentGold,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Current streak",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MutedText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Single inline row: "1 days 0h 9m 3s"
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (streak == 1) "1 day " else "$streak days ",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                letterSpacing = (-0.3).sp
                            )
                            // Semi-transparent small time counter: "0h 9m 3s" with no background, bottom aligned
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                            ) {
                                Text(
                                    text = "$elapsedHours",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = "h ",
                                    fontSize = 10.sp,
                                    color = DarkText.copy(alpha = 0.45f)
                                )
                                Text(
                                    text = "$elapsedMinutes",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = "m ",
                                    fontSize = 10.sp,
                                    color = DarkText.copy(alpha = 0.45f)
                                )
                                Text(
                                    text = "$elapsedSeconds",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkText.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = "s",
                                    fontSize = 10.sp,
                                    color = DarkText.copy(alpha = 0.45f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Personal best: $bestStreakDisplay ${if (bestStreakDisplay == 1) "day" else "days"}",
                            fontSize = 12.sp,
                            color = SubtleText
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFEF3C7))
                        .clickable { showResetDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Section: "Last 7 days"
        Text(
            text = "Last 7 days",
            fontSize = 16.sp,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                last7Days.forEach { (day, isCompletedDay) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MutedText
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isCompletedDay) PrimaryGreen else PrimaryGreenLight.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompletedDay) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed day",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Compass quote card (Quiet, reassuring tone)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryGreenLight.copy(alpha = 0.65f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Compass",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "You're not merely avoiding impulses. You're walking toward stillness and self-respect.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = DarkText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Discipline Metrics
        Text(
            text = "Discipline summary",
            fontSize = 16.sp,
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
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total distractions blocked",
                        fontSize = 14.sp,
                        color = MutedText
                    )
                    Text(
                        text = "${allAttempts.size} times",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Digital freedom score",
                        fontSize = 14.sp,
                        color = MutedText
                    )
                    Text(
                        text = if (allAttempts.isEmpty() && completedSessions.isEmpty()) "100%" else "${(100 - (allAttempts.count { it.wasOverridden } * 5)).coerceIn(70, 100)}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Urges transformed into focus",
                        fontSize = 14.sp,
                        color = MutedText
                    )
                    Text(
                        text = "${completedSessions.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

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
                        viewModel.resetStreak(relapseReason.trim())
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
