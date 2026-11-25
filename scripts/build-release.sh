#!/usr/bin/env bash
# Builds the signed release APK for a tag, for upload to the GitHub release.
#
# F-Droid builds the same tag from source (reproducible builds) and only publishes it if its
# unsigned APK is byte-identical to ours, then ships it with our signature. So this mirrors
# F-Droid's build exactly: a fresh clone of the tag, `git clean`, the recipe's prebuild step,
# and a non-incremental `assembleRelease`. Never build release APKs from the working tree or
# from Android Studio.
#
# Usage: scripts/build-release.sh <tag> [keystore.jks] [key-alias]
#   e.g. scripts/build-release.sh v1.5.3
#
# Signing settings come from a private file outside the repo, so the script (and agents running
# it) never prompt for or see the passwords. Default ~/.config/projectreporter-release/release.env,
# override with PROJECTREPORTER_RELEASE_CONFIG. chmod 600 it. Contents (bash syntax, quoted):
#   KEYSTORE='/path/to/ProjectReporter-Release.jks'
#   KS_PASS='keystore password'
#   KEY_PASS='key password'   # optional, defaults to KS_PASS
#   KEY_ALIAS='alias'         # optional
# Without the file, pass the keystore as the 2nd argument and type the passwords when asked.
#
# Output: dist/<tag>/ProjectReporter-<versionName>.apk (matches the F-Droid recipe's
#         `Binaries:` URL .../releases/download/v%v/ProjectReporter-%v.apk);
#         set PROJECTREPORTER_RELEASE_OUT=<dir> to write somewhere else.
set -euo pipefail

APP=ProjectReporter
CONFIG=${PROJECTREPORTER_RELEASE_CONFIG:-$HOME/.config/projectreporter-release/release.env}
if [[ -f "$CONFIG" ]]; then
    if (( 8#$(stat -c %a "$CONFIG") & 8#077 )); then
        echo "warning: $CONFIG is readable by other users; run: chmod 600 $CONFIG" >&2
    fi
    # shellcheck source=/dev/null
    source "$CONFIG"
fi

TAG=${1:?usage: $0 <tag> [keystore.jks] [key-alias]}
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Tag must look like vX.Y.Z, got $TAG" >&2; exit 1; }
KEYSTORE=${2:-${KEYSTORE:-}}
[[ -n "$KEYSTORE" ]] || { echo "No keystore: pass it as the 2nd argument or set KEYSTORE in $CONFIG" >&2; exit 1; }
KEYSTORE=$(realpath "$KEYSTORE")
ALIAS=${3:-${KEY_ALIAS:-}}

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
OUT=$(realpath -m "${PROJECTREPORTER_RELEASE_OUT:-$REPO/dist/$TAG}")
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
if [[ -z "${JAVA_HOME:-}" && -d "$HOME/android-studio/jbr" ]]; then
    export JAVA_HOME="$HOME/android-studio/jbr"
fi
# apksigner and keytool run `java` from PATH.
[[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"
APKSIGNER=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)

LOCAL_TAG=$(git -C "$REPO" rev-parse --verify --quiet "refs/tags/$TAG") \
    || { echo "Tag $TAG does not exist" >&2; exit 1; }
# F-Droid builds the tag on GitHub, so it must be the same tag we build here.
REMOTE_TAG=$(git -C "$REPO" ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null | cut -f1 || true)
if [[ -z "$REMOTE_TAG" ]]; then
    echo "warning: $TAG is not on origin yet; push it before publishing these APKs" >&2
elif [[ "$REMOTE_TAG" != "$LOCAL_TAG" ]]; then
    echo "Local $TAG ($LOCAL_TAG) differs from origin's ($REMOTE_TAG); never move a pushed tag" >&2
    exit 1
fi

if [[ -z "${KS_PASS:-}" ]]; then
    read -rsp "Keystore password: " KS_PASS; echo
    read -rsp "Key password (Enter if same): " KEY_PASS; echo
fi
export PR_KS_PASS="$KS_PASS" PR_KEY_PASS="${KEY_PASS:-$KS_PASS}"
unset KS_PASS KEY_PASS
# Fail now rather than after a 5-minute build.
keytool -list -keystore "$KEYSTORE" -storepass:env PR_KS_PASS ${ALIAS:+-alias "$ALIAS"} >/dev/null \
    || { echo "Cannot open $KEYSTORE with the given password${ALIAS:+/alias}" >&2; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
git clone --quiet "$REPO" "$WORK/$APP"
cd "$WORK/$APP"
git checkout --quiet --detach "$TAG"
git reset --quiet --hard
git clean -dffxq
# Same prebuild step as the F-Droid recipe (its scanner rejects the foojay toolchain plugin;
# Gradle then uses a locally installed JDK 21, as pinned in gradle/gradle-daemon-jvm.properties).
sed -i -e '/foojay-resolver/d' settings.gradle.kts

echo "==> Building $TAG ($(git rev-parse HEAD))"
./gradlew --quiet --no-daemon assembleRelease

meta=app/build/outputs/apk/release/output-metadata.json
name=$(grep -o '"versionName": *"[^"]*"' "$meta" | cut -d'"' -f4)
code=$(grep -o '"versionCode": *[0-9]*' "$meta" | grep -o '[0-9]*$')
[[ "v$name" == "$TAG" ]] || { echo "versionName $name does not match tag $TAG" >&2; exit 1; }

mkdir -p "$OUT"
apk="$OUT/$APP-$name.apk"
# F-Droid copies our signature onto its own unsigned build, so signing may only add the
# APK Signing Block: no v1 JAR signature (rewrites META-INF; minSdk 30 doesn't need it) and
# no re-alignment of entries (apksigner moves them unless --alignment-preserved).
"$APKSIGNER" sign --ks "$KEYSTORE" ${ALIAS:+--ks-key-alias "$ALIAS"} \
    --ks-pass env:PR_KS_PASS --key-pass env:PR_KEY_PASS \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --v4-signing-enabled false --alignment-preserved true \
    --out "$apk" app/build/outputs/apk/release/app-release-unsigned.apk
"$APKSIGNER" verify "$apk" 2>/dev/null

echo
echo "versionName $name, versionCode $code"
echo "$apk"
"$APKSIGNER" verify --print-certs "$apk" 2>/dev/null | grep -i 'certificate SHA-256'
echo "Upload $apk to the GitHub release for $TAG."
