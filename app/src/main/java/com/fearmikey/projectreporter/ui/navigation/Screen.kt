package com.fearmikey.projectreporter.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Permissions : Screen("permissions")
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    object RecycleBin : Screen("recycle_bin")
    object ReportDetail : Screen("report_detail/{projectId}") {
        fun createRoute(projectId: String) = "report_detail/$projectId"
    }
}
