#!/usr/bin/env bash
# Capture the installed debug APK. PremiumCaptureActivity exists only in src/debug.
set -euo pipefail
capture_dir="${CAPTURE_DIR:-build/outputs/premium-glass}"
capture_apk="${CAPTURE_APK:-app/build/outputs/apk/debug/app-debug.apk}"
mkdir -p "$capture_dir"
trap 'adb logcat -d > "$capture_dir/logcat.txt" || true' EXIT
adb install -r "$capture_apk" </dev/null
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo --es command enter
adb shell am broadcast -a com.android.systemui.demo --es command clock --es hhmm 1230
adb shell am broadcast -a com.android.systemui.demo --es command battery --es level 100 --es plugged false
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell settings put system font_scale 1.0
adb shell wm size 1080x2400
adb shell wm density 480

snapshot() {
    local capture_mode="$1" capture_name="$2" capture_text="$3"
    # The image's Pixel Launcher can show an ANR while display density changes.
    # It is unrelated to this app and is not needed for explicit activity launches.
    adb shell am force-stop com.google.android.apps.nexuslauncher || true
    adb shell am force-stop com.mamoro.investments
    adb shell am start -W -n com.mamoro.investments/.ui.PremiumCaptureActivity --es screen "$capture_mode"
    adb shell am broadcast -a com.android.systemui.demo --es command enter
    adb shell am broadcast -a com.android.systemui.demo --es command clock --es hhmm 1230
    for capture_attempt in 1 2 3 4 5; do
        sleep 2
        adb shell uiautomator dump /sdcard/premium-glass-ui.xml
        adb pull /sdcard/premium-glass-ui.xml "$capture_dir/$capture_name.xml"
        if python3 - "$capture_dir/$capture_name.xml" "$capture_text" <<'PYCHECK'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
labels = ' '.join(n.attrib.get('text','')+' '+n.attrib.get('content-desc','') for n in root.iter())
sys.exit(0 if sys.argv[2] in labels else 1)
PYCHECK
        then
            break
        fi
    done
    adb exec-out screencap -p > "$capture_dir/$capture_name.png"
    python3 - "$capture_dir/$capture_name.xml" "$capture_text" <<'PYFINAL'
import sys
import xml.etree.ElementTree as ET
labels = ' '.join(n.attrib.get('text','')+' '+n.attrib.get('content-desc','') for n in ET.parse(sys.argv[1]).iter())
assert sys.argv[2] in labels, 'Expected screen not visible: '+sys.argv[2]
print('Visible screen verified: '+sys.argv[2])
PYFINAL
    test -s "$capture_dir/$capture_name.png"
}

snapshot portfolio portfolio 'Стоимость портфеля'
snapshot actions actions 'Быстрые действия'
snapshot operations operations 'История операций'
snapshot empty empty 'Стоимость портфеля'
snapshot assets assets 'Мои бумаги'
snapshot analytics analytics 'Структура портфеля'
snapshot more more 'Связь с Кошельком'

adb shell wm size 2560x1600
adb shell wm density 240
snapshot portfolio tablet-landscape 'Стоимость портфеля'
adb shell wm size 1600x2560
snapshot portfolio tablet-portrait 'Стоимость портфеля'

adb shell wm size 1080x2400
adb shell wm density 480
adb shell settings put system font_scale 1.5
snapshot portfolio phone-font-150 'Стоимость портфеля'
snapshot actions actions-font-150 'Быстрые действия'
snapshot operations operations-font-150 'История операций'
snapshot assets assets-font-150 'Мои бумаги'
snapshot analytics analytics-font-150 'Структура портфеля'
snapshot more more-font-150 'Связь с Кошельком'
adb logcat -d > "$capture_dir/logcat.txt"
