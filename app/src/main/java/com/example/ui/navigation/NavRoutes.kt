package com.example.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Home")
    object Blocking : Screen("blocking", "Block")
    object Blocklist : Screen("blocklist", "Blocklist")
    object Progress : Screen("progress", "Progress")
    object More : Screen("more", "More")

    object Focus : Screen("focus", "Focus Mode")
    object Levels : Screen("levels", "Your Journey")
    object Habits : Screen("habits", "Daily Habits")
    object Journal : Screen("journal", "Journal")
    object Onboarding : Screen("onboarding", "Onboarding")
    object Accountability : Screen("accountability", "Accountability")
    object CustomizeBlockScreen : Screen("customize_block_screen", "Customize Block Screen")
}

