UPDATE events
SET name = 'Evento sem nome'
WHERE name IS NULL OR TRIM(name) = '';

UPDATE events
SET `date` = CURRENT_DATE
WHERE `date` IS NULL;