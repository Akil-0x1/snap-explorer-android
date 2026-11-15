"""Run production schema/query SQL on SQLite, including an existing v0.1 database."""
import json,sqlite3,sys
spec=json.load(open(sys.argv[1]))
db=sqlite3.connect(':memory:')
legacy=["CREATE TABLE chats(id TEXT PRIMARY KEY,title TEXT NOT NULL)","CREATE TABLE messages(id INTEGER PRIMARY KEY,chat TEXT NOT NULL,sender TEXT NOT NULL,body TEXT NOT NULL,kind TEXT NOT NULL,date TEXT NOT NULL,epoch INTEGER NOT NULL)","CREATE INDEX chat_time ON messages(chat,epoch,id)","CREATE VIRTUAL TABLE message_search USING fts4(body,sender,tokenize=unicode61)"]
for sql in legacy:db.execute(sql)
db.executemany('INSERT INTO chats VALUES (?,?)',[('alice','Alice'),('bob','Bob')])
rows=[(1,'alice','Alice','Coffee morning','TEXT','date1',1000),(2,'alice','You','Coffee reply','TEXT','date2',2000),(3,'alice','Alice','Tea instead','TEXT','date3',2000),(4,'bob','Bob','Coffee with Bob','TEXT','date4',2500)]
db.executemany('INSERT INTO messages VALUES (?,?,?,?,?,?,?)',rows)
for row in rows:db.execute('INSERT INTO message_search(docid,body,sender) VALUES(?,?,?)',(row[0],row[3],row[2]))
db.commit()
for sql in spec['migration']+spec['extensions']+spec['links']+spec['aliases']+spec['catalog']:db.execute(sql)
assert db.execute('SELECT id,chat,sender,body,kind,date,epoch FROM messages ORDER BY id').fetchall()==rows
assert db.execute('SELECT COUNT(*) FROM messages WHERE media_refs=? AND reply_to=? AND external_id=? AND own=0',('[]','','')).fetchone()[0]==4

def query(name):q=spec['queries'][name];return db.execute(q['sql'],q['args']).fetchall()
assert [r[0] for r in query('search')]==[4,2,1]
assert [r[0] for r in query('scope')]==[2]
assert query('empty')==[]
query('punctuation');assert db.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==4
assert [r[0] for r in query('before')]==[1,2]
assert [r[0] for r in query('through')]==[1,2,3]
assert [r[0] for r in query('after')]==[3]
db.execute("UPDATE messages SET fingerprint='same' WHERE id=1")
try:db.execute("UPDATE messages SET fingerprint='same' WHERE id=2")
except sqlite3.IntegrityError:pass
else:raise AssertionError('fingerprint constraint')
db.commit()
db.execute('BEGIN');db.execute('DELETE FROM messages');db.execute('DELETE FROM message_search');db.rollback()
assert db.execute('SELECT COUNT(*) FROM messages').fetchone()[0]==4
assert len(query('search'))==3
fresh=sqlite3.connect(':memory:')
for sql in spec['core']+spec['extensions']+spec['links']+spec['aliases']+spec['catalog']:fresh.execute(sql)
assert fresh.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('12 production SQLite migration/query checks passed.')

# Cross-page chronology: the old DESC pages mixed October/November at boundaries.
db.execute("INSERT INTO chats VALUES ('paging','Paging')")
for i in range(1,301):db.execute("INSERT INTO messages(id,chat,sender,body,kind,date,epoch) VALUES (?,?,?,?,?,?,?)",(i+1000,'paging','Alice',str(i),'TEXT',str(i),i))
# The cursor IDs reflect the actual stored row IDs.
for name in ['olderPage','newerPage']:
 q=spec['queries'][name];q['args'][-1]=str(int(q['args'][-1])+1000)
latest=query('latestPage');older=query('olderPage');newer=query('newerPage')
assert [r[6] for r in older+latest]==list(range(101,301))
assert [r[6] for r in newer]==list(range(201,301))
# Source switches and chat scope must use the same production filters as exports.
for id,source in [('a','chat'),('b','memories'),('c','chat'),('d','overlay')]:
 db.execute('INSERT INTO media(id,stored_path,original_path,name,mime,kind,source,epoch,bytes,sha256,archive_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)',(id,'/local/'+id,id,id,'image/jpeg','image',source,1700000000000,1,id,'archive'))
db.execute('UPDATE messages SET media_refs=? WHERE id=1',('["a","b"]',))
db.execute('UPDATE messages SET media_refs=? WHERE id=4',('["c"]',))
db.executemany('INSERT INTO message_media(message_id,media_id) VALUES (?,?)',[(1,'a'),(1,'b'),(4,'c')])
db.execute('INSERT INTO memories(id,epoch,date,media_type,media_id,archive_id) VALUES (?,?,?,?,?,?)',('memory',1700000000000,'date','image','a','archive'))
def media(name):q=spec['media'][name];return [r[0] for r in db.execute(q['sql'],q['args'])]
assert media('all')==['a','b']
assert media('chat')==['a','b']
assert media('memories')==['a','b']
assert media('overlay')==[]
assert media('memories')==['a','b'] # switching back does not leak a previous source
print('7 chronology and scoped media filter checks passed.')

for i in range(1,301):db.execute('INSERT INTO message_search(docid,body,sender) VALUES (?,?,?)',(i+1000,'Message '+str(i),'Alex'))
assert query('countMatches')[0][0]==300
assert query('matchFirst')[0][0]==1300
assert query('matchOlder')[0][0]==1299
assert query('matchNewer')[0][0]==1300
assert [r[0] for r in query("resultFirst")]==list(range(1300,1200,-1))
assert [r[0] for r in query("resultOlder")]==list(range(1200,1100,-1))
print("2 paged search-result list checks passed.")
# A v2 database can receive the new derived attachment index without replacing data.
v2=sqlite3.connect(':memory:')
for sql in spec['core']+spec['extensions']:v2.execute(sql)
v2.execute("INSERT INTO chats VALUES ('keep','Keep')")
for sql in spec['links']:v2.execute(sql)
assert v2.execute('SELECT title FROM chats').fetchone()[0]=='Keep'
print('5 indexed match-navigation and v2 migration checks passed.')

for sql in spec['aliases']:v2.execute(sql)
assert v2.execute("SELECT title FROM chats WHERE id='keep'").fetchone()[0]=='Keep'
db.execute("INSERT INTO message_aliases VALUES (2,1,'alice')")
assert [r[0] for r in query('search')]==[4,1]
assert [r[0] for r in query('before')]==[1]
db.execute('DELETE FROM message_aliases')
assert [r[0] for r in query('before')]==[1,2]
print('4 non-destructive duplicate-repair visibility checks passed.')

for sql in spec['catalog']:v2.execute(sql)
assert v2.execute("SELECT title FROM chats WHERE id='keep'").fetchone()[0]=='Keep'
db.execute("INSERT INTO asset_tags VALUES ('a','sticker')")
assert db.execute("SELECT COUNT(*) FROM media WHERE EXISTS(SELECT 1 FROM asset_tags tag WHERE tag.media_id=media.id AND tag.role='sticker')").fetchone()[0]==1
print('2 full-catalog additive migration checks passed.')

# The same memory-index schema and INSERT OR IGNORE policy used by Android search.
grouped=sqlite3.connect(':memory:');grouped.execute(spec['searchGroupingSchema'])
for copy in range(2):
 for i in range(305):
  before=grouped.total_changes;grouped.execute("INSERT OR IGNORE INTO hits(message_id,group_key,copies) VALUES(?,?,1)",(i+copy*1000,str(i)))
  if grouped.total_changes==before:grouped.execute("UPDATE hits SET copies=copies+1 WHERE group_key=?",(str(i),))
assert grouped.execute("SELECT COUNT(*),SUM(copies) FROM hits").fetchone()==(305,610)
assert [r[0] for r in grouped.execute("SELECT position FROM hits ORDER BY position")]==list(range(1,306))
assert len(grouped.execute("SELECT message_id FROM hits WHERE position>100 ORDER BY position LIMIT 100").fetchall())==100
assert grouped.execute("SELECT message_id FROM hits WHERE position=101").fetchone()[0]==100
print('4 stable grouped-search count, page and arrow index checks passed.')
