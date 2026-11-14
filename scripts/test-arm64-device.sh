#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb was not found; install Android platform-tools or enter the Nix development shell." >&2
  exit 1
fi

if [[ -n "${ADB_SERIAL:-}" ]]; then
  device_serial="$ADB_SERIAL"
  if [[ "$(adb -s "$device_serial" get-state 2>/dev/null || true)" != "device" ]]; then
    echo "ADB_SERIAL=$device_serial is not a connected, authorized Android device." >&2
    exit 1
  fi
else
  mapfile -t connected_devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
  if [[ "${#connected_devices[@]}" -ne 1 ]]; then
    echo "Connect exactly one authorized device or set ADB_SERIAL to select one." >&2
    exit 1
  fi
  device_serial="${connected_devices[0]}"
fi

device_abis="$(adb -s "$device_serial" shell getprop ro.product.cpu.abilist | tr -d '\r')"
case ",$device_abis," in
  *,arm64-v8a,*) ;;
  *)
    echo "Connected device does not report arm64-v8a (reported: ${device_abis:-empty})." >&2
    exit 1
    ;;
esac

echo "Running instrumentation tests on $device_serial (ABIs: $device_abis)."
./gradlew :app:connectedDebugAndroidTest -Pchimera.fullAbi=true "$@"
