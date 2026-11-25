-- Floor furni whose size, seat, bed or walkable flag differs from Habbo's own furnidata (xdim / ydim, cansiton,
-- canlayon, canstandon): the client already draws them at Habbo's size, while the server blocked other tiles,
-- let users sit or lie where Habbo does not, or walk through furni Habbo blocks.
--
-- Each row is only touched while it still has the value this hotel shipped with, and the flags only on furni
-- whose interaction leaves them to the furni's own data (default, normal, bed, multiheight, clothing, mutearea,
-- and tent for lying); gates, teleports, rollers, game and wired furni decide in code and are left alone.
-- Idempotent: a rerun changes nothing.

UPDATE `items_base` SET `width` = 1, `length` = 1
    WHERE `type` = 's' AND `width` = 0 AND `length` = 0 AND `item_name` IN (
        'ads_cllava2');

UPDATE `items_base` SET `width` = 1, `length` = 2
    WHERE `type` = 's' AND `width` = 0 AND `length` = 0 AND `item_name` IN (
        'gh_div_wall', 'hween_c16_bkcase');

UPDATE `items_base` SET `width` = 1, `length` = 1
    WHERE `type` = 's' AND `width` = 0 AND `length` = 1 AND `item_name` IN (
        'bazaar_c17_meat', 'bazaar_r17_monkey', 'bazaar_r17_throne');

UPDATE `items_base` SET `width` = 2, `length` = 1
    WHERE `type` = 's' AND `width` = 0 AND `length` = 2 AND `item_name` IN (
        'bazaar_c17_balcony', 'bazaar_r17_fountain');

UPDATE `items_base` SET `width` = 2, `length` = 2
    WHERE `type` = 's' AND `width` = 0 AND `length` = 2 AND `item_name` IN (
        'bazaar_ltd17_ele');

UPDATE `items_base` SET `width` = 1, `length` = 2
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'army15_scanner', 'classic1_wall2', 'hween_c16_bkcase2', 'room_hall15_window', 'room_info15_statue2',
        'room_pcnc15_wood', 'val_c18_wardrobe', 'vikings_indoorstove', 'wintercabin_c19_window',
        'xmas_c18_bearlight', 'xmas_c18_iciclelight');

UPDATE `items_base` SET `width` = 1, `length` = 3
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'bed_budget_one*10', 'classic8_bar', 'drago_c15_bed', 'fball_grp_fnc3', 'js_c16_lounger',
        'jungle_r16_basket', 'purablk_c16_bedone*1', 'purablk_c16_bedone*10', 'purablk_c16_bedone*2',
        'purablk_c16_bedone*3', 'purablk_c16_bedone*4', 'purablk_c16_bedone*5', 'purablk_c16_bedone*6',
        'purablk_c16_bedone*7', 'purablk_c16_bedone*8', 'purablk_c16_bedone*9');

UPDATE `items_base` SET `width` = 2, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'classic8_divider', 'classic8_stairs', 'classic8_wall', 'divider_nor2*10', 'divider_nor4*10',
        'fest_c19_backdrop1', 'fest_c19_backdrop2', 'fest_c19_backdrop3', 'fest_c19_dj', 'fest_c19_hangingdecor',
        'gold_c15_arc_div1', 'hc16_10', 'hc16_12', 'hc16_4', 'hween_c16_vanity', 'hween_c16_vanity2',
        'hween_c16_wall', 'hween_c16_wall2', 'icecream_stand', 'js_c16_drkcab', 'md_sofa', 'paris15_paintings',
        'purablk_c16_shelve', 'room_pcnc15_table1', 'room_pcnc15_wbench', 'room_thr15_frontcur',
        'room_thr15_icecream', 'ship_wheel_g', 'stories_oldmusic_neon', 'val_c18_chest', 'vikings_stonedivdr',
        'vikings_weapon', 'xmas_c17_door', 'xmas_c18_stagebackdrop', 'xmas_c18_stagestairs', 'xmas_c18_stagewindow',
        'xmas_c18_stallroof', 'xmas_c18_stalltable', 'xmas_c19_icicles');

UPDATE `items_base` SET `width` = 2, `length` = 2
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'cine_r18_buggy', 'classic8_floor', 'classic8_rug', 'easter_r18_bonzaifountain', 'fest_c19_cushion1',
        'fest_c19_cushion2', 'fest_c19_cushion3', 'fest_c19_falafelstall', 'fest_c19_parasol1', 'fest_c19_parasol2',
        'fest_c19_parasol3', 'fest_c19_stage', 'fest_c19_ticketstall', 'gold_c15_arc_geysir', 'gold_c15_arc_snow',
        'gold_c15_arc_table', 'gold_c15_arc_tub', 'hc16_7', 'hween_c16_floor', 'hween_c16_floor2',
        'hween_c17_cavefloor', 'hween_r16_grandpiano', 'hween_r16_grandpiano2', 'hween_r19_weepingwillow',
        'lt_c15_bush', 'ny16_stars', 'olympics_c16_softmat', 'paris_c15_shadow', 'room_pcnc15_bbq',
        'room_pcnc15_blanket', 'santorini_ltd17_lighthouse', 'shallow_test', 'table_norja_med*10',
        'table_plasto4_4leg', 'table_plasto4_bigsq', 'table_silo_med*10', 'val16_float1', 'val16_float2',
        'val16_float3', 'val16_float4', 'xmas_c18_rainlightfloor', 'xmas_c18_stage', 'xmas_r18_lightfountain');

UPDATE `items_base` SET `width` = 2, `length` = 3
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'fest_c19_loverstent', 'hween_c16_bed', 'hween_c16_bed2', 'hween_ltd16_ghostcar', 'rare_r19_car1',
        'val_r18_bed', 'xmas13_rug');

UPDATE `items_base` SET `width` = 2, `length` = 6
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'room_thr15_cover');

UPDATE `items_base` SET `width` = 3, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'ads_ontour', 'room_pcnc15_awn', 'room_thr15_div1', 'theatre_curtain', 'vikings_table_g', 'xmas_c18_piano');

UPDATE `items_base` SET `width` = 3, `length` = 2
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'cine_r18_trailer');

UPDATE `items_base` SET `width` = 3, `length` = 3
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'room_pcnc15_gzb', 'xmas_c18_snowballpit');

UPDATE `items_base` SET `width` = 4, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'hc16_1', 'room_pcnc15_dvd1');

UPDATE `items_base` SET `width` = 5, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'room_thr15_div2');

UPDATE `items_base` SET `width` = 5, `length` = 5
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'testing_rentablespace');

UPDATE `items_base` SET `width` = 9, `length` = 4
    WHERE `type` = 's' AND `width` = 1 AND `length` = 1 AND `item_name` IN (
        'room_thr15_stage');

UPDATE `items_base` SET `width` = 1, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 2 AND `item_name` IN (
        'tokyo_c18_magazinerack');

UPDATE `items_base` SET `width` = 2, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 2 AND `item_name` IN (
        'ads_durex_vend', 'coralking_c18_fishnet', 'cpunk_c15_door', 'easter_c18_curtains', 'easter_c18_glassdoor',
        'hblooza14_drinkstall', 'hblooza_icecream', 'olympics_c16_counter', 'paris_c15_arch', 'sofa_silo*10',
        'uni_messwardrobe', 'uni_wardrobe');

UPDATE `items_base` SET `width` = 2, `length` = 2
    WHERE `type` = 's' AND `width` = 1 AND `length` = 2 AND `item_name` IN (
        'xmas15_glowdeerltd');

UPDATE `items_base` SET `width` = 2, `length` = 3
    WHERE `type` = 's' AND `width` = 1 AND `length` = 3 AND `item_name` IN (
        'bed_trad_one', 'hc16_11');

UPDATE `items_base` SET `width` = 3, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 3 AND `item_name` IN (
        'fball_grp_cote');

UPDATE `items_base` SET `width` = 1, `length` = 1
    WHERE `type` = 's' AND `width` = 1 AND `length` = 4 AND `item_name` IN (
        'theatre_wall');

UPDATE `items_base` SET `width` = 1, `length` = 1
    WHERE `type` = 's' AND `width` = 2 AND `length` = 1 AND `item_name` IN (
        'bc_glasspanel*1', 'bc_glasspanel*10', 'bc_glasspanel*11', 'bc_glasspanel*12', 'bc_glasspanel*13',
        'bc_glasspanel*14', 'bc_glasspanel*15', 'bc_glasspanel*16', 'bc_glasspanel*17', 'bc_glasspanel*18',
        'bc_glasspanel*19', 'bc_glasspanel*2', 'bc_glasspanel*20', 'bc_glasspanel*21', 'bc_glasspanel*22',
        'bc_glasspanel*23', 'bc_glasspanel*24', 'bc_glasspanel*25', 'bc_glasspanel*26', 'bc_glasspanel*27',
        'bc_glasspanel*28', 'bc_glasspanel*29', 'bc_glasspanel*3', 'bc_glasspanel*30', 'bc_glasspanel*31',
        'bc_glasspanel*32', 'bc_glasspanel*33', 'bc_glasspanel*34', 'bc_glasspanel*35', 'bc_glasspanel*36',
        'bc_glasspanel*37', 'bc_glasspanel*38', 'bc_glasspanel*39', 'bc_glasspanel*4', 'bc_glasspanel*40',
        'bc_glasspanel*41', 'bc_glasspanel*42', 'bc_glasspanel*43', 'bc_glasspanel*44', 'bc_glasspanel*45',
        'bc_glasspanel*46', 'bc_glasspanel*47', 'bc_glasspanel*48', 'bc_glasspanel*49', 'bc_glasspanel*5',
        'bc_glasspanel*50', 'bc_glasspanel*51', 'bc_glasspanel*52', 'bc_glasspanel*53', 'bc_glasspanel*54',
        'bc_glasspanel*55', 'bc_glasspanel*56', 'bc_glasspanel*57', 'bc_glasspanel*58', 'bc_glasspanel*59',
        'bc_glasspanel*6', 'bc_glasspanel*60', 'bc_glasspanel*61', 'bc_glasspanel*62', 'bc_glasspanel*63',
        'bc_glasspanel*64', 'bc_glasspanel*65', 'bc_glasspanel*66', 'bc_glasspanel*67', 'bc_glasspanel*68',
        'bc_glasspanel*69', 'bc_glasspanel*7', 'bc_glasspanel*8', 'bc_glasspanel*9', 'hween_c19_archway',
        'xmas13_toyhorse');

UPDATE `items_base` SET `width` = 1, `length` = 2
    WHERE `type` = 's' AND `width` = 2 AND `length` = 1 AND `item_name` IN (
        'room_wl15_ele', 'tablet_vendorg');

UPDATE `items_base` SET `width` = 2, `length` = 2
    WHERE `type` = 's' AND `width` = 2 AND `length` = 1 AND `item_name` IN (
        'xmas15_wishltd');

UPDATE `items_base` SET `width` = 2, `length` = 3
    WHERE `type` = 's' AND `width` = 2 AND `length` = 1 AND `item_name` IN (
        'purablk_c16_bed*1', 'purablk_c16_bed*10', 'purablk_c16_bed*2', 'purablk_c16_bed*3', 'purablk_c16_bed*4',
        'purablk_c16_bed*5', 'purablk_c16_bed*6', 'purablk_c16_bed*7', 'purablk_c16_bed*8', 'purablk_c16_bed*9');

UPDATE `items_base` SET `width` = 1, `length` = 1
    WHERE `type` = 's' AND `width` = 2 AND `length` = 2 AND `item_name` IN (
        'coralking_c18_gazebo', 'coralking_ltd18_royalseahorse', 'easter_c17_sprinkler', 'vikings_yggdrasil',
        'xmas_c16_food1', 'xmas_c16_food2', 'xmas_c16_food3', 'xmas_c16_tinychair');

UPDATE `items_base` SET `width` = 1, `length` = 3
    WHERE `type` = 's' AND `width` = 2 AND `length` = 2 AND `item_name` IN (
        'xmas_c16_tinybed');

UPDATE `items_base` SET `width` = 2, `length` = 1
    WHERE `type` = 's' AND `width` = 2 AND `length` = 2 AND `item_name` IN (
        'coralking_r18_vanitytable', 'steampunk_zep', 'xmas_c16_mailshelf');

UPDATE `items_base` SET `width` = 4, `length` = 4
    WHERE `type` = 's' AND `width` = 2 AND `length` = 2 AND `item_name` IN (
        'studio_floorlight');

UPDATE `items_base` SET `width` = 2, `length` = 1
    WHERE `type` = 's' AND `width` = 2 AND `length` = 3 AND `item_name` IN (
        'dessertcafe_c17_rug');

UPDATE `items_base` SET `width` = 2, `length` = 3
    WHERE `type` = 's' AND `width` = 2 AND `length` = 4 AND `item_name` IN (
        'js_r16_boat');

UPDATE `items_base` SET `width` = 1, `length` = 3
    WHERE `type` = 's' AND `width` = 3 AND `length` = 1 AND `item_name` IN (
        'hc16_6');

UPDATE `items_base` SET `width` = 3, `length` = 1
    WHERE `type` = 's' AND `width` = 3 AND `length` = 3 AND `item_name` IN (
        'band_c19_drums');

UPDATE `items_base` SET `width` = 2, `length` = 1
    WHERE `type` = 's' AND `width` = 4 AND `length` = 1 AND `item_name` IN (
        'tokyo_c18_gamesign');

UPDATE `items_base` SET `width` = 3, `length` = 1
    WHERE `type` = 's' AND `width` = 4 AND `length` = 1 AND `item_name` IN (
        'xmas_c16_door');

UPDATE `items_base` SET `width` = 3, `length` = 4
    WHERE `type` = 's' AND `width` = 5 AND `length` = 2 AND `item_name` IN (
        'ads_nick_geyeser');

UPDATE `items_base` SET `allow_lay` = '0'
    WHERE `type` = 's' AND `allow_lay` <> '0' AND `interaction_type` IN ('default', 'normal', 'bed', 'multiheight', 'clothing', 'mutearea', 'tent') AND `item_name` IN (
        'CF_350_jadefish', 'CF_350_sceptre', 'CF_500_moneytree', 'CF_500_orb', 'bazaar_c17_eleengage',
        'bonusrare18_3*0', 'bonusrare18_3*1', 'bonusrare18_3*2', 'bonusrare18_3*3', 'bonusrare18_3*4',
        'bonusrare18_3*5', 'bonusrare19_1*0', 'bonusrare19_1*1', 'bonusrare19_1*2', 'bonusrare19_1*3',
        'bonusrare19_1*4', 'bonusrare19_1*5', 'bonusrare19_2*0', 'bonusrare19_3*0', 'bonusrare19_3*1',
        'bonusrare19_3*2', 'bonusrare19_3*3', 'bonusrare19_3*4', 'bonusrare19_3*5', 'bonusrare19_4*0',
        'bonusrare19_4*1', 'bonusrare19_4*2', 'bonusrare19_4*3', 'bonusrare19_4*4', 'bonusrare19_4*5',
        'bonusrare20_1a*1', 'bonusrare20_1a*2', 'bonusrare20_1a*3', 'bonusrare20_1a*4', 'bonusrare20_1a*5',
        'bonusrare20_1a*6', 'bonusrare20_2*1', 'bonusrare20_2*2', 'bonusrare20_2*3', 'bonusrare20_2*4',
        'bonusrare20_2*5', 'bonusrare20_2*6', 'bonusrare20_3*1', 'bonusrare20_3*2', 'bonusrare20_3*3',
        'bonusrare20_3*4', 'bonusrare20_3*5', 'bonusrare20_3*6', 'bonusrare20_4*0', 'bonusrare20_4*1',
        'bonusrare20_4*2', 'bonusrare20_4*3', 'bonusrare20_4*4', 'bonusrare20_4*5', 'booster_c20_box1',
        'clothing_featherflower', 'clothing_r18_longwavy', 'clothing_sasquatchmask', 'clothing_studentcap',
        'coralking_ltd18_royalseahorse', 'easter_c20_darkrock1', 'easter_c20_lightrock1', 'gift_bff_a', 'gift_bff_b',
        'gothic15_toilet', 'gothic_bed', 'hc17_10', 'hc20_9', 'horse_hairdye_04_horsesho', 'hween_c18_lablocker',
        'kinkysofa', 'mutearea_sign2', 'paris15_bicycle', 'rare_colourable_pillar*3',
        'rare_colourable_scifirocket*3', 'rare_colourable_scifirocket*4', 'rare_parasol', 'rare_prize17xmas',
        'rare_prize18*0', 'rare_prize18_xmas', 'rare_prize19*0', 'rare_prize20*0', 'rare_prize20*1',
        'rare_prize20*10', 'rare_prize20*11', 'rare_prize20*12', 'rare_prize20*13', 'rare_prize20*2',
        'rare_prize20*3', 'rare_prize20*4', 'rare_prize20*5', 'rare_prize20*6', 'rare_prize20*7', 'rare_prize20*8',
        'rare_prize20*9', 'table_silo_small', 'tokyo_c18_gacha2', 'tokyo_c18_toy1', 'tokyo_c18_toy10',
        'tokyo_c18_toy2', 'tokyo_c18_toy3', 'tokyo_c18_toy4', 'tokyo_c18_toy5', 'tokyo_c18_toy6', 'tokyo_c18_toy7',
        'tokyo_c18_toy8', 'tokyo_c18_toy9', 'waasa_bunk_bed', 'xmas_c17_girl', 'xmas_c17_window', 'xmas_c19_box1b',
        'xmas_c19_box2b', 'xmas_c19_box3b', 'xmas_c19_box4b', 'xmas_c19_box5b', 'xmas_c19_box6b');

UPDATE `items_base` SET `allow_lay` = '1'
    WHERE `type` = 's' AND `allow_lay` = '0' AND `interaction_type` IN ('default', 'normal', 'bed', 'multiheight', 'clothing', 'mutearea', 'tent') AND `item_name` IN (
        'jp_bed', 'rainbow_c19_bed', 'sf_pod_dark', 'sleepingbag*11', 'test_effectswitch', 'test_tent',
        'xmas_c16_tinybed');

UPDATE `items_base` SET `allow_sit` = '0'
    WHERE `type` = 's' AND `allow_sit` <> '0' AND `interaction_type` IN ('default', 'normal', 'bed', 'multiheight', 'clothing', 'mutearea') AND `item_name` IN (
        'CF_350_jadefish', 'CF_350_sceptre', 'CF_500_moneytree', 'CF_500_orb', 'anna_pill*1', 'anna_pill*2',
        'anna_pill*3', 'anna_pill*4', 'anna_pill*5', 'bonusrare18_3*0', 'bonusrare18_3*1', 'bonusrare18_3*2',
        'bonusrare18_3*3', 'bonusrare18_3*4', 'bonusrare18_3*5', 'bonusrare19_1*0', 'bonusrare19_1*1',
        'bonusrare19_1*2', 'bonusrare19_1*3', 'bonusrare19_1*4', 'bonusrare19_1*5', 'bonusrare19_3*0',
        'bonusrare19_3*1', 'bonusrare19_3*2', 'bonusrare19_3*3', 'bonusrare19_3*4', 'bonusrare19_3*5',
        'bonusrare20_1a*1', 'bonusrare20_1a*2', 'bonusrare20_1a*3', 'bonusrare20_1a*4', 'bonusrare20_1a*5',
        'bonusrare20_1a*6', 'bonusrare20_2*1', 'bonusrare20_2*2', 'bonusrare20_2*3', 'bonusrare20_2*4',
        'bonusrare20_2*5', 'bonusrare20_2*6', 'bonusrare20_3*1', 'bonusrare20_3*2', 'bonusrare20_3*3',
        'bonusrare20_3*4', 'bonusrare20_3*5', 'bonusrare20_3*6', 'bonusrare20_4*0', 'bonusrare20_4*1',
        'bonusrare20_4*2', 'bonusrare20_4*3', 'bonusrare20_4*4', 'bonusrare20_4*5', 'booster_c20_box1', 'bw_pool_a1',
        'clothing_featherflower', 'clothing_r18_longwavy', 'clothing_sasquatchmask', 'clothing_studentcap',
        'easter_c18_recyclebin', 'easter_c20_darkrock1', 'easter_c20_lightrock1', 'gift_bff_a', 'gift_bff_b',
        'greek_r19_chariot', 'hc20_9', 'horse_hairdye_04_horsesho', 'hween_c18_lablocker', 'laundry_c18_ironing',
        'mutearea_sign2', 'nest_plow_bro', 'nest_plow_reg', 'nest_plow_skl', 'rare_colourable_pillar*3',
        'rare_colourable_scifirocket*3', 'rare_colourable_scifirocket*4', 'rare_dragonlamp*8', 'rare_parasol',
        'rare_prize17xmas', 'rare_prize18*0', 'rare_prize18_xmas', 'rare_prize19*0', 'rare_prize20*0',
        'rare_prize20*1', 'rare_prize20*10', 'rare_prize20*11', 'rare_prize20*12', 'rare_prize20*13',
        'rare_prize20*2', 'rare_prize20*3', 'rare_prize20*4', 'rare_prize20*5', 'rare_prize20*6', 'rare_prize20*7',
        'rare_prize20*8', 'rare_prize20*9', 'sunsetcafe_c20_benchcornerconcave', 'tokyo_c18_gacha2',
        'tokyo_c18_toy1', 'tokyo_c18_toy10', 'tokyo_c18_toy2', 'tokyo_c18_toy3', 'tokyo_c18_toy4', 'tokyo_c18_toy5',
        'tokyo_c18_toy6', 'tokyo_c18_toy7', 'tokyo_c18_toy8', 'tokyo_c18_toy9', 'xmas_c17_girl', 'xmas_c17_window',
        'xmas_c19_box1b', 'xmas_c19_box2b', 'xmas_c19_box3b', 'xmas_c19_box4b', 'xmas_c19_box5b', 'xmas_c19_box6b');

UPDATE `items_base` SET `allow_sit` = '1'
    WHERE `type` = 's' AND `allow_sit` = '0' AND `interaction_type` IN ('default', 'normal', 'bed', 'multiheight', 'clothing', 'mutearea') AND `item_name` IN (
        'anc_comfy_tree', 'art_c20_artstool', 'art_c20_gold3', 'bonusrare19_2*1', 'bonusrare19_2*2',
        'bonusrare19_2*3', 'bonusrare19_2*4', 'bonusrare19_2*5', 'easter_c20_kayak', 'easter_c20_stickteepee',
        'easter_r18_bigbunny', 'gothic_bed', 'hc17_10', 'india_c20_eleseat', 'india_c20_longpillow',
        'india_r20_exotictent', 'market_c19_trolley', 'neonpunk_c20_sofa', 'pcnc_tree', 'rainbow_c19_chair',
        'tokyo_c18_motorbike', 'waasa_bunk_bed', 'xmas_c17_engage_ted', 'xmas_c19_balcony', 'xmas_ltd17_ted',
        'xmas_ltd19_icethrone');

UPDATE `items_base` SET `allow_walk` = '0'
    WHERE `type` = 's' AND `allow_walk` <> '0' AND `interaction_type` IN ('default', 'normal', 'bed', 'multiheight', 'clothing', 'mutearea') AND `item_name` IN (
        'CF_350_jadefish', 'CF_350_sceptre', 'CF_500_moneytree', 'CF_500_orb', 'ads_m6_floor',
        'bazaar_c17_eleengage', 'bed_budget*10', 'bed_silo_two_notrd', 'bonusrare18_3*0', 'bonusrare18_3*1',
        'bonusrare18_3*2', 'bonusrare18_3*3', 'bonusrare18_3*4', 'bonusrare18_3*5', 'bonusrare19_1*0',
        'bonusrare19_1*1', 'bonusrare19_1*2', 'bonusrare19_1*3', 'bonusrare19_1*4', 'bonusrare19_1*5',
        'bonusrare19_2*0', 'bonusrare19_3*0', 'bonusrare19_3*1', 'bonusrare19_3*2', 'bonusrare19_3*3',
        'bonusrare19_3*4', 'bonusrare19_3*5', 'bonusrare19_4*0', 'bonusrare19_4*1', 'bonusrare19_4*2',
        'bonusrare19_4*3', 'bonusrare19_4*4', 'bonusrare19_4*5', 'bonusrare20_1a*1', 'bonusrare20_1a*2',
        'bonusrare20_1a*3', 'bonusrare20_1a*4', 'bonusrare20_1a*5', 'bonusrare20_1a*6', 'bonusrare20_3*1',
        'bonusrare20_3*2', 'bonusrare20_3*3', 'bonusrare20_3*4', 'bonusrare20_3*5', 'bonusrare20_3*6',
        'bonusrare20_4*0', 'bonusrare20_4*1', 'bonusrare20_4*2', 'bonusrare20_4*3', 'bonusrare20_4*4',
        'bonusrare20_4*5', 'booster_c20_box1', 'byesw_hand', 'clothing_featherflower', 'clothing_r18_longwavy',
        'clothing_sasquatchmask', 'clothing_studentcap', 'coralking_ltd18_royalseahorse', 'dessertcafe_c17_shelf',
        'easter_c20_darkrock1', 'easter_c20_lightrock1', 'easterduck', 'gift_bff_a', 'gift_bff_b', 'gothic15_toilet',
        'habbo20_c20_blackgoldballoon', 'habbo20_c20_rosegoldballoon', 'hblooza14_hotair_b', 'hblooza14_hotair_y',
        'horse_hairdye_04_horsesho', 'hween_c15_sdcrack', 'hween_c18_olddocs2', 'hween_c18_toy2old',
        'hween_c19_pumpkinpatch', 'kinkysofa', 'lt_spider', 'magazines', 'neonpunk_c20_sofa', 'paris_c15_roof',
        'pcnc_gazebohack', 'qt_sum11_shell1', 'rare_colourable_pillar*3', 'rare_colourable_scifirocket*3',
        'rare_colourable_scifirocket*4', 'rare_parasol', 'rare_prize17xmas', 'rare_prize18*0', 'rare_prize18_xmas',
        'rare_prize19*0', 'rare_prize20*0', 'rare_prize20*1', 'rare_prize20*10', 'rare_prize20*11',
        'rare_prize20*12', 'rare_prize20*13', 'rare_prize20*2', 'rare_prize20*3', 'rare_prize20*4', 'rare_prize20*5',
        'rare_prize20*6', 'rare_prize20*7', 'rare_prize20*8', 'rare_prize20*9', 'rare_xmas_screen',
        'room_noob_table2', 'theatre_edge', 'toilet', 'tokyo_c18_gacha2', 'tokyo_c18_scarydino', 'tokyo_c18_toy1',
        'tokyo_c18_toy10', 'tokyo_c18_toy2', 'tokyo_c18_toy3', 'tokyo_c18_toy4', 'tokyo_c18_toy5', 'tokyo_c18_toy6',
        'tokyo_c18_toy7', 'tokyo_c18_toy8', 'tokyo_c18_toy9', 'uni_games', 'uni_manga', 'uni_pizza', 'uni_snacks',
        'xmas_c17_girl', 'xmas_c17_window', 'xmas_c19_balcony', 'xmas_c19_box1b', 'xmas_c19_box2b', 'xmas_c19_box3b',
        'xmas_c19_box4b', 'xmas_c19_box5b', 'xmas_c19_box6b');

UPDATE `items_base` SET `allow_walk` = '1'
    WHERE `type` = 's' AND `allow_walk` = '0' AND `interaction_type` IN ('default', 'normal', 'bed', 'multiheight', 'clothing', 'mutearea') AND `item_name` IN (
        'anna_pill*1', 'anna_pill*2', 'anna_pill*3', 'anna_pill*4', 'anna_pill*5', 'army_c15_sandbag',
        'art_c20_clotheshanger', 'art_c20_light', 'art_c20_paintings', 'art_c20_shelves', 'bazaar_c17_basket',
        'bazaar_c17_chillies', 'bazaar_c17_clothroof', 'bazaar_c17_clothroof2', 'bazaar_c17_curtainblue',
        'bazaar_c17_curtaingreenpurpletrim', 'bazaar_c17_curtainpink', 'bazaar_c17_curtainpinkbluetrim',
        'bazaar_c17_curtainpurple', 'bazaar_c17_curtainpurpleyellowtrim', 'bazaar_c17_curtainyellow',
        'bazaar_c17_curtainyellowbluetrim', 'bazaar_c17_displaytable', 'bazaar_c17_flycarpet',
        'bazaar_c17_marketroofbig', 'bc_block_0*11', 'bc_halfcylinder*1', 'bc_halfcylinder*10', 'bc_halfcylinder*11',
        'bc_halfcylinder*12', 'bc_halfcylinder*13', 'bc_halfcylinder*14', 'bc_halfcylinder*15', 'bc_halfcylinder*16',
        'bc_halfcylinder*17', 'bc_halfcylinder*18', 'bc_halfcylinder*19', 'bc_halfcylinder*2', 'bc_halfcylinder*20',
        'bc_halfcylinder*21', 'bc_halfcylinder*22', 'bc_halfcylinder*23', 'bc_halfcylinder*24', 'bc_halfcylinder*25',
        'bc_halfcylinder*26', 'bc_halfcylinder*27', 'bc_halfcylinder*28', 'bc_halfcylinder*29', 'bc_halfcylinder*3',
        'bc_halfcylinder*30', 'bc_halfcylinder*31', 'bc_halfcylinder*32', 'bc_halfcylinder*33', 'bc_halfcylinder*34',
        'bc_halfcylinder*35', 'bc_halfcylinder*36', 'bc_halfcylinder*37', 'bc_halfcylinder*38', 'bc_halfcylinder*39',
        'bc_halfcylinder*4', 'bc_halfcylinder*40', 'bc_halfcylinder*41', 'bc_halfcylinder*42', 'bc_halfcylinder*43',
        'bc_halfcylinder*44', 'bc_halfcylinder*45', 'bc_halfcylinder*46', 'bc_halfcylinder*47', 'bc_halfcylinder*48',
        'bc_halfcylinder*49', 'bc_halfcylinder*5', 'bc_halfcylinder*50', 'bc_halfcylinder*51', 'bc_halfcylinder*52',
        'bc_halfcylinder*53', 'bc_halfcylinder*54', 'bc_halfcylinder*55', 'bc_halfcylinder*56', 'bc_halfcylinder*57',
        'bc_halfcylinder*58', 'bc_halfcylinder*59', 'bc_halfcylinder*6', 'bc_halfcylinder*60', 'bc_halfcylinder*61',
        'bc_halfcylinder*62', 'bc_halfcylinder*63', 'bc_halfcylinder*64', 'bc_halfcylinder*65', 'bc_halfcylinder*66',
        'bc_halfcylinder*67', 'bc_halfcylinder*68', 'bc_halfcylinder*69', 'bc_halfcylinder*7', 'bc_halfcylinder*8',
        'bc_halfcylinder*9', 'bc_sphere*1', 'bc_sphere*10', 'bc_sphere*11', 'bc_sphere*12', 'bc_sphere*13',
        'bc_sphere*14', 'bc_sphere*15', 'bc_sphere*16', 'bc_sphere*17', 'bc_sphere*18', 'bc_sphere*19',
        'bc_sphere*2', 'bc_sphere*20', 'bc_sphere*21', 'bc_sphere*22', 'bc_sphere*23', 'bc_sphere*24',
        'bc_sphere*25', 'bc_sphere*26', 'bc_sphere*27', 'bc_sphere*28', 'bc_sphere*29', 'bc_sphere*3',
        'bc_sphere*30', 'bc_sphere*31', 'bc_sphere*32', 'bc_sphere*33', 'bc_sphere*34', 'bc_sphere*35',
        'bc_sphere*36', 'bc_sphere*37', 'bc_sphere*38', 'bc_sphere*39', 'bc_sphere*4', 'bc_sphere*40',
        'bc_sphere*41', 'bc_sphere*42', 'bc_sphere*43', 'bc_sphere*44', 'bc_sphere*45', 'bc_sphere*46',
        'bc_sphere*47', 'bc_sphere*48', 'bc_sphere*49', 'bc_sphere*5', 'bc_sphere*50', 'bc_sphere*51',
        'bc_sphere*52', 'bc_sphere*53', 'bc_sphere*54', 'bc_sphere*55', 'bc_sphere*56', 'bc_sphere*57',
        'bc_sphere*58', 'bc_sphere*59', 'bc_sphere*6', 'bc_sphere*60', 'bc_sphere*61', 'bc_sphere*62',
        'bc_sphere*63', 'bc_sphere*64', 'bc_sphere*65', 'bc_sphere*66', 'bc_sphere*67', 'bc_sphere*68',
        'bc_sphere*69', 'bc_sphere*7', 'bc_sphere*8', 'bc_sphere*9', 'bc_standinghalfcylinder*1',
        'bc_standinghalfcylinder*10', 'bc_standinghalfcylinder*11', 'bc_standinghalfcylinder*12',
        'bc_standinghalfcylinder*13', 'bc_standinghalfcylinder*14', 'bc_standinghalfcylinder*15',
        'bc_standinghalfcylinder*16', 'bc_standinghalfcylinder*17', 'bc_standinghalfcylinder*18',
        'bc_standinghalfcylinder*19', 'bc_standinghalfcylinder*2', 'bc_standinghalfcylinder*20',
        'bc_standinghalfcylinder*21', 'bc_standinghalfcylinder*22', 'bc_standinghalfcylinder*23',
        'bc_standinghalfcylinder*24', 'bc_standinghalfcylinder*25', 'bc_standinghalfcylinder*26',
        'bc_standinghalfcylinder*27', 'bc_standinghalfcylinder*28', 'bc_standinghalfcylinder*29',
        'bc_standinghalfcylinder*3', 'bc_standinghalfcylinder*30', 'bc_standinghalfcylinder*31',
        'bc_standinghalfcylinder*32', 'bc_standinghalfcylinder*33', 'bc_standinghalfcylinder*34',
        'bc_standinghalfcylinder*35', 'bc_standinghalfcylinder*36', 'bc_standinghalfcylinder*37',
        'bc_standinghalfcylinder*38', 'bc_standinghalfcylinder*39', 'bc_standinghalfcylinder*4',
        'bc_standinghalfcylinder*40', 'bc_standinghalfcylinder*41', 'bc_standinghalfcylinder*42',
        'bc_standinghalfcylinder*43', 'bc_standinghalfcylinder*44', 'bc_standinghalfcylinder*45',
        'bc_standinghalfcylinder*46', 'bc_standinghalfcylinder*47', 'bc_standinghalfcylinder*48',
        'bc_standinghalfcylinder*49', 'bc_standinghalfcylinder*5', 'bc_standinghalfcylinder*50',
        'bc_standinghalfcylinder*51', 'bc_standinghalfcylinder*52', 'bc_standinghalfcylinder*53',
        'bc_standinghalfcylinder*54', 'bc_standinghalfcylinder*55', 'bc_standinghalfcylinder*56',
        'bc_standinghalfcylinder*57', 'bc_standinghalfcylinder*58', 'bc_standinghalfcylinder*59',
        'bc_standinghalfcylinder*6', 'bc_standinghalfcylinder*60', 'bc_standinghalfcylinder*61',
        'bc_standinghalfcylinder*62', 'bc_standinghalfcylinder*63', 'bc_standinghalfcylinder*64',
        'bc_standinghalfcylinder*65', 'bc_standinghalfcylinder*66', 'bc_standinghalfcylinder*67',
        'bc_standinghalfcylinder*68', 'bc_standinghalfcylinder*69', 'bc_standinghalfcylinder*7',
        'bc_standinghalfcylinder*8', 'bc_standinghalfcylinder*9', 'bc_standingtriangularprism*1',
        'bc_standingtriangularprism*10', 'bc_standingtriangularprism*11', 'bc_standingtriangularprism*12',
        'bc_standingtriangularprism*13', 'bc_standingtriangularprism*14', 'bc_standingtriangularprism*15',
        'bc_standingtriangularprism*16', 'bc_standingtriangularprism*17', 'bc_standingtriangularprism*18',
        'bc_standingtriangularprism*19', 'bc_standingtriangularprism*2', 'bc_standingtriangularprism*20',
        'bc_standingtriangularprism*21', 'bc_standingtriangularprism*22', 'bc_standingtriangularprism*23',
        'bc_standingtriangularprism*24', 'bc_standingtriangularprism*25', 'bc_standingtriangularprism*26',
        'bc_standingtriangularprism*27', 'bc_standingtriangularprism*28', 'bc_standingtriangularprism*29',
        'bc_standingtriangularprism*3', 'bc_standingtriangularprism*30', 'bc_standingtriangularprism*31',
        'bc_standingtriangularprism*32', 'bc_standingtriangularprism*33', 'bc_standingtriangularprism*34',
        'bc_standingtriangularprism*35', 'bc_standingtriangularprism*36', 'bc_standingtriangularprism*37',
        'bc_standingtriangularprism*38', 'bc_standingtriangularprism*39', 'bc_standingtriangularprism*4',
        'bc_standingtriangularprism*40', 'bc_standingtriangularprism*41', 'bc_standingtriangularprism*42',
        'bc_standingtriangularprism*43', 'bc_standingtriangularprism*44', 'bc_standingtriangularprism*45',
        'bc_standingtriangularprism*46', 'bc_standingtriangularprism*47', 'bc_standingtriangularprism*48',
        'bc_standingtriangularprism*49', 'bc_standingtriangularprism*5', 'bc_standingtriangularprism*50',
        'bc_standingtriangularprism*51', 'bc_standingtriangularprism*52', 'bc_standingtriangularprism*53',
        'bc_standingtriangularprism*54', 'bc_standingtriangularprism*55', 'bc_standingtriangularprism*56',
        'bc_standingtriangularprism*57', 'bc_standingtriangularprism*58', 'bc_standingtriangularprism*59',
        'bc_standingtriangularprism*6', 'bc_standingtriangularprism*60', 'bc_standingtriangularprism*61',
        'bc_standingtriangularprism*62', 'bc_standingtriangularprism*63', 'bc_standingtriangularprism*64',
        'bc_standingtriangularprism*65', 'bc_standingtriangularprism*66', 'bc_standingtriangularprism*67',
        'bc_standingtriangularprism*68', 'bc_standingtriangularprism*69', 'bc_standingtriangularprism*7',
        'bc_standingtriangularprism*8', 'bc_standingtriangularprism*9', 'birds_r18_cardinal', 'celestial_c19_aurora',
        'celestial_c19_floor', 'cine_glass', 'cland_c15_roof', 'cland_c15_table', 'classic8_bar',
        'classic8_curtains', 'classic8_divider', 'classic8_records', 'classic9_table', 'coralking_c18_boulders1',
        'coralking_c18_bubblerock', 'coralking_c18_clamshell1', 'coralking_c18_clamshell2',
        'coralking_c18_clamshell3', 'coralking_c18_fishnet', 'cpunk_c15_table', 'darkelegant_c20_walldecor',
        'darkmodern_c20_display', 'darkmodern_c20_neonlight', 'darkmodern_c20_pictures', 'darkmodern_c20_tubelights',
        'darkmodern_c20_tv', 'darkmodern_c20_wall', 'dessertcafe_c17_vase', 'dng_cube', 'easter_c17_barn',
        'easter_c17_roof', 'easter_c17_roof2', 'easter_c18_recyclebin', 'easter_c20_camprug',
        'easter_c20_fishingpole', 'easter_c20_heatherrock', 'easter_c20_logwalk', 'easter_c20_mossydivider',
        'easter_c20_mountainslopes', 'easter_c20_scatteredforestfloor', 'fball_cote', 'fest_c19_skull1', 'fx_flare',
        'gaming_c18_cubelight', 'garden_rosefence', 'gh_div_cor', 'gothic_c15_chandelier', 'gothic_c15_crn',
        'gothiccafe_c20_rosepainting', 'gothiccafe_c20_windoor', 'greek_r19_chariot', 'hblooza_tfnc_1',
        'hblooza_tfnc_2*1', 'hblooza_tfnc_2*2', 'hblooza_tfnc_2*3', 'hblooza_tfnc_2*4', 'hc16_8', 'hc20_1',
        'hc20_10', 'hc20_11', 'hc20_12', 'hc20_2', 'hc20_3', 'hc20_4', 'hc20_5', 'hc20_6', 'hc20_7', 'hc20_8',
        'hole1x1', 'hole1x1test', 'hole2', 'hole3', 'hole4', 'hs_bpass', 'hween14_candletrail', 'hween_c16_balcony',
        'hween_c17_pillar', 'hween_c17_pillarsmall', 'hween_c17_rock2', 'hween_c17_wall',
        'hween_c18_brokenglasswall', 'hween_c18_flashlight', 'hween_c18_labglasswall', 'hween_c18_oldlablocker',
        'hween_c19_bigroof', 'hween_c19_broomstaffstand', 'hween_c19_smallroof', 'hween_r19_weepingwillow',
        'india_c20_archway', 'india_c20_beadbellsdeco', 'india_c20_flowerbelldeco', 'india_c20_light',
        'jungle_c16_light', 'jungle_c16_pot', 'jungle_c16_pot2', 'jungle_c16_pot3', 'jungle_c16_table',
        'jungle_c16_table2', 'jungle_c16_table3', 'jungle_c16_watertrap', 'laundry_c18_basket',
        'market_c19_antitheftalarm', 'modern_c17_desk', 'modern_c17_lowtable', 'modern_c17_photos',
        'modern_c17_shelf', 'modern_c17_speakers', 'modern_c17_wall', 'mystics_bspike2', 'mystics_btile2',
        'neonpunk_c20_bows', 'neonpunk_c20_lights', 'neonpunk_c20_stardeco', 'neonpunk_c20_strawberries',
        'neonpunk_c20_unicornposter', 'nest_plow_bro', 'ny2013_aftermath', 'olympics_c16_light',
        'olympics_c16_table', 'olympics_c16_yogamat*1', 'olympics_c16_yogamat*2', 'olympics_c16_yogamat*3',
        'olympics_c16_yogamat*4', 'olympics_c16_yogamat*5', 'olympics_c16_yogamat*6', 'pencils', 'pirate_barrel1',
        'pwad', 'rainbow_c19_flags', 'rainbow_c19_lights', 'rainbow_c19_rug', 'rainyday_c19_rainwindows',
        'rainyday_c20_coathanger', 'rainyday_c20_glowstars', 'rare_r20_grandebackdrop', 'room_hcl15_table2',
        'room_wl15_table1', 'room_wl15_table2', 'santorini_c17_arch', 'santorini_c17_rockfloat', 'snowb_rail',
        'steampunk_c15_stage', 'steampunk_pillar_1', 'steampunk_table_1', 'stone_flowerbed',
        'summer_c17_burgertruck', 'suncity_c19_divider', 'suncity_c19_glasswall', 'sunsetcafe_c20_swordfish',
        'sunsetcafe_c20_wall', 'tokyo_c18_adposters', 'tokyo_c18_magazinerack', 'tokyo_c18_neonsign1',
        'tokyo_c18_railing', 'tokyo_c18_residential', 'tokyo_c18_sakura', 'tokyo_c18_shopdisplay',
        'tokyo_c18_shopfront', 'usva_shelf', 'val13_build_1', 'val13_div_1', 'val13_div_2', 'val13_div_3_crnr2',
        'val13_gond_back', 'val13_gond_front', 'val13_table', 'val16_float2', 'val_c20_lights', 'vote_majority',
        'wildwest_desk', 'wintercabin_c19_fence', 'wintercabin_c19_fireplace', 'xmas13_rug', 'xmas_c15_advent7',
        'xmas_c15_advent8', 'xmas_c16_wall', 'xmas_c17_drain', 'xmas_c17_pillar', 'xmas_c18_stagebackdrop',
        'xmas_c18_stagecurtains', 'xmas_c18_stagewindow', 'zengarden_c18_divider', 'zengarden_c18_light',
        'zengarden_c18_moss', 'zengarden_c20_bamboo', 'zengarden_c20_divider', 'zengarden_r18_fountain');
