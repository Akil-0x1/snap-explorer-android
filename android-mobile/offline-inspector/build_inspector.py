"""Build using existing local libraries only; never fetches dependencies or reads exports."""
from pathlib import Path
import os,subprocess,zipfile,shutil,hashlib
root=Path(__file__).resolve().parent
jdk=Path(os.environ.get('SNAP_JAVA_HOME','/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home'))
lib=root/'lib';lib.mkdir(exist_ok=True)
names=['jsoup-1.24.1.jar','commons-compress-1.28.0.jar','commons-io-2.20.0.jar','commons-codec-1.19.0.jar','commons-lang3-3.18.0.jar','gson-2.13.2.jar','json-20260719.jar']
for name in names:
 source=root.parent/'.tools'/name
 if source.is_file():shutil.copy2(source,lib/name)
 if not (lib/name).is_file():raise SystemExit('Missing local dependency. No download attempted.')
# The launcher uses lib/*: retire old JSON copies so classpath order cannot
# silently load an older vulnerable JAR after a rebuild. Only generated copies
# are moved; source downloads and all signing material stay unchanged.
for old in lib.glob('json-*.jar'):
 if old.name in names:continue
 retired=root/'.retired-libs';retired.mkdir(exist_ok=True)
 destination=retired/(hashlib.sha256(old.read_bytes()).hexdigest()[:16]+'-'+old.name)
 if destination.exists():
  if destination.read_bytes()!=old.read_bytes():raise SystemExit('Unexpected retired-library collision.')
  old.unlink()
 else:old.rename(destination)
stream_source=root.parent/'app/src/main/java/local/snapexplorer/mobile/JsonRecords.java'
if stream_source.is_file():shutil.copy2(stream_source,root/'src/JsonRecords.java')
classes=root/'.classes';classes.mkdir(exist_ok=True)
subprocess.run([str(jdk/'bin/javac'),'--release','8','-cp',os.pathsep.join(str(lib/name) for name in names),'-d',str(classes),str(root/'src/OfflineInspector.java'),str(root/'src/JsonRecords.java')],check=True)
with zipfile.ZipFile(lib/'inspector.jar','w',zipfile.ZIP_DEFLATED) as out:
 for p in classes.rglob('*.class'):out.write(p,p.relative_to(classes))
print('Built local inspector. No personal exports were accessed.')
