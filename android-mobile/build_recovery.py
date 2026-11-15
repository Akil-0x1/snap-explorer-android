"""Build a separately signed, side-by-side Recovery app. Never updates the original package."""
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess,zipfile,shutil,secrets,hashlib,re
from build import ROOT,BUILD,BT,ANDROID,JDK,ARTIFACTS,run
KEYS=ROOT/'.keys-recovery';ANDROID_NS='http://schemas.android.com/apk/res/android';PACKAGE='local.snapexplorer.recovery'
def main():
 run('python3',ROOT/'build.py')
 ET.register_namespace('android',ANDROID_NS);tree=ET.parse(ROOT/'app/src/main/AndroidManifest.xml');m=tree.getroot();m.set('package',PACKAGE);m.set('{'+ANDROID_NS+'}versionCode','16');m.set('{'+ANDROID_NS+'}versionName','0.4.6-recovery')
 app=m.find('application');app.set('{'+ANDROID_NS+'}label','Snap Explorer Recovery')
 for a in app.findall('activity'):
  attr='{'+ANDROID_NS+'}name';name=a.attrib[attr]
  if name.startswith('.'):a.set(attr,'local.snapexplorer.mobile'+name)
 manifest=BUILD/'recovery-manifest.xml';tree.write(manifest,encoding='utf-8',xml_declaration=True)
 run(BT/'aapt2','link','-o',BUILD/'recovery-base.apk','-I',ANDROID,'--manifest',manifest,'--custom-package','local.snapexplorer.mobile','-A',ROOT/'app/src/main/assets',BUILD/'resources.zip')
 with zipfile.ZipFile(BUILD/'recovery-base.apk','a',zipfile.ZIP_DEFLATED) as z:
  for dex in (BUILD/'dex').glob('*.dex'):z.write(dex,dex.name)
 run(BT/'zipalign','-f','4',BUILD/'recovery-base.apk',BUILD/'recovery-aligned.apk')
 KEYS.mkdir(mode=0o700,exist_ok=True);key=KEYS/'recovery.p12';password=KEYS/'password'
 if not key.exists():
  backup=Path.home()/'Library/Application Support/Android Development Signing Keys/SnapExplorerRecovery'
  if (backup/'recovery.p12').is_file() and (backup/'password').is_file():
   shutil.copyfile(backup/'recovery.p12',key);shutil.copyfile(backup/'password',password);key.chmod(0o600);password.chmod(0o600)
 if not key.exists() or not password.is_file():raise SystemExit('The Recovery signing key is missing. Restore its private development-key backup; refusing to generate a replacement identity.')
 apk=ARTIFACTS/'Snap-Explorer-Recovery-0.4.6.apk'
 run(JDK/'bin/java','-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','snap-recovery','--ks-pass','file:'+str(password),'--out',apk,BUILD/'recovery-aligned.apk')
 verify=subprocess.check_output([str(JDK/'bin/java'),'-jar',str(BT/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(apk)],text=True)
 badging=subprocess.check_output([str(BT/'aapt2'),'dump','badging',str(apk)],text=True);permissions=subprocess.check_output([str(BT/'aapt2'),'dump','permissions',str(apk)],text=True)
 assert "package: name='"+PACKAGE+"'" in badging
 assert 'uses-permission' not in permissions
 assert 'ef6d1fdf8e1e4f0feae13b9b87533ee828c802ac3850fbb0cfce08dbb91af541' not in verify
 run(BT/'zipalign','-c','4',apk)
 (ARTIFACTS/'RECOVERY-APK-VERIFICATION.txt').write_text(verify+'\n'+badging+'\n'+permissions)
 digest=hashlib.sha256(apk.read_bytes()).hexdigest();(ARTIFACTS/'RECOVERY-APK-SHA256SUMS.txt').write_text(digest+'  '+apk.name+'\n')
 print('Separate Recovery app signed and verified: '+str(apk));print('Package: '+PACKAGE+'; original app is not replaced.');print('SHA256: '+digest)
if __name__=='__main__':main()
