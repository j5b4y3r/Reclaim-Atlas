package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

data class JourneyStage(
    val stageNumber: Int,
    val title: String,
    val description: String,
    val requiredDays: Int
)

@Composable
fun LevelsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val streak by viewModel.dailyStreak.collectAsState()

    val stages = listOf(
        JourneyStage(1, "Doomer", "You're aware of the cycle. That's the first step.", 0),
        JourneyStage(2, "Awakening", "The mental fog clears. You notice the triggers.", 3),
        JourneyStage(3, "Reclaimer", "Taking back your time, attention, and agency.", 7),
        JourneyStage(4, "Quiet Warrior", "You're not just resisting. You're rebuilding.", 14),
        JourneyStage(5, "Unshakable", "The urge arrives, and calmly passes through you.", 30),
        JourneyStage(6, "GigaMind", "Deep focus, grounded strength, unbroken clarity.", 60),
        JourneyStage(7, "True Self", "You're free. You have fully reclaimed your life.", 90)
    )

    // Current stage index based on streak
    val currentStageIndex = when {
        streak >= 90 -> 6
        streak >= 60 -> 5
        streak >= 30 -> 4
        streak >= 14 -> 3
        streak >= 7 -> 2
        streak >= 3 -> 1
        else -> 0
    }
    val currentStage = stages[currentStageIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("levels_screen")
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardSurface)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DarkText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Your journey",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "A progression of self-mastery",
                    fontSize = 12.sp,
                    color = MutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Active Identity Spotlight Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AccentGoldLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryGreenLight)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Current Identity",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = currentStage.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    letterSpacing = (-0.4).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentStage.description,
                    fontSize = 14.sp,
                    color = MutedText,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Path of Growth",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
            letterSpacing = (-0.2).sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Stages List (Quiet, clear, purposeful)
        stages.forEachIndexed { index, stage ->
            val isPassed = index < currentStageIndex
            val isCurrent = index == currentStageIndex
            val isLocked = index > currentStageIndex

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCurrent -> PrimaryGreenLight.copy(alpha = 0.6f)
                        else -> CardSurface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isPassed -> PrimaryGreen
                                    isCurrent -> AccentGold
                                    else -> CardSurface
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isPassed -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            isCurrent -> Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Active",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = SubtleText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stage.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLocked) MutedText else DarkText
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Now",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = stage.description,
                            fontSize = 12.sp,
                            color = if (isLocked) SubtleText else MutedText,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "${stage.requiredDays}d",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) AccentGold else SubtleText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
