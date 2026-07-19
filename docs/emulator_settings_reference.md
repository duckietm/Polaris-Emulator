# Emulator Settings Reference

## Scope

This document inventories the non-wired keys currently stored in `emulator_settings` based on `Default Database/FullDB.sql`. Wired-specific keys are documented separately in `docs/wired_tools_reference.md`.

Each entry below mirrors the comment written by `Database Updates/003_add_comment_column_to_emulator_settings.sql`, so the documentation and in-database comments stay aligned.

## Table schema

```sql
CREATE TABLE `emulator_settings` (
  `key` varchar(100) NOT NULL,
  `value` varchar(512) NOT NULL,
  `comment` text NOT NULL,
  PRIMARY KEY (`key`)
);
```

## Inventory summary

- Total non-wired keys documented here: `329`
- Source of defaults: `Default Database/FullDB.sql`
- Value type is inferred from the default string stored in SQL.

## Group index

- `allowed` (1)
- `apollyon` (1)
- `basejump` (2)
- `bots` (1)
- `bubblealerts` (6)
- `bundle` (2)
- `callback` (3)
- `camera` (11)
- `catalog` (5)
- `commands` (3)
- `console` (1)
- `custom` (1)
- `db` (4)
- `debug` (7)
- `discount` (5)
- `easter_eggs` (1)
- `enc` (4)
- `essentials` (2)
- `flood` (1)
- `ftp` (4)
- `furniture` (1)
- `gamecenter` (16)
- `gamedata` (1)
- `guardians` (5)
- `hotel` (169)
- `hotelview` (5)
- `imager` (6)
- `images` (2)
- `info` (1)
- `invisible` (1)
- `io` (3)
- `logging` (6)
- `marketplace` (1)
- `monsterplant` (2)
- `moodlight` (1)
- `navigator` (1)
- `networking` (1)
- `notify` (1)
- `path` (1)
- `pathfinder` (4)
- `pirate_parrot` (2)
- `postit` (1)
- `pyramids` (1)
- `retro` (1)
- `room` (4)
- `rosie` (2)
- `runtime` (1)
- `save` (2)
- `scripter` (1)
- `seasonal` (7)
- `subscriptions` (12)
- `team` (1)
- `youtube` (1)

## `allowed`

Validation rules for usernames and account-facing inputs.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `allowed.username.characters` | `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890-=!?@:,.` | `list` | Characters allowed when users choose or change a username. |

## `apollyon`

Custom project-specific behaviour switches.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `apollyon.cooldown.amount` | `250` | `integer` | Cooldown in milliseconds used by the Apollyon-specific behaviour or command flow. |

## `basejump`

BaseJump or FastFood launcher URLs.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `basejump.assets.url` | `http://localhost/gamecenter/gamecenter_basejump/BasicAssets.swf` | `url` | Asset URL used by the BaseJump or FastFood game client. |
| `basejump.url` | `http://localhost/game/BaseJump.swf` | `url` | SWF URL used to launch the BaseJump or FastFood game client. |

## `bots`

Miscellaneous visitor-bot display settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `bots.visitor.dateformat` | `yyyy-mm-dd HH:mm` | `string` | Date format used by visitor bots when they print timestamps. |

## `bubblealerts`

Bubble notification behaviour and assets.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `bubblealerts.enabled` | `1` | `boolean` | Master switch for bubble alert notifications. |
| `bubblealerts.notif_friendonline.enabled` | `1` | `boolean` | Enable bubble alerts when friends come online. |
| `bubblealerts.notif_friendonline.image` | `${image.library.url}notifications/figure?p=%figure%` | `template` | Image template used when showing friend-online bubble alerts. |
| `bubblealerts.notif_friendonline.useimage` | `1` | `boolean` | Use the configured figure image inside friend-online bubble alerts. |
| `bubblealerts.notif_marketplace.enabled` | `1` | `boolean` | Show bubble alerts for marketplace notifications. |
| `bubblealerts.notif_purchase.limited` | `0` | `boolean` | Show bubble alerts for limited-item purchases. |

## `bundle`

Bundle-specific toggles for pets and bots.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `bundle.bots.enabled` | `1` | `boolean` | Allow bots to be included in room bundles or package rewards. |
| `bundle.pets.enabled` | `1` | `boolean` | Allow pets to be included in room bundles or package rewards. |

## `callback`

HTTP callback integrations for external services.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `callback.get.version` | `1` | `boolean` | Enable the GET callback used to report version to external services. |
| `callback.post.errors` | `1` | `boolean` | Enable the POST callback used to report errors to external services. |
| `callback.post.statistics` | `1` | `boolean` | Enable the POST callback used to report statistics to external services. |

## `camera`

Camera costs, storage and publish settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `camera.enabled` | `1` | `boolean` | Enable the in-room camera feature. |
| `camera.extradata` | `{\"t\":%timestamp%, \"u\":\"%id%\", \"s\":%room_id%, \"w\":\"%url%\"}` | `template` | Extradata template written into camera photo items when they are created. |
| `camera.item_id` | `45970` | `integer` | Base item ID used by the generated camera photo furniture. |
| `camera.price.credits` | `2` | `integer` | Credit price charged when taking a camera photo. |
| `camera.price.points` | `0` | `boolean` | Amount of activity points charged when taking a camera photo. |
| `camera.price.points.publish` | `10` | `integer` | Amount of activity points charged when publishing a camera photo. |
| `camera.price.points.publish.type` | `0` | `boolean` | Activity point type used for the camera publish cost. |
| `camera.price.points.type` | `0` | `boolean` | Activity point type used for the camera capture cost. |
| `camera.publish.delay` | `180` | `integer` | Delay in seconds before a published camera photo becomes available. |
| `camera.url` | `http://localhost/usercontent/camera/` | `url` | Base URL where camera images are published. |
| `camera.use.https` | `1` | `boolean` | Force HTTPS when generating camera image URLs. |

## `catalog`

Catalog behaviour that is not wired-specific.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `catalog.guild.hc_required` | `1` | `boolean` | Require HC or VIP status before users can create a guild. |
| `catalog.guild.price` | `10` | `integer` | Credit cost required to create a guild. |
| `catalog.ltd.page.soldout` | `761` | `integer` | Layout or image ID used when a limited page is sold out. |
| `catalog.ltd.random` | `1` | `boolean` | Randomize the order or selection of limited catalog items. |
| `catalog.page.vipgifts` | `0` | `boolean` | Catalog page ID used for VIP gift redemption. |

## `commands`

Command-specific restrictions and compatibility flags.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `commands.cmd_chatcolor.banned_numbers` | `23;33;34` | `list` | Semicolon-separated list of chat color IDs blocked for the chatcolor command. |
| `commands.cmd_staffonline.min_rank` | `2` | `integer` | Minimum permission rank required to use the staffonline command. |
| `commands.plugins.oldstyle` | `0` | `boolean` | Use the legacy command plugin loading style. |

## `console`

Console behaviour.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `console.mode` | `1` | `boolean` | Controls the emulator console mode or console output style. |

## `custom`

Fork-specific custom behaviour switches.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `custom.stacking.enabled` | `0` | `boolean` | Enable custom item stacking behaviour outside the default stacking rules. |

## `db`

Database pooling and batching controls.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `db.max.partition.size` | `2` | `integer` | Maximum batch or partition size used by partitioned database operations. |
| `db.min.partition.size` | `1` | `boolean` | Minimum batch or partition size used by partitioned database operations. |
| `db.pool.maxsize` | `12` | `integer` | Maximum size of the database connection pool. |
| `db.pool.minsize` | `8` | `integer` | Minimum number of open connections kept in the database pool. |

## `debug`

Verbose debug output toggles.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `debug.mode` | `1` | `boolean` | Enable general emulator debug mode. |
| `debug.show.errors` | `1` | `boolean` | Show internal debug error messages. |
| `debug.show.headers` | `0` | `boolean` | Show packet headers in debug logs. |
| `debug.show.packets` | `0` | `boolean` | Print packet-level debug output. |
| `debug.show.packets.undefined` | `0` | `boolean` | Print debug output for undefined incoming or outgoing packets. |
| `debug.show.sql.exception` | `1` | `boolean` | Log SQL exceptions to the console. |
| `debug.show.users` | `1` | `boolean` | Show user-related debug messages. |

## `discount`

Discount batch rules for catalog purchases.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `discount.additional.thresholds` | `40;99` | `list` | Semicolon-separated discount thresholds used for extra batch bonuses. |
| `discount.batch.free.items` | `1` | `boolean` | Number of free items granted inside one discount batch. |
| `discount.batch.size` | `6` | `integer` | Number of items required for one discount batch. |
| `discount.bonus.min.discounts` | `1` | `boolean` | Minimum number of discount batches required before the bonus logic applies. |
| `discount.max.allowed.items` | `100` | `integer` | Maximum number of catalog items that can participate in one discount batch. |

## `easter_eggs`

Optional easter egg features.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `easter_eggs.enabled` | `1` | `boolean` | Enable or disable the feature controlled by `easter_eggs.enabled`. |

## `enc`

Encryption and RSA settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `enc.d` | `` | `string` | RSA private exponent used by the encryption layer. |
| `enc.e` | `` | `string` | RSA public exponent used by the encryption layer. |
| `enc.enabled` | `1` | `boolean` | Enable RSA encryption support for the socket handshake. |
| `enc.n` | `` | `string` | RSA modulus used by the encryption layer. |

## `essentials`

Essentials plugin or command values.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `essentials.cmd_kill.effect.killer` | `164;182` | `list` | Semicolon-separated effect IDs used by the kill command for the killer. |
| `essentials.cmd_kill.effect.victim` | `93;89` | `list` | Semicolon-separated effect IDs used by the kill command for the victim. |

## `flood`

Flood-control compatibility switches.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `flood.with.rights` | `0` | `boolean` | Allow users with room rights to bypass the normal flood protection. |

## `ftp`

FTP integration settings for generated assets.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `ftp.enabled` | `0` | `boolean` | Enable FTP uploads for generated assets. |
| `ftp.host` | `example.com` | `string` | FTP host used for asset uploads. |
| `ftp.password` | `password123` | `string` | FTP password used for asset uploads. |
| `ftp.user` | `root` | `string` | FTP username used for asset uploads. |

## `furniture`

General furniture interaction behaviour.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `furniture.talking.range` | `2` | `integer` | Maximum tile distance at which talking furniture can react to nearby speech. |

## `gamecenter`

Gamecenter launchers and theme settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `gamecenter.fastfood.apiKey` | `` | `string` | API key used by the FastFood or BaseJump integration. |
| `gamecenter.fastfood.assets` | `http://localhost/swf/c_images/gamecenter_basejump/` | `url` | Asset base URL used by the FastFood or BaseJump game client. |
| `gamecenter.fastfood.background.color` | `68bbd2` | `string` | Background color used by the FastFood launcher UI. |
| `gamecenter.fastfood.enabled` | `true` | `boolean` | Enable the FastFood or BaseJump gamecenter integration. |
| `gamecenter.fastfood.text.color` | `ffffff` | `string` | Text color used by the FastFood launcher UI. |
| `gamecenter.fastfood.theme` | `default` | `string` | Theme name used by the FastFood launcher. |
| `gamecenter.snowwar.artic.bg` | `http://localhost/swf/c_images/gamecenter_snowwar/snst_bg_1_a_big.png` | `url` | Background image used for the SnowWar Arctic map. |
| `gamecenter.snowwar.assets` | `http://localhost/swf/c_images/gamecenter_snowwar/` | `url` | Asset base URL used by the SnowWar game client. |
| `gamecenter.snowwar.dragoncave.bg` | `http://localhost/swf/c_images/gamecenter_snowwar/snst_bg_2_big.png` | `url` | Background image used for the SnowWar Dragon Cave map. |
| `gamecenter.snowwar.enabled` | `true` | `boolean` | Enable the SnowWar gamecenter integration. |
| `gamecenter.snowwar.fightnight.bg` | `http://localhost/swf/c_images/gamecenter_snowwar/snst_bg_3_noscale.png` | `url` | Background image used for the SnowWar Fight Night map. |
| `gamecenter.snowwar.game.background.color` | `93d4f3` | `string` | Background color used by the SnowWar launcher UI. |
| `gamecenter.snowwar.game.start.time` | `15` | `integer` | Countdown in seconds before a SnowWar round starts. |
| `gamecenter.snowwar.game.text.color` | `000000` | `integer` | Text color used by the SnowWar launcher UI. |
| `gamecenter.snowwar.players.min` | `2` | `integer` | Minimum number of players required to start SnowWar. |
| `gamecenter.snowwar.room.id` | `0` | `boolean` | Room ID used as the SnowWar lobby or host room. |

## `gamedata`

Remote gamedata sources.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `gamedata.figuredata.url` | `https://habbo.com/gamedata/figuredata/0` | `url` | Remote figuredata URL used when the hotel loads avatar figure definitions. |

## `guardians`

Guardians and report-review settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `guardians.accept.timer` | `90` | `integer` | Time in seconds that guardians have to accept a case. |
| `guardians.maximum.guardians.total` | `10` | `integer` | Maximum number of guardians that can be assigned to one case. |
| `guardians.maximum.resends` | `2` | `integer` | Maximum number of times an unanswered guardian case can be resent. |
| `guardians.minimum.votes` | `5` | `integer` | Minimum number of guardian votes required to resolve a case. |
| `guardians.reporting.cooldown` | `900` | `integer` | Cooldown in seconds before the same user can open a new guardian report. |

## `hotel`

Core hotel gameplay, economy, room, catalog and moderation settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `hotel.alert.oldstyle` | `0` | `boolean` | Use the legacy generic alert window style. |
| `hotel.allow.ignore.staffs` | `1` | `boolean` | Allow users to ignore staff accounts. |
| `hotel.auto.credits.amount` | `100` | `integer` | Amount of credits granted on each automatic payout. |
| `hotel.auto.credits.enabled` | `1` | `boolean` | Enable automatic credits payouts. |
| `hotel.auto.credits.hc_modifier` | `1` | `boolean` | Multiplier applied to automatic credits payouts for HC users. |
| `hotel.auto.credits.ignore.hotelview` | `1` | `boolean` | Skip users staying in hotel view when giving automatic credits payouts. |
| `hotel.auto.credits.ignore.idled` | `0` | `boolean` | Skip idle users when giving automatic credits payouts. |
| `hotel.auto.credits.interval` | `600` | `integer` | Interval in seconds between automatic credits payouts. |
| `hotel.auto.gotwpoints.enabled` | `0` | `boolean` | Enable automatic gotwpoints payouts. |
| `hotel.auto.gotwpoints.hc_modifier` | `1` | `boolean` | Multiplier applied to automatic gotwpoints payouts for HC users. |
| `hotel.auto.gotwpoints.ignore.hotelview` | `1` | `boolean` | Skip users staying in hotel view when giving automatic gotwpoints payouts. |
| `hotel.auto.gotwpoints.ignore.idled` | `1` | `boolean` | Skip idle users when giving automatic gotwpoints payouts. |
| `hotel.auto.gotwpoints.interval` | `600` | `integer` | Interval in seconds between automatic gotwpoints payouts. |
| `hotel.auto.gotwpoints.name` | `shell` | `string` | Internal currency name used by the automatic gotwpoints payout. |
| `hotel.auto.gotwpoints.type` | `4` | `integer` | Currency type ID used by the automatic gotwpoints payout. |
| `hotel.auto.pixels.amount` | `100` | `integer` | Amount of pixels granted on each automatic payout. |
| `hotel.auto.pixels.enabled` | `1` | `boolean` | Enable automatic pixels payouts. |
| `hotel.auto.pixels.hc_modifier` | `1` | `boolean` | Multiplier applied to automatic pixels payouts for HC users. |
| `hotel.auto.pixels.ignore.hotelview` | `1` | `boolean` | Skip users staying in hotel view when giving automatic pixels payouts. |
| `hotel.auto.pixels.ignore.idled` | `1` | `boolean` | Skip idle users when giving automatic pixels payouts. |
| `hotel.auto.pixels.interval` | `600` | `integer` | Interval in seconds between automatic pixels payouts. |
| `hotel.auto.points.amount` | `5` | `integer` | Amount of points granted on each automatic payout. |
| `hotel.auto.points.enabled` | `1` | `boolean` | Enable automatic points payouts. |
| `hotel.auto.points.hc_modifier` | `1` | `boolean` | Multiplier applied to automatic points payouts for HC users. |
| `hotel.auto.points.ignore.hotelview` | `0` | `boolean` | Skip users staying in hotel view when giving automatic points payouts. |
| `hotel.auto.points.ignore.idled` | `0` | `boolean` | Skip idle users when giving automatic points payouts. |
| `hotel.auto.points.interval` | `600` | `integer` | Interval in seconds between automatic points payouts. |
| `hotel.banzai.points.tile.fill` | `0` | `boolean` | Configuration value used by `hotel.banzai.points.tile.fill`. |
| `hotel.banzai.points.tile.lock` | `1` | `boolean` | Configuration value used by `hotel.banzai.points.tile.lock`. |
| `hotel.banzai.points.tile.steal` | `0` | `boolean` | Configuration value used by `hotel.banzai.points.tile.steal`. |
| `hotel.bot.butler.commanddistance` | `5` | `integer` | Maximum tile distance from which a butler bot accepts commands. |
| `hotel.bot.butler.servedistance` | `5` | `integer` | Maximum tile distance from which a butler bot can serve requests. |
| `hotel.bot.chat.minimum.interval` | `5` | `integer` | Minimum number of seconds between bot chat lines. |
| `hotel.bot.max.chatdelay` | `604800` | `integer` | Maximum bot chat delay allowed when configuring scripted speech. |
| `hotel.bot.max.chatlength` | `120` | `integer` | Maximum number of characters allowed in bot chat lines. |
| `hotel.bot.max.namelength` | `15` | `integer` | Maximum number of characters allowed in bot names. |
| `hotel.bots.max.inventory` | `25` | `integer` | Maximum number of bots allowed in one inventory. |
| `hotel.bots.max.room` | `10` | `integer` | Maximum number of bots allowed in one room. |
| `hotel.calendar.default` | `test` | `string` | Default calendar campaign name or identifier. |
| `hotel.calendar.enabled` | `0` | `boolean` | Enable the hotel calendar feature. |
| `hotel.calendar.pixels.hc_modifier` | `2.0` | `number` | Multiplier applied to calendar pixel rewards for HC users. |
| `hotel.calendar.starttimestamp` | `1593561600` | `integer` | Unix timestamp used as the calendar start date. |
| `hotel.catalog.discounts.amount` | `6` | `integer` | Number of discount slots or discount batches shown by the catalog. |
| `hotel.catalog.items.display.ordernum` | `1` | `boolean` | Respect catalog item order numbers when rendering pages. |
| `hotel.catalog.ltd.limit.enabled` | `1` | `boolean` | Enable daily purchase limits for limited catalog items. |
| `hotel.catalog.purchase.cooldown` | `1` | `boolean` | Cooldown in seconds between catalog purchases. |
| `hotel.catalog.recycler.enabled` | `1` | `boolean` | Enable the catalog recycler feature. |
| `hotel.chat.max.length` | `100` | `integer` | Maximum number of characters allowed in one public chat message. |
| `hotel.daily.respect` | `3` | `integer` | Daily amount of respect points available for users. |
| `hotel.daily.respect.pets` | `3` | `integer` | Daily amount of pet respect points available for users. |
| `hotel.ecotron.enabled` | `1` | `boolean` | Enable or disable the feature controlled by `hotel.ecotron.enabled`. |
| `hotel.ecotron.rarity.chance.1` | `1` | `boolean` | Configuration value used by `hotel.ecotron.rarity.chance.1`. |
| `hotel.ecotron.rarity.chance.2` | `4` | `integer` | Configuration value used by `hotel.ecotron.rarity.chance.2`. |
| `hotel.ecotron.rarity.chance.3` | `40` | `integer` | Configuration value used by `hotel.ecotron.rarity.chance.3`. |
| `hotel.ecotron.rarity.chance.4` | `200` | `integer` | Configuration value used by `hotel.ecotron.rarity.chance.4`. |
| `hotel.ecotron.rarity.chance.5` | `2000` | `integer` | Configuration value used by `hotel.ecotron.rarity.chance.5`. |
| `hotel.flood.mute.time` | `30` | `integer` | Mute duration in seconds applied by the hotel flood protection. |
| `hotel.floorplan.max.totalarea` | `4096` | `integer` | Maximum total floorplan area allowed for custom rooms. |
| `hotel.floorplan.max.widthlength` | `64` | `integer` | Maximum floorplan width or length allowed for custom rooms. |
| `hotel.freeze.onfreeze.loose.explosionboost` | `3` | `integer` | Number of explosion boosts lost when a player gets frozen. |
| `hotel.freeze.onfreeze.loose.snowballs` | `5` | `integer` | Number of snowballs lost when a player gets frozen. |
| `hotel.freeze.onfreeze.time.frozen` | `5` | `integer` | Time in seconds a player remains frozen. |
| `hotel.freeze.points.block` | `1` | `boolean` | Score awarded for blocking tiles in Freeze. |
| `hotel.freeze.points.effect` | `3` | `integer` | Score awarded for using Freeze effects or power-up actions. |
| `hotel.freeze.points.freeze` | `10` | `integer` | Score awarded for freezing another player in Freeze. |
| `hotel.freeze.powerup.chance` | `33` | `integer` | Chance for Freeze power-ups to spawn. |
| `hotel.freeze.powerup.max.lives` | `3` | `integer` | Maximum number of extra lives granted by a Freeze power-up. |
| `hotel.freeze.powerup.max.snowballs` | `5` | `integer` | Maximum number of extra snowballs granted by a Freeze power-up. |
| `hotel.freeze.powerup.protection.stack` | `1` | `boolean` | Allow Freeze protection power-ups to stack. |
| `hotel.freeze.powerup.protection.time` | `10` | `integer` | Protection time in seconds after receiving a Freeze protection power-up. |
| `hotel.friendcategory` | `0` | `boolean` | Default friend category ID assigned to new friends. |
| `hotel.furni.gym.achievement.olympics_c16_crosstrainer` | `CrossTrainer` | `string` | Configuration value used by `hotel.furni.gym.achievement.olympics_c16_crosstrainer`. |
| `hotel.furni.gym.achievement.olympics_c16_trampoline` | `Trampolinist` | `string` | Configuration value used by `hotel.furni.gym.achievement.olympics_c16_trampoline`. |
| `hotel.furni.gym.achievement.olympics_c16_treadmill` | `Jogger` | `string` | Configuration value used by `hotel.furni.gym.achievement.olympics_c16_treadmill`. |
| `hotel.furni.gym.forcerot.olympics_c16_crosstrainer` | `1` | `boolean` | Configuration value used by `hotel.furni.gym.forcerot.olympics_c16_crosstrainer`. |
| `hotel.furni.gym.forcerot.olympics_c16_trampoline` | `0` | `boolean` | Configuration value used by `hotel.furni.gym.forcerot.olympics_c16_trampoline`. |
| `hotel.furni.gym.forcerot.olympics_c16_treadmill` | `1` | `boolean` | Configuration value used by `hotel.furni.gym.forcerot.olympics_c16_treadmill`. |
| `hotel.gifts.box_types` | `0,1,2,3,4,5,6,8` | `list` | Comma-separated list of gift box type IDs allowed in the catalog. |
| `hotel.gifts.length.max` | `300` | `integer` | Maximum message length allowed on gift notes. |
| `hotel.gifts.ribbon_types` | `0,1,2,3,4,5,6,7,8,9,10` | `list` | Comma-separated list of ribbon type IDs allowed in the catalog. |
| `hotel.gifts.special.price` | `10` | `integer` | Credit price used by special gift boxes. |
| `hotel.home.room` | `0` | `boolean` | Room ID used as the default home room for new users. |
| `hotel.inventory.max.items` | `7500` | `integer` | Maximum number of items allowed in one inventory. |
| `hotel.item.trap.hween14_rare2` | `3000` | `integer` | Configuration value used by `hotel.item.trap.hween14_rare2`. |
| `hotel.item.trap.hween_c17_handstrap` | `3000` | `integer` | Configuration value used by `hotel.item.trap.hween_c17_handstrap`. |
| `hotel.item.trap.hween_c17_spiketrap` | `3000` | `integer` | Configuration value used by `hotel.item.trap.hween_c17_spiketrap`. |
| `hotel.item.trap.pirate_sandtrap` | `3000` | `integer` | Configuration value used by `hotel.item.trap.pirate_sandtrap`. |
| `hotel.jukebox.limit.large` | `20` | `integer` | Track limit used by large jukebox furniture. |
| `hotel.jukebox.limit.normal` | `10` | `integer` | Track limit used by normal jukebox furniture. |
| `hotel.log.chat` | `1` | `boolean` | Enable logging for chat. |
| `hotel.log.chat.private` | `1` | `boolean` | Enable logging for chat private. |
| `hotel.log.room.enter` | `1` | `boolean` | Enable logging for room enter. |
| `hotel.log.trades` | `1` | `boolean` | Enable logging for trades. |
| `hotel.marketplace.currency` | `0` | `boolean` | Currency type used for marketplace prices and taxes. |
| `hotel.marketplace.enabled` | `1` | `boolean` | Enable or disable the feature controlled by `hotel.marketplace.enabled`. |
| `hotel.max.bots.room` | `10` | `integer` | Maximum number of bots allowed in one room. |
| `hotel.max.duckets` | `9000000` | `integer` | Maximum amount of duckets a user can hold. |
| `hotel.messenger.offline.messaging.enabled` | `1` | `boolean` | Enable or disable the feature controlled by `hotel.messenger.offline.messaging.enabled`. |
| `hotel.messenger.search.maxresults` | `50` | `integer` | Maximum number of results returned by messenger user searches. |
| `hotel.name` | `Habbo Hotel` | `string` | Public hotel name shown across the client and outgoing messages. |
| `hotel.navigator.camera` | `1` | `boolean` | Enable navigator room previews or camera mode. |
| `hotel.navigator.owner` | `HabboHotel` | `string` | Default owner name displayed by the navigator. |
| `hotel.navigator.popular.amount` | `35` | `integer` | Number of rooms shown in the popular rooms list. |
| `hotel.navigator.popular.category.maxresults` | `10` | `integer` | Maximum number of rooms shown per popular category. |
| `hotel.navigator.popular.listtype` | `1` | `boolean` | List type used for the popular rooms tab. |
| `hotel.navigator.populartab.publics` | `0` | `boolean` | Include public rooms inside the popular rooms tab. |
| `hotel.navigator.search.maxresults` | `35` | `integer` | Maximum number of results returned by navigator searches. |
| `hotel.navigator.sort.ordernum` | `1` | `boolean` | Respect order numbers when sorting navigator results. |
| `hotel.navigator.staffpicks.categoryid` | `1` | `boolean` | Category ID used for the staff picks tab. |
| `hotel.nux.gifts.enabled` | `0` | `boolean` | Enable the NUX gift flow for new users. |
| `hotel.pets.max.inventory` | `25` | `integer` | Maximum number of pets allowed in one inventory. |
| `hotel.pets.max.room` | `10` | `integer` | Maximum number of pets allowed in one room. |
| `hotel.pets.name.length.max` | `15` | `integer` | Maximum pet name length. |
| `hotel.pets.name.length.min` | `3` | `integer` | Minimum pet name length. |
| `hotel.player.name` | `Habbo` | `string` | Generic player label used by text templates and client messages. |
| `hotel.purchase.ltd.limit.daily.item` | `3` | `integer` | Maximum number of the same limited item a user can buy per day. |
| `hotel.purchase.ltd.limit.daily.total` | `10` | `integer` | Maximum number of limited items a user can buy per day across all limited sales. |
| `hotel.refill.daily` | `86400` | `integer` | Cooldown in seconds before daily counters such as respect are refilled. |
| `hotel.rollers.speed.maximum` | `100` | `integer` | Maximum roller delay or speed value accepted by roller furniture. |
| `hotel.room.enter.logs` | `1` | `boolean` | Enable room-entry logs. |
| `hotel.room.floorplan.check.enabled` | `1` | `boolean` | Validate custom floorplans before rooms are saved. |
| `hotel.room.furni.max` | `2500` | `integer` | Maximum amount of furniture allowed in one room. |
| `hotel.room.nooblobby` | `3` | `integer` | Room ID used as the newbie lobby. |
| `hotel.room.public.doortile.kick` | `0` | `boolean` | Kick users who stand on public room door tiles. |
| `hotel.room.rollers.norules` | `0` | `boolean` | Allow rollers to ignore normal placement rules. |
| `hotel.room.rollers.roll_avatars.max` | `1` | `boolean` | Maximum number of avatars that rollers can move at once. |
| `hotel.room.stickies.max` | `200` | `integer` | Maximum number of sticky notes allowed in one room. |
| `hotel.room.stickypole.prefix` | `%timestamp%, %username%:\\r` | `template` | Prefix template written by sticky pole furniture. |
| `hotel.room.tags.staff` | `staff;official;habbo` | `list` | Semicolon-separated staff room tags. |
| `hotel.rooms.auto.idle` | `1` | `boolean` | Allow empty rooms to switch into the idle state automatically. |
| `hotel.rooms.deco_hosting` | `1` | `boolean` | Enable decoration-hosting features for rooms. |
| `hotel.rooms.handitem.time` | `100` | `integer` | Time in seconds before temporary hand items are cleared. |
| `hotel.rooms.max.favorite` | `30` | `integer` | Maximum number of favorite rooms allowed per user. |
| `hotel.roomuser.idle.cycles` | `300` | `integer` | Idle cycle count before a room user is marked idle. |
| `hotel.roomuser.idle.cycles.kick` | `900` | `integer` | Idle cycle count before a room user is kicked for idling. |
| `hotel.roomuser.idle.not_dancing.ignore.wired_idle` | `0` | `boolean` | Ignore the wired idle status when checking the room idle rule. |
| `hotel.sanctions.enabled` | `1` | `boolean` | Enable the sanctions system. |
| `hotel.shop.discount.modifier` | `6` | `integer` | Modifier used by the shop discount calculation. |
| `hotel.talenttrack.enabled` | `1` | `boolean` | Enable the talent track feature. |
| `hotel.targetoffer.id` | `1` | `boolean` | Offer ID requested when the client asks for a targeted offer. |
| `hotel.teleport.locked.allowed` | `1` | `boolean` | Allow users to use teleports inside locked rooms when they otherwise qualify. |
| `hotel.trading.enabled` | `1` | `boolean` | Enable room trading. |
| `hotel.trading.requires.perk` | `0` | `boolean` | Require the trading perk before users may trade. |
| `hotel.trophies.length.max` | `300` | `integer` | Maximum value used by `hotel.trophies.length.max`. |
| `hotel.users.clothingvalidation.onchangelooks` | `0` | `boolean` | Run clothing validation when the related action occurs: onchangelooks. |
| `hotel.users.clothingvalidation.onfballgate` | `0` | `boolean` | Run clothing validation when the related action occurs: onfballgate. |
| `hotel.users.clothingvalidation.onhcexpired` | `0` | `boolean` | Run clothing validation when the related action occurs: onhcexpired. |
| `hotel.users.clothingvalidation.onlogin` | `0` | `boolean` | Run clothing validation when the related action occurs: onlogin. |
| `hotel.users.clothingvalidation.onmannequin` | `0` | `boolean` | Run clothing validation when the related action occurs: onmannequin. |
| `hotel.users.clothingvalidation.onmimic` | `0` | `boolean` | Run clothing validation when the related action occurs: onmimic. |
| `hotel.users.max.friends` | `300` | `integer` | Maximum number of friends allowed for normal users. |
| `hotel.users.max.friends.hc` | `1100` | `integer` | Maximum number of friends allowed for HC users. |
| `hotel.users.max.rooms` | `50` | `integer` | Maximum number of rooms allowed for normal users. |
| `hotel.users.max.rooms.hc` | `75` | `integer` | Maximum number of rooms allowed for HC users. |
| `hotel.view.ltdcountdown.enabled` | `1` | `boolean` | Enable the limited-countdown hotel-view widget. |
| `hotel.view.ltdcountdown.itemid` | `10388` | `integer` | Item ID shown by the limited-countdown widget. |
| `hotel.view.ltdcountdown.itemname` | `trophy_netsafety_0` | `string` | Item name shown by the limited-countdown widget. |
| `hotel.view.ltdcountdown.pageid` | `13` | `integer` | Catalog page ID linked by the limited-countdown widget. |
| `hotel.view.ltdcountdown.timestamp` | `1519496132` | `integer` | Unix timestamp used by the limited-countdown widget. |
| `hotel.welcome.alert.delay` | `10000` | `integer` | Delay in milliseconds before the welcome alert is shown. |
| `hotel.welcome.alert.enabled` | `0` | `boolean` | Enable the welcome alert shown after login. |
| `hotel.welcome.alert.message` | `Welcome to Habbo Hotel %user%!` | `template` | Message template used by the welcome alert. |
| `hotel.welcome.alert.oldstyle` | `0` | `boolean` | Use the legacy welcome alert window style. |
| `hotel.wordfilter.automute` | `1` | `boolean` | Mute duration in minutes applied when word-filter automute is triggered. |
| `hotel.wordfilter.enabled` | `1` | `boolean` | Enable the word filter system. |
| `hotel.wordfilter.messenger` | `1` | `boolean` | Apply the word filter to messenger messages. |
| `hotel.wordfilter.normalise` | `1` | `boolean` | Normalise text before checking it against the word filter. |
| `hotel.wordfilter.replacement` | `bobba` | `string` | Replacement word used when text is censored. |
| `hotel.wordfilter.rooms` | `1` | `boolean` | Apply the word filter to room chat. |

## `hotelview`

Hotel-view widgets and promotional data.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `hotelview.halloffame.query` | `SELECT users.look, users.username, users.id, users_settings.hof_points FROM users_settings INNER JOIN users ON users_settings.user_id = users.id WHERE hof_points > 0 ORDER BY hof_points DESC, users.id ASC LIMIT 10` | `sql` | SQL query used to populate the hotel-view hall of fame panel. |
| `hotelview.promotional.points` | `100` | `integer` | Amount of activity points awarded by the hotel-view promotion. |
| `hotelview.promotional.points.type` | `5` | `integer` | Activity point type used by the hotel-view promotional reward. |
| `hotelview.promotional.reward.id` | `11043` | `integer` | Base item ID used by the hotel-view promotional reward. |
| `hotelview.promotional.reward.name` | `bonusbag20_2` | `string` | Public item name used by the hotel-view promotional reward. |

## `imager`

Internal image generator paths and URLs.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `imager.internal.enabled` | `1` | `boolean` | Generate images locally instead of relying on an external imager service. |
| `imager.location.badgeparts` | `/var/www/testhotel/Cosmic/public/usercontent/badgeparts` | `string` | Filesystem path where badge part assets are stored. |
| `imager.location.output.badges` | `/var/www/testhotel/Cosmic/public/usercontent/badgeparts/generated/` | `string` | Filesystem output path for generated badges. |
| `imager.location.output.camera` | `/var/www/testhotel/Cosmic/public/usercontent/camera/` | `string` | Filesystem output path for saved camera photos. |
| `imager.location.output.thumbnail` | `/var/www/testhotel/Cosmic/public/usercontent/camera/thumbnail/` | `string` | Filesystem output path for generated camera thumbnails. |
| `imager.url.youtube` | `imager.php?url=http://img.youtube.com/vi/%video%/default.jpg` | `template` | Template URL used to fetch YouTube thumbnails. |

## `images`

Static client image path helpers.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `images.gamecenter.basejump` | `c_images/gamecenter_basejump/` | `string` | Client asset path used for the basejump gamecenter images. |
| `images.gamecenter.snowwar` | `c_images/gamecenter_snowwar/` | `string` | Client asset path used for the snowwar gamecenter images. |

## `info`

Global information panel toggle.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `info.shown` | `1` | `boolean` | Show the hotel information panel or startup information message. |

## `invisible`

Invisible-mode behaviour.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `invisible.prevent.chat` | `0` | `boolean` | Prevent invisible users from speaking in rooms. |

## `io`

Socket and Netty threading settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `io.bossgroup.threads` | `1` | `boolean` | Number of Netty boss-group threads used by the socket server. |
| `io.client.multithreaded.handler` | `1` | `boolean` | Handle incoming client packets with a multi-threaded pipeline. |
| `io.workergroup.threads` | `5` | `integer` | Number of Netty worker-group threads used by the socket server. |

## `logging`

Structured emulator logging toggles.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `logging.debug` | `0` | `boolean` | Enable extra debug logging in the emulator logger. |
| `logging.errors.packets` | `0` | `boolean` | Log packet parsing errors. |
| `logging.errors.runtime` | `1` | `boolean` | Log runtime exceptions. |
| `logging.errors.sql` | `1` | `boolean` | Log SQL errors. |
| `logging.packets` | `0` | `boolean` | Log packet traffic in the standard logger. |
| `logging.packets.undefined` | `0` | `boolean` | Log undefined packets in the standard logger. |

## `marketplace`

Marketplace compatibility flag.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `marketplace.enabled` | `1` | `boolean` | Global switch for the marketplace subsystem. |

## `monsterplant`

Monster plant seed item mapping.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `monsterplant.seed.item_id` | `4582` | `integer` | Configuration value used by `monsterplant.seed.item_id`. |
| `monsterplant.seed_rare.item_id` | `4604` | `integer` | Configuration value used by `monsterplant.seed_rare.item_id`. |

## `moodlight`

Moodlight validation switches.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `moodlight.color_check.enabled` | `1` | `boolean` | Validate moodlight color values before applying them. |

## `navigator`

Navigator static definitions.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `navigator.eventcategories` | `1,Hottest Events,false;2,Parties & Music,true;3,Role Play,true;4,Help Desk,true;5,Trading,true;6,Games,true;7,Debates & Discussions,true;8,Grand Openings,true;9,Friending,true;10,Jobs,true;11,Group Events,true` | `list` | Semicolon-separated navigator event category definitions shown in the events tab. |

## `networking`

Low-level networking compatibility switches.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `networking.tcp.proxy` | `0` | `boolean` | Enable TCP proxy-aware networking behaviour. |

## `notify`

Server-side notification automation.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `notify.staff.chat.auto.report` | `1` | `boolean` | Automatically notify staff when a chat report is created. |

## `path`

Asset path helpers.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `path.furniture.icons` | `${image.library.url}/icons/` | `template` | Base path used by the client to load furniture icon assets. |

## `pathfinder`

Pathfinder safety and performance settings.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `pathfinder.execution_time.milli` | `25` | `integer` | Maximum pathfinder execution time in milliseconds before aborting. |
| `pathfinder.max_execution_time.enabled` | `1` | `boolean` | Enforce the pathfinder execution time limit. |
| `pathfinder.step.allow.falling` | `1` | `boolean` | Allow the pathfinder to walk down falling steps. |
| `pathfinder.step.maximum.height` | `1.1` | `number` | Maximum height difference the pathfinder may step onto. |

## `pirate_parrot`

Pirate parrot text and bubble behaviour.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `pirate_parrot.message.bubble` | `28` | `integer` | Chat bubble style ID used by the pirate parrot. |
| `pirate_parrot.message.count` | `6` | `integer` | Number of predefined messages available to the pirate parrot. |

## `postit`

Post-it constraints.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `postit.charlimit` | `366` | `integer` | Maximum number of characters allowed on post-it notes. |

## `pyramids`

Pyramids minigame timing.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `pyramids.max.delay` | `18` | `integer` | Maximum delay allowed in the Pyramids minigame or puzzle timing. |

## `retro`

Retro compatibility switches.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `retro.style.homeroom` | `1` | `boolean` | Use retro-style home room behaviour in the navigator or onboarding flow. |

## `room`

Generic room chat and promotion behaviour.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `room.chat.delay` | `0` | `boolean` | Extra room chat delay applied before users can speak again. |
| `room.chat.mutearea.allow_whisper` | `1` | `boolean` | Allow whispering while a user stands inside a mute area. |
| `room.chat.prefix.format` | `[<font color=\"%color%\">%prefix%</font>] ` | `string` | HTML or text format used for room chat prefixes. |
| `room.promotion.badge` | `RADZZ` | `string` | Badge code displayed on promoted rooms. |

## `rosie`

Rosie-related client notifications and purchase currency.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `rosie.bubble.image.url` | `${image.library.url}notifications/generic.png` | `template` | Image used by Rosie bubble notifications. |
| `rosie.buyroom.currency.type` | `5` | `integer` | Currency type used by Rosie when buying a room or room package. |

## `runtime`

Executor and thread sizing.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `runtime.threads` | `8` | `integer` | Configuration value used by `runtime.threads`. |

## `save`

Chat persistence toggles.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `save.private.chats` | `1` | `boolean` | Configuration value used by `save.private.chats`. |
| `save.room.chats` | `1` | `boolean` | Configuration value used by `save.room.chats`. |

## `scripter`

Scripter or modtool integration.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `scripter.modtool.tickets` | `1` | `boolean` | Expose moderation tickets to the scripter or automation tooling. |

## `seasonal`

Seasonal currency mapping.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `seasonal.currency.diamond` | `5` | `integer` | Currency type ID used for diamonds. |
| `seasonal.currency.ducket` | `0` | `boolean` | Currency type ID used for duckets. |
| `seasonal.currency.names` | `ducket;pixel;shell;diamond` | `list` | Semicolon-separated display names for seasonal currency types. |
| `seasonal.currency.pixel` | `0` | `boolean` | Currency type ID used for pixels. |
| `seasonal.currency.shell` | `4` | `integer` | Currency type ID used for shells. |
| `seasonal.primary.type` | `5` | `integer` | Primary seasonal currency type ID. |
| `seasonal.types` | `0;1;2;3;4;5;101;102;103;104;105` | `list` | Semicolon-separated list of currency type IDs treated as seasonal currencies. |

## `subscriptions`

HC scheduler, payday and discount configuration.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `subscriptions.hc.achievement` | `VipHC` | `string` | Achievement code granted for the HC subscription tier. |
| `subscriptions.hc.discount.days_before_end` | `7` | `integer` | Number of days before expiry when HC discount offers become available. |
| `subscriptions.hc.discount.enabled` | `1` | `boolean` | Enable discounted HC renewal offers. |
| `subscriptions.hc.payday.creditsspent_reset_on_expire` | `1` | `boolean` | Reset tracked credits spent when the HC subscription expires. |
| `subscriptions.hc.payday.currency` | `credits` | `string` | Currency rewarded by the HC payday system. |
| `subscriptions.hc.payday.enabled` | `1` | `boolean` | Enable the HC payday reward system. |
| `subscriptions.hc.payday.interval` | `1 month` | `string` | Date interval used between HC payday reward runs. |
| `subscriptions.hc.payday.next_date` | `2020-10-15 00:00:00` | `string` | Next scheduled execution date for HC payday rewards. |
| `subscriptions.hc.payday.percentage` | `10` | `integer` | Percentage of eligible spending returned by HC payday. |
| `subscriptions.hc.payday.streak` | `7=5;30=10;60=15;90=20;180=25;365=30` | `list` | Semicolon-separated streak thresholds and rewards for HC payday. |
| `subscriptions.scheduler.enabled` | `1` | `boolean` | Enable the subscription background scheduler. |
| `subscriptions.scheduler.interval` | `10` | `integer` | Interval in minutes between subscription scheduler runs. |

## `trax`

Trax song editor: users compose their own jukebox songs.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `trax.editor.enabled` | `1` | `boolean` | Master switch for the Trax song editor. |
| `trax.editor.max_songs` | `5` | `integer` | How many own songs a user may own at once. |
| `trax.editor.song.cost.currency` | `5` | `integer` | Currency charged for a new song: `-1` = credits, otherwise a `users_currency` type (0 = duckets, 5 = diamonds). |
| `trax.editor.song.cost.amount` | `25` | `integer` | Price of one new song in the configured currency; `0` = free. |
| `trax.editor.disk.base_item` | `0` | `integer` | `items_base` id used for the delivered music disc; `0` = first base item with interaction_type `musicdisc`. |

## `team`

Compatibility markers for team or wired integrations.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `team.wired.update.rc-1` | `DO NOT REMOVE THIS SETTING!` | `string` | Compatibility marker used by the custom team wired implementation. Do not remove. |

## `youtube`

YouTube integration credentials.

| Key | Default value | Type | Purpose |
|---|---|---|---|
| `youtube.apikey` | `` | `string` | API key used by the YouTube integration. |

