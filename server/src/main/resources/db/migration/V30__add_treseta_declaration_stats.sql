ALTER TABLE user_treseta_stats
    ADD COLUMN IF NOT EXISTS declarations_made INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_treseta_stats
    ADD COLUMN IF NOT EXISTS declaration_points INTEGER NOT NULL DEFAULT 0;
