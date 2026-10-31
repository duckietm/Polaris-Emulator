SET NAMES utf8mb4;

SELECT id, name AS broken_name, CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4) AS repaired_name
FROM rooms
WHERE name <> ''
  AND name NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
  AND CASE WHEN name NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
      THEN HEX(CONVERT(name USING latin1)) REGEXP '^([0-7][0-9A-F]|C[2-9A-F][89AB][0-9A-F]|D[0-9A-F][89AB][0-9A-F]|E[0-9A-F][89AB][0-9A-F][89AB][0-9A-F]|F[0-4][89AB][0-9A-F][89AB][0-9A-F][89AB][0-9A-F])*$'
       AND HEX(CONVERT(name USING latin1)) REGEXP '^(..)*[C-F][0-9A-F]'
      ELSE 0 END;

DROP PROCEDURE IF EXISTS fix_room_text_encoding;

DELIMITER //

CREATE PROCEDURE fix_room_text_encoding()
BEGIN
    DECLARE pass INT DEFAULT 0;

    WHILE pass < 4 DO
        UPDATE rooms
        SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
        WHERE name <> ''
          AND name NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
          AND CASE WHEN name NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
              THEN HEX(CONVERT(name USING latin1)) REGEXP '^([0-7][0-9A-F]|C[2-9A-F][89AB][0-9A-F]|D[0-9A-F][89AB][0-9A-F]|E[0-9A-F][89AB][0-9A-F][89AB][0-9A-F]|F[0-4][89AB][0-9A-F][89AB][0-9A-F][89AB][0-9A-F])*$'
               AND HEX(CONVERT(name USING latin1)) REGEXP '^(..)*[C-F][0-9A-F]'
              ELSE 0 END;

        UPDATE rooms
        SET description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
        WHERE description <> ''
          AND description NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
          AND CASE WHEN description NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
              THEN HEX(CONVERT(description USING latin1)) REGEXP '^([0-7][0-9A-F]|C[2-9A-F][89AB][0-9A-F]|D[0-9A-F][89AB][0-9A-F]|E[0-9A-F][89AB][0-9A-F][89AB][0-9A-F]|F[0-4][89AB][0-9A-F][89AB][0-9A-F][89AB][0-9A-F])*$'
               AND HEX(CONVERT(description USING latin1)) REGEXP '^(..)*[C-F][0-9A-F]'
              ELSE 0 END;

        UPDATE rooms
        SET tags = CONVERT(CAST(CONVERT(tags USING latin1) AS BINARY) USING utf8mb4)
        WHERE tags <> ''
          AND tags NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
          AND CASE WHEN tags NOT REGEXP '[^\\x{00}-\\x{7F}\\x{A0}-\\x{FF}€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ\\x{81}\\x{8D}\\x{8F}\\x{90}\\x{9D}]'
              THEN HEX(CONVERT(tags USING latin1)) REGEXP '^([0-7][0-9A-F]|C[2-9A-F][89AB][0-9A-F]|D[0-9A-F][89AB][0-9A-F]|E[0-9A-F][89AB][0-9A-F][89AB][0-9A-F]|F[0-4][89AB][0-9A-F][89AB][0-9A-F][89AB][0-9A-F])*$'
               AND HEX(CONVERT(tags USING latin1)) REGEXP '^(..)*[C-F][0-9A-F]'
              ELSE 0 END;

        SET pass = pass + 1;
    END WHILE;
END //

DELIMITER ;

CALL fix_room_text_encoding();

DROP PROCEDURE fix_room_text_encoding;

SELECT id, name FROM rooms WHERE name REGEXP '(Ã.|Â.|â€)';
