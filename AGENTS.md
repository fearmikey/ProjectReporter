# Project Reporter - Guidelines for AI Agents & Developers

## CRITICAL ARCHITECTURAL CONSTRAINTS

### 1. STRICT 100% FOSS POLICY (F-DROID COMPLIANCE)
* **Zero Proprietary SDKs:** Project Reporter is strictly Free and Open Source Software (FOSS), targeted for distribution on F-Droid and other open-source Android repositories.
* **FORBIDDEN DEPENDENCIES:**
  * **NO** Google Play Services (`com.google.android.gms:*`)
  * **NO** Google Mobile Ads / AdMob (`com.google.android.gms:play-services-ads`)
  * **NO** Google Play Billing (`com.android.billingclient:*`)
  * **NO** ML Kit or closed-source vision/speech SDKs (`com.google.mlkit:*`)
  * **NO** Firebase SDKs that pull in non-free binaries or Play Services requirements
  * **NO** Analytics, tracking, or proprietary telemetry libraries
* **ALLOWED DEPENDENCIES:**
  * Android Jetpack libraries (`androidx.*`)
  * Kotlin standard/coroutines/serialization libraries
  * Open-source third-party dependencies with permissive/copyleft FOSS licenses (Apache 2.0, MIT, BSD, GPL, MPL)
* **NO PRODUCT FLAVORS FOR PROPRIETARY PLUGINS:** Do not add product flavor dimensions or build variants that rely on non-free or Google Play-specific dependencies.

### 2. PRIVACY & OFFLINE FIRST
* The application must remain fully functional offline.
* All user data (projects, reports, photos, notes, inspector profiles, templates, backups) must stay local on device or sync via user-configured open backup/restore options (e.g., local JSON/ZIP export).

## CODEBASE NAVIGATION & AGENT MANIFEST (.agent_manifest.json)

### MANDATORY PRE-FLIGHT CONSULTATION
* **Consult Before Modifying Code:** Before attempting any code modification or adding new features, agents **MUST** consult `.agent_manifest.json` to identify the architectural pattern, component locations, and specific files responsible for data flow. Do not guess file paths.

### MANDATORY POST-SESSION MANIFEST MAINTENANCE
* **End-of-Session Manifest Updates:** At the end of every development session or task where files or architectural components are added, removed, moved, or modified, agents **MUST** update `.agent_manifest.json` to keep the code map and feature data flows up to date.

## RELEASE & PUBLISHING WORKFLOW (F-DROID)

### AUTOMATED F-DROID UPDATES
The app is published on F-Droid with Reproducible Builds enabled and auto-update tracking via Git tags. When helping the user create a new release, agents **MUST** follow this exact workflow to ensure F-Droid successfully picks up the update:

1. **Bump Versions:** Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. **Reproducible Build Configuration:** Ensure `vcsInfo { include = false }` is present in `app/build.gradle.kts` under `buildTypes.release`. This prevents AGP from embedding local Git commit metadata in `META-INF/version-control-info.textproto`, which breaks F-Droid reproducible build verification. Also keep `dependenciesInfo { includeInApk = false; includeInBundle = false }` so AGP doesn't add the Google-encrypted dependency metadata block that F-Droid rejects.
3. **Fastlane Changelog:** Create a new text file at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` containing the concise release notes.
4. **Standard Changelog & Readme:** Update the main `CHANGELOG.md` with detailed bullet points under `## [X.Y.Z] - YYYY-MM-DD` and update the version badge in `README.md`.
5. **Commit & Tag the Release:** Commit the release changes, then create an annotated tag strictly following the `vX.Y.Z` format (e.g., `git tag -a v1.5.2 -m "Release v1.5.2"`).
6. **F-Droid Metadata Recipe (separate follow-up commit):** A commit cannot contain its own hash, so update `fdroid/com.fearmikey.projectreporter.yml` in a second commit *after* tagging:
   * Add a new `Builds:` entry with `versionName` and `versionCode`, and update `CurrentVersion` / `CurrentCode`.
   * Set `commit` to the FULL 40-character hash of the tagged release commit (`git rev-parse vX.Y.Z^{commit}`). Never use a tag name or short hash, and never amend/rebase the tagged commit afterwards.
   * Ensure `gradle:` is set to `- yes` (do NOT use `- foss` as product flavors were removed).
   * Keep `Binaries:` (`.../releases/download/v%v/app-release.apk`) and `AllowedAPKSigningKeys:` (signing cert SHA-256 `48ffd109a23daf7205b50aeccb463667ed31e50c3605280ab7ab654821a70ff0`) unchanged.
   * Categories must come from F-Droid's `config/categories.yml` (currently `Note`, `Writing`).
7. **Push:** `git push origin master vX.Y.Z`. Do NOT use `--tags` (it pushes every local tag, including stale ones). Never force-push published tags.
8. **Reproducible Build Binary:** F-Droid requires the compiled APK to verify reproducible builds. Instruct the user to:
   * Build the signed release APK (`app-release.apk`) from the tagged commit using their secure `ProjectReporter-Release.jks` keystore. The filename must stay `app-release.apk` to match `Binaries:`.
   * Go to GitHub Releases, draft a new release for the newly pushed `vX.Y.Z` tag.
   * Upload the `app-release.apk` file.
   * Verify: with the signature removed, the APK must be byte-identical to a local `./gradlew clean assembleRelease` `app-release-unsigned.apk`.

### PRIVACY OF COMMIT METADATA
* Commits and tags must use `fearmikey@users.noreply.github.com`. Never set a personal or work email in the repo's git config.
