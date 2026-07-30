# Implementation Plan - Default Flash Mode Settings

This plan outlines the steps to allow users to set a default flash mode in the app settings, which will then be applied when the camera is opened.

## User Review Required

> [!NOTE]
> The camera will now default to "Automatic" flash mode by default, as requested. Users can change this behavior in the "General" section of the settings menu.

## Proposed Changes

### Data Layer

#### [MODIFY] [SettingsRepository.kt](file:///home/michael/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/data/repository/SettingsRepository.kt)
- Add `FlashModeOption` enum (OFF, ON, AUTO).
- Add a DataStore key for `default_flash_mode`.
- Update `ThemeSettings` (or create a new `GeneralSettings` data class) to include `defaultFlashMode`.
- Expose the new setting in `themeSettings` flow (to keep it centralized for now, or split if it grows).
- Add `setDefaultFlashMode(option: FlashModeOption)`.

### UI Layer

#### [MODIFY] [SettingsViewModel.kt](file:///home/michael/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/ui/viewmodel/SettingsViewModel.kt)
- Expose the default flash mode from the repository.
- Add a method to update the default flash mode.

#### [MODIFY] [SettingsScreen.kt](file:///home/michael/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/ui/screen/SettingsScreen.kt)
- Add a "General" section (or add to an existing one).
- Add a `ListItem` for "Default Flash Mode" that opens a selection dialog.

### Camera Layer

#### [MODIFY] [CameraPreview.kt](file:///home/michael/AndroidStudioProjects/ProjectReporter/app/src/main/java/com/fearmikey/projectreporter/ui/component/CameraPreview.kt)
- Update `CameraPreview` to accept a `defaultFlashMode` parameter (or collect it from a ViewModel if we add one specifically for the camera, but for now we can pass it through `ReportDetailScreen`).
- Map the `FlashModeOption` to CameraX's `ImageCapture.FLASH_MODE_*`.
- Initialize `flashMode` state with the value from settings.

## Verification Plan

### Manual Verification
1.  **Settings**: Go to settings and change "Default Flash Mode" to "On".
2.  **Camera**: Open the camera and verify the flash icon shows "On" by default.
3.  **Persistence**: Change the setting to "Off", restart the app, and verify the camera defaults to "Off".
4.  **Automatic Default**: Ensure that on first launch (or after clearing data), the default is "Auto".
