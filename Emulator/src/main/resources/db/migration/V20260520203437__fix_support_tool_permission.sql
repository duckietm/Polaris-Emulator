-- Restrict support tools to Support rank and above.

UPDATE `permission_definitions`
   SET `rank_1` = 0,
       `rank_2` = 0,
       `rank_3` = 0,
       `rank_4` = 1,
       `rank_5` = 1,
       `rank_6` = 1,
       `rank_7` = 1
 WHERE `permission_key` = 'acc_supporttool';
