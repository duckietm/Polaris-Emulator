-- Commande :afk — bascule manuelle de l'etat absent.
-- La commande fonctionne sans ces lignes (valeurs par defaut codees dans
-- AfkCommand.java) ; elles permettent de personnaliser les messages et
-- d'afficher la commande dans :commands.
-- ON DUPLICATE KEY UPDATE value=value preserve toute valeur deja modifiee.
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.keys.cmd_afk', 'afk;absent;away'),
	('commands.description.cmd_afk', ':afk'),
	('commands.generic.cmd_afk.away', 'You are now away. Type :afk to return.'),
	('commands.generic.cmd_afk.back', 'Welcome back!')
ON DUPLICATE KEY UPDATE `value` = `value`;
