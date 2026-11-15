#!/usr/bin/env python3
import os,subprocess
from build import ROOT,TOOLS,BUILD,JDK,JSOUP,RUNTIME_LIBS
out=BUILD/'tests';out.mkdir(parents=True,exist_ok=True);source=ROOT/'app/src/main/java/local/snapexplorer/mobile'
cp=os.pathsep.join(map(str,[*RUNTIME_LIBS,TOOLS/'json-20260719.jar']))
names=['ChatMessage','ChatParser','ArchiveImporter','MediaItem','MemoryItem','MediaNames','YuvPlanes','YuvOverlay','ExportFormats','DatabaseSchema','DatabaseQueries','PcmAudio','DatasetSnapshot','MetadataFiles','JsonRecords','MediaSelection','ChatPresentation','ConversationSearch','MessageCopies','MessageIdentity','ChatSourceReader','MediaReferences','LocalMediaIndex','DataCategories','MetadataTextPage','SearchGrouping','SenderDirection']
if (source/'MetadataAnalytics.java').exists():names.append('MetadataAnalytics')
tests=['ImporterTest','UpgradeImporterTest','MediaRulesTest','ExportFormatsTest','DatabaseFixtures','DatasetSnapshotTest','PcmAudioTest','MetadataFilesTest','DuplicateEntriesTest','FolderAndLargeImportTest','ChatPresentationTest','MessageCopiesTest','FullExportTest','PublicFixtureTest','StickerReferenceTest','SearchGroupingTest','PortableMediaIndexTest','SenderDirectionTest','JsonDependencyTest']
if (ROOT/'tests/MetadataAnalyticsTest.java').exists():tests.append('MetadataAnalyticsTest')
subprocess.run([str(JDK/'bin/javac'),'--release','8','-cp',cp,'-d',str(out),*[str(source/(name+'.java')) for name in names],*[str(ROOT/'tests'/(name+'.java')) for name in tests]],check=True)
classpath=os.pathsep.join([str(out),cp])
for test in tests:
 args=[str(JDK/'bin/java'),'-cp',classpath,'local.snapexplorer.mobile.'+test]
 if test=='FolderAndLargeImportTest':args.insert(1,'-Xmx96m')
 if test=='PublicFixtureTest':args.append(str(ROOT/'tests/fixtures/public-snapchat'))
 if test=='ImporterTest':args.append(str(ROOT/'app/src/main/assets/sample.zip'))
 if test=='DuplicateEntriesTest':args.append(str(ROOT/'tests/fixtures'))
 if test=='DatabaseFixtures':
  fixture=subprocess.run(args,check=True,capture_output=True,text=True).stdout
  (BUILD/'database-fixtures.json').write_text(fixture)
  subprocess.run(['python3',str(ROOT/'tests/database_checks.py'),str(BUILD/'database-fixtures.json')],check=True)
 else:subprocess.run(args,check=True)
