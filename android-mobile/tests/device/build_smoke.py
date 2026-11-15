from pathlib import Path
import sys,subprocess,zipfile,shutil
sys.path.insert(0,str(Path(__file__).resolve().parents[2]))
from build import JDK,BT,ANDROID,BUILD,KEYS,ROOT
KEYS=ROOT/'.keys-recovery'
out=ROOT/'.device-test';out.mkdir(exist_ok=True);(out/'classes').mkdir(exist_ok=True);(out/'dex').mkdir(exist_ok=True)
def run(*args):subprocess.run(list(map(str,args)),check=True)
run(JDK/'bin/javac','--release','8','-cp',str(ANDROID)+':'+str(BUILD/'classes'),'-d',out/'classes',*sorted(Path(__file__).parent.glob('*.java')))
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for p in (out/'classes').rglob('*.class'):z.write(p,p.relative_to(out/'classes'))
run(JDK/'bin/java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',ANDROID,'--classpath',BUILD/'classes.jar','--output',out/'dex',out/'classes.jar')
run(BT/'aapt2','link','-o',out/'unsigned.apk','-I',ANDROID,'--manifest',Path(__file__).with_name('AndroidManifest.xml'),'-A',Path(__file__).with_name('assets'),'-A',ROOT/'tests/fixtures/public-snapchat')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex')
run(BT/'zipalign','-f','4',out/'unsigned.apk',out/'aligned.apk')
run(JDK/'bin/java','-jar',BT/'lib/apksigner.jar','sign','--ks',KEYS/'recovery.p12','--ks-key-alias','snap-recovery','--ks-pass','file:'+str(KEYS/'password'),'--out',out/'smoke.apk',out/'aligned.apk')
