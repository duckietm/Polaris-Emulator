-- acc_navigator_staff now also lets the server accept staff models: public room and game
-- layouts, and club_only models without Habbo Club. Comment only. Idempotent.

UPDATE `permission_definitions`
SET `comment` = 'Staff room models and categories when creating a room (also club_only models without HC).'
WHERE `permission_key` = 'acc_navigator_staff';
