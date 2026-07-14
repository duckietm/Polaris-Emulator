DELETE FROM rooms WHERE id = 900002;
DELETE FROM users WHERE id = 900001 OR username = 'e2e_reconnect';
INSERT INTO users (
    id, username, password, mail, account_created, motto, rank,
    auth_ticket, auth_ticket_expires_at, ip_register, ip_current
) VALUES (
    900001, 'e2e_reconnect', '!', 'e2e-reconnect@example.invalid', UNIX_TIMESTAMP(),
    'Login reconnect E2E', 1, @e2e_sso_ticket, DATE_ADD(NOW(), INTERVAL 1 HOUR),
    '127.0.0.1', '127.0.0.1'
);

INSERT INTO rooms (
    id, owner_id, owner_name, name, description, model, state, users_max, category
) VALUES (
    900002, 900001, 'e2e_reconnect', 'Reconnect E2E room',
    'Synthetic room used only by the isolated reconnect test', 'model_a', 'open', 5, 1
);
