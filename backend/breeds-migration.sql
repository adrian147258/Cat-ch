-- Run once on existing databases created before the breed column was added.
-- Existing posts remain unclassified because their breed is not known.
ALTER TABLE posts ADD COLUMN breed TEXT;
