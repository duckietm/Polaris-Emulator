-- Navigator search filters - companion to the gameserver fix for the catalog
-- 'Find groups to join!' button (navigator/search/hotel_view/group:).

INSERT IGNORE INTO `navigator_filter` (`key`, `field`, `compare`, `database_query`) VALUES
('anything', 'getName', 'contains', 'SELECT rooms.* FROM rooms WHERE rooms.name LIKE ?'),
('roomname', 'getName', 'contains', 'SELECT rooms.* FROM rooms WHERE rooms.name LIKE ?'),
('owner', 'getOwnerName', 'equals_ignore_case', 'SELECT rooms.* FROM rooms WHERE rooms.owner_name LIKE ?'),
('tag', 'getTags', 'contains', 'SELECT rooms.* FROM rooms WHERE rooms.tags LIKE ?'),
('group', 'getGuildName', 'contains', 'SELECT rooms.* FROM rooms INNER JOIN guilds ON guilds.room_id = rooms.id WHERE guilds.name LIKE ?');
