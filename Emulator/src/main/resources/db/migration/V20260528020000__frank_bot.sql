ALTER TABLE `bots`
  MODIFY COLUMN `type` VARCHAR(32)
  NOT NULL DEFAULT 'generic';

INSERT INTO `permission_definitions`
  (`permission_key`, `max_value`, `comment`,
   `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`)
VALUES
  ('acc_bot_frank', 1, 'Required to purchase the Frank mascot bot from the catalog.',
   0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);


CREATE TABLE IF NOT EXISTS `bot_chat_responses` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `bot_type` VARCHAR(32) NOT NULL,
  `keys` VARCHAR(255) NOT NULL COMMENT 'semicolon-separated trigger words',
  `responses` TEXT NOT NULL COMMENT 'newline-separated replies; bot picks one at random',
  PRIMARY KEY (`id`),
  KEY `bot_type` (`bot_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The old loose update may already have been applied on a running hotel. Seed
-- Frank only when that bot type has no responses, so Flyway adoption does not
-- append a second copy to a table which has no natural unique key.
INSERT INTO `bot_chat_responses` (`bot_type`, `keys`, `responses`)
SELECT seed.`bot_type`, seed.`keys`, seed.`responses`
FROM (
  SELECT 'frank' AS `bot_type`, '__door_triggers' AS `keys`, 'show me the door\nkick me\ni want to leave\nlet me out' AS `responses`
  UNION ALL SELECT 'frank', '__door_lines', 'Right this way - mind the step!\nAnd out you go. Come back soon!\nAllow me to escort you to the exit.\nThere''s the door. Farewell, true believer!'
  UNION ALL SELECT 'frank', '__busy_whisper', 'Sorry, I am currently busy. Please wait until I am available.'
  UNION ALL SELECT 'frank', 'frank', 'Hello, I''m Frank! Welcome to Habbo.'
  UNION ALL SELECT 'frank', 'help', 'What do you need help with?'
  UNION ALL SELECT 'frank', 'thanks;thank you', 'Just doing my job, true believer!'
  UNION ALL SELECT 'frank', 'new', 'Welcome to Habbo! I hope you have a great time here.'
  UNION ALL SELECT 'frank', 'rooms', 'Looking for somewhere fun? Try the Navigator - thousands of rooms to explore!'
  UNION ALL SELECT 'frank', 'sulake', 'Sulake is the company behind Habbo. Take a look: https://www.sulake.com'
  UNION ALL SELECT 'frank', 'vip;hc', 'VIP gets you more outfits, more furni, more everything. Worth it!'
  UNION ALL SELECT 'frank', 'music', 'Snoop Dogg, Frank Sinatra and a little Beethoven on Sundays.'
  UNION ALL SELECT 'frank', 'movie', 'I''m a Casablanca man. Black and white films are an underrated art.'
  UNION ALL SELECT 'frank', 'game', 'Battleship. Always Battleship.'
  UNION ALL SELECT 'frank', 'snowstorm', 'Honestly? I''m terrible at Snowstorm. Don''t tell anyone.'
  UNION ALL SELECT 'frank', 'furni', 'Best furniture maker in town - hands down, the folks at Sulake.'
  UNION ALL SELECT 'frank', 'animal;cat;pet', 'I have a cat called Mr. Whiskers. He runs the place, really.'
  UNION ALL SELECT 'frank', 'miranda', 'Miranda. The love of my life. Don''t get me started.'
  UNION ALL SELECT 'frank', 'frank black', 'Named after the man himself. Frank Black is a hero of mine.'
  UNION ALL SELECT 'frank', 'life', 'Life is like a bowl of popcorn - warm, salty and buttery.'
  UNION ALL SELECT 'frank', 'job;work', 'I''m sure you can find work in one of the guest rooms!'
  UNION ALL SELECT 'frank', 'snouthill', 'Snouthill... so many memories.'
  UNION ALL SELECT 'frank', 'wife', 'I had a wife once. She broke my stereo.'
  UNION ALL SELECT 'frank', 'baseball', 'Oh, I used to love to go down to the old ball park and watch Christy Mathewson and Honus Wagner at bat.'
  UNION ALL SELECT 'frank', 'mark', 'I don''t trust Mark.'
  UNION ALL SELECT 'frank', 'vietnam', 'Vietnam? Don''t ask. Worst trip of my life.'
  UNION ALL SELECT 'frank', 'pills;drugs', 'Drugs are bad, mmkay?'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM `bot_chat_responses` existing
  WHERE existing.`bot_type` = 'frank'
);

INSERT IGNORE INTO `bot_serves` (`keys`, `item`) VALUES
('sunflower',                                          1002),
('cola;habbo cola',                                    19),
('rose',                                               1000),
('book',                                               1003),
('tea',                                                27),
('coffee',                                             8),
('migraine;headache;pills',                            1015),
('radioactive liquid;radioactive',                     30),
('turkey;can of turkey',                               70);


INSERT IGNORE INTO `items_base` (`id`, `sprite_id`, `item_name`, `public_name`, `width`, `length`, `stack_height`, `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`, `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `type`, `interaction_type`, `interaction_modes_count`, `vending_ids`, `multiheight`, `customparams`)
VALUES (19001, 0, 'bot_frank', 'Frank', 1, 1, 0.00, '0', '0', '0', '1', '0', '0', '0', '0', '0', 'r', 'default', 1, '0', '0', '0');

INSERT IGNORE INTO `catalog_items` (`item_ids`, `page_id`, `offer_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`, `extradata`)
VALUES ('19001', 8, 19001, 'Frank', 0, 0, 0, 1, 'name:Frank;motto:Welcome to Habbo!;figure:hr-3499-33.sh-290-90.ch-3971-72-73.lg-270-73.hd-205-1-1.fa-1206-67.ha-3409-73-72;gender:m');
