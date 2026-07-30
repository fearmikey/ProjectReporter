# Tasks - Default Flash Mode Settings

- [x] Update Data Layer
    - [x] Modify `SettingsRepository.kt`: Add `FlashModeOption` and DataStore logic
- [x] Update UI Layer
    - [x] Modify `SettingsViewModel.kt`: Expose new settings
    - [x] Modify `SettingsScreen.kt`: Add Flash Mode setting and dialog
- [x] Update Camera Layer
    - [x] Modify `ReportDetailScreen.kt`: Pass flash setting to camera
    - [x] Modify `CameraPreview.kt`: Initialize flash mode from settings
- [x] Verification
    - [x] Verify default is Auto on first launch
    - [x] Verify setting changes persist and affect camera
    - [x] Verify build
