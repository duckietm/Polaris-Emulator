-- =====================================================================
-- fill_room_207_wiredlab.sql
-- Riempi la room 207 ("wired room", owner tester = user_id 5) con UN furni
-- per ogni tipo wired in items_base (309), separati a scacchiera in 7 isole
-- per categoria, su un model aperto su misura (40x28 = 1120 tile).
--
-- REVERT:
--   UPDATE rooms SET model='model_k' WHERE id=207;
--   DELETE FROM items WHERE room_id=207;
--   DELETE FROM room_models WHERE name='wiredlab';
-- =====================================================================

START TRANSACTION;

-- 1) Model aperto 40x28, tutto pavimento, porta a sinistra-centro (0,14)
REPLACE INTO room_models (name, door_x, door_y, door_dir, heightmap, public_items, club_only)
VALUES ('wiredlab', 0, 14, 2,
        TRIM(TRAILING CONCAT(CHAR(13),CHAR(10)) FROM REPEAT(CONCAT(REPEAT('0',40),CHAR(13),CHAR(10)),28)),
        '', '0');

-- 2) Assegna il model alla room 207
UPDATE rooms SET model='wiredlab' WHERE id=207;

-- 3) Svuota la room
DELETE FROM items WHERE room_id=207;

-- 4) Piazza i 309 wired in isole a scacchiera (ogni furni separato sui 4 lati),
--    legati a tester = user_id 5. Formula: x=ox+col*2+(row%2), y=oy+row.
INSERT INTO items (user_id, room_id, item_id, wall_pos, x, y, z, rot, extra_data, wired_data, limited_data, guild_id)
SELECT 5, 207, t.id, '',
       t.ox + ((t.k-1) % t.c)*2 + (FLOOR((t.k-1)/t.c) % 2) AS x,
       t.oy + FLOOR((t.k-1)/t.c)                          AS y,
       0, 0, '', '', '0:0', 0
FROM (
  SELECT g.id,
    ROW_NUMBER() OVER (PARTITION BY g.cat ORDER BY g.interaction_type) AS k,
    CASE g.cat WHEN 'act' THEN 1 WHEN 'trg' THEN 25 WHEN 'cnd' THEN 1
               WHEN 'slc' THEN 25 WHEN 'xtra' THEN 25 WHEN 'var' THEN 25 ELSE 33 END AS ox,
    CASE g.cat WHEN 'act' THEN 1 WHEN 'trg' THEN 1  WHEN 'cnd' THEN 15
               WHEN 'slc' THEN 10 WHEN 'xtra' THEN 16 WHEN 'var' THEN 22 ELSE 22 END AS oy,
    CASE g.cat WHEN 'act' THEN 11 WHEN 'trg' THEN 7 WHEN 'cnd' THEN 9
               WHEN 'slc' THEN 5 WHEN 'xtra' THEN 4 WHEN 'var' THEN 3 ELSE 3 END AS c
  FROM (
    SELECT MIN(id) AS id, interaction_type,
      CASE WHEN SUBSTRING_INDEX(SUBSTRING(interaction_type,4),'_',1) IN ('trg','cnd','act','slc','var','xtra')
           THEN SUBSTRING_INDEX(SUBSTRING(interaction_type,4),'_',1) ELSE 'misc' END AS cat
    FROM items_base WHERE interaction_type LIKE 'wf\_%'
    GROUP BY interaction_type
  ) g
) t;

COMMIT;
