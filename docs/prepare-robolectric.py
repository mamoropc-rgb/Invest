"""Cache the two pinned test SDKs, with integrity checks and bounded Maven backoff."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import time
import urllib.error
import urllib.request

PINS = {
    '14-robolectric-10818077-i7': '7cdc0b1306e7865f1acd25a363073149654caee5b8e278c5ae37ff4724185724eff39f0b0111800b521e640bb28698d9d2e655134a801569a0f07470e7b5f455',
    '15-robolectric-12650502-i7': '87bdd0341838f5f630e1ff412ab29cb2f06e0f8cb7751f14679d11b4ee6084873001f8f6a8581ae6e139fa7c9a654b632412827430f984ebecc3c2588674f7e7',
}


def digest(path):
    result = hashlib.sha512()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            result.update(block)
    return result.hexdigest()


def prepare(directory):
    directory.mkdir(parents=True, exist_ok=True)
    report = []
    for version, expected in PINS.items():
        filename = f'android-all-instrumented-{version}.jar'
        target = directory / filename
        cached = target.is_file() and digest(target) == expected
        if not cached:
            gradle = Path.home() / '.gradle/caches/modules-2/files-2.1/org.robolectric/android-all-instrumented' / version
            for candidate in gradle.glob('*/*.jar'):
                if digest(candidate) == expected:
                    shutil.copy2(candidate, target)
                    cached = True
                    break
        if not cached:
            url = f'https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/{version}/{filename}'
            temporary = target.with_suffix('.jar.part')
            for attempt in range(5):
                try:
                    request = urllib.request.Request(url, headers={'User-Agent': 'Invest-CI-test-SDK-cache/1.0'})
                    with urllib.request.urlopen(request, timeout=60) as response, temporary.open('wb') as output:
                        for block in iter(lambda: response.read(1024 * 1024), b''):
                            output.write(block)
                    assert digest(temporary) == expected, f'SDK integrity check failed: {filename}'
                    temporary.replace(target)
                    break
                except (urllib.error.URLError, TimeoutError) as error:
                    temporary.unlink(missing_ok=True)
                    if isinstance(error, urllib.error.HTTPError) and error.code not in (429, 500, 502, 503, 504):
                        raise
                    if attempt == 4:
                        raise
                    retry_after = error.headers.get('Retry-After', '') if isinstance(error, urllib.error.HTTPError) else ''
                    wait = max(min(15 * 2 ** attempt, 60), int(retry_after) if retry_after.isdigit() else 0)
                    if wait > 60:
                        raise RuntimeError(f'Maven requests a {wait}s pause; retry the workflow later') from error
                    print(f'Maven SDK download deferred; waiting {wait}s before retry', flush=True)
                    time.sleep(wait)
        assert target.is_file() and digest(target) == expected
        report.append(dict(file=filename, sha512=expected, bytes=target.stat().st_size, cached=cached))
        print(f'Verified SDK: {filename}; cached={cached}', flush=True)
    (directory / 'sdk-cache-report.json').write_text(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True, type=Path)
    prepare(parser.parse_args().directory)
