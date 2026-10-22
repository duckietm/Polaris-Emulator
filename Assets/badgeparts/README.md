# Group badge parts

The standard group badge parts (`badgepart_<name>.png`), one per `firstvalue` / `secondvalue` in the
`guilds_elements` table. The client ships no parts of its own.

Copy this folder to your web server (for example `/var/www/gamedata/badgeparts/`) and point both sides at it:

- emulator: `imager.location.badgeparts` = that folder (the badge imager reads the parts from it)
- client: `badge.asset.grouparts.url` = its URL with `badgepart_%part%.png` (the client loads the parts from it)

Custom parts go in the same folder. Setup and limits: `docs/discord/group-badges-setup.md`.
