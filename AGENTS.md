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

## RELEASE WORKFLOW (GitHub Releases + F-Droid reproducible builds)

Releases are agent-run. The signed APK is built by `scripts/build-release.sh` exactly the way F-Droid builds it, so F-Droid can verify its own build against our APK (reproducible builds) and ship it with our signature.

### F-Droid status: RE-CHECK AT THE START OF EVERY RELEASE
**Current state: A (not submitted)** (checked 2026-11-25). The app is not in fdroiddata `origin/master`, there is no MR, and it's not on f-droid.org (API 404).
* The draft recipe is on branch `com.fearmikey.projectreporter` in the worktree `~/fdroid/fdroiddata-projectreporter`, as **one local, unpushed commit**. The fork already has a stale, never-submitted branch with the same name; the first push overwrites it with `--force-with-lease`.
* Plan: open the inclusion MR together with release **1.5.3**, the first release built by the script and named `ProjectReporter-<ver>.apk`.

The state changes when an MR is opened or merged, so re-run these checks before every release and update this section (state, MR link, date):
1. `git -C ~/fdroid/fdroiddata fetch origin master`, then `git -C ~/fdroid/fdroiddata show origin/master:metadata/com.fearmikey.projectreporter.yml`. If the file exists, the app is merged: note `Binaries:`/`binary:`/`AllowedAPKSigningKeys` and the latest builds. Only fetch in `~/fdroid/fdroiddata`. Never switch its checked-out branch, because other sessions use it; use worktrees.
2. Open MRs: `curl -s "https://gitlab.com/api/v4/projects/fdroid%2Ffdroiddata/merge_requests?state=opened&search=com.fearmikey.projectreporter&in=title,description"`. For an open MR, read its recent notes (`.../merge_requests/<iid>/notes?sort=desc`) and pipeline status, and respect reviewer requests.
3. `curl -s https://f-droid.org/api/v1/packages/com.fearmikey.projectreporter` (404 means not published). If it is published, download `https://f-droid.org/repo/com.fearmikey.projectreporter_<versionCode>.apk` to a temp dir and compare its `apksigner verify --print-certs` SHA-256 with the release certificate below.

| State | How you know | What's allowed |
|---|---|---|
| **A. Not submitted** | not on origin/master, no open MR, not published | Update the draft recipe in the worktree; ask before pushing or opening the MR. |
| **B. MR open** | open MR, not on origin/master | Update the MR's recipe: replace the build entry instead of adding more, keep the MR at one commit (amend + `--force-with-lease`), rebase only if GitLab reports a conflict. Ask before every push. |
| **C. Merged, F-Droid-signed** | merged without `AllowedAPKSigningKeys`, or the published signer differs from our cert | **Never** add `Binaries:`/`binary:`/`AllowedAPKSigningKeys`: switching keys would cut off existing F-Droid users. GitHub release + tag only. |
| **C2. Merged, not yet published** | merged, API returns 404 | Same as C or D, depending on the merged recipe. Don't change signing while the first build is pending. |
| **D. Merged, reproducible** | merged with `AllowedAPKSigningKeys`, published signer equals our cert | Build exactly as the merged recipe expects. Asset names must match its `Binaries:` URL; renaming needs an fdroiddata MR merged *first*. |

In every merged state (C, C2, D):
* Never push to, or reuse, the inclusion-MR branch.
* Never open MRs just to bump versions. F-Droid's checkupdates bot adds builds from our tags (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`).
* Only touch fdroiddata when the recipe itself must change, and ask first. Then use a new worktree from a fresh `origin/master`, make the smallest possible change, and run `fdroid rewritemeta` + `fdroid lint`.
* If a bot update is pending, or another open MR touches this app, stop and ask.

### Release facts
* **App:** applicationId `com.fearmikey.projectreporter`. One universal APK, minSdk 30, no own native code, no ABI splits. `versionCode` goes up by 1 per release.
* **Tags:** annotated `vX.Y.Z` on the release commit. The recipe reads `versionCode`/`versionName` from `app/build.gradle.kts` at the tag.
* **Release certificate SHA-256:** `48ffd109a23daf7205b50aeccb463667ed31e50c3605280ab7ab654821a70ff0`, from `~/Documents/Keystores/ProjectReporter-Release.jks`.
* **GitHub asset:** `ProjectReporter-<versionName>.apk`, matching the recipe's `Binaries: https://github.com/fearmikey/ProjectReporter/releases/download/v%v/ProjectReporter-%v.apk`. Releases up to 1.5.2 used `app-release.apk`.
* **Recipe prebuild:** `sed -i -e '/foojay-resolver/d' ../settings.gradle.kts`, because F-Droid's scanner rejects the foojay toolchain plugin. The Gradle daemon then uses a locally installed JDK 21, pinned in `gradle/gradle-daemon-jvm.properties`; locally that's `~/.jdks/ms-21.*`. The script applies the same step.
* **Reproducibility:** keep `vcsInfo { include = false }` (release) and `dependenciesInfo { includeInApk = false; includeInBundle = false }` in `app/build.gradle.kts`. Verified 2026-11-25: v1.5.2 builds byte-identical from fresh clones at different paths, matches the published APK under `fdroidserver.common.verify_apks`, and passed a local `fdroid build` reference-binary check.
* **AGP must stay at 8.13.2 or newer (fixed in 1.5.3):** AGP 8.7.3 bundles R8 8.7.18, which has a race in horizontal class merging (R8 commit `a918b89a194d`, b/434861604) that makes the ART baseline profile non-deterministic. In 1 of 7 builds of v1.5.2, `assets/dexopt/baseline.prof`/`.profm` lost one merged lambda while all dex files stayed identical, so F-Droid's verification failed. AGP 8.13.2 bundles R8 8.13.19, which includes the fix. Never downgrade below an AGP whose R8 has it (R8 8.10.31+, 8.11/8.12 after 2025-08-05, 8.13+). If `fdroid build` ever reports "compared built binary to supplied reference binary but failed" and only `assets/dexopt/baseline.prof*` differ, suspect this class of bug. Don't move the tag: retry, or fix forward in a new version.
* **Build environment:** `JAVA_HOME=~/android-studio/jbr`, `ANDROID_HOME=~/Android/Sdk`; `java` isn't on `PATH` by default (the script handles both). A clean build takes about 5 minutes or more, mostly R8.
* **Signing secrets:** `~/.config/projectreporter-release/release.env` (chmod 600) with `KEYSTORE`, `KS_PASS`, and optionally `KEY_PASS` and `KEY_ALIAS`. Agents must **never read, print or grep that file, and never ask for passwords in chat**. If it's missing, give the user this to run:
  ```
  mkdir -p -m 700 ~/.config/projectreporter-release && ( umask 077
    read -rsp 'Keystore password: ' ks; echo; read -rsp 'Key password (Enter if same): ' kp; echo
    { printf 'KEYSTORE=%q\n' "$HOME/Documents/Keystores/ProjectReporter-Release.jks"; printf 'KS_PASS=%q\n' "$ks"
      [ -n "$kp" ] && printf 'KEY_PASS=%q\n' "$kp"; } > ~/.config/projectreporter-release/release.env )
  ```

### Trigger
When the user says **"do a release"** or **"new release build"**, that approves committing, tagging, pushing `master` and the tag, running the script, and creating the GitHub release. It does **not** approve pushing anything to fdroiddata or GitLab (fork branches, MRs, comments).

### Steps
1. Re-check the F-Droid state (above) and update the status section if it changed.
2. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, and `project_info` in `.agent_manifest.json`.
3. `CHANGELOG.md`: turn `## [Unreleased]` into `## [X.Y.Z] - YYYY-MM-DD`, or add a new section at the top. Never add to an already released section.
4. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` with concise notes (500 characters or fewer).
5. Update `README.md` if features changed (it has no version badge), and update `.agent_manifest.json` for any file or architecture changes.
6. Commit `Release X.Y.Z: <summary>` (no AI attribution), then `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
7. `git push origin master vX.Y.Z`. Never use `--tags`, and never force-push.
8. Run `scripts/build-release.sh vX.Y.Z` in the background. If the shell times out, check `pgrep -f build-release.sh` and `dist/vX.Y.Z/` before retrying.
9. Check that the printed certificate SHA-256 equals the release certificate above. **Stop if anything differs.**
10. `gh release create vX.Y.Z dist/vX.Y.Z/ProjectReporter-X.Y.Z.apk --title "ProjectReporter X.Y.Z" --notes "<the CHANGELOG section>"`. If `gh` is unavailable, ask the user to upload in the browser.
11. F-Droid follow-up, by state:
    * **A:** in `~/fdroid/fdroiddata-projectreporter`, update the build entry (`versionName`, `versionCode`, `commit` = full `git rev-parse vX.Y.Z^{commit}`) plus `CurrentVersion`/`CurrentVersionCode`. Run `fdroid rewritemeta` and `fdroid lint`, then `fdroid build -v com.fearmikey.projectreporter:<versionCode>` with `JAVA_HOME` set. Don't combine `-l` with a versionCode. The build downloads the GitHub APK and must report that the build matches the reference binary. Amend the single commit. **Ask** before `git push --force-with-lease fork com.fearmikey.projectreporter` and before opening the MR ('New app: ProjectReporter (com.fearmikey.projectreporter)', ticking "Enable Reproducible Builds" and saying the APKs come from `scripts/build-release.sh`). After that, the state is B.
    * **B:** same recipe update; ask before pushing, and push only after the GitHub release has its APK.
    * **C/C2/D:** nothing. The bot picks up the tag.

### Release rules
* Never build release APKs from the working tree or Android Studio (incremental builds give a different `classes.dex`).
* Never move or re-create a pushed tag. If a release is broken, fix it forward with a new version.
* Never change the signing key, the signing flags, or (in state D) the asset name.
* Never add path-, time- or machine-dependent build inputs (timestamps, absolute paths, git hashes in `BuildConfig`, and so on).
* If a shell command times out, check whether it actually ran (git reflog, `pgrep`, output files) before retrying.

### PRIVACY OF COMMIT METADATA
* Commits and tags must use `fearmikey@users.noreply.github.com`. Never set a personal or work email in the repo's git config.
