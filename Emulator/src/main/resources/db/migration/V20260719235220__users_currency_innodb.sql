SET @polaris_users_currency_engine := (
    SELECT ENGINE
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users_currency'
);

SET @polaris_users_currency_engine_sql := IF(
    @polaris_users_currency_engine = 'InnoDB',
    'DO 0',
    'ALTER TABLE `users_currency` ENGINE=InnoDB ROW_FORMAT=DYNAMIC'
);

PREPARE polaris_users_currency_engine_stmt
    FROM @polaris_users_currency_engine_sql;
EXECUTE polaris_users_currency_engine_stmt;
DEALLOCATE PREPARE polaris_users_currency_engine_stmt;
