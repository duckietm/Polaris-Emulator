ALTER TABLE `bots`
  MODIFY COLUMN `type` ENUM('generic','visitor_log','bartender','weapons_dealer','frank')
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

INSERT INTO `bot_chat_responses` (`bot_type`, `keys`, `responses`) VALUES
('frank', '__door_triggers', 'show me the door\nkick me\ni want to leave\nlet me out'),
('frank', '__door_lines', 'Right this way - mind the step!\nAnd out you go. Come back soon!\nAllow me to escort you to the exit.\nThere''s the door. Farewell, true believer!'),
('frank', '__busy_whisper', 'Sorry, I am currently busy. Please wait until I am available.'),
('frank', 'frank',         'Hello, I''m Frank! Welcome to Habbo.'),
('frank', 'help',          'What do you need help with?'),
('frank', 'thanks;thank you', 'Just doing my job, true believer!'),
('frank', 'new',           'Welcome to Habbo! I hope you have a great time here.'),
('frank', 'rooms',         'Looking for somewhere fun? Try the Navigator - thousands of rooms to explore!'),
('frank', 'sulake',        'Sulake is the company behind Habbo. Take a look: https://www.sulake.com'),
('frank', 'vip;hc',        'VIP gets you more outfits, more furni, more everything. Worth it!'),
('frank', 'music',         'Snoop Dogg, Frank Sinatra and a little Beethoven on Sundays.'),
('frank', 'movie',         'I''m a Casablanca man. Black and white films are an underrated art.'),
('frank', 'game',          'Battleship. Always Battleship.'),
('frank', 'snowstorm',     'Honestly? I''m terrible at Snowstorm. Don''t tell anyone.'),
('frank', 'furni',         'Best furniture maker in town - hands down, the folks at Sulake.'),
('frank', 'animal;cat;pet','I have a cat called Mr. Whiskers. He runs the place, really.'),
('frank', 'miranda',       'Miranda. The love of my life. Don''t get me started.'),
('frank', 'frank black',   'Named after the man himself. Frank Black is a hero of mine.'),
('frank', 'life',          'Life is like a bowl of popcorn - warm, salty and buttery.'),
('frank', 'job;work',      'I''m sure you can find work in one of the guest rooms!'),
('frank', 'snouthill',     'Snouthill... so many memories.'),
('frank', 'wife',          'I had a wife once. She broke my stereo.'),
('frank', 'baseball',      'Oh, I used to love to go down to the old ball park and watch Christy Mathewson and Honus Wagner at bat.'),
('frank', 'mark',          'I don''t trust Mark.'),
('frank', 'vietnam',       'Vietnam? Don''t ask. Worst trip of my life.'),
('frank', 'pills;drugs',   'Drugs are bad, mmkay?');

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

-- VERY IMPORTANT !!!!
-- First check if the items_base ID and catalog_items ID is not in use !
-- After the SQL please go to the catalog_items table and change the page_id to where your BOTS are located

INSERT IGNORE INTO `items_base` (`id`, `sprite_id`, `item_name`, `public_name`, `width`, `length`, `stack_height`, `allow_stack`, `allow_sit`, `allow_lay`, `allow_walk`, `allow_gift`, `allow_trade`, `allow_recycle`, `allow_marketplace_sell`, `allow_inventory_stack`, `type`, `interaction_type`, `interaction_modes_count`, `vending_ids`, `multiheight`, `customparams`)
VALUES (19001, 0, 'bot_frank', 'Frank', 1, 1, 0.00, '0', '0', '0', '1', '0', '0', '0', '0', '0', 'r', 'default', 1, '0', '0', '0');

INSERT IGNORE INTO `catalog_items` (`item_ids`, `page_id`, `offer_id`, `catalog_name`, `cost_credits`, `cost_points`, `points_type`, `amount`, `extradata`)
VALUES ('19001', 8, 19001, 'Frank', 0, 0, 0, 1, 'name:Frank;motto:Welcome to Habbo!;figure:hr-3499-33.sh-290-90.ch-3971-72-73.lg-270-73.hd-205-1-1.fa-1206-67.ha-3409-73-72;gender:m');
