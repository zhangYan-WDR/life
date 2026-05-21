ALTER TABLE recipes
    ADD COLUMN cover_url VARCHAR(512) DEFAULT NULL AFTER note,
    ADD COLUMN cover_object_key VARCHAR(255) DEFAULT NULL AFTER cover_url,
    ADD COLUMN reference_url VARCHAR(1024) DEFAULT NULL AFTER cover_object_key;
