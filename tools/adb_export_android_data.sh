#!/usr/bin/env bash
#
# adb_export_android_data.sh
#
# Copies Android/data (or one app's folder inside it) off a NON-ROOTED Android
# phone to this computer, using the official Android Debug Bridge.
#
# Why this exists: from Android 11, the operating system refuses Android/data and
# Android/obb to every app — including with "All files access" and the folder
# picker. No app can read them. The `adb` shell, however, runs as the shell user
# with broader rights, so a cable + this script can copy the files out. Nothing is
# bypassed or exploited: this is the documented developer interface.
#
# Usage:
#   ./adb_export_android_data.sh                    # export the whole Android/data
#   ./adb_export_android_data.sh com.whatsapp       # export one app's folder
#   ./adb_export_android_data.sh com.whatsapp out   # ...into a chosen folder
#
# Requirements: adb on PATH, USB debugging enabled, phone unlocked and authorised.

set -uo pipefail

PKG="${1:-}"
DEST="${2:-./android-data-export}"
REMOTE="/sdcard/Android/data"

say()  { printf '%s\n' "$*"; }
fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || fail \
"adb not found on PATH.
  Install Android Platform Tools: https://developer.android.com/tools/releases/platform-tools
  Then reopen this terminal and run the script again."

say "Checking for a connected phone..."
DEVICES="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
COUNT="$(printf '%s\n' "$DEVICES" | grep -c . || true)"

if [ "$COUNT" -eq 0 ]; then
  say ""
  adb devices
  fail "No authorised device found.
  On the phone: Settings -> About phone -> tap Build number 7 times,
  then Settings -> Developer options -> enable USB debugging,
  plug in the cable and accept the 'Allow USB debugging?' prompt."
fi
if [ "$COUNT" -gt 1 ]; then
  fail "More than one device is connected. Disconnect the others, or set ANDROID_SERIAL."
fi

TARGET="$REMOTE"
[ -n "$PKG" ] && TARGET="$REMOTE/$PKG"

say "Phone found. Checking access to $TARGET ..."
if ! adb shell "ls '$TARGET'" >/dev/null 2>&1; then
  say ""
  say "The shell could not list $TARGET on this device."
  say ""
  say "Some OEM builds restrict the shell user too. Options, in order of ease:"
  say "  1. Try again with the phone unlocked and the screen on."
  say "  2. If the app is debuggable, its own folder can be read with run-as:"
  say "       adb shell run-as $PKG tar -c . | tar -x -C \"$DEST/$PKG\""
  say "  3. Check whether the media you want is actually in Android/media instead —"
  say "     messaging apps (WhatsApp, Telegram) keep their media there, and it IS"
  say "     readable by the gallery app with 'All files access'."
  exit 2
fi

mkdir -p "$DEST"
say "Copying $TARGET -> $DEST"
say "(this can take a while for large folders)"

if ! adb pull "$TARGET" "$DEST"; then
  say ""
  say "The copy stopped part way. Partial files are in $DEST."
  say "Re-run the script to resume what is missing."
  exit 3
fi

say ""
say "Done. Copied into: $DEST"
say ""
say "To browse these in ProGallery:"
say "  1. Copy the exported folder to the phone, e.g. into Download/AndroidExport"
say "       adb push \"$DEST\" /sdcard/Download/AndroidExport"
say "  2. In ProGallery: Deep Scan -> add that folder -> Scan."
say ""
say "Nothing was modified on the phone; the export is a read-only copy."
