-- Remove the retired personal-prefix product while preserving nickname icons and rank prefixes.
SET @delete_prefix_only_rules = IF(
    EXISTS(
        SELECT 1
        FROM `information_schema`.`COLUMNS`
        WHERE `TABLE_SCHEMA` = DATABASE()
          AND `TABLE_NAME` = 'wordfilter'
          AND `COLUMN_NAME` = 'prefix_only'
    ),
    'DELETE FROM `wordfilter` WHERE `prefix_only` = ''1''',
    'SELECT 1'
);
PREPARE delete_prefix_only_rules_statement FROM @delete_prefix_only_rules;
EXECUTE delete_prefix_only_rules_statement;
DEALLOCATE PREPARE delete_prefix_only_rules_statement;

ALTER TABLE `wordfilter`
DROP COLUMN IF EXISTS `prefix_only`;

DELETE FROM `permission_definitions`
WHERE `permission_key` IN (
    'cmd_give_prefix',
    'cmd_list_prefixes',
    'cmd_remove_prefix',
    'cmd_prefix_blacklist'
);

DELETE FROM `emulator_texts`
WHERE `key` LIKE 'commands.%cmd_give_prefix%'
   OR `key` LIKE 'commands.%cmd_list_prefixes%'
   OR `key` LIKE 'commands.%cmd_remove_prefix%'
   OR `key` LIKE 'commands.%cmd_prefix_blacklist%';

SET @delete_custom_prefix_offers = IF(
    EXISTS(
        SELECT 1
        FROM `information_schema`.`COLUMNS`
        WHERE `TABLE_SCHEMA` = DATABASE()
          AND `TABLE_NAME` = 'catalog_items'
          AND `COLUMN_NAME` = 'type'
    ),
    'DELETE FROM `catalog_items` WHERE `type` = ''custom_prefix''',
    'SELECT 1'
);
PREPARE delete_custom_prefix_offers_statement FROM @delete_custom_prefix_offers;
EXECUTE delete_custom_prefix_offers_statement;
DEALLOCATE PREPARE delete_custom_prefix_offers_statement;

DROP TABLE IF EXISTS `user_prefixes`;
DROP TABLE IF EXISTS `custom_prefixes_catalog`;
DROP TABLE IF EXISTS `custom_prefix_settings`;
DROP TABLE IF EXISTS `custom_prefix_blacklist`;
DROP TABLE IF EXISTS `user_visual_settings`;
