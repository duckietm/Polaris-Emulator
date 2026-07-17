-- Representative operator-owned data layered onto the packaged Arcturus 3.5.5
-- baseline. The two old forum tables come from the supplied BaseDB MS 3.5.5.sql;
-- Polaris does not use them, but a converter must tolerate and preserve them.

CREATE TABLE `old_guilds_forums` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `guild_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `subject` mediumtext NOT NULL,
  `message` longtext NOT NULL,
  `state` enum('OPEN','CLOSED','HIDDEN_BY_ADMIN','HIDDEN_BY_STAFF') NOT NULL DEFAULT 'OPEN',
  `admin_id` int(11) NOT NULL DEFAULT 0,
  `pinned` enum('0','1') NOT NULL DEFAULT '0',
  `locked` enum('0','1') NOT NULL DEFAULT '0',
  `timestamp` int(11) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `old_guilds_forums_comments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `thread_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `timestamp` int(11) NOT NULL,
  `message` longtext NOT NULL,
  `state` enum('OPEN','CLOSED','HIDDEN_BY_ADMIN','HIDDEN_BY_STAFF') NOT NULL DEFAULT 'OPEN',
  `admin_id` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;

INSERT INTO `users`
  (`id`, `username`, `password`, `mail`, `account_created`, `last_online`, `motto`,
   `look`, `gender`, `rank`, `credits`, `pixels`, `points`, `auth_ticket`,
   `ip_register`, `ip_current`, `machine_id`, `home_room`)
VALUES
  (4242, 'running_hotel_owner',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'owner@example.test', 1700000000, 1700001000, 'Keep this hotel data',
   'hd-180-1.hr-100-0', 'M', 7, 98765, 4321, 123, 'ARCTURUS-TICKET',
   '192.0.2.10', '192.0.2.11', 'fixture-machine', 4242);

INSERT INTO `rooms`
  (`id`, `owner_id`, `owner_name`, `name`, `description`, `model`)
VALUES
  (4242, 4242, 'running_hotel_owner', 'Existing Arcturus Room',
   'This room must survive the Polaris conversion.', 'model_a');

INSERT INTO `items`
  (`id`, `user_id`, `room_id`, `item_id`, `x`, `y`, `z`, `rot`, `extra_data`)
VALUES
  (4242, 4242, 4242, 1, 2, 3, 0.000000, 2, 'existing-item-data');

INSERT INTO `users_currency` (`user_id`, `type`, `amount`)
VALUES (4242, 5, 777);

INSERT INTO `emulator_settings` (`key`, `value`)
VALUES ('fixture.operator.setting', 'keep-me');
