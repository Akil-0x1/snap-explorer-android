from pathlib import Path
import sys,subprocess,tempfile,zipfile,json,hashlib,os,shutil
JDK=Path(os.environ.get('SNAP_JAVA_HOME','/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home'))
ROOT=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='synthetic-inspector-') as temporary:
 base=Path(temporary);home=base/'synthetic-home';home.mkdir();photo='11111111-1111-1111-1111-111111111111';sticker='22222222-2222-2222-2222-222222222222'
 source=base/'SECRET_CONTACT-private-export.zip'
 with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
  def put(name,value):z.writestr(name,json.dumps(value) if isinstance(value,dict) else value)
  put('json/chat_history.json',{'SECRET_CONVERSATION':[{'From':'SECRET_PERSON','Created':'2026-11-01 12:00:00 UTC','Content':'SECRET_MESSAGE','Media IDs':[photo,sticker],'Sticker Path':'custom_stickers/'+sticker+'.webp'}]})
  put('json/custom_stickers.json',{'Custom Stickers':[{'ID':sticker,'Sticker Media URL':'https://secret.invalid/SECRET_TOKEN/'+sticker+'.webp'},{'ID':717330595}]})
  put('custom_stickers/Snapchat-717330595.nomedia',b'synthetic unclassified sticker')
  put('json/memories_history.json',{'Saved Media':[{'Media Type':'VIDEO','Download Link':'https://secret.invalid/SECRET_TOKEN/missing.mp4'}]})
  put('json/friends.json',{'Friends':[{'Username':'SECRET_PERSON','Display Name':'SECRET_DISPLAY'}]})
  put('json/SECRET_UNKNOWN.json',{'SECRET_SCHEMA':[{'SECRET_KEY':'SECRET_VALUE','Media Path':'shared.png'}]})
  put('chat_media/'+photo+'.jpg',b'synthetic image')
  put('custom_stickers/'+sticker+'.webp',b'synthetic sticker')
  put('stickers/static.png',b'synthetic png')
  put('bitmoji/avatar.png',b'synthetic avatar')
  put('media/first/shared.png',b'synthetic first');put('media/second/shared.png',b'synthetic second')
  put('chat_media/voice.wav',b'synthetic audio');put('chat_media/movie.mp4',b'synthetic video');put('attachments/document.pdf',b'synthetic PDF')
  put('html/chat_history/subpage_SECRET_CONVERSATION.html','<h1>SECRET_PERSON</h1><div class="rightpanel"><div><h4>SECRET_PERSON</h4><p>SECRET_MESSAGE</p><img src="../../chat_media/'+photo+'.jpg"><h6>2026-11-01 12:00:00 UTC</h6></div></div>')
 original=hashlib.sha256(source.read_bytes()).hexdigest()
 command=['/usr/bin/sandbox-exec','-p','(version 1)(allow default)(deny network*)',str(JDK/'bin/java'),'-Xmx96m','-Duser.home='+str(home),'-cp',str(ROOT/'lib/*'),'OfflineInspector']
 result=subprocess.run(command+[str(source)],capture_output=True,text=True,check=True)
 reports=home/'Library/Application Support/Snap Export Inspector';run=next(reports.glob('report-*'));data=json.loads((run/'summary.json').read_text());output=(run/'summary.json').read_text()+(run/'report.html').read_text()+result.stdout+result.stderr
 assert 'SECRET_' not in output and 'secret.invalid' not in output and photo not in output and sticker not in output
 assert str(source) not in output
 assert data['media_files']==10,data
 assert data['asset_categories']['Custom sticker files']==2
 assert data['unrecognized_asset_format_files']==1
 assert data['asset_categories']['Sticker files']==1
 assert data['asset_categories']['Bitmoji and avatar files']==1
 assert data['asset_categories']['Audio and voice files']==1
 assert data['asset_categories']['Videos']==1
 assert data['asset_categories']['Documents and other attachments']==1
 assert data['dataset_record_categories']['Stickers and custom stickers']==2
 assert data['unknown_json_files']==1
 assert data['json_chat_records_observed']==1 and data['html_chat_entries_observed']==1
 assert data['reference_counts']['Sticker references']>=3,data['reference_counts']
 assert data['reference_counts']['References with one local candidate']>=4
 assert data['reference_counts']['References with ambiguous local candidates']==1
 assert data['reference_counts']['Remote references without a local candidate']==1
 assert data['metadata_files_partially_read_or_unsupported']==0
 assert hashlib.sha256(source.read_bytes()).hexdigest()==original
 # A selected folder can contain nested ZIP parts and loose assets.
 folder=base/'folder';folder.mkdir();shutil.copy2(source,folder/'part.zip');(folder/'extra.gif').write_bytes(b'synthetic gif')
 subprocess.run(command+[str(folder)],capture_output=True,text=True,check=True)
 latest=Path((reports/'latest-report.txt').read_text()).parent
 assert json.loads((latest/'summary.json').read_text())['media_files']==11
 # A malformed JSON must not leak its path or contents through an exception.
 bad=base/'SECRET_BAD.zip'
 with zipfile.ZipFile(bad,'w') as z:z.writestr('json/SECRET_PRIVATE.json','{"SECRET_NAME": "SECRET_VALUE", broken')
 failed=subprocess.run(command+[str(bad)],capture_output=True,text=True,check=True)
 bad_run=Path((reports/'latest-report.txt').read_text()).parent
 assert json.loads((bad_run/'summary.json').read_text())['metadata_files_partially_read_or_unsupported']==1
 assert 'SECRET_' not in failed.stdout+failed.stderr+(bad_run/'summary.json').read_text()
 # Cleanup deletes generated reports only, retaining any user-added files and originals.
 unrelated=run/'personal-backup.zip';unrelated.write_bytes(b'keep this unrelated file')
 subprocess.run(command+['--deletepersonal'],capture_output=True,text=True,check=True)
 assert unrelated.read_bytes()==b'keep this unrelated file'
 assert not (run/'summary.json').exists() and not (run/'report.html').exists()
 assert hashlib.sha256(source.read_bytes()).hexdigest()==original
 # Assert that the macOS policy denies the Java process network access.
 probe=base/'NetworkProbe.java';probe.write_text('public class NetworkProbe {public static void main(String[] a)throws Exception{try{new java.net.Socket("127.0.0.1",9);System.exit(2);}catch(java.net.SocketException e){String m=e.getMessage().toLowerCase();if(m.contains("not permitted")||m.contains("permission denied")){System.out.println("NETWORK BLOCKED");return;}System.exit(3);}}}')
 subprocess.run([str(JDK/'bin/javac'),'-d',str(base),str(probe)],check=True,capture_output=True)
 probe_result=subprocess.run(['/usr/bin/sandbox-exec','-p','(version 1)(allow default)(deny network*)',str(JDK/'bin/java'),'-cp',str(base),'NetworkProbe'],capture_output=True,text=True,check=True)
 assert 'NETWORK BLOCKED' in probe_result.stdout
print('PASS: full inventory, custom stickers, reference candidates, unknown metadata, privacy redaction, malformed input, safe cleanup, unchanged originals, and enforced network denial. Synthetic data only.')
