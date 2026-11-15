"""Build using existing local libraries only; never fetches dependencies or reads exports."""
from pathlib import Path
import os,subprocess,zipfile,shutil
root=Path(__file__).resolve().parent
jdk=Path(os.environ.get('SNAP_JAVA_HOME','/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home'))
lib=root/'lib';lib.mkdir(exist_ok=True)
names=['jsoup-1.24.1.jar','commons-compress-1.28.0.jar','commons-io-2.20.0.jar','commons-codec-1.19.0.jar','commons-lang3-3.18.0.jar','gson-2.13.2.jar','json-20250517.jar']
for name in names:
 source=root.parent/'.tools'/name
 if source.is_file():shutil.copy2(source,lib/name)
 if not (lib/name).is_file():raise SystemExit('Missing local dependency. No download attempted.')
stream_source=root.parent/'app/src/main/java/local/snapexplorer/mobile/JsonRecords.java'
if stream_source.is_file():shutil.copy2(stream_source,root/'src/JsonRecords.java')
classes=root/'.classes';classes.mkdir(exist_ok=True)
subprocess.run([str(jdk/'bin/javac'),'--release','8','-cp',os.pathsep.join(str(lib/name) for name in names),'-d',str(classes),str(root/'src/OfflineInspector.java'),str(root/'src/JsonRecords.java')],check=True)
with zipfile.ZipFile(lib/'inspector.jar','w',zipfile.ZIP_DEFLATED) as out:
 for p in classes.rglob('*.class'):out.write(p,p.relative_to(classes))
print('Built local inspector. No personal exports were accessed.')
