-- The client ships badge part images that guilds_elements never listed, so the
-- badge editor could not offer them and badges using them would not render:
--   badgepart_symbol_omie.png      -> symbol 'omie'
--   badgepart_base_transparent.png -> transparent base
--
-- Ids continue after the current maximum per type (symbol 212, base 28). The
-- badge code wire format supports part ids up to 999 (GuildBadgeBuilder).
--
-- INSERT IGNORE keeps any row a hotel has already added under the same id/type,
-- so the migration is safe to replay.
--
-- Hotels running the internal badge imager (imager.internal.enabled) also need
-- the matching badgepart_*.png files in imager.location.badgeparts, otherwise
-- the imager logs "Missing Badge Part" for these two parts.
INSERT IGNORE INTO `guilds_elements` (`id`, `firstvalue`, `secondvalue`, `type`, `enabled`) VALUES
    (213, 'symbol_omie.gif', '', 'symbol', '1'),
    (29, 'base_transparent.gif', '', 'base', '1');
