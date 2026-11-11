-- The first-login setup. An account made on the client's register screen chose its look and starter room there; an
-- account a CMS made did not, so the client offers both at its first login. One row per account that went through
-- either path: completed = 1 from the register screen or once the offer is answered, 0 while the offer is open.
-- Accounts that already exist get no row and are never asked.
CREATE TABLE IF NOT EXISTS `users_first_login_setup` (
    `user_id` int(11) NOT NULL,
    `completed` tinyint(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES ('hotel.first_login.setup.enabled', '1');
