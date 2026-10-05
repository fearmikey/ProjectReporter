# Changelog

All notable changes to this project will be documented in this file.

## [1.4.0] - 2026-08-31
### Added
- `AGENTS.md` specifying F-Droid release workflow, build verification, and version control standards.
- Edit Site Report dialog (`EditProjectDialog`) for editing project numbers, names, and engineer details.
- Custom company logo image upload with automatic color extraction via AndroidX Palette API.
- Photo watermark configuration options for timestamp, GPS location, and project details in settings.

### Improved
- CameraPreview component with enhanced capture and preview controls.
- Profile setup screen and settings repository preferences.
- Theme system supporting dynamic branding color schemes.

## [1.3.0] - 2026-08-31
### Added
- Predictive back gesture support via `enableOnBackInvokedCallback`.
- Custom Coil `ImageLoader` configuration with crossfade transitions and memory caching controls.

### Improved
- Standardized UI state collection across screens using lifecycle-aware `collectAsStateWithLifecycle()`.
- Enabled R8 code minification and resource shrinking for release builds, with ProGuard rules for Apache POI dependencies.
- Updated core libraries including Compose BOM, Room, Hilt, Lifecycle, CameraX, and Navigation Compose.

## [1.2.0] - 2026-07-30
### Added
- Word document (.docx) export support using Apache POI.
- Professional PDF formatting with consistent headers and page numbering on all pages.

### Fixed
- PDF text wrapping issues for long notes and photo annotations.
- Site observations cutting off in PDF grid layouts.

### Improved
- Photo detail view: Optimized note visibility with tightened layout.
- Photo detail view: Implemented auto-scroll to keep cursor in view when typing or keyboard opens.
- Export UI: Added format selector for choosing between PDF and Word.

## [1.1.0] - 2026-07-23
### Added
- Project documentation (README.md and CHANGELOG.md).
- Settings screen for theme management.
- Recycle bin functionality for project recovery.
- PDF export service for site reports.

## [1.0.0] - Initial Release
### Added
- Core project reporting functionality.
- Camera integration for site photos.
- Local storage with Room database.
