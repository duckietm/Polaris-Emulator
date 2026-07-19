ALTER TABLE catalog_club_offers
    ADD COLUMN IF NOT EXISTS giftable ENUM('0','1') NOT NULL DEFAULT '0';
    
INSERT IGNORE INTO emulator_texts (`key`, `value`)
VALUES ('prereg.reward.you.received', 'You have recived:'),
       ('generic.days', 'days');
