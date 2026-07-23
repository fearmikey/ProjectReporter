# Implementation Plan - App Metadata & Versioning

This plan adds dynamic app metadata to the settings menu and updates the project versioning.

## User Review Required

> [!WARNING]
> - **GitHub Publishing**: The `git` command is not available in my execution environment. I will perform all code changes, but you will need to manually commit and push to GitHub once I'm finished.

## Proposed Changes

### 1. Dynamic Versioning & Build Config
- **[MODIFY] [build.gradle.kts](file:///C:/Users/mrohloff/AndroidStudioProjects/ProjectReporter/app/build.gradle.kts)**:
    - Enable `buildConfig = true` in `buildFeatures`.
    - Update `versionCode` to `2`.
    - Update `versionName` to `"1.1.0"`.

### 2. Settings Menu Metadata
- **[MODIFY] [SettingsScreen.kt](file:///C:/Users/mrohloff/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/ui/screen/SettingsScreen.kt)**:
    - Add a footer section to the `Column`.
    - Display "ProjectReporter" (App Name).
    - Display `BuildConfig.VERSION_NAME` (Dynamic Version).
    - Add a clickable "GitHub Repository" link using `UriHandler`.

## Verification Plan

### Automated Tests
- Build verification to ensure `BuildConfig` is generated and accessible.

### Manual Verification
- Open Settings and verify the app name and version (1.1.0) appear at the bottom.
- Tap the GitHub link to ensure it attempts to open the browser.
