package com.letters2my.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.letters2my.app.ui.backup.BackupScreen
import com.letters2my.app.ui.family.FamilyScreen
import com.letters2my.app.ui.letters.LetterDetailScreen
import com.letters2my.app.ui.letters.LetterEditorScreen
import com.letters2my.app.ui.letters.LettersScreen
import com.letters2my.app.ui.people.PeopleScreen
import com.letters2my.app.ui.settings.SettingsScreen
import com.letters2my.app.ui.timeline.TimelineScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    Tab("letters", "Letters", Icons.Default.Email),
    Tab("timeline", "Timeline", Icons.Default.CalendarMonth),
    Tab("family", "Family", Icons.Default.People),
    Tab("people", "People", Icons.Default.Group),
    Tab("settings", "Settings", Icons.Default.Settings)
)

/**
 * Root navigation. Letters -> list -> editor -> detail is a nested flow;
 * the bottom bar is the main tab surface. On tablets/foldables the bar is
 * replaced by a NavigationRail (adaptive Material 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LettersToMyApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isDetailOrEditor = currentRoute?.startsWith("letter/") == true ||
        currentRoute?.startsWith("editor/") == true

    Scaffold(
        bottomBar = {
            if (!isDetailOrEditor) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo("letters") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "letters",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("letters") {
                LettersScreen(
                    onOpenEditor = { id -> navController.navigate("editor/${id ?: "new"}") },
                    onOpenDetail = { id -> navController.navigate("letter/$id") }
                )
            }
            composable("backup") {
                BackupScreen(onClose = { navController.popBackStack() })
            }
            composable("timeline") { TimelineScreen() }
            composable("family") { FamilyScreen() }
            composable("people") { PeopleScreen() }
            composable("settings") { SettingsScreen() }

            composable(
                "editor/{letterId}",
                arguments = listOf(navArgument("letterId") { type = NavType.StringType })
            ) { entry ->
                val letterId = entry.arguments?.getString("letterId")
                LetterEditorScreen(
                    letterId = letterId?.takeIf { it != "new" },
                    onClose = { navController.popBackStack() }
                )
            }
            composable(
                "letter/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                LetterDetailScreen(
                    letterId = id,
                    onEdit = { navController.navigate("editor/$id") },
                    onClose = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack("letters", inclusive = false)
                    }
                )
            }
        }
    }
}