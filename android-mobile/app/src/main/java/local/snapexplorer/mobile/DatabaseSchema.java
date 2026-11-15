package local.snapexplorer.mobile;

/** Framework-free schema statements used by Android and host migration tests. */
public final class DatabaseSchema {
    public static final int VERSION = 5;
    public static final String[] CORE = {
        "CREATE TABLE chats(id TEXT PRIMARY KEY, title TEXT NOT NULL)",
        "CREATE TABLE messages(id INTEGER PRIMARY KEY, chat TEXT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, kind TEXT NOT NULL, date TEXT NOT NULL, epoch INTEGER NOT NULL,media_refs TEXT NOT NULL DEFAULT '[]',reply_to TEXT NOT NULL DEFAULT '',external_id TEXT NOT NULL DEFAULT '',own INTEGER NOT NULL DEFAULT 0,fingerprint TEXT)",
        "CREATE INDEX chat_time ON messages(chat,epoch,id)",
        "CREATE VIRTUAL TABLE message_search USING fts4(body,sender,tokenize=unicode61)"
    };
    public static final String[] FROM_V1 = {
        "ALTER TABLE messages ADD COLUMN media_refs TEXT NOT NULL DEFAULT '[]'",
        "ALTER TABLE messages ADD COLUMN reply_to TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE messages ADD COLUMN external_id TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE messages ADD COLUMN own INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE messages ADD COLUMN fingerprint TEXT"
    };
    public static final String[] EXTENSIONS = {
        "CREATE UNIQUE INDEX IF NOT EXISTS message_fingerprint ON messages(fingerprint)",
        "CREATE TABLE archives(id TEXT PRIMARY KEY,label TEXT,source_hash TEXT,imported_at INTEGER,messages INTEGER,media INTEGER,bytes INTEGER,source_path TEXT,report TEXT)",
        "CREATE UNIQUE INDEX archive_hash ON archives(source_hash)",
        "CREATE TABLE archive_messages(archive_id TEXT,message_id INTEGER,PRIMARY KEY(archive_id,message_id))",
        "CREATE TABLE media(id TEXT PRIMARY KEY,stored_path TEXT,original_path TEXT,name TEXT,mime TEXT,kind TEXT,source TEXT,epoch INTEGER,latitude REAL,longitude REAL,bytes INTEGER,sha256 TEXT,archive_id TEXT)",
        "CREATE INDEX media_date ON media(source,epoch)",
        "CREATE INDEX media_name ON media(name)",
        "CREATE TABLE memories(id TEXT PRIMARY KEY,epoch INTEGER,date TEXT,media_type TEXT,latitude REAL,longitude REAL,media_id TEXT,archive_id TEXT)",
        "CREATE TABLE metadata(id TEXT PRIMARY KEY,filename TEXT,json TEXT,archive_id TEXT)",
        "CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT)"
    };
    public static final String[] FROM_V2 = {
        "CREATE TABLE IF NOT EXISTS message_media(message_id INTEGER NOT NULL,media_id TEXT NOT NULL,PRIMARY KEY(message_id,media_id))",
        "CREATE INDEX IF NOT EXISTS media_messages ON message_media(media_id,message_id)"
    };
    public static final String[] FROM_V3 = {
        "CREATE TABLE IF NOT EXISTS message_aliases(alias_id INTEGER PRIMARY KEY,canonical_id INTEGER NOT NULL,chat TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS repaired_chat ON message_aliases(chat)"
    };
    public static final String[] FROM_V4 = {
        "CREATE TABLE IF NOT EXISTS asset_tags(media_id TEXT NOT NULL,role TEXT NOT NULL,PRIMARY KEY(media_id,role))",
        "CREATE INDEX IF NOT EXISTS tagged_assets ON asset_tags(role,media_id)",
        "CREATE TABLE IF NOT EXISTS dataset_catalog(metadata_id TEXT PRIMARY KEY,category TEXT,records INTEGER,refs INTEGER,matched INTEGER,issue TEXT)"
    };
    private DatabaseSchema() { }
}
