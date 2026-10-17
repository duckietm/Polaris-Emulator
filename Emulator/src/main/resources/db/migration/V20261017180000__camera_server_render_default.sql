-- Camera photos are rendered by the server by default (the official client's model), so a client can
-- no longer upload any picture as a photo. Only hotels still on the old default 'auto' move; a mode
-- someone chose stays.
UPDATE `emulator_settings`
SET `value` = 'server'
WHERE `key` = 'camera.render.mode'
  AND `value` = 'auto';
