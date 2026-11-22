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
from upgrade_policy import verify_preservation

parser = argparse.ArgumentParser()
parser.add_argument('--old-apk', required=True, type=Path)
parser.add_argument('--new-apk', required=True, type=Path)
parser.add_argument('--output', type=Path, default=Path('build/outputs/upgrade-check'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
package = 'com.mamoro.investments'
db_dir = f'/data/user/0/{package}/databases'

def command(*items, timeout=120):
    result = subprocess.run(list(map(str, items)), stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, timeout=timeout)
    print(result.stdout.strip(), flush=True)
    result.check_returncode()
    return result.stdout.strip().replace('\r', '')

def adb(*items):
    return command('adb', *items)

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
adb('install', args.old_apk.resolve())
adb('shell', 'am', 'start', '-W', '-n', f'{package}/.MainActivity')
for attempt in range(20):
    exists = subprocess.run(['adb','shell','test','-f',f'{db_dir}/investments_v1.db']).returncode == 0
    if exists:
        break
    time.sleep(1)
assert exists, 'Old app did not create its database'
adb('shell', 'am', 'force-stop', package)
adb('pull', db_dir, args.output / 'before')
database = next((args.output / 'before').rglob('investments_v1.db'))
connection = sqlite3.connect(database)
now = int(time.time() * 1000)
connection.execute('INSERT INTO securities VALUES (?,?,?,?,?,?,?)',
                   ('qa-security', 'Проверка обновления', 'QA', 'STOCK', 12000, now, 0))
connection.execute('INSERT INTO wallet_accounts VALUES (?,?,?,?,?)',
                   ('qa-broker', 'Тестовый счёт', 'RUB', 1000000, now))
connection.execute('INSERT INTO trades VALUES (?,?,?,?,?,?,?,?,?)',
                   ('qa-trade','qa-security','BUY',1000000,10000,100,now,'qa-broker',0))
connection.execute('INSERT INTO income VALUES (?,?,?,?,?,?,?,?)',
                   ('qa-income','qa-security','DIVIDEND',500,'Проверка сохранения',now,'qa-broker',0))
payload = json.dumps(dict(schema_version=1, event_id='qa-event', operation_id='qa-trade',
                         source_app=package, event_kind='TRADE_UPSERT', security_id='qa-security',
                         ticker='QA', side='BUY', quantity_micros=1000000, unit_price_minor=10000,
                         fee_minor=100, cash_delta_minor=-10100, currency='RUB',
                         wallet_account_id='qa-broker', happened_at=now))
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
with (args.output / 'updated-release.png').open('wb') as output:
    subprocess.run(['adb','exec-out','screencap','-p'],check=True,stdout=output)
adb('shell','am','force-stop',package)
adb('pull',db_dir,args.output/'after')
after = snapshot(next((args.output/'after').rglob('investments_v1.db')))
preservation = verify_preservation(before, after)
report = dict(installed_over_previous=True, **preservation,
              certificate_sha256=new_cert, old_version=old_manifest, new_version=new_manifest,
              old_apk_sha256=hashlib.sha256(args.old_apk.read_bytes()).hexdigest(),
              new_apk_sha256=hashlib.sha256(args.new_apk.read_bytes()).hexdigest(),
              row_counts={name:len(rows) for name,rows in after['rows'].items()})
(args.output/'upgrade-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False,indent=2))
