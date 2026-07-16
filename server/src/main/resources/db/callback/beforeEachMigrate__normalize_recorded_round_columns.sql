DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'winner_user_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE recorded_rounds RENAME COLUMN winner_user_id TO user_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'winner_name'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'recorded_rounds' AND column_name = 'name'
    ) THEN
        ALTER TABLE recorded_rounds RENAME COLUMN winner_name TO name;
    END IF;
END $$;
