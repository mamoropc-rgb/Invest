"""Verify a signed APK update on a fresh disposable Android emulator only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET
from upgrade_policy import verify_preservation
from upgrade_ui import bounds as node_bounds, launcher_anr_close

parser = argparse.ArgumentParser()
parser.add_argument('--old-apk', required=True, type=Path)
parser.add_argument('--new-apk', required=True, type=Path)
parser.add_argument('--output', type=Path, default=Path('build/outputs/upgrade-check'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
(args.output / 'run-status.json').write_text(json.dumps({'status': 'started', 'personal_data': False}))
package = 'com.mamoro.investments'
db_dir = f'/data/user/0/{package}/databases'

def command(*items, timeout=120, quiet=False):
    result = subprocess.run(list(map(str, items)), stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=timeout)
    if not quiet:
        print(result.stdout.strip(), flush=True)
    result.check_returncode()
    return result.stdout.strip().replace('\r', '')

def adb(*items, **kwargs):
    return command('adb', *items, **kwargs)

assert adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Only a disposable emulator is allowed'
assert f'package:{package}' not in adb('shell', 'pm', 'list', 'packages', package), 'A fresh emulator is required'
apksigner = sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))[-1]
aapt = apksigner.parent / 'aapt'
def manifest(apk):
    text = command(aapt, 'dump', 'badging', apk)
    found = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text)
    assert found, 'Cannot read APK metadata'
    return dict(package=found[1], code=int(found[2]), version=found[3])
old_manifest, new_manifest = manifest(args.old_apk), manifest(args.new_apk)
assert old_manifest['package'] == new_manifest['package'] == package, 'Application ID changed'
assert new_manifest['code'] > old_manifest['code'], 'A newer versionCode is required'

def certificate(apk):
    output = command(apksigner, 'verify', '--print-certs', apk)
    return re.search(r'certificate SHA-256 digest: ([a-f0-9]+)', output).group(1)

old_cert, new_cert = certificate(args.old_apk), certificate(args.new_apk)
assert old_cert == new_cert, 'Release signing certificate changed'
root_ready = False
for attempt in range(3):
    # adbd restarts during root activation and can disconnect before replying.
    result = subprocess.run(['adb','root'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, timeout=30)
    print(result.stdout.strip(), flush=True)
    adb('wait-for-device')
    if adb('shell', 'id', '-u') == '0':
        root_ready = True
        break
    time.sleep(1)
assert root_ready, 'The disposable emulator must support adb root'

def ui_node(text=None, class_name=None, attempts=10):
    recovered_launcher = False
    for attempt in range(attempts):
        adb('shell','uiautomator','dump','/sdcard/invest-upgrade-ui.xml')
        xml = adb('shell','cat','/sdcard/invest-upgrade-ui.xml', quiet=True)
        (args.output / 'last-ui.xml').write_text(xml)
        tree = ET.fromstring(xml)
        launcher_button = launcher_anr_close(tree)
        if launcher_button is not None:
            assert not recovered_launcher, 'Pixel Launcher repeatedly stopped responding'
            recovered_launcher = True
            left, top, right, bottom = launcher_button
            print('Recovering the disposable emulator launcher; app ANRs are not ignored', flush=True)
            adb('shell','input','tap',(left+right)//2,(top+bottom)//2)
            adb('shell','am','start','-W','-n',f'{package}/.MainActivity')
            continue
        for node in tree.iter('node'):
            if (text is None or node.get('text') == text) and (class_name is None or node.get('class') == class_name):
                rectangle = node_bounds(node)
                if rectangle is not None:
                    return rectangle
        time.sleep(1)
    raise AssertionError('Expected UI control was not visible: '+str(text or class_name))

def tap_ui(**match):
    left, top, right, bottom = ui_node(**match)
    adb('shell','input','tap',(left+right)//2,(top+bottom)//2)

adb('install', args.old_apk.resolve())
adb('shell', 'am', 'start', '-W', '-n', f'{package}/.MainActivity')
for attempt in range(20):
    exists = subprocess.run(['adb','shell','test','-f',f'{db_dir}/investments_v1.db']).returncode == 0
    if exists:
        break
    time.sleep(1)
assert exists, 'Old app did not create its database'
token_seeded = old_manifest['code'] >= 7
if token_seeded:
    # Exercise the old signed Release UI. No API request is made by saving this fake credential.
    tap_ui(text='Ещё')
    tap_ui(text='Подключить')
    tap_ui(class_name='android.widget.EditText')
    adb('shell','input','text','emulator-only-upgrade-credential')
    adb('shell','input','keyevent','4')
    tap_ui(text='Сохранить')
    ui_node(text='Токен сохранён на устройстве')
adb('shell', 'am', 'force-stop', package)
preferences = f'/data/user/0/{package}/shared_prefs/t_invest_connection.xml'
token_before = adb('shell','cat',preferences) if token_seeded else None
if token_seeded:
    assert 'emulator-only-upgrade-credential' not in token_before, 'Credential must be encrypted'
adb('pull', db_dir, args.output / 'before')
database = next((args.output / 'before').rglob('investments_v1.db'))
connection = sqlite3.connect(database)
now = int(time.time() * 1000)
trade_time = now - 30 * 86_400_000 if token_seeded else now
connection.execute('INSERT INTO securities VALUES (?,?,?,?,?,?,?)',
                   ('qa-security', 'Проверка обновления', 'QA', 'BOND' if token_seeded else 'STOCK', 12000, now, 0))
connection.execute('INSERT INTO wallet_accounts VALUES (?,?,?,?,?)',
                   ('qa-broker', 'Тестовый счёт', 'RUB', 1000000, now))
connection.execute('INSERT INTO trades VALUES (?,?,?,?,?,?,?,?,?)',
                   ('qa-trade','qa-security','BUY',1000000,10000,100,trade_time,'qa-broker',0))
connection.execute('INSERT INTO income VALUES (?,?,?,?,?,?,?,?)',
                   ('qa-income','qa-security','COUPON' if token_seeded else 'DIVIDEND',500,'Проверка сохранения',now,'qa-broker',0))
if connection.execute('PRAGMA user_version').fetchone()[0] == 2:
    day = now // 86_400_000
    connection.execute('INSERT INTO bond_reference VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
        ('qa-security','qa-bond-uid','','QA-ISIN','QA','TQOB','Проверка обновления','RUB','1000','1.50',
         day-365,day-364,day+365,2,0,0,0,now,day-365,day+365))
    connection.execute('INSERT INTO bond_coupons VALUES (?,?,?,?,?,?,?,?,?,?,?)',
        ('qa-security','qa-bond-uid/1',1,day-1,day-3,day-185,day-2,'5.00','RUB','COUPON_TYPE_CONSTANT',1))
    connection.execute('INSERT INTO bond_trade_dates VALUES (?,?,?)',('qa-trade',day-30,day-29))
    connection.execute('INSERT INTO bond_coupon_receipts VALUES (?,?,?)',('qa-security','qa-bond-uid/1','qa-income'))
payload = json.dumps(dict(schema_version=1, event_id='qa-event', operation_id='qa-trade',
                         source_app=package, event_kind='TRADE_UPSERT', security_id='qa-security',
                         ticker='QA', side='BUY', quantity_micros=1000000, unit_price_minor=10000,
                         fee_minor=100, cash_delta_minor=-10100, currency='RUB',
                         wallet_account_id='qa-broker', happened_at=trade_time))
connection.execute('INSERT INTO sync_events VALUES (?,?,?,?,?,?)',
                   (1,'qa-event','qa-trade','TRADE_UPSERT',payload,now))
connection.execute('INSERT OR REPLACE INTO sync_state VALUES (?,?)', (1,0))
connection.commit()
seeded = args.output / 'seeded.db'
destination = sqlite3.connect(seeded)
connection.backup(destination)
destination.execute('PRAGMA wal_checkpoint(TRUNCATE)')
destination.execute('PRAGMA journal_mode=DELETE')
destination.close()
connection.close()

def snapshot(path):
    with sqlite3.connect(path) as db:
        schema = list(db.execute("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"))
        return dict(version=db.execute('PRAGMA user_version').fetchone()[0], schema=schema,
                    rows={name: sorted(list(db.execute(f'SELECT * FROM "{name}"')),
                                       key=lambda row: json.dumps(row,ensure_ascii=False)) for name,_ in schema})

before = snapshot(seeded)
owner = adb('shell','stat','-c','%u:%g',db_dir)
adb('shell','rm','-f',f'{db_dir}/investments_v1.db-wal',f'{db_dir}/investments_v1.db-shm')
adb('push',seeded,f'{db_dir}/investments_v1.db')
adb('shell','chown',owner,f'{db_dir}/investments_v1.db')
adb('shell','chmod','660',f'{db_dir}/investments_v1.db')
adb('shell','restorecon',f'{db_dir}/investments_v1.db')
install_result = adb('install','-r',args.new_apk.resolve())
assert 'Success' in install_result, 'Update installation failed'
adb('shell','am','start','-W','-n',f'{package}/.MainActivity')
time.sleep(3)
if token_seeded:
    tap_ui(text='Ещё')
    ui_node(text='Токен сохранён на устройстве')
    ui_node(text='Заменить токен')
    assert adb('shell','cat',preferences) == token_before, 'Saved encrypted credential changed'
with (args.output / 'updated-release.png').open('wb') as output:
    subprocess.run(['adb','exec-out','screencap','-p'],check=True,stdout=output)
adb('shell','am','force-stop',package)
adb('pull',db_dir,args.output/'after')
after = snapshot(next((args.output/'after').rglob('investments_v1.db')))
preservation = verify_preservation(before, after)
report = dict(installed_over_previous=True, **preservation,
              certificate_sha256=new_cert, old_version=old_manifest, new_version=new_manifest,
              saved_token_preserved=token_seeded,
              old_apk_sha256=hashlib.sha256(args.old_apk.read_bytes()).hexdigest(),
              new_apk_sha256=hashlib.sha256(args.new_apk.read_bytes()).hexdigest(),
              row_counts={name:len(rows) for name,rows in after['rows'].items()})
(args.output/'upgrade-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
(args.output/'run-status.json').write_text(json.dumps({'status': 'passed', 'personal_data': False}))
print(json.dumps(report,ensure_ascii=False,indent=2))
