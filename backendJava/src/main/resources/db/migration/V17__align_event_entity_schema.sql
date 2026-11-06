ALTER TABLE events
    ADD CONSTRAINT chk_events_name_non_null CHECK (name IS NOT NULL);

ALTER TABLE events
    MODIFY COLUMN name VARCHAR(150) NOT NULL,
    MODIFY COLUMN map_url VARCHAR(500) NULL;

ALTER TABLE events
    DROP CHECK chk_events_name_non_null;

ALTER TABLE events
    ADD COLUMN event_date DATE NULL,
    ADD COLUMN image_url VARCHAR(500) NULL,
    ADD COLUMN registration_url VARCHAR(500) NULL,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE events
SET event_date = DATE(`date`)
WHERE event_date IS NULL
  AND `date` IS NOT NULL;
