package com.fearmikey.projectreporter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.ui.navigation.Screen
import com.fearmikey.projectreporter.ui.screen.*
import com.fearmikey.projectreporter.ui.theme.ProjectReporterTheme
import com.fearmikey.projectreporter.ui.viewmodel.MainViewModel
import com.fearmikey.projectreporter.ui.viewmodel.ProfileState
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            
            val profileState by mainViewModel.profileState.collectAsState()
            val appTheme by settingsViewModel.appTheme.collectAsState()

            val isDarkTheme = when (appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            ProjectReporterTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val context = LocalContext.current

                LaunchedEffect(profileState) {
                    val state = profileState
                    if (state is ProfileState.Success) {
                        val profile = state.profile
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasCameraPermission) {
                            navController.navigate(Screen.Permissions.route) {
                                popUpTo(0)
                            }
                        } else if (profile == null) {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(0)
                            }
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (profileState is ProfileState.Loading) {
                        // Show nothing or a splash/loading indicator while determining profile state
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        val currentProfile = (profileState as? ProfileState.Success)?.profile

                        NavHost(
                            navController = navController,
                            startDestination = Screen.Dashboard.route,
                            modifier = Modifier.padding(padding)
                        ) {
                            composable(Screen.Permissions.route) {
                                PermissionScreen(
                                    onPermissionsGranted = {
                                        if (currentProfile == null) {
                                            navController.navigate(Screen.Onboarding.route) {
                                                popUpTo(Screen.Permissions.route) { inclusive = true }
                                            }
                                        } else {
                                            navController.navigate(Screen.Dashboard.route) {
                                                popUpTo(Screen.Permissions.route) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                            composable(Screen.Onboarding.route) {
                                ProfileSetupScreen(
                                    onProfileCreated = {
                                        navController.navigate(Screen.Dashboard.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(Screen.Dashboard.route) {
                                MainDashboardScreen(
                                onProjectClick = { projectId ->
                                    navController.navigate(Screen.ReportDetail.createRoute(projectId))
                                },
                                onSettingsClick = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onRecycleBinClick = {
                                    navController.navigate(Screen.RecycleBin.route)
                                }
                            )
                            }
                            composable(Screen.Settings.route) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Screen.RecycleBin.route) {
                            RecycleBinScreen(onBack = { navController.popBackStack() })
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
}
