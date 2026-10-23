#!/bin/bash
# Credential-free smoke on API 34: install, launch, boot-receiver (the Android 14 mic-FGS crash path),
# service start refusal handling. Does not log in.
P=com.maya.agent
APK=app/build/outputs/apk/debug/app-debug.apk
L=smoke-log.txt; : > $L
say(){ echo "$*" | tee -a $L; }
say "api=$(adb shell getprop ro.build.version.sdk)"
adb install -r $APK 2>&1 | tee -a $L
adb shell pm grant $P android.permission.RECORD_AUDIO
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 15
adb exec-out screencap -p > smoke-1-launch.png
say "launch_pid=$(adb shell pidof $P)"
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
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n $P/.service.BootReceiver | tee -a $L
sleep 8
say "after_boot_pid=$(adb shell pidof $P)"
adb shell dumpsys notification --noredact | grep -c "Phone restart hua" | sed 's/^/boot_notice_count=/' | tee -a $L
adb shell dumpsys activity services $P | grep -c "MayaForegroundService" | sed 's/^/fgs_service_records=/' | tee -a $L
# direct service start from shell while app is in background (should be refused cleanly, not crash)
adb shell am start-foreground-service -n $P/.service.MayaForegroundService | tee -a $L
sleep 6
adb exec-out screencap -p > smoke-2-after.png
adb logcat -d -v brief > logcat.txt
say "fatal_count=$(grep -c 'FATAL EXCEPTION' logcat.txt)"
grep -a -B1 -A12 'FATAL EXCEPTION' logcat.txt | grep -a "$P\|Exception" | head -10 | tee -a $L
say "wake_model_present_by_default=$(adb shell run-as $P ls files/vosk-model 2>&1 | head -1)"
