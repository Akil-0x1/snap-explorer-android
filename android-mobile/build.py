#!/usr/bin/env python3
"""Build and sign an offline Android APK using only the project-local Android tools."""
from pathlib import Path
import hashlib
import os
import secrets
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / '.tools'
BUILD = ROOT / '.build'
ARTIFACTS = ROOT / 'artifacts'
KEYS = ROOT / '.keys'
JDK = Path(os.environ.get('SNAP_JAVA_HOME', '/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home'))
ANDROID = TOOLS / 'platform/android-35/android.jar'
BT = TOOLS / 'build-tools/android-15'
JSOUP = TOOLS / 'jsoup-1.24.1.jar'
RUNTIME_LIBS = [JSOUP] + [TOOLS / name for name in ['commons-compress-1.28.0.jar','commons-io-2.20.0.jar','commons-codec-1.19.0.jar','commons-lang3-3.18.0.jar','gson-2.13.2.jar']]

def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)

def main():
    for path in [JDK/'bin/javac', ANDROID, BT/'aapt2', *RUNTIME_LIBS]:
        if not path.exists():
            raise SystemExit(f'Missing build prerequisite: {path}. See README.md.')
    # Screenshot capture is an explicit user requirement, including old installs.
    for source in (ROOT/'app/src/main/java').rglob('*.java'):
        if 'FLAG_SECURE' in source.read_text():
            raise SystemExit('Screenshot-blocking window flag unexpectedly present: '+str(source))
    # Recovery build: never create a replacement signing identity automatically.
    # The output is deliberately unsigned and must not be installed over the phone app.
    if BUILD.exists():
        shutil.rmtree(BUILD)  # Only generated compiler output; never touches source or signing keys.
    for folder in ['classes', 'generated', 'dex']:
        (BUILD/folder).mkdir(parents=True, exist_ok=True)
    ARTIFACTS.mkdir(exist_ok=True)
    run(BT/'aapt2', 'compile', '--dir', ROOT/'app/src/main/res', '-o', BUILD/'resources.zip')
    run(BT/'aapt2', 'link', '-o', BUILD/'base.apk', '-I', ANDROID,
        '--manifest', ROOT/'app/src/main/AndroidManifest.xml', '--java', BUILD/'generated',
        '-A', ROOT/'app/src/main/assets', BUILD/'resources.zip')
    sources = sorted((ROOT/'app/src/main/java').rglob('*.java')) + sorted((BUILD/'generated').rglob('*.java'))
    run(JDK/'bin/javac', '--release', '8', '-Xlint:unchecked', '-cp', os.pathsep.join(map(str,[ANDROID,*RUNTIME_LIBS])),
        '-d', BUILD/'classes', *sources)
    with zipfile.ZipFile(BUILD/'classes.jar', 'w') as jar:
        for file in sorted((BUILD/'classes').rglob('*.class')):
            jar.write(file, file.relative_to(BUILD/'classes').as_posix())
    run(JDK/'bin/java', '-cp', BT/'lib/d8.jar', 'com.android.tools.r8.D8', '--release',
        '--min-api', '26', '--lib', ANDROID, '--output', BUILD/'dex', BUILD/'classes.jar', *RUNTIME_LIBS)
    shutil.copyfile(BUILD/'base.apk', BUILD/'unsigned.apk')
    with zipfile.ZipFile(BUILD/'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        for dex in sorted((BUILD/'dex').glob('*.dex')): apk.write(dex, dex.name)
    run(BT/'zipalign', '-f', '-p', '4', BUILD/'unsigned.apk', BUILD/'aligned.apk')
    run(BT/'zipalign', '-c', '-p', '4', BUILD/'aligned.apk')
    apk = ARTIFACTS/'Snap-Explorer-Recovered-0.4.2-UNSIGNED.apk'
    shutil.copyfile(BUILD/'aligned.apk',apk)
    permissions = subprocess.run([str(BT/'aapt2'),'dump','permissions',str(apk)],check=True,capture_output=True,text=True).stdout
    if 'uses-permission' in permissions: raise SystemExit('Unexpected Android permission')
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    (ARTIFACTS/'UNSIGNED-SHA256SUMS.txt').write_text(digest+'  '+apk.name+'\n')
    print('Recovered source compiled and packaged. No signing key was generated.')
    print('UNSIGNED build for verification only; do not install: '+str(apk))

if __name__ == '__main__': main()
