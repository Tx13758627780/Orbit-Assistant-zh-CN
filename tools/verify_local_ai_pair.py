#!/usr/bin/env python3
"""Check the actual shipped APKs, including the embedded optional installer."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--main-apk', type=Path, required=True)
    parser.add_argument('--component-apk', type=Path, required=True)
    parser.add_argument('--certificate', required=True)
    args = parser.parse_args()
    expected_pin = args.certificate.replace(':', '').lower()
    assert re.fullmatch('[0-9a-f]{64}', expected_pin)
    results = {}
    for name, apk, package in [('main', args.main_apk, 'com.orbit.assistant'),
                               ('component', args.component_apk, 'com.orbit.assistant.local')]:
        signed = subprocess.check_output([str(args.build_tools/'apksigner'), 'verify',
                                           '--print-certs', str(apk)], text=True)
        pins = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signed)
        assert len(pins) == 1 and pins[0].lower() == expected_pin, (name, pins)
        badging = subprocess.check_output([str(args.build_tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
        identity = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
        assert identity and identity.group(1) == package
        with zipfile.ZipFile(apk) as archive:
            dex_files = [n for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n)]
            assert any(expected_pin.encode() in archive.read(n).lower() for n in dex_files), name
        results[name] = {'package': package, 'versionCode': int(identity.group(2)),
                         'versionName': identity.group(3), 'size': apk.stat().st_size,
                         'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
                         'certificateSha256': pins[0]}
    assert results['main']['versionCode'] == results['component']['versionCode']
    assert results['main']['versionName'] == results['component']['versionName']
    with zipfile.ZipFile(args.main_apk) as archive:
        metadata = json.loads(archive.read('assets/orbit-local/orbit-update.json'))['component']
        embedded = archive.read('assets/orbit-local/' + metadata['apkAssetName'])
        assert embedded == args.component_apk.read_bytes()
        assert metadata['apkSha256'] == results['component']['sha256']
        assert metadata['apkSize'] == len(embedded)
        assert metadata['certificateSha256'].lower() == expected_pin
        assert metadata['protocol'] == 3
        assert metadata['packageName'] == results['component']['package']
        assert metadata['versionCode'] == results['component']['versionCode']
        assert metadata['versionName'] == results['component']['versionName']
    results['embeddedComponent'] = 'identical to signed component; all manifest fields verified'
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
