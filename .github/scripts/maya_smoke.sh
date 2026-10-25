#!/bin/bash
# Credential-free smoke on API 34: install, launch, boot-receiver (the Android 14 mic-FGS crash path),
# service start refusal handling. Does not log in.
P=com.maya.agent
FAIL=0
fail(){ echo "ASSERT FAIL: $*" | tee -a smoke-log.txt; FAIL=1; }
APK=app/build/outputs/apk/debug/app-debug.apk
L=smoke-log.txt; : > $L
say(){ echo "$*" | tee -a $L; }
say "api=$(adb shell getprop ro.build.version.sdk)"
adb install -r $APK 2>&1 | tee -a $L
adb shell pm grant $P android.permission.RECORD_AUDIO
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
LAUNCH_PID=""
for i in $(seq 1 9); do
  sleep 5
  LAUNCH_PID=$(adb shell pidof $P | tr -d '\r')
  [ -n "$LAUNCH_PID" ] && break
done
if [ -z "$LAUNCH_PID" ]; then
  adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  for i in $(seq 1 6); do
    sleep 5
    LAUNCH_PID=$(adb shell pidof $P | tr -d '\r')
    [ -n "$LAUNCH_PID" ] && break
  done
fi
say "launch_pid=$LAUNCH_PID"
if [ -z "$LAUNCH_PID" ]; then
  # logcat was cleared right before monkey, so this window is launch-only:
  # distinguish deterministic startup fault (fatal/ANR/missing activity) from timing flake
  say "launcher_resolution=$(adb shell cmd package resolve-activity --brief $P 2>&1 | tr '\n' ' ' | tail -c 200)"
  adb logcat -d -v brief | grep -aE 'FATAL EXCEPTION|AndroidRuntime|ANR in|ActivityManager|ActivityTaskManager' | tail -40 | tee -a $L
fi
adb exec-out screencap -p > smoke-1-launch.png
# owner had the background service switched on, then the phone reboots
cat > /tmp/maya_state.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><boolean name="service_enabled" value="true" /></map>
XML
adb push /tmp/maya_state.xml /data/local/tmp/maya_state.xml
adb shell am force-stop $P
adb shell "run-as $P mkdir -p shared_prefs"
adb shell "run-as $P cp /data/local/tmp/maya_state.xml shared_prefs/maya_state.xml"
say "prefs_written=$(adb shell run-as $P cat shared_prefs/maya_state.xml | grep -c service_enabled)"
adb shell am force-stop $P
# google_apis images allow adb root; the shell uid may not send BOOT_COMPLETED (run 40023104215: Permission Denial)
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n $P/.service.BootReceiver 2>&1 | tee -a $L > /tmp/boot.out
grep -q "Permission Denial\|Exception" /tmp/boot.out && fail "boot broadcast not delivered"
grep -q "Broadcast completed" /tmp/boot.out || fail "boot broadcast did not complete"
sleep 8
BN=$(adb shell dumpsys notification --noredact | grep -c "Phone restarted"); say "boot_notice_count=$BN"
[ "$BN" -ge 1 ] || fail "no tap-to-resume notice after boot on API 34"
FS=$(adb shell dumpsys activity services $P | grep -c "ServiceRecord.*MayaForegroundService"); say "fgs_service_records_after_boot=$FS"
[ "$FS" -eq 0 ] || fail "boot started the mic FGS on API 34"
# direct service start from shell while app is in background (should be refused cleanly, not crash)
adb shell am start-foreground-service -n $P/.service.MayaForegroundService 2>&1 | tee -a $L
sleep 6
adb exec-out screencap -p > smoke-2-after.png
adb logcat -d -v brief > logcat.txt
FC=$(grep -c 'FATAL EXCEPTION' logcat.txt); say "fatal_count=$FC"
[ "$FC" -eq 0 ] || fail "app crashed"
[ -n "$(adb shell pidof $P)" ] || say "note: app process not running at end (service refused and stopped cleanly is acceptable)"
[ -n "$LAUNCH_PID" ] || fail "app did not launch"
grep -a -B1 -A12 'FATAL EXCEPTION' logcat.txt | grep -a "$P\|Exception" | head -10 | tee -a $L

# --- Approved Free-talk voice screen render pass (credential-free) ---
# Navigates onboarding -> skip sign-in -> Home -> mic, then screencaps the
# voice page. Placeholder backend means the session must fail; what we prove
# here is that the approved screen renders on a real emulator (layout, orb,
# state, end button) and that mint failure is surfaced, not a crash.
tap_on() { # $1 = exact text or content-desc as it appears in the UI dump
  local need="$1" x="" y="" i
  for i in $(seq 1 6); do
    adb shell uiautomator dump /sdcard/wd.xml >/dev/null 2>&1
    adb shell "cat /sdcard/wd.xml" > /tmp/wd.xml 2>/dev/null
    read x y <<EOF2
$(python3 - "$need" <<'PY'
import sys, re
s = open('/tmp/wd.xml').read()
needle = sys.argv[1]
for m in re.finditer(r'<node[^>]*>', s):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    d = re.search(r'content-desc="([^"]*)"', tag)
    if (t and t.group(1) == needle) or (d and d.group(1) == needle):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
EOF2
    [ -n "$x" ] && break
    sleep 3
  done
  if [ -z "$x" ]; then say "tap_on: '$need' not found"; return 1; fi
  adb shell input tap "$x" "$y"
  say "tapped '$need' at $x,$y"
  return 0
}

# Let the emulator's system launcher finish initializing before Maya takes
# the foreground: runs 40163689772 + 40165084097 both ANR'd the launcher
# ("Input dispatching timed out (no focused window)") when Maya launched
# during launcher init; its modal dialog then blocked every tap.
sleep 20
adb shell am force-stop $P
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
adb exec-out screencap -p > smoke-p1-welcome.png
# A system ANR dialog (e.g. "Pixel Launcher isn't responding") can steal the
# foreground for the whole run (run 40107620068: every onboarding tap missed).
# While the modal dialog is up, uiautomator sees ONLY the dialog - never the
# app behind it - so drain the dialog first (bounded), then judge the app.
for i in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/anr.xml >/dev/null 2>&1
  adb shell cat /sdcard/anr.xml 2>/dev/null | grep -a -q "isn't responding" || break
  tap_on "Wait" && say "anr_dialog_dismissed=Wait (drain $i)"
  sleep 6
done
adb shell uiautomator dump /sdcard/anr2.xml >/dev/null 2>&1
if adb shell cat /sdcard/anr2.xml 2>/dev/null | grep -a -q "isn't responding"; then
  say "anr_dialog=PERSISTED after bounded drain - continuing anyway (dialog may clear mid-run)"
fi
# Only a genuinely unrendered app (dialog gone, no app markers) gets a cold relaunch.
if ! adb shell cat /sdcard/anr2.xml 2>/dev/null | grep -a -q "isn't responding" \
  && ! adb shell cat /sdcard/anr2.xml 2>/dev/null | grep -a -q -e 'Get started' -e 'MAYA' -e 'What can I do'; then
  say "anr_recovery=force-stop+cold-relaunch"
  adb shell am force-stop $P >/dev/null 2>&1
  sleep 2
  adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 12
fi
tap_on "Get started" || fail "onboarding: Get started not found"
sleep 3
adb exec-out screencap -p > smoke-p2-permissions1.png
tap_on "Continue" || fail "onboarding: Continue not found"
sleep 3
adb exec-out screencap -p > smoke-p2b-permissions2.png
tap_on "Not now" || fail "onboarding: Not now not found"
sleep 3
adb exec-out screencap -p > smoke-p3-signin.png
# No guest/no-account mode anymore (owner-only, personal API): the smoke enters
# via the DEBUG-only demo extra; release builds ignore it.
adb shell am force-stop $P
adb shell am start -n $P/.MainActivity --ez maya_smoke_demo true >/dev/null 2>&1
sleep 5
adb exec-out screencap -p > smoke-3-home.png
tap_on "Mic" || fail "home: mic affordance not found"
sleep 10
adb exec-out screencap -p > smoke-4-voice.png
# The voice session must fail against the placeholder backend; the app must
# stay alive and keep the approved screen up.
if [ -z "$(adb shell pidof $P)" ]; then
  adb logcat -d -v brief | grep -a -B2 -A12 'FATAL EXCEPTION' | head -30 | tee -a $L
  fail "app died during voice-screen pass"
fi
tap_on "End voice chat" && sleep 2 || fail "voice: End voice chat close button not found"
adb exec-out screencap -p > smoke-5-back-home.png
# Remaining pages via the bottom nav (empty/demo states: placeholder backend,
# signed out - no memories, no chats, no vault entries).
tap_on "Chat" || say "note: Chat tab not found"
sleep 3
adb exec-out screencap -p > smoke-p6-chat.png
tap_on "Memory" || say "note: Memory tab not found"
sleep 3
adb exec-out screencap -p > smoke-p7-memory.png
tap_on "Vault" || say "note: Vault tab not found"
sleep 3
adb exec-out screencap -p > smoke-p8-vault.png
tap_on "Settings" || say "note: Settings tab not found"
sleep 3
adb exec-out screencap -p > smoke-p9-settings.png
# Settings is a long scroll page: capture the lower regions the first shot cuts off
adb shell input swipe 540 1900 540 300 600
sleep 2
adb exec-out screencap -p > smoke-p9b-settings-mid.png
adb shell input swipe 540 1900 540 300 600
sleep 2
adb exec-out screencap -p > smoke-p9c-settings-bottom.png
# Wake-word full credential-free path (approved 2026-10-25): scroll Settings
# back to the TOP (toggle is in the first section - two blind swipes missed it
# in run 40166318290), toggle on, capture the consent copy, accept Download,
# then prove: model installed, mic record client active, foreground
# notification with Pause/Stop, survival across a screen-off/on cycle, and
# clean shutdown when toggled off. No credentials involved.
wx=""; wy=""
for i in 1 2 3 4; do
  adb shell uiautomator dump /sdcard/wd.xml >/dev/null 2>&1
  adb shell "cat /sdcard/wd.xml" > /tmp/wd.xml 2>/dev/null
  read wx wy <<EOF2
$(python3 - <<'PY'
import re
s = open('/tmp/wd.xml').read()
for m in re.finditer(r'<node[^>]*>', s):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    if t and t.group(1).startswith('Wake with'):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print(1000, (y1 + y2) // 2)
            break
PY
)
EOF2
  [ -n "$wx" ] && break
  adb shell input swipe 540 300 540 1900 600
  sleep 2
done
if [ -n "$wx" ]; then
  adb shell input tap "$wx" "$wy"
  sleep 2
  adb exec-out screencap -p > smoke-p9d-wake-consent.png
  adb shell uiautomator dump /sdcard/wd.xml >/dev/null 2>&1
  if adb shell cat /sdcard/wd.xml 2>/dev/null | grep -a -q 'Offline model download?'; then
    say "wake_consent_dialog=shown"
    tap_on "Download" || say "wake_download_button=NOT_FOUND"
    # Poll for the pinned model to finish installing (38MB, CI network).
    ok=""
    for i in $(seq 1 40); do
      if adb shell run-as $P ls files/vosk-model/am/final.mdl >/dev/null 2>&1; then ok=1; break; fi
      sleep 6
    done
    if [ -n "$ok" ]; then
      say "wake_model_installed=YES - pinned Vosk model present after consent download"
      sleep 4
      adb shell dumpsys audio 2>/dev/null | grep -a -i -q 'com.maya.agent' \
        && say "wake_mic_active=YES - audio record client registered for com.maya.agent" \
        || say "wake_mic_active=NO - no audio record client for com.maya.agent"
      adb shell input swipe 540 0 540 900 400
      sleep 2
      adb exec-out screencap -p > smoke-p9e-wake-notification.png
      adb shell uiautomator dump /sdcard/wd.xml >/dev/null 2>&1
      adb shell cat /sdcard/wd.xml 2>/dev/null | grep -a -q 'Pause' \
        && say "wake_notification=YES - foreground notification with Pause/Stop visible" \
        || say "wake_notification=NO - Pause/Stop notification not found in shade"
      adb shell input keyevent KEYCODE_BACK
      sleep 1
      # Screen-off survival: service + engine must still be alive after off/on.
      adb shell input keyevent KEYCODE_POWER
      sleep 6
      adb shell input keyevent KEYCODE_POWER
      sleep 2
      adb shell input swipe 540 1800 540 600 400
      sleep 2
      adb shell dumpsys activity services $P 2>/dev/null | grep -a -q 'MayaForegroundService' \
        && say "wake_service_survives_screenoff=YES" \
        || say "wake_service_survives_screenoff=NO - foreground service gone after screen cycle"
      adb shell dumpsys audio 2>/dev/null | grep -a -i -q 'com.maya.agent' \
        && say "wake_mic_after_screenoff=YES" || say "wake_mic_after_screenoff=NO"
      # Cleanup: toggle wake back off, verify the mic client is released.
      adb shell uiautomator dump /sdcard/wd.xml >/dev/null 2>&1
      adb shell "cat /sdcard/wd.xml" > /tmp/wd.xml 2>/dev/null
      read wx2 wy2 <<EOF2
$(python3 - <<'PY'
import re
s = open('/tmp/wd.xml').read()
for m in re.finditer(r'<node[^>]*>', s):
    tag = m.group(0)
    t = re.search(r'text="([^"]*)"', tag)
    if t and t.group(1).startswith('Wake with'):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print(1000, (y1 + y2) // 2)
            break
PY
)
EOF2
      if [ -n "$wx2" ]; then
        adb shell input tap "$wx2" "$wy2"
        sleep 4
        adb shell dumpsys audio 2>/dev/null | grep -a -i -q 'com.maya.agent' \
          && say "wake_off_clean=NO - mic client still registered after toggle off" \
          || say "wake_off_clean=YES - mic released on toggle off"
      else
        say "note: wake toggle not relocated for cleanup"
      fi
    else
      say "wake_model_installed=NO - model missing after bounded download wait"
    fi
  else
    say "wake_consent_dialog=NOT_SHOWN - unexpected state after toggle tap"
    tap_on "No" || true
  fi
else
  say "note: wake toggle not located after bounded scroll-to-top"
fi

SEED='kal sabhaar kitne baje hai'
# Seed and send from the Chat page: its send affordance has an exact
# content-desc ("Send" once the field is non-blank) and send() clears the
# field, so a post-send dump match can only come from the thread - never
# from leftover input text (previous Home-seed + "Mic" tap false positive).
tap_on "Chat" || say "note: Chat tab not found for seeding"
sleep 2
tap_on "Speak or type..." || say "note: chat input not found"
sleep 2
# No extra taps here: any tap outside the field drops its focus.
type_text_keyevents "$SEED"
sleep 2
adb shell uiautomator dump /sdcard/maya-ui.xml >/dev/null 2>&1
if adb exec-out cat /sdcard/maya-ui.xml 2>/dev/null | grep -q 'kal sabhaar kitne baje hai'; then
  say "seeded_text_present=yes"
else
  say "seeded_text_present=NO - retype once"
  tap_on "Speak or type..." || true
  sleep 1
  type_text_keyevents "$SEED"
  sleep 2
  adb shell uiautomator dump /sdcard/maya-ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/maya-ui.xml 2>/dev/null | grep -q 'kal sabhaar kitne baje hai' \
    && say "seeded_text_present=yes(retry)" || say "seeded_text_present=STILL_NO"
fi
adb exec-out screencap -p > smoke-p10-chat-seeded.png
tap_on "Send" || say "chat_send_button=NOT_FOUND - send affordance missing after typing"
sleep 5
adb exec-out screencap -p > smoke-p10b-chat-after-send.png
# Source-verified expectation (ChatScreen.send): addUser appends the typed text to
# the thread BEFORE any backend call, so the message is visible signed-out too.
# The input field is cleared by send(), so any remaining match is the thread.
adb shell uiautomator dump /sdcard/maya-chat.xml >/dev/null 2>&1
CHATXML=$(adb exec-out cat /sdcard/maya-chat.xml 2>/dev/null)
if echo "$CHATXML" | grep -q 'kal sabhaar kitne baje hai'; then
  say "chat_send_thread_visible=YES - typed message reached the chat thread"
  echo "$CHATXML" | grep -q 'Please sign in first' \
    && say "chat_signedout_reply=present (expected on emulator without an account)" \
    || say "chat_signedout_reply=absent"
else
  say "chat_send_thread_visible=NO - typed message did not reach the chat thread"
fi
adb exec-out screencap -p > smoke-p10c-chat-thread.png
tap_on "Home" || say "note: Home tab not found after chat"
sleep 2
if [ -z "$(adb shell pidof $P)" ]; then
  adb logcat -d -v brief | grep -a -B2 -A12 'FATAL EXCEPTION' | head -30 | tee -a $L
  fail "app died during page-render pass"
fi
say "page_render_pass=done"


# Default-assistant presence: prove the VoiceInteractionService is registered,
# the role can actually be held, and the system binds our service (CI fixture
# only - on a real phone the owner picks it in system UI themselves).
say "== assistant role verification =="
VISQ=$(adb shell pm query-intent-services -a android.service.voice.VoiceInteractionService 2>&1 | tr -d '\r')
echo "$VISQ" | grep -a 'com.maya' | tee -a $L
if echo "$VISQ" | grep -q "$P"; then
  say "vis_intent_visible=YES - system resolves Maya VoiceInteractionService"
else
  say "vis_intent_visible=NO - system does not resolve Maya VIS for the VIS intent"
  adb shell dumpsys package $P 2>/dev/null | grep -a -A6 'MayaVoiceInteractionService' | head -12 | tee -a $L
fi
# cmd role exits 0 even when the grant fails - verify via get-role-holders.
adb shell cmd role add-role-holder android.app.role.ASSISTANT $P >/dev/null 2>&1
HOLDERS=$(adb shell cmd role get-role-holders android.app.role.ASSISTANT 2>&1 | tr -d '\r')
say "assistant_holders=$HOLDERS"
if echo "$HOLDERS" | grep -q "$P"; then
  say "assistant_role_granted=YES"
  adb shell dumpsys voiceinteraction 2>/dev/null | grep -a -i -m4 'maya' | tee -a $L
  adb shell cmd role remove-role-holder android.app.role.ASSISTANT $P >/dev/null 2>&1
else
  say "assistant_role_granted=NO (emulator image refused shell grant; not a fail)"
fi
adb shell am start -a android.settings.MANAGE_DEFAULT_APPS_SETTINGS >/dev/null 2>&1
sleep 3
adb exec-out screencap -p > smoke-p11-default-apps.png
adb shell am start -a android.settings.VOICE_INPUT_SETTINGS >/dev/null 2>&1
sleep 3
adb exec-out screencap -p > smoke-p11b-assistant-picker.png
# Choice-list proof: open the default-assistant row and check Maya is selectable.
if tap_on "Default digital assistant app"; then
  sleep 2
  adb exec-out screencap -p > smoke-p11c-assistant-choices.png
  adb shell uiautomator dump /sdcard/wd2.xml >/dev/null 2>&1
  if adb shell cat /sdcard/wd2.xml 2>/dev/null | grep -a -q -i 'Maya'; then
    say "maya_in_assistant_choices=YES"
  else
    say "maya_in_assistant_choices=NO - choice list did not show Maya"
  fi
else
  say "maya_in_assistant_choices=UNKNOWN - default-assistant row not found"
fi
adb shell am force-stop com.android.settings 2>/dev/null
adb shell am start -n $P/.MainActivity >/dev/null 2>&1; sleep 3
say "wake_model_present_by_default=$(adb shell run-as $P ls files/vosk-model 2>&1 | head -1)"

exit $FAIL
