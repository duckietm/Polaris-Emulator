-- The base database seeds a catalog prefix "Staff Pick" whose text is "Staff": any player could buy a prefix that
-- poses as the hotel team, although "staff" is on the prefix blacklist. The offer is switched off, not deleted;
-- players who bought it keep it, and staff can still grant such a prefix.
UPDATE `custom_prefixes_catalog`
SET `enabled` = 0
WHERE `id` = 3 AND `display_name` = 'Staff Pick' AND `text` = 'Staff';
