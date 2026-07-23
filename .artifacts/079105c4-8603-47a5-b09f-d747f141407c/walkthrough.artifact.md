# App Versioning & Metadata Update Walkthrough

I have updated the app version to `1.1.0` and added dynamic metadata to the settings menu.

## Key Changes

### 1. Dynamic Settings Footer
- **App Metadata**: The bottom of the Settings menu now displays the app name ("ProjectReporter") and the current version.
- **Dynamic Versioning**: The version is pulled directly from the app's build configuration, ensuring it remains accurate after future updates.
- **Developer Link**: Added a clickable "GitHub Repository" link that opens your GitHub profile in the system browser.

### 2. Project Version Update
- **Version Name**: Updated to `1.1.0`.
- **Version Code**: Incremented to `2`.
- **Build Features**: Enabled `buildConfig` to support future automated metadata requirements.

## How to Test

1. **Verify Metadata**: Navigate to the **Settings** menu. Scroll to the bottom to see "ProjectReporter", "Version 1.1.0", and the GitHub link.
2. **Test Link**: Tap the GitHub link to verify it opens the web browser.

---
> [!IMPORTANT]
> **Manual Action Required**: I have updated the code and confirmed the build, but I cannot perform the `git commit` or `push` directly. Please commit these changes and push them to your GitHub repository to complete the publication.

render_diffs(file:///C:/Users/mrohloff/AndroidStudioProjects/ProjectReporter/app/build.gradle.kts)
render_diffs(file:///C:/Users/mrohloff/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/ui/viewmodel/SettingsViewModel.kt)
render_diffs(file:///C:/Users/mrohloff/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/ui/screen/SettingsScreen.kt)
