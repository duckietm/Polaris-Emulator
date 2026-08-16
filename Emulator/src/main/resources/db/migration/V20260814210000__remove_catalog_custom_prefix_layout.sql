-- Retire the Catalog Studio-only custom_prefix page layout without touching user prefix customization.
UPDATE `catalog_pages`
SET `page_layout` = 'default_3x3'
WHERE `page_layout` = 'custom_prefix';

UPDATE `catalog_pages_bc`
SET `page_layout` = 'default_3x3'
WHERE `page_layout` = 'custom_prefix';

ALTER TABLE `catalog_pages`
    MODIFY COLUMN `page_layout` ENUM(
        'default_3x3','club_buy','club_gift','frontpage','spaces','recycler','recycler_info','recycler_prizes',
        'trophies','plasto','marketplace','marketplace_own_items','spaces_new','soundmachine','guilds','guild_furni',
        'info_duckets','info_rentables','info_pets','roomads','single_bundle','sold_ltd_items','badge_display','bots',
        'pets','pets2','pets3','productpage1','room_bundle','recent_purchases','default_3x3_color_grouping',
        'guild_forum','vip_buy','info_loyalty','loyalty_vip_buy','collectibles','petcustomization','frontpage_featured',
        'builders_club_frontpage','builders_club_addons','builders_club_loyalty','root','monkey','niko','mad_money'
    ) NOT NULL DEFAULT 'default_3x3';

ALTER TABLE `catalog_pages_bc`
    MODIFY COLUMN `page_layout` ENUM(
        'default_3x3','club_buy','club_gift','frontpage','spaces','recycler','recycler_info','recycler_prizes',
        'trophies','plasto','marketplace','marketplace_own_items','spaces_new','soundmachine','guilds','guild_furni',
        'info_duckets','info_rentables','info_pets','roomads','single_bundle','sold_ltd_items','badge_display','bots',
        'pets','pets2','pets3','productpage1','room_bundle','recent_purchases','default_3x3_color_grouping',
        'guild_forum','vip_buy','info_loyalty','loyalty_vip_buy','collectibles','petcustomization','frontpage_featured',
        'builders_club_frontpage','builders_club_addons','builders_club_loyalty','root','monkey','niko','mad_money'
    ) NOT NULL DEFAULT 'default_3x3';
