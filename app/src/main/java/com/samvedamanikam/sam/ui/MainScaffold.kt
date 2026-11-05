package com.samvedamanikam.sam.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samvedamanikam.sam.ui.budget.BudgetScreen
import com.samvedamanikam.sam.ui.budget.PageDetailScreen
import com.samvedamanikam.sam.ui.common.LocalSnackbar
import com.samvedamanikam.sam.ui.home.HomeScreen
import com.samvedamanikam.sam.ui.settings.SettingsScreen
import com.samvedamanikam.sam.ui.todos.TodosScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Todos("todos", "Todos", Icons.Filled.CheckCircle),
    Budget("budget", "Budget", Icons.Filled.ShoppingCart),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

/** Bottom navigation; leaves room for a Payments tab later. */
@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true ||
                            (tab == Tab.Budget && backStack?.destination?.route?.startsWith("page/") == true)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(padding)) {
                composable(Tab.Home.route) { HomeScreen(onOpenBudget = { nav.switchTab(Tab.Budget.route) }, onOpenTodos = { nav.switchTab(Tab.Todos.route) }) }
                composable(Tab.Todos.route) { TodosScreen() }
                composable(Tab.Budget.route) { BudgetScreen(onOpenPage = { nav.navigate("page/$it") }) }
                composable(Tab.Settings.route) { SettingsScreen() }
                composable("page/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                    PageDetailScreen(pageId = entry.arguments?.getInt("id") ?: 0, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
