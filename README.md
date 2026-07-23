# Project Reporter

## Overview
Project Reporter is an Android application designed for site engineers to document site visits efficiently. It allows users to create project reports, capture photos, add notes, and export the final documentation as PDF files.

## Key Features
- Project Management: Organize site visits by project ID and name.
- Multimedia Documentation: Capture and attach photos directly to project reports.
- Annotation: Add detailed notes to specific project entries.
- Export Capabilities: Generate professional PDF reports for sharing and archival.
- Data Persistence: Local database storage using Room for offline access.
- User Profiles: Maintain engineer information for consistent report headers.
- Recycle Bin: Safety mechanism for deleted reports.

## Technical Stack
- Language: Kotlin
- UI Framework: Jetpack Compose
- Architecture: MVVM with Hilt for Dependency Injection
- Local Database: Room
- Image Loading: Coil
- Background Tasks: Kotlin Coroutines and Flow
- Export Service: Custom PDF generation logic

## Getting Started
To build the project, ensure you have the latest version of Android Studio installed.
1. Clone the repository.
2. Sync the project with Gradle files.
3. Run the application on an Android device or emulator (API 30+).

## License
Refer to the project license for usage terms.
