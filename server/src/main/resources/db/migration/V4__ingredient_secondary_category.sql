ALTER TABLE ingredient_catalog_system
    ADD COLUMN secondary_category VARCHAR(64) DEFAULT NULL AFTER category;

ALTER TABLE ingredient_catalog_family
    ADD COLUMN secondary_category VARCHAR(64) DEFAULT NULL AFTER category;
