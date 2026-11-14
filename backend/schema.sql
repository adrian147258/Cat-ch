CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS posts (id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, name TEXT NOT NULL, text TEXT NOT NULL, object_key TEXT NOT NULL, content_type TEXT NOT NULL, size_bytes INTEGER NOT NULL, created_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS posts_created ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS posts_owner_created ON posts(owner_id,created_at DESC);
CREATE TABLE IF NOT EXISTS reactions (post_id TEXT NOT NULL, user_id TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('like','save')), PRIMARY KEY(post_id,user_id,kind));
CREATE INDEX IF NOT EXISTS reactions_user_kind ON reactions(user_id,kind);
CREATE TABLE IF NOT EXISTS quota (id INTEGER PRIMARY KEY CHECK(id=1), uploads INTEGER NOT NULL);
INSERT OR IGNORE INTO quota(id,uploads) VALUES(1,0);
CREATE TRIGGER IF NOT EXISTS post_upload_count AFTER INSERT ON posts BEGIN UPDATE quota SET uploads=uploads+1 WHERE id=1; END;
