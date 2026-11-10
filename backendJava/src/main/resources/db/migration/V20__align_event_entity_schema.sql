ALTER TABLE events
    ADD CONSTRAINT chk_events_name_non_null CHECK (name IS NOT NULL);

ALTER TABLE events
    MODIFY COLUMN name VARCHAR(150) NOT NULL,
    MODIFY COLUMN map_url VARCHAR(500) NULL;

ALTER TABLE events
    DROP CHECK chk_events_name_non_null;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'events'
       AND column_name = 'event_date') = 0,
    'ALTER TABLE events ADD COLUMN event_date DATE NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'events'
       AND column_name = 'image_url') = 0,
    'ALTER TABLE events ADD COLUMN image_url VARCHAR(500) NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'events'
       AND column_name = 'registration_url') = 0,
    'ALTER TABLE events ADD COLUMN registration_url VARCHAR(500) NULL',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'events'
       AND column_name = 'active') = 0,
    'ALTER TABLE events ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE',
    'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

UPDATE events
SET event_date = DATE(`date`)
WHERE event_date IS NULL
  AND `date` IS NOT NULL;
