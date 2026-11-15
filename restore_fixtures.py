from pathlib import Path
from datetime import datetime,timezone
import json,zipfile,warnings,struct,zlib
base=Path(__file__).resolve().parent/'android-mobile';root=base/'tests/fixtures';root.mkdir(parents=True,exist_ok=True)
with warnings.catch_warnings():
 warnings.simplefilter('ignore',UserWarning)
 for name,payloads in [('duplicate-media-different.zip',[b'first distinct media',b'second distinct media']),('duplicate-media-identical.zip',[b'identical media',b'identical media'])]:
  with zipfile.ZipFile(root/name,'w',zipfile.ZIP_DEFLATED) as z:
   for payload in payloads:z.writestr('chat_media/repeated.mp4',payload)
 with zipfile.ZipFile(root/'duplicate-metadata.zip','w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('json/account.json','{"name":"first"}');z.writestr('json/account.json','{"name":"second"}')
 with zipfile.ZipFile(root/'duplicate-with-chat.zip','w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('chat_media/repeated.mp4',b'first');z.writestr('chat_media/repeated.mp4',b'second')
  z.writestr('html/chat_history/subpage_example.html','<h1>Chat History with Example</h1><div class="rightpanel"><div><h4>Example</h4><span>MEDIA</span><p>Video</p><h6>2026-11-01 12:00:00 UTC</h6><video src="../../chat_media/repeated.mp4"></video></div></div>')
with zipfile.ZipFile(root/'bad-media-crc.zip','w',zipfile.ZIP_STORED) as z:z.writestr('chat_media/a.mp4',b'valid original')
p=root/'bad-media-crc.zip';raw=bytearray(p.read_bytes());nl,el=struct.unpack_from('<HH',raw,26);raw[30+nl+el]^=1;p.write_bytes(raw)
with zipfile.ZipFile(root/'zip64-entry.zip','w') as z:
 with z.open('chat_media/zip64.mp4','w',force_zip64=True) as out:out.write(b'synthetic ZIP64 payload')
public=root/'public-snapchat';public.mkdir(exist_ok=True)
def msg(t,fr,kind,body,ids=''):
 return {'From':fr,'Media Type':kind,'Created':t+' UTC','Content':body,'Conversation Title':None,'IsSender':fr=='me_user','Created(microseconds)':int(datetime.strptime(t,'%Y-%m-%d %H:%M:%S').replace(tzinfo=timezone.utc).timestamp())*1000000,'IsSaved':False,'Media IDs':ids}
a={'pal':[msg('2024-01-02 10:00:00','pal','TEXT','hi'),msg('2024-01-02 10:01:00','me_user','TEXT','hey')]};b={'pal':[msg('2024-01-02 10:01:00','me_user','TEXT','hey'),msg('2024-01-01 09:00:00','pal','MEDIA',None,'b~abc|b~def')],'other':[msg('2024-02-02 00:00:00','other','STICKER',None)]}
for name,value in [('chat_history.json',a),('chat_history-copy.json',b)]:(public/name).write_text(json.dumps(value,indent=2)+'\n')
(public/'README.md').write_text('Synthetic export-format test records adapted from the MIT-licensed AxialForge/Unpacker tests.\nSource: https://github.com/AxialForge/unpacker-v2/blob/75c1772af998f59570a1805fc7031ec2b6d10563/test/snapchat-account.test.js\nNot an actual person\'s export.\n')
assets=base/'tests/device/assets';assets.mkdir(exist_ok=True)
def png(rgb):
 def chunk(t,p):return struct.pack('>I',len(p))+t+p+struct.pack('>I',zlib.crc32(t+p))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes(rgb)*16)*16))+chunk(b'IEND',b'')
for name,rgb in [('sticker.png',(255,0,0)),('avatar.png',(0,0,255)),('overlay.png',(0,255,0))]:(assets/name).write_bytes(png(rgb))
print('Recreated synthetic ZIP, public-format and image fixtures; no account files read.')
