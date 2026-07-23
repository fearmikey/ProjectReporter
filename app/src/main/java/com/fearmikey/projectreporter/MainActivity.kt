package com.fearmikey.projectreporter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fearmikey.projectreporter.ui.navigation.Screen
import com.fearmikey.projectreporter.ui.screen.MainDashboardScreen
import com.fearmikey.projectreporter.ui.screen.ReportDetailScreen
import com.fearmikey.projectreporter.ui.theme.ProjectReporterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectReporterTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(Screen.Dashboard.route) {
                            MainDashboardScreen(
                                onProjectClick = { projectId ->
                                    navController.navigate(Screen.ReportDetail.createRoute(projectId))
                                }
                            )
                        }
                        composable(
                            route = Screen.ReportDetail.route,
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
                            ReportDetailScreen(
                                projectId = projectId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
