package com.fearmikey.projectreporter.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object ReportDetail : Screen("report_detail/{projectId}") {
        fun createRoute(projectId: String) = "report_detail/$projectId"
    }
}
