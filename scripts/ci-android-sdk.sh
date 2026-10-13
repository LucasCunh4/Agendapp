#!/usr/bin/env bash
set -euo pipefail

# Os runners oficiais Ubuntu incluem o SDK e suas licenças.
# Reutilize essa instalação sem baixar outro pacote de command-line tools.
agendapp_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$agendapp_sdk_root" ]]; then
    echo "Android SDK ausente no runner." >&2
    exit 1
fi
agendapp_sdk_manager="$agendapp_sdk_root/cmdline-tools/latest/bin/sdkmanager"
test -x "$agendapp_sdk_manager"
"$agendapp_sdk_manager" --version

if [[ ! -f "$agendapp_sdk_root/platforms/android-35/android.jar" ||
      ! -x "$agendapp_sdk_root/build-tools/35.0.0/apksigner" ]]; then
    "$agendapp_sdk_manager" 'platforms;android-35' 'build-tools;35.0.0'
fi

test -f "$agendapp_sdk_root/platforms/android-35/android.jar"
test -x "$agendapp_sdk_root/build-tools/35.0.0/apksigner"
if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' \
        "$agendapp_sdk_root" "$agendapp_sdk_root" >> "$GITHUB_ENV"
fi
