CREATE TABLE IF NOT EXISTS card_draws (
  user_id TEXT NOT NULL,
  day TEXT NOT NULL,
  tier TEXT NOT NULL CHECK(tier IN ('green','blue','purple','gold')),
  card_index INTEGER NOT NULL CHECK(card_index >= 0),
  created_at INTEGER NOT NULL,
  PRIMARY KEY(user_id, day)
);
CREATE INDEX IF NOT EXISTS card_draws_user_created ON card_draws(user_id,created_at DESC);
