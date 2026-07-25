DELETE FROM items WHERE id = 900004;
DELETE FROM rooms WHERE id = 900002;
DELETE FROM messenger_conversations WHERE direct_key = '900001:900003';
DELETE FROM messenger_friendships
WHERE (user_one_id = 900001 AND user_two_id = 900003)
   OR (user_one_id = 900003 AND user_two_id = 900001);
DELETE FROM users WHERE id IN (900001, 900003)
   OR username IN ('e2e_reconnect', 'e2e_messenger_peer');
INSERT INTO users (
    id, username, password, mail, account_created, motto, rank,
    auth_ticket, auth_ticket_expires_at, ip_register, ip_current
) VALUES
    (900001, 'e2e_reconnect', '!', 'e2e-reconnect@example.invalid', UNIX_TIMESTAMP(),
     'Login reconnect E2E', 1, @e2e_sso_ticket, DATE_ADD(NOW(), INTERVAL 1 HOUR),
     '127.0.0.1', '127.0.0.1'),
    (900003, 'e2e_messenger_peer', '!', 'e2e-messenger-peer@example.invalid', UNIX_TIMESTAMP(),
     'Messenger E2E peer', 1, @e2e_second_sso_ticket, DATE_ADD(NOW(), INTERVAL 1 HOUR),
     '127.0.0.1', '127.0.0.1');

INSERT INTO messenger_friendships (user_one_id, user_two_id, friends_since) VALUES
    (900001, 900003, UNIX_TIMESTAMP()),
    (900003, 900001, UNIX_TIMESTAMP());

INSERT INTO rooms (
    id, owner_id, owner_name, name, description, model, state, users_max, category
) VALUES (
    900002, 900001, 'e2e_reconnect', 'Reconnect E2E room',
    'Synthetic room used only by the isolated reconnect test', 'model_a', 'open', 5, 1
);

INSERT INTO items (
    id, user_id, room_id, item_id, wall_pos, x, y, z, rot,
    extra_data, wired_data, limited_data, guild_id
) VALUES (
    900004, 900001, 0, 18, '', 0, 0, 0.000000, 0,
    '', '', '0:0', 0
);

-- The `emulator_settings` overlay is applied after the database connects and
-- overwrites config.ini, so every key below is decided here rather than in
-- config.ini.template. The base database ships the legacy aliases
-- ws.nitro.host='0.0.0.0' and ws.nitro.port='2096', which normal startup copies
-- into ws.host/ws.port; without these rows the WebSocket listener and the
-- /__e2e probe bind 0.0.0.0:2096 instead of the port the harness advertises and
-- readiness never succeeds. Seeding the canonical keys first also makes that
-- copy a no-op, because it only fills in absent keys.
-- Keep these values in sync with e2e/config.ini.template.
INSERT INTO emulator_settings (`key`, `value`) VALUES
    ('ws.enabled', 'true'),
    ('ws.host', '127.0.0.1'),
    ('ws.port', @e2e_ws_port),
    -- 'error' is the sentinel the upgrade handler uses for a request with no
    -- Origin header, which is what the Node test client sends.
    ('ws.whitelist', 'localhost,127.0.0.1,error'),
    ('crypto.ws.enabled', '0'),
    ('enc.enabled', '0'),
    ('client.release.allowed', 'NITRO-3-6-0'),
    ('session.reconnect.grace.seconds', '30')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
