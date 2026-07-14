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
