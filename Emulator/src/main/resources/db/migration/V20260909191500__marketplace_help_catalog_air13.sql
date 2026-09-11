-- AIR 13 catalog, marketplace and help gaps (section 4.2): purchasable chat bubble styles, the
-- limited-edition raffle, and the report status the "my reports" window shows.

-- Chat bubble styles the catalog sells. `catalog_item_id` points at the catalog offer
-- that grants the style; 0 (the default) keeps a bubble unpurchasable, which is how every
-- existing row stays exactly as it was.
ALTER TABLE `chat_bubbles`
    ADD COLUMN IF NOT EXISTS `catalog_item_id` INT NOT NULL DEFAULT 0;

-- Who owns which purchasable chat bubble style. The client is told the whole list with
-- its user data (event 946) and each later grant on its own (event 2580).
CREATE TABLE IF NOT EXISTS `users_chat_styles` (
    `user_id` INT NOT NULL,
    `style_id` INT NOT NULL,
    `timestamp` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`user_id`, `style_id`),
    KEY `style_id` (`style_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- What the reporter's own "my reports" window shows about each report they filed:
-- when it was decided, whether the decision carried a sanction, whether that sanction came
-- from auto-moderation, and where an appeal stands (0 none, 1 pending, 2 reviewed with an
-- action, 3 reviewed without one).
ALTER TABLE `support_tickets`
    ADD COLUMN IF NOT EXISTS `closed_timestamp` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `sanctioned` TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `sanction_auto` TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `appeal_state` TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `appeal_timestamp` INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `appeal_resolved_timestamp` INT NOT NULL DEFAULT 0;

ALTER TABLE `support_tickets`
    ADD INDEX IF NOT EXISTS `sender_timestamp` (`sender_id`, `timestamp`);

-- The limited-edition raffle is off unless a hotel turns it on; with it off a limited
-- purchase completes immediately, exactly as it did before.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.catalog.ltd.raffle.enabled', '0', 'Hold buyers of the same limited-edition offer for a few seconds and draw the copies among them (AIR 13 LTD raffle).'),
    ('hotel.catalog.ltd.raffle.seconds', '10', 'How long the LTD raffle collects buyers before it draws.')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
    ('help.report.appeal.staffalert', '%username% appealed report #%id%.');
