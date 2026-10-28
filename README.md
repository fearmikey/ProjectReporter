# Project Reporter

## Overview
Project Reporter is an Android application designed for site engineers to document site visits efficiently. It allows users to create project reports, capture photos, add notes, and export the final documentation as PDFs.

## Key Features
- Project Management: Organize and edit site visits by project ID, name, and engineer details.
- Multimedia Documentation: Capture and attach photos directly to project reports with configurable photo watermarks (Timestamp, GPS, Project Details).
- Custom Branding: Company logo upload support with dynamic color scheme extraction using AndroidX Palette.
- Annotation & Rich Notes: Add detailed notes with interactive bullet lists, numbered lists, checklists, and undo/redo support.
- Export Capabilities & Notifications: Generate professional PDF and Word (.docx) reports with completion notifications and quick download folder access.
- Data Backup & Restore: Full JSON and media ZIP export/import for complete cross-device data backup.
- Flexible Role Customization: Role-aware field labeling tailored for engineers, site inspectors, architects, or custom titles.
- Data Persistence: Local database storage using Room for offline access.
- User Profiles: Maintain inspector information for consistent report headers.
- Accessible Dynamic Themes: Custom Material 3 theme engine with WCAG 2.x AA/AAA color contrast validation, High Contrast mode, and AMOLED dark mode.
- Tactile Feedback: System-integrated haptic responses for selection, deletion, and restoration actions.
- Recycle Bin: Safety mechanism for deleted reports.
- Optimized Performance: Memory-cached image loading with Coil, lifecycle-aware state collection, and predictive back navigation support.

## Technical Stack
- Language: Kotlin
- UI Framework: Jetpack Compose with Material 3
- Architecture: MVVM with Hilt for Dependency Injection
- Local Database: Room
- Image Loading: Coil (custom ImageLoader with memory caching and crossfade)
- Async Processing: Kotlin Coroutines & Lifecycle-Aware Flows
- Export Services: Custom iText-based PDF and Apache POI-based Word document generation
- Build Optimization: R8 code minification and resource shrinking

## Getting Started
To build the project, ensure you have the latest version of Android Studio installed.
1. Clone the repository.
2. Sync the project with Gradle files.
3. Run the application on an Android device or emulator (API 30+).

## License
Refer to the project license for usage terms.
