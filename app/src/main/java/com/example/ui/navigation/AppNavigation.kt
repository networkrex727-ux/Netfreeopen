package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.active.ActiveSessionScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.guest.GuestScreen
import com.example.ui.home.HomeScreen
import com.example.ui.host.HostScreen

object Screen {
    const val Login = "login"
    const val Home = "home"
    const val Host = "host"
    const val Guest = "guest"
    const val ActiveSession = "active_session/{token}/{isHost}/{limitMB}"
    
    fun activeSessionRoute(token: String, isHost: Boolean, limitMB: Long): String {
        return "active_session/$token/$isHost/$limitMB"
    }
}

@Composable
fun AppNavigation(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Login) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home) {
            HomeScreen(
                onNavigateToHost = { navController.navigate(Screen.Host) },
                onNavigateToGuest = { navController.navigate(Screen.Guest) },
                onLogout = {
                    navController.navigate(Screen.Login) {
                        popUpTo(Screen.Home) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Host) {
            HostScreen(
                onNavigateToActive = { token, limitMB ->
                    navController.navigate(Screen.activeSessionRoute(token, true, limitMB)) {
                        popUpTo(Screen.Host) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Guest) {
            GuestScreen(
                onNavigateToActive = { token, limitMB ->
                    navController.navigate(Screen.activeSessionRoute(token, false, limitMB)) {
                        popUpTo(Screen.Guest) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.ActiveSession,
            arguments = listOf(
                navArgument("token") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType },
                navArgument("limitMB") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token") ?: ""
            val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
            val limitMB = backStackEntry.arguments?.getLong("limitMB") ?: 1024L
            
            ActiveSessionScreen(
                token = token,
                isHost = isHost,
                limitMB = limitMB,
                onSessionEnded = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.ActiveSession) { inclusive = true }
                    }
                }
            )
        }
    }
}
