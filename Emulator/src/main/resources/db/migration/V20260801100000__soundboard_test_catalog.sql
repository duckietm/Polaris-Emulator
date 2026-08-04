INSERT INTO `soundboard_sounds` (`name`, `url`, `enabled`, `sort_order`, `min_rank`)
SELECT seed.`name`, seed.`url`, 1, seed.`sort_order`, 1
FROM (
    SELECT 'Bong' AS `name`, '/sounds/soundboard/bong.ogg' AS `url`, 30 AS `sort_order`
    UNION ALL SELECT 'Click', '/sounds/soundboard/click.ogg', 40
    UNION ALL SELECT 'Conferma', '/sounds/soundboard/confirmation.ogg', 50
    UNION ALL SELECT 'Errore', '/sounds/soundboard/error.ogg', 60
    UNION ALL SELECT 'Vetro', '/sounds/soundboard/glass.ogg', 70
    UNION ALL SELECT 'Glitch', '/sounds/soundboard/glitch.ogg', 80
    UNION ALL SELECT 'Apertura', '/sounds/soundboard/open.ogg', 90
    UNION ALL SELECT 'Chiusura', '/sounds/soundboard/close.ogg', 100
    UNION ALL SELECT 'Pizzico', '/sounds/soundboard/pluck.ogg', 110
    UNION ALL SELECT 'Domanda', '/sounds/soundboard/question.ogg', 120
    UNION ALL SELECT 'Selezione', '/sounds/soundboard/select.ogg', 130
    UNION ALL SELECT 'Interruttore', '/sounds/soundboard/switch.ogg', 140
    UNION ALL SELECT 'Tic', '/sounds/soundboard/tick.ogg', 150
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM `soundboard_sounds` existing
    WHERE existing.`url` = seed.`url`
)
AND (SELECT COUNT(*) FROM `soundboard_sounds`) <= 487;
