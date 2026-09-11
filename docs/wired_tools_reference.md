# Wired Creator Tools (`:wired`) Reference

## 1. Scope

This document describes the current `:wired` tooling that was added across:

- `Arcturus-Morningstar-Extended` (server-side data, diagnostics, config)
- `Nitro_Render_V3` (packet parsing and room/session metadata)
- `Nitro-V3` (UI, monitor, inspection, previews, inline editing)

It focuses on:

- the `Monitor` tab
- the `Inspection` tab (`furni`, `user`, `global`)
- the `wired_emulator_settings` database table
- the formulas and thresholds behind the monitor statistics

---

## 2. High-level architecture

### 2.1 Data flow

`Emulator` -> `Nitro_Render_V3` -> `Nitro-V3`

- The emulator computes room diagnostics and exposes extra room, furni, user, and monitor metadata.
- The renderer parses those packets and stores the values in room/session data objects.
- Nitro reads those values and renders them in the `:wired` UI.

### 2.2 Main files

- Emulator diagnostics:
  - `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredEngine.java`
  - `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredRoomDiagnostics.java`
  - `Emulator/src/main/java/com/eu/habbo/messages/outgoing/wired/WiredMonitorDataComposer.java`
- Renderer parsing:
  - `Nitro_Render_V3/packages/communication/src/messages/parser/roomevents/WiredMonitorDataParser.ts`
- Nitro UI:
  - `Nitro-V3/src/components/wired-tools/WiredCreatorToolsView.tsx`

---

## 3. Database configuration

## 3.1 New table

Wired configuration is now separated from `emulator_settings` into:

```sql
wired_emulator_settings (
  key,
  value,
  comment
)
```

Migration file:

- `Database Updates/002_move_wired_settings_to_wired_emulator_settings.sql`

The migration:

1. creates `wired_emulator_settings`
2. imports existing wired values from `emulator_settings`
3. inserts defaults for new monitor/diagnostic keys when missing
4. removes migrated wired keys from `emulator_settings`

### 3.2 Compatibility behaviour

The emulator still has a **fallback read path**:

- first it reads wired keys from `wired_emulator_settings`
- if a wired key is still missing there, it can still read it from the old `emulator_settings`

This allows safe rollout during migration.

## 3.3 Wired configuration keys

| Key | Default | Purpose |
|---|---:|---|
| `wired.engine.enabled` | `1` | Compatibility flag kept for old configs. Wired now runs through the new engine only. |
| `wired.engine.exclusive` | `1` | Compatibility flag kept for old configs. Wired now runs through the new engine only. |
| `wired.engine.maxStepsPerStack` | `100` | Maximum internal processing steps allowed for one stack execution. |
| `wired.engine.debug` | `0` | Enables verbose wired engine logging. |
| `wired.custom.enabled` | `0` | Enables legacy custom wired compatibility logic. |
| `hotel.wired.furni.selection.count` | `5` | Maximum furni count selectable/storable by wired boxes. |
| `hotel.wired.max_delay` | `20` | Maximum accepted wired delay value for delayed effects. |
| `hotel.wired.message.max_length` | `100` | Maximum length of wired/bot text fields. |
| `wired.effect.teleport.delay` | `500` | Delay in milliseconds used by wired teleports. |
| `wired.place.under` | `0` | Allows wired furniture placement under other items. |
| `wired.tick.interval.ms` | `50` | Global tick interval in milliseconds for repeater-style wired. |
| `wired.tick.resolution` | `100` | Legacy compatibility tick resolution value. |
| `wired.tick.debug` | `0` | Enables verbose logging for the tick service. |
| `wired.tick.thread.priority` | `6` | Java thread priority for the tick service. |
| `wired.highscores.displaycount` | `25` | Maximum wired highscore entries shown to the user. |
| `wired.abuse.max.recursion.depth` | `10` | Maximum recursive wired depth before execution stops. |
| `wired.abuse.max.events.per.window` | `100` | Maximum identical events allowed inside the abuse rate-limit window. |
| `wired.abuse.rate.limit.window.ms` | `10000` | Time window in milliseconds used by the abuse limiter. |
| `wired.abuse.ban.duration.ms` | `600000` | Room wired-ban duration in milliseconds after abuse detection. |
| `wired.monitor.usage.window.ms` | `1000` | Rolling window size used to calculate monitor usage. |
| `wired.monitor.usage.limit` | `1000` | Maximum usage budget allowed in one monitor window. |
| `wired.monitor.delayed.events.limit` | `100` | Maximum delayed wired events that may be pending in one room. |
| `wired.monitor.overload.average.ms` | `50` | Average execution threshold in milliseconds for overload tracking. |
| `wired.monitor.overload.peak.ms` | `150` | Peak execution threshold in milliseconds for overload tracking. |
| `wired.monitor.overload.consecutive.windows` | `2` | Consecutive overloaded windows required before `EXECUTOR_OVERLOAD`. |
| `wired.monitor.heavy.usage.percent` | `70` | Usage percentage threshold that contributes to `MARKED_AS_HEAVY`. |
| `wired.monitor.heavy.consecutive.windows` | `5` | Consecutive heavy windows required before the room is marked heavy. |
| `wired.monitor.heavy.delayed.percent` | `60` | Delayed queue percentage threshold that contributes to the heavy state. |

---

## 4. Monitor tab

## 4.1 Statistics shown in the UI

The `Monitor` tab currently shows:

- `Wired usage`
- `Is heavy`
- `Room furni`
- `Wall furni`
- `Delayed events`
- `Average execution`
- `Peak execution`
- `Recursion`
- `Killed remaining`
- `Permanent furni vars`

### 4.1.1 `Wired usage`

Format:

```text
usageCurrentWindow / usageLimitPerWindow
```

Source:

- server-side `WiredRoomDiagnostics`

Meaning:

- `usageCurrentWindow` = cost consumed in the current rolling monitor window
- `usageLimitPerWindow` = max allowed budget before `EXECUTION_CAP`

### 4.1.2 `Is heavy`

Format:

```text
Yes / No
```

Source:

- server-side boolean from `WiredRoomDiagnostics`

Meaning:

- `Yes` if the room has crossed the heavy thresholds for enough consecutive windows

### 4.1.3 `Room furni`

Format:

```text
(floor count + wall count) / roomItemLimit
```

Source:

- numerator: renderer room object counts
- denominator: server room item limit exposed in room/session data

### 4.1.4 `Wall furni`

Format:

```text
wall count / roomItemLimit
```

Important note:

- there is **no separate wall-only cap** here
- the denominator is the same room furni limit exposed by the server

### 4.1.5 `Delayed events`

Format:

```text
delayedEventsPending / delayedEventsLimit
```

Source:

- server-side `WiredRoomDiagnostics`

### 4.1.6 `Average execution`

Format:

```text
averageExecutionMs + "ms"
```

Meaning:

- average execution time of sampled stacks inside the current monitor window

### 4.1.7 `Peak execution`

Format:

```text
peakExecutionMs + "ms"
```

Meaning:

- highest sampled execution time inside the current monitor window

### 4.1.8 `Recursion`

Format:

```text
recursionDepthCurrent / recursionDepthLimit
```

Meaning:

- current nested wired call depth vs the configured recursion cap

### 4.1.9 `Killed remaining`

Format:

```text
killedRemainingSeconds + "s"
```

Meaning:

- remaining room cooldown while wired execution is temporarily halted by protection logic

### 4.1.10 `Permanent furni vars`

Format:

```text
customVariableEntryCount / 60
```

Current meaning:

- the numerator is the total number of renderer-side entries stored inside `RoomObjectVariable.FURNITURE_CUSTOM_VARIABLES`
- the denominator `60` is currently a fixed UI denominator

This is currently **renderer-side custom variable count**, not a DB row count.

---

## 4.2 Cost model behind `Wired usage`

The current estimated stack cost is computed in the emulator.

### 4.2.1 Base formula

```text
cost = 1
cost += number_of_conditions
cost += 2 for each selector effect
cost += 3 for each non-selector effect
cost += 4 extra for each delayed effect
cost += recursionDepth * 2
cost = max(1, cost)
```

### 4.2.2 Practical breakdown

| Element | Cost |
|---|---:|
| Base stack cost | `1` |
| Each condition | `+1` |
| Each selector effect | `+2` |
| Each regular effect | `+3` |
| Each delayed effect | `+4` extra |
| Each recursion level | `+2` |

### 4.2.3 Example

If a stack has:

- `2` conditions
- `1` selector
- `2` normal effects
- `1` delayed effect
- recursion depth `1`

Then:

```text
1
+ 2 conditions
+ 2 selector
+ 6 regular effects
+ 4 delayed effect extra
+ 2 recursion
= 17
```

That `17` is what is attempted against:

```text
usageCurrentWindow + estimatedCost <= usageLimitPerWindow
```

If the result would exceed the budget, the engine records `EXECUTION_CAP`.

---

## 4.3 Heavy / overload calculations

### 4.3.1 Overload

A monitor window is considered overloaded when:

```text
executionSamplesCurrentWindow > 0
AND (
  averageExecutionMs >= overloadAverageThresholdMs
  OR
  peakExecutionMs >= overloadPeakThresholdMs
)
```

After `wired.monitor.overload.consecutive.windows` consecutive overloaded windows:

- the room logs `EXECUTOR_OVERLOAD`

### 4.3.2 Heavy

A monitor window is considered heavy when at least one of these is true:

```text
usagePercent >= heavyUsageThresholdPercent
OR
delayedPercent >= heavyDelayedThresholdPercent
OR
overloadWindow == true
```

Where:

```text
usagePercent = round(usageCurrentWindow * 100 / usageLimitPerWindow)
delayedPercent = round(delayedEventsPending * 100 / delayedEventsLimit)
```

After `wired.monitor.heavy.consecutive.windows` consecutive heavy windows:

- the room is marked heavy
- the monitor logs `MARKED_AS_HEAVY`

---

## 4.4 Error / warning log types

The monitor currently supports:

- `EXECUTION_CAP`
- `DELAYED_EVENTS_CAP`
- `EXECUTOR_OVERLOAD`
- `MARKED_AS_HEAVY`
- `KILLED`
- `RECURSION_TIMEOUT`
- `NO_TARGETS` — a chain fired and an effect had nothing to act on, so it did
  nothing and said nothing
- `UNREACHABLE` — a furni is waiting on something the room has no way of
  producing, such as a highscore board in a room with no game that can end

Each log/history entry can carry:

- type
- severity
- amount/count
- latest occurrence
- reason/motivation
- trigger/source label
- trigger/source id

---

## 4.5 Variables tab: clearing a variable

Next to `Manage` the Variables tab offers `Clear this variable`, the official
"delete all" action: it takes a user or furni variable away from every holder,
in the room and in permanent storage, and fires the trigger's `deleted` event
once per holder. It goes through the existing manage packet as action `2`.
The button only appears to the owner of the definition box (or a staff member
with `ACC_ANYROOMOWNER`), and the server enforces the same rule in
`WiredVariableClearPolicy`; room and array variables are not covered.

## 5. Inspection tab

## 5.1 Furni inspection

Current variables include:

- `@id`
- `@class_id`
- `@height`
- `@state`
- `@position.x`
- `@position.y`
- `@rotation`
- `@altitude`
- `@wallitem_offset` (wall items only)
- `@type`
- `@dimensions.x`
- `@dimensions.y`
- `@owner_id`
- dynamic flags:
  - `@can_sit_on`
  - `@can_lay_on`
  - `@can_stand_on`
  - `@is_stackable`
- extra teleport variable when relevant:
  - `~teleport.target_id`

Editable fields:

- `@state`
- `@position.x`
- `@position.y`
- `@rotation`
- `@altitude`
- `@wallitem_offset` (wall items)

Important notes:

- floor moves are sent through wired-style movement flow/animation
- wall item updates use wall position recomposition
- booleans such as sit/lay/stand/stack come from `items_base`-derived metadata, not from `FurnitureData.json`

## 5.2 User inspection

Current variables include:

- `@index`
- `@type`
- `@gender`
- `@level`
- `@achievement_score`
- `@position.x`
- `@position.y`
- `@direction`
- `@altitude`
- `@favourite_group_id`
- `@room_entry`
- `@room_entry.teleport_id`
- `@user_id` / `@bot_id` / `@pet_id`

Dynamic flags/actions include:

- `@is_hc`
- `@has_rights`
- `@is_owner`
- `@is_group_admin`
- `@is_mute`
- `@is_trading`
- `@is_frozen`
- `@effect`
- `@team_score`
- `@team_color`
- `@team_type`
- `@sign`
- `@dance`
- `@is_idle`
- `@handitems`

Editable fields:

- `@position.x`
- `@position.y`
- `@direction`

## 5.3 Global inspection

Current variables include:

- `@furni_count`
- `@user_count`
- `@wired_timer`
- `@teams.red.score`
- `@teams.green.score`
- `@teams.blue.score`
- `@teams.yellow.score`
- `@teams.red.size`
- `@teams.green.size`
- `@teams.blue.size`
- `@teams.yellow.size`
- `@room_id`
- `@group_id`
- `@timezone_server`
- `@timezone_client`
- `@current_time`
- `@current_time.millisecond_of_second`
- `@current_time.seconds_of_minute`
- `@current_time.minute_of_hour`
- `@current_time.hour_of_day`
- `@current_time.day_of_week`
- `@current_time.day_of_month`
- `@current_time.day_of_year`
- `@current_time.week_of_year`
- `@current_time.month_of_year`
- `@current_time.year`

Important notes:

- `@timezone_server` comes from the emulator room/session snapshot and follows `hotel.timezone`
- `@timezone_client` comes from the browser
- `@wired_timer` is currently client-side time since room entry
- `@current_time.*` is currently based on the server hotel time snapshot plus client-side progression

---

## 6. UI behaviour notes

### 6.1 Monitor windows

The monitor now uses real Nitro card windows for:

- info
- log history
- error information

This means they are:

- closable with the standard Nitro card close button
- draggable
- resizable

### 6.2 Keep selected

In `Inspection`:

- when `Keep selected` is enabled
- clicking another furni/user does **not** replace the current preview/selection

### 6.3 Inline editing

Inline editors:

- can be opened by clicking the row
- submit on `Enter`
- stop accidental room chat typing while the input is focused

---

### 6.4 Window style

The settings tab's account preferences carry a **Wired style** picker, as the official client's do
(`wiredmenu.settings.preferences.wired_style.default` = "Default (%name%)"). Four looks: the client's
own default, and `volter`, `ubuntu` and `volter_blue`, reproduced colour for colour from the official
`wired_style_*.xml` layouts (window background, ruler, text, inputs, source button, menu items). The
official client has six; the layouts of `illumina` (its default), `volter_green` and `volter_yellow`
are not in the AIR 13 dump, so they are not offered. The choice is a client-side preference stored
with the other three in local storage; nothing is sent to the emulator. The window carries the class
`nitro-wired--style-<name>` (`octane-wired--style-<name>` after the Octane rename) and the skin is a
CSS override block in `WiredView.css`.

### 6.5 Tabs since 2026-09-06: Transactions and Info

The room transaction log that the chests tab used to open as a modal window is a tab of its own
(`wiredmenu.transactions.tab`), between chests and settings: same filters (furni and coins, only coins,
only furni), same paging, and a row click still narrows the log to one chest ("All chests" widens it
again). The chests tab keeps a button that switches to it.

The Info tab (the official client has it, keys `wiredmenu.info.tab`/`wiredmenu.info.title`) is a
read-only summary: furni in the room against the limit, wall furni, permanent furni variables, the
wired boxes in the room counted by family (triggers, conditions, effects, selectors, add-ons; recognised
by the `wf_trg_`/`wf_cnd_`/`wf_act_`/`wf_slc_`/`wf_xtra_` furni class prefixes), the number of dialog
codes this client can open per family, and the documentation link from `wired.api.docs.link` when set.

The container `WiredCreatorToolsView.tsx` also lost its four inline windows (monitor history, monitor
info, variable management, managed holder) to files of their own in the same folder; the state still
lives in the container.

### 6.6 Client-side extras around the wired menu (2026-09-06)

- Chat commands `:wf` / `:wired` open the wired menu, `:var` opens it on the variables tab and
  `:inspect` on the inspection tab, as in the official client. They are client-only.
- The user settings' "Other" section has the official "Disable wired whispers" switch: the client
  drops incoming whispers whose bubble style is the wired one (34) before they reach the bubbles or
  the chat history. The official client stores this preference server-side; ours is local until a
  preferences packet exists.

### 6.7 AIR 13 leftovers wired up (2026-09-09)

Eight official packets that the AIR 13 client speaks but nothing answered are now served end to end.
Every id is the official one except two, which were already taken on this hotel:

| Direction | Packet | Header |
|---|---|---:|
| server -> client | `WiredEnvironment` (hasClickUserWired, enabledAchievements) | 347 |
| server -> client | `WiredClickSettings` (userOption, furniOption) | 2288 |
| server -> client | `WiredLogPage` | 918 |
| server -> client | `AllVariablesHash` | 1646 |
| server -> client | `AllVariablesDiff` | 2498 |
| server -> client | `WiredClickUserResponse` | 9460 (custom: 420 is `GuildListComposer`) |
| server -> client | `WiredUserVariablesPage` | 9461 (custom: 2901 is `PetInformationComposer`) |
| server -> client | variable holders (variable + objectId/value pairs) | 9462 (custom: 2031 is `RoomModelComposer`) |
| client -> server | `userSelected` | 3122 |
| client -> server | wired menu permissions + timezone | 1936 |
| client -> server | room state reload / rollback | 3761 |
| client -> server | room log page with filters | 3882 |
| client -> server | variable holders page with filters | 975 |
| client -> server | variable holders request | 2973 |
| client -> server | cached variable hashes | 1497 |
| client -> server | all-variables hash request | 1735 |

Behaviour notes:

- **Room entry** now carries `WiredEnvironment` (`RoomManager.enterRoom`). `hasClickUserWired` is true
  when the room holds a `wf_trg_click_user` / `wf_trg_click_bot` box; `enabledAchievements` lists the
  distinct achievements of the room's `WiredEffectGiveAchievement` boxes. The room-tools achievements
  button is hidden when that list is empty, as in `RoomToolsWidget.as:50-52`.
- **Clicking an avatar** goes through the server while `hasClickUserWired` is set: the client sends
  3122 and only opens the infostand when `WiredClickUserResponse.openMenu` comes back true. The older
  custom `ClickUserEvent` (10020) and its `avatar-info/block-menu` link are left in place.
- **Room logs** (918/3882) page the room's wired diagnostics history with a level filter (the
  severity ordinal), a source filter (the diagnostic type ordinal) and a free-text query. It is a
  second, server-paged view next to the monitor history window, opened from the Monitor tab.
- **Variable overview** (975/2973/9461/9462) pages the holders of one wired variable with the
  official user-type and sort filters, and can ask for every holder to highlight them. The variable
  ids are `room:`/`user:`/`furni:`/`ctx:` plus the definition item id.
- **Variable cache sync** (1735/1646/1497/2498) is the official hash-then-diff exchange: the client
  asks for the set hash, and only when it differs does it send its cached per-variable hashes and get
  back the removed and the added/updated variables, in chunks of 100.
- **Timezone**: the settings packet pair now carries the room's timezone. It is stored in a new
  `room_wired_settings.timezone` column, written by the official 1936 packet (modify mask, read mask,
  timezone) and read back on `WiredRoomSettingsDataComposer` (which therefore gained a trailing
  string; the wired packet golden fixture was regenerated for it). The picker offers the
  `wired.timezones` configuration entries, falling back to a short built-in list.
- **Reload / rollback** (3761): `false` drops the cached wired stacks so the room is wired up again
  from the furniture as it stands; `true` additionally re-reads every box's `wired_data` from the
  database, discarding edits that were never saved. Both require modify rights.

## 7. Current limitations

- `Permanent furni vars` currently uses a fixed denominator (`60`) in the Nitro UI
- `@wired_timer` is still client-side, not a dedicated server timer
- `wired.tick.resolution` is kept for compatibility/documentation, but the current tick service uses `wired.tick.interval.ms`
- `wired.highscores.displaycount` is migrated/documented, but its usage should be validated in the current runtime path if highscore behaviour is changed later
- `WiredClickSettings` (2288) is parsed and kept in the wired tools store, but the renderer has no
  `setClickSettings` equivalent yet, so the click-walk-behind / pass-through modes are not applied to
  the room engine and nothing sends the packet from the server side
