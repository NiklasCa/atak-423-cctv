#!/usr/bin/env bash
# ==============================================================================
# Package Source Archive for TAK.gov Third-Party Pipeline
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

# Root folder name inside the zip determines the built APK name prefix on tak.gov
ROOT_FOLDER_NAME="${1:-$(basename "$PROJECT_DIR")}"
OUTPUT_DIR="${2:-$(dirname "$PROJECT_DIR")}"
ZIP_NAME="${ROOT_FOLDER_NAME}-source.zip"
OUTPUT_ZIP="${OUTPUT_DIR}/${ZIP_NAME}"

echo "=========================================================="
echo " Preparing TAK.gov Third-Party Source Archive"
echo " Project Directory  : ${PROJECT_DIR}"
echo " Archive Root Folder: ${ROOT_FOLDER_NAME}"
echo " Output Archive     : ${OUTPUT_ZIP}"
echo "=========================================================="

# 1. Pre-flight verification
if grep -q "org.gradle.java.home" "${PROJECT_DIR}/gradle.properties" 2>/dev/null; then
    echo "ERROR: 'org.gradle.java.home' found in gradle.properties!"
    echo "The ephemeral build server on tak.gov will fail if a local java.home path is set."
    echo "Please remove it from gradle.properties (use ~/.gradle/gradle.properties for local builds)."
    exit 1
fi

if [ ! -f "${PROJECT_DIR}/gradlew" ]; then
    echo "ERROR: gradlew script missing from ${PROJECT_DIR}!"
    exit 1
fi

chmod +x "${PROJECT_DIR}/gradlew"

# 2. Create a clean temporary directory for staging
STAGING_DIR="$(mktemp -d -t takgov_pkg_XXXXXX)"
cleanup() {
    rm -rf "$STAGING_DIR"
}
trap cleanup EXIT

TARGET_DIR="${STAGING_DIR}/${ROOT_FOLDER_NAME}"
mkdir -p "$TARGET_DIR"

echo "Staging files into ${ROOT_FOLDER_NAME}/..."

# Use rsync to copy only source files, strictly excluding build/temp/cache/IDE files
rsync -a \
    --exclude=".git/" \
    --exclude=".gitignore" \
    --exclude=".gradle/" \
    --exclude=".takdev/" \
    --exclude=".idea/" \
    --exclude="build/" \
    --exclude="*/build/" \
    --exclude="local.properties" \
    --exclude="*.apk" \
    --exclude="*.aar" \
    --exclude="*.keystore" \
    --exclude="generate_dummy_streams.sh" \
    --exclude="package_for_takgov.sh" \
    --exclude=".DS_Store" \
    "${PROJECT_DIR}/" "${TARGET_DIR}/"

# 3. Create the zip archive with single root directory
echo "Creating zip archive..."
rm -f "$OUTPUT_ZIP"
(
    cd "$STAGING_DIR"
    zip -q -r "$OUTPUT_ZIP" "$ROOT_FOLDER_NAME"
)

echo "Archive created successfully!"
echo ""
echo "Archive details:"
ls -lh "$OUTPUT_ZIP"
echo ""
echo "Archive root structure check:"
unzip -l "$OUTPUT_ZIP" | head -n 15
echo "..."
echo ""
echo "=========================================================="
echo " Ready for upload to TAK.gov User Builds:"
echo " 1. Open https://tak.gov/user_builds"
echo " 2. Upload: ${OUTPUT_ZIP}"
echo "=========================================================="
