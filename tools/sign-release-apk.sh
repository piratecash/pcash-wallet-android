#!/usr/bin/env bash
set -euo pipefail

GPG_KEY_ID="${GPG_KEY_ID:-A6F0CB1BB25FFE99}"
DRY_RUN=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
GRADLE_FILE="${PROJECT_ROOT}/app/build.gradle"
APK_DIR="${PROJECT_ROOT}/app/build/outputs/apk/release"

usage() {
    cat <<USAGE
Usage: tools/sign-release-apk.sh [options]

Options:
  --key-id KEY_ID     GPG key ID to use (default: ${GPG_KEY_ID})
  --apk-dir DIR       Directory with release APKs (default: app/build/outputs/apk/release)
  --dry-run           Print commands without writing signatures/checksum
  -h, --help          Show this help

The script reads versionName from app/build.gradle and expects:
  app/build/outputs/apk/release/p.cash-<version>.apk
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --key-id)
            if [[ $# -lt 2 || -z "$2" ]]; then
                echo "Error: --key-id requires a value." >&2
                exit 1
            fi
            GPG_KEY_ID="$2"
            shift 2
            ;;
        --apk-dir)
            if [[ $# -lt 2 || -z "$2" ]]; then
                echo "Error: --apk-dir requires a value." >&2
                exit 1
            fi
            APK_DIR="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Error: unknown option '$1'." >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ ! -f "${GRADLE_FILE}" ]]; then
    echo "Error: Gradle file not found: ${GRADLE_FILE}" >&2
    exit 1
fi

VERSION="$(awk -F\" '/^[[:space:]]*versionName[[:space:]]+"/ { print $2; exit }' "${GRADLE_FILE}")"

if [[ -z "${VERSION}" ]]; then
    echo "Error: could not read versionName from ${GRADLE_FILE}" >&2
    exit 1
fi

APK_NAME="p.cash-${VERSION}.apk"
APK_PATH="${APK_DIR}/${APK_NAME}"
CHECKSUM_NAME="${APK_NAME}.sha256"

if [[ ! -d "${APK_DIR}" ]]; then
    echo "Error: APK directory not found: ${APK_DIR}" >&2
    exit 1
fi

if [[ ! -f "${APK_PATH}" ]]; then
    if [[ "${DRY_RUN}" -eq 1 ]]; then
        echo "Warning: APK not found yet: ${APK_PATH}" >&2
    else
        echo "Error: APK not found: ${APK_PATH}" >&2
        echo "Build the release APK first, for example: ./gradlew :app:assembleRelease" >&2
        exit 1
    fi
fi

if [[ "${DRY_RUN}" -eq 0 ]]; then
    command -v gpg >/dev/null 2>&1 || { echo "Error: gpg is not installed or not in PATH." >&2; exit 1; }
    command -v shasum >/dev/null 2>&1 || { echo "Error: shasum is not installed or not in PATH." >&2; exit 1; }
fi

run() {
    if [[ "${DRY_RUN}" -eq 1 ]]; then
        printf '+'
        printf ' %q' "$@"
        printf '\n'
    else
        "$@"
    fi
}

echo "Release version: ${VERSION}"
echo "APK: ${APK_PATH}"
echo "GPG key: ${GPG_KEY_ID}"

cd "${APK_DIR}"

run gpg --yes --local-user "${GPG_KEY_ID}" \
    --armor --detach-sign \
    --output "${APK_NAME}.asc" \
    "${APK_NAME}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    printf '+ shasum -a 256 %q > %q\n' "${APK_NAME}" "${CHECKSUM_NAME}"
else
    shasum -a 256 "${APK_NAME}" > "${CHECKSUM_NAME}"
fi

run gpg --yes --local-user "${GPG_KEY_ID}" \
    --armor --detach-sign \
    --output "${CHECKSUM_NAME}.asc" \
    "${CHECKSUM_NAME}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "Would create release signing artifacts:"
else
    echo "Created release signing artifacts:"
fi
echo "  ${APK_DIR}/${APK_NAME}.asc"
echo "  ${APK_DIR}/${CHECKSUM_NAME}"
echo "  ${APK_DIR}/${CHECKSUM_NAME}.asc"
