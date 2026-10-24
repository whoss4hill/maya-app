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
say "wake_model_present_by_default=$(adb shell run-as $P ls files/vosk-model 2>&1 | head -1)"

exit $FAIL
