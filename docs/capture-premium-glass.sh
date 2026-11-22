#!/usr/bin/env bash
# Capture the installed debug APK. PremiumCaptureActivity exists only in src/debug.
set -euo pipefail
capture_dir="${CAPTURE_DIR:-build/outputs/premium-glass}"
capture_apk="${CAPTURE_APK:-app/build/outputs/apk/debug/app-debug.apk}"
capture_device="${CAPTURE_DEVICE:-all}"
[[ "$capture_device" == phone || "$capture_device" == tablet || "$capture_device" == all ]]
mkdir -p "$capture_dir"
trap 'adb logcat -d > "$capture_dir/logcat.txt" || true' EXIT
adb install -r "$capture_apk" </dev/null
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo --es command enter
adb shell am broadcast -a com.android.systemui.demo --es command clock --es hhmm 1024
adb shell am broadcast -a com.android.systemui.demo --es command battery --es level 100 --es plugged false
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell settings put system font_scale 1.0
adb shell wm set-fix-to-user-rotation enabled
adb shell wm set-user-rotation lock 0

snapshot() {
    local capture_mode="$1" capture_name="$2" capture_text="$3"
    local capture_width="${4:-1080}" capture_height="${5:-2400}"
    # The image's Pixel Launcher can show an ANR while display density changes.
    # It is unrelated to this app and is not needed for explicit activity launches.
    adb shell am force-stop com.google.android.apps.nexuslauncher || true
    adb shell am force-stop com.mamoro.investments
    adb shell am start -W -n com.mamoro.investments/.ui.PremiumCaptureActivity --es screen "$capture_mode"
    adb shell am broadcast -a com.android.systemui.demo --es command enter
    adb shell am broadcast -a com.android.systemui.demo --es command clock --es hhmm 1024
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
    python3 - "$capture_dir/$capture_name.png" "$capture_width" "$capture_height" <<'PYSIZE'
from pathlib import Path
import struct
import sys
png = Path(sys.argv[1]).read_bytes()
assert png[:8] == b'\x89PNG\r\n\x1a\n'
actual = struct.unpack('>II', png[16:24])
expected = (int(sys.argv[2]), int(sys.argv[3]))
assert actual == expected, f'Unexpected screenshot size: {actual}, expected {expected}'
print('Screenshot dimensions verified:', actual)
PYSIZE
}

if [[ "$capture_device" != tablet ]]; then
adb shell wm size 1080x2400
adb shell wm density 480
snapshot portfolio portfolio 'Стоимость портфеля'
snapshot actions actions 'Быстрые действия'
snapshot operations operations 'История операций'
snapshot empty empty 'Стоимость портфеля'
snapshot assets assets 'Мои бумаги'
snapshot analytics analytics 'Структура портфеля'
snapshot more more 'Т‑Инвестиции'
snapshot bond-search bond-search 'Название, тикер или ISIN'
snapshot bond-reference bond-reference 'Дата выпуска'
snapshot bond-calendar bond-calendar 'Календарь купонов'
snapshot bond-received bond-received 'Полученные'
snapshot bond-schedule bond-schedule 'Расписание'
adb shell am force-stop com.mamoro.investments
adb shell am start -W -n com.mamoro.investments/.ui.PremiumCaptureActivity --es screen bond-keystore-check
adb exec-out run-as com.mamoro.investments cat files/bond-keystore-check.json > "$capture_dir/bond-keystore-check.json"
python3 - "$capture_dir/bond-keystore-check.json" <<'PYKEYSTORE'
import json, sys
result = json.load(open(sys.argv[1]))
assert result.get('success'), result
assert result['round_trip'] and result['encrypted_at_rest'] and result['cleared'], result
print('Actual Android Keystore round trip passed')
PYKEYSTORE
adb shell am force-stop com.mamoro.investments
adb shell run-as com.mamoro.investments rm -f files/tinvest-network-check.json
adb shell am start -W -n com.mamoro.investments/.ui.PremiumCaptureActivity --es screen bond-network-check
for capture_attempt in $(seq 1 45); do
    if adb shell run-as com.mamoro.investments test -f files/tinvest-network-check.json; then break; fi
    sleep 1
done
adb exec-out run-as com.mamoro.investments cat files/tinvest-network-check.json > "$capture_dir/tinvest-network-check.json"
python3 - "$capture_dir/tinvest-network-check.json" <<'PYTLS'
import json, sys
result = json.load(open(sys.argv[1]))
assert result.get('success'), result
assert result['api_root_trusted'] and result['other_hosts_reject_root'] and result['cleartext_blocked'], result
print('Native Android per-host certificate policy passed:', result)
PYTLS
fi

if [[ "$capture_device" != phone ]]; then
# The workflow uses a native 2560x1600 tablet profile, avoiding the phone AVD's
# forced-width cap. Capture both logical orientations without scaling any PNG.
adb shell wm size 2560x1600
adb shell wm density 320
snapshot portfolio tablet-landscape 'Стоимость портфеля' 2560 1600
snapshot bond-calendar bond-tablet-landscape 'Календарь купонов' 2560 1600
adb shell wm size 1600x2560
snapshot portfolio tablet-portrait 'Стоимость портфеля' 1600 2560
snapshot bond-calendar bond-tablet-portrait 'Календарь купонов' 1600 2560
adb shell settings put system font_scale 1.5
adb shell wm size 2560x1600
snapshot portfolio tablet-landscape-font-150 'Стоимость портфеля' 2560 1600
snapshot bond-calendar bond-tablet-landscape-font-150 'Календарь купонов' 2560 1600
adb shell wm size 1600x2560
snapshot portfolio tablet-portrait-font-150 'Стоимость портфеля' 1600 2560
snapshot bond-calendar bond-tablet-portrait-font-150 'Календарь купонов' 1600 2560
adb shell settings put system font_scale 1.0
fi

if [[ "$capture_device" != tablet ]]; then
adb shell wm size 1080x2400
adb shell wm density 480
adb shell settings put system font_scale 1.5
snapshot portfolio phone-font-150 'Стоимость портфеля'
snapshot actions actions-font-150 'Быстрые действия'
snapshot operations operations-font-150 'История операций'
snapshot assets assets-font-150 'Мои бумаги'
snapshot analytics analytics-font-150 'Структура портфеля'
snapshot more more-font-150 'Т‑Инвестиции'
snapshot bond-calendar bond-calendar-font-150 'Календарь купонов'
snapshot bond-reference bond-reference-font-150 'Облигации'
fi
adb logcat -d > "$capture_dir/logcat.txt"
