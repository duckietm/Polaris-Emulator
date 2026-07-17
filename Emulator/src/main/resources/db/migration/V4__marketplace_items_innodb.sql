-- V4: convert marketplace_items to InnoDB so a marketplace purchase can use
-- row locks and rollback when the Java transaction refactor lands. MyISAM
-- supports neither; this migration enables atomicity but does not provide it alone.
-- The table ships as MyISAM ROW_FORMAT=FIXED; InnoDB rejects ROW_FORMAT=FIXED, so
-- the row format is reset in the same statement.
-- Guarded: only rebuilds when not already InnoDB. Small table (listings), so the
-- brief write-block of a COPY rebuild is acceptable. Large MyISAM tables
-- (users_currency, catalog_items_limited) are deferred to the out-of-band runbook
-- (see TESTING_AND_MIGRATIONS_PLAN.md 2.8.1).
SET @engine := (SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='marketplace_items');
SET @ddl := IF(@engine = 'MyISAM',
    'ALTER TABLE `marketplace_items` ENGINE=InnoDB ROW_FORMAT=DYNAMIC',
    'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
