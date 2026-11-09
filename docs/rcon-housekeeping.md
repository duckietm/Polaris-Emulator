# RCON housekeeping contract (All-in-1 Manager)

The RCON commands the **All-in-1 Manager** desktop tool uses to do the hotel's
housekeeping without the in-game panel:

| Key | Class | Purpose |
| --- | ----- | ------- |
| [`housekeeping`](#housekeeping) | `polaris.messages.rcon.Housekeeping` | The housekeeping panel's actions, picked by `data.action` |
| [`banuser`](#banuser) | `polaris.messages.rcon.BanUser` | Ban a user (account, IP, machine or super ban) |
| [`unbanuser`](#unbanuser) | `polaris.messages.rcon.UnbanUser` | Lift every active ban of a user |
| [`reloadcontent`](#reloadcontent) | `polaris.messages.rcon.ReloadContent` | Reload one kind of hotel content |

This document is the contract the Manager is built against. Field names, value
ranges, error keys and reply shapes below are exact.

## Transport and envelope

One JSON object per TCP connection to `rcon.host:rcon.port` (the address must be
in `rcon.allowed`, default `127.0.0.1`); the server answers with one JSON object
and closes the connection.

```json
{ "key": "housekeeping", "data": { "action": "user.kick", "user_id": 12, "reason": "Spam" } }
```

```json
{ "status": 0, "message": "" }
```

- `key` is matched case-insensitively with underscores removed (`ban_user` = `banuser`).
- `data` must be an object; send `{}` when a command or action takes no field.
  Unknown fields are ignored. A field of the wrong JSON type fails the whole
  request with status 4 `command failed`.
- The same commands are reachable over the CMS HTTP API (`POST /api/cms/command`)
  for a key scoped to them: `housekeeping` is in scope group `housekeeping`,
  `banuser`/`unbanuser` in `moderation`, `reloadcontent` in `cache`.

### Status codes

| `status` | Name | Meaning |
| -------- | ---- | ------- |
| 0 | `STATUS_OK` | Done. `message` is `""`, or the JSON document of a query action |
| 1 | `STATUS_ERROR` | Refused: invalid input, a rule of the action, unknown action/target |
| 2 | `USER_NOT_FOUND` | The user (or the new room owner) does not exist |
| 3 | `ROOM_NOT_FOUND` | The room does not exist (or, for `hotel.alert` to a room, is not loaded) |
| 4 | `SYSTEM_ERROR` | A database write, a hash or a reload failed, or the command threw |

Envelope-level replies that can come back for any key: `{"status":1,"message":"invalid request"}`
(no `key`/`data`, or not JSON), `{"status":1,"message":"unknown command"}`,
`{"status":1,"message":"rate limited"}` (`rcon.rate_limit.*`), and
`{"status":4,"message":"command failed"}` (the payload could not be parsed or the
command crashed). A request larger than `rcon.max_payload_bytes` is refused.

### The system operator

RCON has no logged-in user. Every action here runs as the **system operator**:

- audit log (`housekeeping_log`): `actor_id` **0**, `actor_name` **`All-in-1 Manager`**,
  detail suffixed with `ip=rcon`;
- the rank-of-operator guards of the panel are skipped (RCON is root-level): any
  user, room owner, rank or permission may be acted on, and `hotel.lockdown` does not
  need the top rank. The emergency lockdown and the panel permissions do not apply
  to RCON;
- every input check (lengths, ranges, existence) and every side effect of the panel
  handler is kept: alerts to the target, leaving rooms, live updates of online users,
  database writes, broadcasts, reloads;
- staff notes written by RCON carry author id 0 and author name `All-in-1 Manager`;
  a hotel alert is signed `-All-in-1 Manager`; bans carry staff id 0; tickets taken
  by RCON show moderator `All-in-1 Manager` (id 0).

Each `housekeeping` action calls the same static core as its panel handler (named
below), so both stay identical.

---

## `housekeeping`

`data.action` (string, required, exact and case-sensitive) picks the action; the
other fields of `data` are its arguments. Integer fields left out are `0`, string
fields left out are `null` (treated as empty unless stated otherwise).

| Field | Type | Used by |
| ----- | ---- | ------- |
| `action` | string | all (required) |
| `user_id` | int | `user.*` |
| `room_id` | int | `room.*` |
| `ban_id` | int | `ban.revoke` |
| `ticket_id` | int | `ticket.close`, `ticket.reply` |
| `note_id` | int | `user.note.delete` |
| `owner_id` | int | `room.transfer` |
| `rank` | int | `user.set_rank`, `hotel.permission.set` |
| `seconds` | int | `user.set_rank` |
| `minutes` | int | `room.mute`, `hotel.maintenance` |
| `hours` | int | `user.trade_lock` |
| `days` | int | `user.set_hc` |
| `item_id` | int | `user.grant_item` |
| `quantity` | int | `user.grant_item` |
| `value` | int | `hotel.permission.set` (required there) |
| `reason` | string | `user.kick`, `user.trade_lock` |
| `message` | string | `hotel.alert`, `hotel.maintenance`, `ticket.reply` |
| `note` | string | `user.note.add` |
| `recipient` | string | `hotel.alert` |
| `word`, `replacement` | string | `hotel.wordfilter.*` |
| `permission` | string | `hotel.permission.set` |
| `target` | string | `hotel.reload` |
| `mode` | string | `hotel.maintenance` |
| `resolution` | string | `ticket.close` |
| `enabled` | bool | `hotel.lockdown` (required there) |
| `close` | bool | `ticket.reply` |
| `name`, `description`, `users_max`, `category_id`, `trade_mode`, `tags` | see `room.settings` | `room.settings` |

### Replies

- Success: status `0`, message `""`, except the query actions and
  `user.reset_password`, whose message is a JSON document (a string to parse).
- Failure: the status from the table above and, as `message`, the panel's error key
  (`housekeeping.error.*`), listed per action below. The mapping is:

| Error key | Status |
| --------- | ------ |
| `housekeeping.error.user_not_found`, `housekeeping.error.new_owner_not_found` | 2 |
| `housekeeping.error.room_not_found`, `housekeeping.error.room_not_active` | 3 |
| `housekeeping.error.db_failed`, `hash_failed`, `economy_failed`, `note_failed`, `wordfilter_failed`, `permission_failed`, `reload_failed` | 4 |
| every other key or sentence | 1 |

- Unknown action: status 1, message
  `Unknown action. Use one of: user.kick, user.trade_lock, user.reset_password, user.set_rank, user.grant_item, user.set_hc, user.note.add, user.note.delete, room.open, room.close, room.settings, room.mute, room.kick_all, room.transfer, room.delete, hotel.alert, hotel.maintenance, hotel.maintenance.status, hotel.lockdown, hotel.lockdown.status, hotel.wordfilter.add, hotel.wordfilter.remove, hotel.permission.set, hotel.reload, hotel.dashboard, ban.revoke, ticket.list, ticket.close, ticket.reply`
- `action` missing or blank: status 1, message `action is required`.
- An action that throws: status 4, message `The action failed: <ExceptionClass>`.

Every successful mutating action writes one audit entry; the audit action key is
given per action.

### Users

#### `user.kick` — HousekeepingKickUserEvent

| Field | Rule |
| ----- | ---- |
| `user_id` | > 0, the user must be **online** |
| `reason` | optional, trimmed, at most 500 characters |

Takes the user out of their room and, when `reason` is not empty, shows it to them
as an alert. Errors: `invalid_input`, `user_offline`, `target_unkickable` (the user
holds `acc_unkickable`). Audit `user.kick`.

#### `user.trade_lock` — HousekeepingTradeLockUserEvent

| Field | Rule |
| ----- | ---- |
| `user_id` | > 0, online or offline |
| `hours` | >= 1 (capped so the end fits in a 32-bit timestamp) |
| `reason` | optional, trimmed, at most 500 characters |

Writes a `sanctions` row with `trade_locked_until = now + hours*3600` (keeping the
user's sanction level and probation) and `users_settings.can_trade = '0'`; an
online user loses trading at once and sees the reason as an alert when it is not
empty. Errors: `invalid_input`, `user_not_found`, `db_failed`. Audit `user.trade_lock`.

#### `user.reset_password` — HousekeepingResetUserPasswordEvent

| Field | Rule |
| ----- | ---- |
| `user_id` | > 0 |

Sets a random 12-character password (letters and digits without look-alikes),
stored as a BCrypt hash; clears `auth_ticket`, bumps `access_token_version` and
revokes remember-me tokens, so every session of the user ends.
Success message: `{"password":"<the new password>"}`. Errors: `invalid_input`,
`user_not_found`, `hash_failed`, `db_failed`. Audit `user.reset_password`
(the password is never logged).

#### `user.set_rank` — HousekeepingSetUserRankEvent

| Field | Rule |
| ----- | ---- |
| `user_id` | > 0 |
| `rank` | > 0, an existing rank id |
| `seconds` | 0 = lasting rank (also ends a running temporary rank); 1 to 31536000 (one year) = temporary rank |

A temporary rank returns the user to the rank they had before (the first one, if
temporary ranks are stacked) when it runs out. An online user gets the rank badge,
effect, permissions and mod tool at once. Refused with `lockout` when the change
would leave nobody with housekeeping access or permission editing. Errors:
`invalid_input`, `rank_not_found`, `user_not_found`, `lockout`, `db_failed`.
Audit `user.set_rank` (`fromRankId=… rankId=… [until=…]`).

#### `user.grant_item` — HousekeepingGrantItemEvent

| Field | Rule |
| ----- | ---- |
| `user_id` | > 0, the user must exist |
| `item_id` | > 0, an existing **base item** id (`items_base.id`) |
| `quantity` | >= 1; more than 100 is silently capped to 100 |

Inserts `quantity` furni into the user's inventory (an online user sees them the
next time the inventory is loaded). Errors: `invalid_input`, `user_not_found`,
`item_not_found`, `economy_failed`, and for a Web API wired box at the per-user
limit `wiredfurni.web_api.error.one_per_user` / `wiredfurni.web_api.error.max_per_user`.
Audit `user.grant_item`.

#### `user.set_hc` — HousekeepingSetHcSubscriptionEvent

| Field | Rule |
| ----- | ---- |
| `user_id` | > 0 |
| `days` | >= 0; 0 ends the subscription now, otherwise adds `days` to the current end (or to now when it already ended) |

Online and offline users. Errors: `invalid_input`, `user_not_found`, `db_failed`.
Audit `user.set_hc`.

#### `user.note.add` / `user.note.delete` — HousekeepingUserNoteEvent

| Action | Fields |
| ------ | ------ |
| `user.note.add` | `user_id` > 0 (must exist), `note` 1 to 500 characters (trimmed) |
| `user.note.delete` | `user_id` > 0 (must exist), `note_id` the note's id |

Internal staff notes on a user (the panel's user notes list). RCON may delete **any**
note of the user (a staff member deletes only their own). Errors: `user_not_found`,
`invalid_input` (empty note), `input_too_long`, `note_failed`, `note_not_found`
(delete matched no note of that user). Audit `user.note.add` (`note_length=…`) /
`user.note.delete` (`note_id=…`).

### Rooms

The room is loaded if it is not (`room.delete` loads it with its furni, so they can
be returned to their owners).

#### `room.open` / `room.close` — HousekeepingRoomStateEvent

`room_id` > 0. Sets the room's door to open / locked (doorbell) and stores it.
Errors: `invalid_input`, `room_not_found`. Audit `room.open` / `room.close`.

#### `room.settings` — HousekeepingSaveRoomSettingsEvent

| Field | Type | Rule (HousekeepingRoomSettingsInput) |
| ----- | ---- | ------------------------------------ |
| `room_id` | int | > 0, required |
| `name` | string | trimmed, 1 to 50 characters, not blank |
| `description` | string | trimmed, at most 250 characters (may be empty) |
| `users_max` | int | 1 to 200 |
| `category_id` | int | an existing navigator category id |
| `trade_mode` | int | 0 = no trading, 1 = only the room owner may start a trade, 2 = everyone |
| `tags` | string[] | at most 2 entries; each trimmed, at most 15 characters, no `;`; blanks and duplicates are dropped; `[]` clears the tags |

Every settings field is optional: **a field left out (or `null`) keeps the room's
current value**. The room is saved at once and the users inside get the settings
update. Errors: `invalid_input`, `invalid_category`, `room_not_found`.
Audit `room.settings`.

#### `room.mute` — HousekeepingMuteRoomEvent

`room_id` > 0, `minutes` >= 0: `0` unmutes, any value above 0 mutes the room until
it is unmuted (the value is not a timer). Errors: `invalid_input`, `room_not_found`.
Audit `room.mute`.

#### `room.kick_all` — HousekeepingKickAllFromRoomEvent

`room_id` > 0. Sends everybody out of the room. Errors: `invalid_input`,
`room_not_found`. Audit `room.kick_all`.

#### `room.transfer` — HousekeepingTransferRoomOwnershipEvent

`room_id` > 0, `owner_id` > 0 (the new owner). Stores the new owner id and name and
updates the loaded room. Errors: `invalid_input`, `room_not_found`,
`new_owner_not_found` (status 2), `db_failed`. Audit `room.transfer`.

#### `room.delete` — HousekeepingDeleteRoomEvent

`room_id` > 0. Deletes the room for good, as its owner does from the navigator:
furni, bots and pets go back to their owners, the room's group is removed, and its
rights, votes, word filter and custom model go with it. Errors: `invalid_input`,
`room_not_found`, `db_failed`. Audit `room.delete`.

### Hotel

#### `hotel.alert` — HousekeepingSendHotelAlertEvent

| Field | Rule |
| ----- | ---- |
| `message` | trimmed, 1 to 1000 characters |
| `recipient` | optional, default `hotel` |

`recipient` format (trimmed; the part before the first `:` is the scope):

| `recipient` | Who gets it | Error when |
| ----------- | ----------- | ---------- |
| `hotel` (or empty / left out) | every online user who has not blocked staff alerts | — |
| `staff` | every online user holding `cmd_staffalert` | — |
| `user:<username>` | that one online user (username, case-insensitive) | `user_offline` (status 1): not online or no name |
| `room:<room id>` | the users in that room; the room must be loaded (active) | `room_not_active` (status 3) |

Any other scope: `invalid_input`. The alert reads `<message>\r\n-All-in-1 Manager`.
Errors also: `alert_empty`, `input_too_long`. Audit `hotel.alert`, or
`hotel.alert.user` / `hotel.alert.room` / `hotel.alert.staff` (detail `reached=<n>`).

#### `hotel.maintenance` — HousekeepingMaintenanceEvent

| `mode` | Fields | Effect |
| ------ | ------ | ------ |
| `start` | `minutes` 0 to 120, `message` optional (at most 200 characters) | Starts a countdown: online users are told the hotel closes in N minutes and reminded at 30, 15, 10, 5, 3, 2 and 1 minutes left; at 0 maintenance is switched on and everyone below the maintenance rank is disconnected. `minutes` 0 does that at once. Replaces a running countdown. A non-empty `message` becomes the maintenance message. |
| `now` | `message` optional | Same as `start` with `minutes` 0. |
| `cancel` | — | Stops a running countdown; maintenance stays as it is. Error `no_countdown` when none was running. |
| `disable` | — | Stops any countdown and switches maintenance off. |

Errors: `invalid_input` (unknown or missing mode, minutes out of range),
`input_too_long`, `no_countdown`. Audit `hotel.maintenance.start` (`minutes=… message=…`),
`.cancel`, `.disable` (`now` is audited as `start` with minutes 0).

#### `hotel.maintenance.status`

No fields. Message:

```json
{"enabled":false,"min_rank":5,"message":"…","countdown_ends_at":0}
```

`enabled`: maintenance is on; `min_rank`: lowest rank still let in; `message`: the
maintenance message; `countdown_ends_at`: unix time the running countdown ends, `0`
when none runs.

#### `hotel.lockdown` — HousekeepingLockdownEvent

`enabled` (bool, required; left out → `invalid_input`). Switches the panel's emergency
lockdown (only the highest rank may use the in-game panel while it is on) and stores
it in `emulator_settings` (`housekeeping.lockdown`). RCON is not affected by the
lockdown. Errors: `invalid_input`, `db_failed`. Audit `hotel.lockdown.on` / `.off`.

#### `hotel.lockdown.status`

No fields. Message: `{"enabled":true}`.

#### `hotel.wordfilter.add` / `hotel.wordfilter.remove` — HousekeepingWordFilterEvent

| Action | Fields |
| ------ | ------ |
| `hotel.wordfilter.add` | `word` 1 to 256 characters (trimmed); `replacement` at most 16 characters, empty or left out = the default replacement (`bobba`) |
| `hotel.wordfilter.remove` | `word` 1 to 256 characters |

Adding an existing word changes its replacement. The word filter is reloaded
afterwards (as `:update_wordfilter`). Errors: `invalid_input`, `input_too_long`,
`word_not_found` (remove), `wordfilter_failed`. Audit `hotel.wordfilter.add` /
`hotel.wordfilter.remove`.

#### `hotel.permission.set` — HousekeepingSetPermissionEvent

| Field | Rule |
| ----- | ---- |
| `permission` | an existing `permission_definitions.permission_key` |
| `rank` | an existing rank id |
| `value` | required, 0 to the permission's `max_value`: 0 = not allowed, 1 = allowed, 2 = in own rooms only |

Sets one cell of the permission matrix, then reloads the permissions (as
`:update_permissions`), which re-binds every online user to the new table. Setting
`acc_housekeeping` or `cmd_update_permissions` to 0 on the last rank holding it is
refused with `lockout`. Errors: `invalid_input`, `lockout`, `permission_failed`.
Audit `hotel.permission.set`.

#### `hotel.reload` — HousekeepingReloadEvent

`target`: one of `catalog`, `texts`, `permissions`, `items`, `navigator`, `config`,
`wordfilter` (exact). Reloads it as the matching in-game `Update*Command` does (see
[`reloadcontent`](#reloadcontent), which takes more targets). Errors:
`Unknown target. Use one of: catalog, texts, permissions, items, navigator, config, wordfilter`
(status 1), the `reloadcontent` errors. Audit `hotel.reload.<target>`.

#### `hotel.dashboard` — HousekeepingGetDashboardEvent

No fields. Message:

```json
{"online_users":12,"total_users":5400,"active_rooms":7,"total_rooms":880,
 "peak_online_today":12,"peak_online_all_time":12,"pending_tickets":3,
 "server_uptime_seconds":86400,"server_version":"Polaris"}
```

`active_rooms`: loaded rooms with at least one user. `pending_tickets`: support
tickets open or picked. The hotel keeps no peak, so both peaks are the current
online count. Uptime counts from the first housekeeping class load (about server
start). A table that cannot be read leaves its numbers at 0.

### Bans

#### `ban.revoke` — HousekeepingRevokeBanEvent

`ban_id` > 0 (`bans.id`). Ends that one active ban, leaving the user's other bans.
Errors: `invalid_input`, `no_active_ban`. Audit `ban.revoke` (`ban=<id>`). To lift
every ban of a user use [`unbanuser`](#unbanuser).

### Support tickets

The mod tool's calls for help (`ModToolManager`). Only open and picked tickets are
in play; a closed ticket is `ticket_not_found`.

#### `ticket.list`

No fields. Message: a JSON array of the open and picked tickets, oldest id first:

```json
[{"id":41,"state":"open","category_id":2,"reporter_id":10,"reporter_name":"Frank",
  "reported_id":22,"reported_name":"Spammer","moderator_id":0,"moderator_name":"",
  "message":"He keeps spamming","room_id":120,"room_name":"Frank's Cafe","created_at":1762700000}]
```

`state` is `open` or `picked`; `moderator_id`/`moderator_name` are the moderator
holding a picked ticket (`0`/`""` for an open one); `room_id` `0` and `room_name`
`""` when the ticket has no room; `created_at` is a unix timestamp.

#### `ticket.close`

| Field | Rule |
| ----- | ---- |
| `ticket_id` | an open or picked ticket |
| `resolution` | `resolved`, `useless` or `abusive` |

The system operator takes the ticket and closes it with the mod tool's verdict: the
reporter, if online, gets the handled / useless / abusive notice; `abusive` also
adds one to the reporter's abusive-call count; the moderators' lists drop the
ticket. Errors: `invalid_input` (unknown resolution), `ticket_not_found`.
Audit `ticket.close` (target the reporter, `ticket=<id> resolution=<r>`).

#### `ticket.reply`

| Field | Rule |
| ----- | ---- |
| `ticket_id` | an open or picked ticket |
| `message` | trimmed, 1 to 1000 characters |
| `close` | optional bool, default false |

Sends the reporter the message as an answer to their call for help (the mod tool's
issue response alert, as a moderator holding the ticket sends). The reporter must
be online. With `close: true` the ticket is then closed as `resolved`. Errors:
`invalid_input`, `ticket_not_found`, `user_offline`. Audit `ticket.reply`
(`ticket=<id> closed=<bool> length=<n>`).

---

## `banuser`

| Field | Type | Rule |
| ----- | ---- | ---- |
| `user_id` | int | > 0, required |
| `duration` | int | seconds, > 0, required |
| `reason` | string | optional, at most 200 characters (trimmed; the `bans.ban_reason` column) |
| `type` | string | `account` (default when left out), `ip`, `machine` or `super` (case-insensitive) |

Bans like the mod tool (`ModToolManager.banAsSystem`), with staff id 0 and no rank
check: an online user is shown the ban and disconnected (a parked session is
evicted too). `ip`, `machine` and `super` also ban the other accounts online on the
same address / machine (up to 50). A `machine` or `super` ban of a user without a
known machine id becomes an `ip` ban, and an `ip` (or `super`) ban without a known
IP becomes an `account` ban.

| Reply | When |
| ----- | ---- |
| `0` `""` | banned |
| `1` `invalid user` / `duration must be a positive number of seconds` / `reason must be at most 200 characters` | invalid field |
| `1` `type must be one of: account, ip, machine, super` | unknown type |
| `2` `user not found` | no such user |
| `4` `ban failed` | nothing was banned |

Audit `user.ban` (`seconds=… type=<effective type> accounts=<n> reason=…`).

## `unbanuser`

| Field | Type | Rule |
| ----- | ---- | ---- |
| `user_id` | int | > 0, required |

Lifts every active ban recorded on the user (account, IP, machine and super),
whoever placed it (`ModToolManager.unban` without a moderator).

| Reply | When |
| ----- | ---- |
| `0` `""` | at least one ban lifted |
| `1` `invalid user` | `user_id` missing or not positive |
| `1` `no active ban` | nothing was lifted |
| `2` `user not found` | no such user |

Audit `user.unban` (`username=…`).

## `reloadcontent`

| Field | Type | Rule |
| ----- | ---- | ---- |
| `target` | string | required; case-insensitive, `_` and `-` ignored (`pet_data` = `petdata`) |

Reloads one kind of content exactly as the in-game command does, then the clients
that need it are told (catalog, items, hotel view, permissions, reward tracks):

| `target` | Same as (in-game command class) | Reloads |
| -------- | ------- | ------- |
| `achievements` | `UpdateAchievementsCommand` | achievements |
| `bots` | `UpdateBotsCommand` | bot definitions |
| `calendar` | `UpdateCalendarCommand` | calendar campaigns |
| `catalog` | `UpdateCatalogCommand` | catalog, crafting; broadcasts the catalog update, discounts, marketplace and gift configuration, recycler |
| `chatbubbles` | `UpdateChatBubblesCommand` | chat bubbles |
| `config` | `UpdateConfigCommand` | `emulator_settings` / configuration |
| `dailytasks` | (no command) | daily tasks (`DailyTaskManager.reload`) |
| `discounts` | `UpdateCatalogCommand.reloadDiscounts` | the `discount.*` settings; broadcasts the bulk discount rules (no catalog reload, no cooldown) |
| `guildparts` | `UpdateGuildPartsCommand` | group badge parts and the badge imager |
| `hotelview` | `UpdateHotelViewCommand` | news, hall of fame, landing view (broadcast) |
| `items` | `UpdateItemsCommand` | base items, crackables, sound tracks; resends loaded rooms' height maps |
| `navigator` | `UpdateNavigatorCommand` | navigator and room models |
| `permissions` | `UpdatePermissionsCommand` | ranks and permissions; re-binds online users (permission audit as `All-in-1 Manager`) |
| `petdata` | `UpdatePetDataCommand` | pet data |
| `polls` | `UpdatePollsCommand` | polls |
| `quests` | (no command) | quests (`QuestManager.reload`) |
| `rewardtracks` | `UpdateRewardTracksCommand` | reward tracks (broadcast) |
| `texts` | `UpdateTextsCommand` | texts and command keys |
| `wordfilter` | `UpdateWordFilterCommand` | word filter |
| `youtube` | `UpdateYoutubePlaylistsCommand` | YouTube playlists |

There is no `discounts` target: bulk-discount settings are configuration
(`discount.*`) and come back with `config`, and `catalog` resends them to clients.

| Reply | When |
| ----- | ---- |
| `0` `""` | reloaded |
| `1` `target is required` | no target |
| `1` `Unknown target. Use one of: achievements, bots, calendar, catalog, chatbubbles, config, dailytasks, discounts, guildparts, hotelview, items, navigator, permissions, petdata, polls, quests, rewardtracks, texts, wordfilter, youtube` | unknown target |
| `1` `The catalog was reloaded less than 5 seconds ago; try again shortly.` | catalog cooldown (shared with the in-game catalog update command) |
| `1` `The bots could not be reloaded.` | the bot reload reported failure |
| `4` `The <target> could not be reloaded.` | the reload threw (e.g. `texts`) |

Audit `hotel.reload.<target>` (`target=<target>`).
