#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
#                         Sustainable Power Systems Lab <https://sps-lab.org>
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Run the instrumented Espresso suite locally against an Android emulator,
# replacing the manual `nightly-instrumented.yml` workflow_dispatch cycle for
# day-to-day work. Replicates exactly what CI does:
#
#   - boots the AVD (KVM-accelerated; falls back to software with a warning)
#   - disables window / transition / animator scales (Espresso requires this)
#   - runs `./gradlew :app:connectedDebugAndroidTest --no-daemon`
#
# Prerequisites (one-time):
#   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
#   ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "emulator" \
#       "system-images;android-35;default;x86_64" "platforms;android-35"
#   sudo apt install -y qemu-kvm
#   sudo modprobe kvm_amd  # or kvm_intel
#   sudo usermod -aG kvm "$USER"  # log out + back in
#   echo no | ~/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd \
#       -n joulie-test -k "system-images;android-35;default;x86_64"
#
# Usage:
#   scripts/run-instrumented.sh                      # full suite
#   scripts/run-instrumented.sh ChartsFragmentTest   # one class
#   scripts/run-instrumented.sh stop                 # tear the emulator down

set -euo pipefail

ANDROID_SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"
AVD="${JOULIE_AVD:-joulie-test}"
SERIAL="emulator-5554"
LOG="${TMPDIR:-/tmp}/joulie-emulator.log"

emulator_running() {
    pgrep -af "emulator -avd $AVD" >/dev/null 2>&1 \
        && "$ADB" -s "$SERIAL" shell true >/dev/null 2>&1
}

ensure_booted() {
    if emulator_running; then
        echo "[run-instrumented] AVD '$AVD' already running on $SERIAL."
        return
    fi
    echo "[run-instrumented] Starting AVD '$AVD' (log: $LOG)..."
    if [ ! -e /dev/kvm ] || ! groups | grep -qw kvm; then
        echo "[run-instrumented] WARNING: /dev/kvm missing or user not in kvm group;"
        echo "[run-instrumented]          falling back to software acceleration."
        echo "[run-instrumented]          Boot may take 10+ minutes."
    fi
    "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect -no-snapshot >"$LOG" 2>&1 &
    disown
    echo "[run-instrumented] Waiting for boot (sys.boot_completed = 1)..."
    until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
    done
    echo "[run-instrumented] Boot complete."
}

disable_animations() {
    "$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0
    "$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0
    "$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0
    echo "[run-instrumented] Animations disabled (matches CI's disable-animations: true)."
}

stop_emulator() {
    if pgrep -af "emulator -avd $AVD" >/dev/null 2>&1; then
        echo "[run-instrumented] Stopping AVD '$AVD'..."
        "$ADB" -s "$SERIAL" emu kill 2>/dev/null || true
        sleep 2
        pkill -f "emulator -avd $AVD" 2>/dev/null || true
    else
        echo "[run-instrumented] AVD '$AVD' is not running."
    fi
}

run_tests() {
    cd "$(dirname "$0")/.."
    if [ $# -gt 0 ]; then
        local pkg="org.spsl.evtracker"
        local pattern="$1"
        echo "[run-instrumented] Running tests matching: $pattern"
        ./gradlew :app:connectedDebugAndroidTest \
            -Pandroid.testInstrumentationRunnerArguments.class="$pkg.$pattern" \
            --no-daemon
    else
        echo "[run-instrumented] Running full instrumented suite..."
        ./gradlew :app:connectedDebugAndroidTest --no-daemon
    fi
}

case "${1:-run}" in
    stop)
        stop_emulator
        ;;
    *)
        ensure_booted
        disable_animations
        if [ "${1:-run}" = "run" ]; then
            run_tests
        else
            run_tests "$@"
        fi
        ;;
esac
