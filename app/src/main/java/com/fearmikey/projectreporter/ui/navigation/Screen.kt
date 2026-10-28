package com.fearmikey.projectreporter.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Permissions : Screen("permissions")
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    /** Global bin when no project is given; otherwise scoped to that project's photos and notes. */
    object RecycleBin : Screen("recycle_bin?projectId={projectId}") {
        fun createRoute(projectId: String? = null) =
            if (projectId == null) "recycle_bin" else "recycle_bin?projectId=${Uri.encode(projectId)}"
    }
    object ReportDetail : Screen("report_detail/{projectId}") {
        fun createRoute(projectId: String) = "report_detail/$projectId"
    }
}
