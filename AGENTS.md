# AGENTS.md — F-Droid Release Workflow Guidelines

This document outlines instructions and standard operating procedures for AI agents and developers maintaining and releasing **Project Reporter** for **F-Droid**.

---

## 1. F-Droid Compliance & Open Source Rules

- **Strict FOSS Compliance**: All dependencies in `app/build.gradle.kts` and `gradle/libs.versions.toml` must remain 100% Free and Open Source Software (FOSS). Do not include proprietary SDKs, closed-source tracking/analytics, or non-FOSS binaries.
- **Reproducible Builds**: Maintain clean build configs using Android Gradle Plugin (AGP), Kotlin, and Gradle wrapper. Ensure no local path dependencies exist in `build.gradle.kts` files.
- **Licensing**: Ensure all source files and assets comply with open-source license terms.

---

## 2. Release Workflow Protocol

When tasked with preparing or executing a release for F-Droid, execute the following steps in sequence:

### Step 1: Bump Application Version
In `app/build.gradle.kts`:
- Increment `versionCode` by `1` (e.g., `4` → `5`).
- Update `versionName` adhering to Semantic Versioning (`MAJOR.MINOR.PATCH`, e.g., `"1.3.0"` → `"1.4.0"`).

### Step 2: Update Changelog
In `CHANGELOG.md`:
- Insert a new release section header at the top under `# Changelog`:
  ```markdown
  ## [X.Y.Z] - YYYY-MM-DD
  ```
- Categorize changes using standard headings:
  - `### Added`: New user-facing features or developer tooling.
  - `### Changed`: Modifications to existing features or configurations.
  - `### Fixed`: Bug fixes.
  - `### Improved`: Refactorings, UI enhancements, or performance optimizations.

### Step 3: Update README
In `README.md`:
- Update feature highlights or technical stack descriptions if new libraries or capabilities were added in the release.
- Ensure all version-dependent documentation remains accurate.

### Step 4: Verify Build Quality
Before committing, execute Gradle tasks to confirm build integrity:
```bash
./gradlew clean assembleRelease
```
Ensure there are no compilation errors, missing resources, or unresolved dependencies.

### Step 5: Git Commit, Tag, and Push
Execute Git version control commands to record the release and trigger F-Droid build indexers:
1. **Stage files**:
   ```bash
   git add app/build.gradle.kts CHANGELOG.md README.md AGENTS.md
   ```
2. **Commit changes**:
   ```bash
   git commit -m "Bump version to X.Y.Z: Summary of key changes"
   ```
3. **Tag release**:
   ```bash
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   ```
4. **Push commits and tags to GitHub**:
   ```bash
   git push origin master --tags
   ```

---

## 3. Quick Reference Commands

| Task | Command |
| :--- | :--- |
| **Clean Build** | `./gradlew clean` |
| **Build Release APK** | `./gradlew app:assembleRelease` |
| **Git Push Release** | `git push origin master --tags` |
