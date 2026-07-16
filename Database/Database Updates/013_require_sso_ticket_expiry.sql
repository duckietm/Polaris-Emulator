-- SSO tickets without an expiry were historically accepted indefinitely.
-- The emulator now requires auth_ticket_expires_at >= NOW() on every SSO lookup.
-- Invalidate legacy non-empty tickets so they cannot be replayed after this update;
-- users receive a fresh, expiring ticket on their next CMS login.
UPDATE `users`
SET `auth_ticket` = ''
WHERE `auth_ticket` <> ''
  AND `auth_ticket_expires_at` IS NULL;
