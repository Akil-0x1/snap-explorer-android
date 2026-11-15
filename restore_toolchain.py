from pathlib import Path
from urllib.request import urlopen
from concurrent.futures import ThreadPoolExecutor
import hashlib,zipfile,os,time,subprocess
BASE=Path(__file__).resolve().parent/'android-mobile/.tools';BASE.mkdir(parents=True,exist_ok=True)
def fetch(url,path,expected=None,algorithm='sha256'):
 if path.exists() and expected and hashlib.new(algorithm,path.read_bytes()).hexdigest()==expected:return
 for attempt in range(3):
  try:
   subprocess.run(['/usr/bin/curl','--fail','--location','--retry','2','--silent','--show-error','--output',str(path),url],check=True)
   if expected and hashlib.new(algorithm,path.read_bytes()).hexdigest()!=expected:raise ValueError('Checksum mismatch: '+path.name)
   return
  except Exception:
   if attempt==2:raise
   time.sleep(1)
def sdk(spec):
 name,sha,out=spec;p=BASE/name;fetch('https://dl.google.com/android/repository/'+name,p,sha,'sha1')
 with zipfile.ZipFile(p) as z:
  for info in z.infolist():
   if '..' in Path(info.filename).parts or info.filename.startswith('/'):raise ValueError('Unsafe SDK member')
  z.extractall(BASE/out)
  for info in z.infolist():
   mode=(info.external_attr>>16)&0o777
   if mode and not info.is_dir():os.chmod(BASE/out/info.filename,mode)
 print('Verified and restored SDK:',name,flush=True)
def jar(spec):
 group,name,version=spec;filename=f'{name}-{version}.jar';url='https://repo.maven.apache.org/maven2/'+group.replace('.','/')+'/'+name+'/'+version+'/'+filename
 expected=None
 for algorithm in ['sha256','sha512','sha1']:
  result=subprocess.run(['/usr/bin/curl','--fail','--location','--silent',url+'.'+algorithm],capture_output=True,text=True)
  if result.returncode==0:
   expected=result.stdout.strip().split()[0]
   if len(expected)==hashlib.new(algorithm).digest_size*2:break
   expected=None
 if expected is None:raise ValueError('No published checksum for '+filename)
 fetch(url,BASE/filename,expected,algorithm)
 print('Verified library:',filename,flush=True)
with ThreadPoolExecutor(max_workers=3) as pool:
 futures=[pool.submit(sdk,x) for x in [('platform-35_r02.zip','0bb560a90a7a2cbd0dd8348224d518b638fe7949','platform'),('build-tools_r35_macosx.zip','93ab8ce91230e067b5add4bfa79919c52b27f072','build-tools')]]
 futures += [pool.submit(jar,x) for x in [('org.jsoup','jsoup','1.24.1'),('org.apache.commons','commons-compress','1.28.0'),('commons-io','commons-io','2.20.0'),('commons-codec','commons-codec','1.19.0'),('org.apache.commons','commons-lang3','3.18.0'),('com.google.code.gson','gson','2.13.2'),('org.json','json','20260719')]]
 for f in futures:f.result()
