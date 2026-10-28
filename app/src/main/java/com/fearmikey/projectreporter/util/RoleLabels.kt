package com.fearmikey.projectreporter.util

/**
 * Builds role-aware field labels so the app isn't tied to a single profession.
 * With a role such as "site inspector" the labels read "Site Inspector Name";
 * without a role they fall back to neutral wording.
 */
object RoleLabels {
    private const val MAX_ROLE_LENGTH = 32

    /** Title-cased role, or null if no usable role is set. */
    fun roleTitle(role: String?): String? {
        val trimmed = role?.trim()?.replace(Regex("\\s+"), " ")
        if (trimmed.isNullOrEmpty() || trimmed.length > MAX_ROLE_LENGTH) return null
        return trimmed.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }

    /** Label for the profile owner's own name field (onboarding / profile editing). */
    fun yourNameLabel(role: String?): String = roleTitle(role)?.let { "$it Name" } ?: "Your Name"

    /** Label for the person preparing a report (project dialogs). */
    fun nameLabel(role: String?): String = roleTitle(role)?.let { "$it Name" } ?: "Prepared By"

    fun phoneLabel(role: String?): String = roleTitle(role)?.let { "$it Phone Number" } ?: "Phone Number"

    /** Short label used in exported report tables. */
    fun exportPhoneLabel(role: String?): String = roleTitle(role)?.let { "$it Phone" } ?: "Contact Phone"

    /** Prefix shown before the preparer's name on project cards. */
    fun cardPrefix(role: String?): String = roleTitle(role) ?: "By"
}
