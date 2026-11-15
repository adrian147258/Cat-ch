-- Add public profile data without changing existing users or posts.
CREATE TABLE IF NOT EXISTS profiles (
  user_id TEXT PRIMARY KEY,
  signature TEXT NOT NULL DEFAULT '',
  cover_key TEXT,
  cover_type TEXT,
  updated_at INTEGER NOT NULL DEFAULT 0
);
