# Wired Full Reference

## 1. Scope

This document is a code-based reference for the current wired runtime of the Polaris gameserver (`Gameserver/` in this monorepo).

It covers:

- general wired engine rules
- tick and delay rules
- protection and monitor rules
- custom variable rules
- every registered wired trigger, effect, selector, condition, extra, variable definition, and special wired item

Primary runtime sources used for this reference (paths are relative to `Gameserver/`):

- `Emulator/src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java` (item registration)
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredManager.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredEngine.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredEventDispatcher.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredStackExecutor.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredExecutionGuard.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredRoomDiagnostics.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/tick/WiredTickService.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/` (the wired boxes themselves)

This file describes runtime behavior and the configuration surface, not the client UI layout in detail. See also:

- `docs/wired_tools_reference.md` for the `:wired` monitor and the creator tools
- `docs/wired/README.md` for the parity notes and the known gaps
- `docs/wired_bug_audit.md` for the audit history

Every entry lists the item key (the `interaction_type` in `items_base`) and the Java class that implements it. Where one class is registered under several keys, each key has its own entry or points to the main one.

---

## 2. Wired Engine, Tick, and General Runtime Rules

### 2.1 Main runtime architecture

The wired runtime is centered around these components:

- `WiredManager`
  - initializes the wired runtime and loads config
  - owns the centralized engine and the stack index
  - exposes the high-level trigger methods (user click, walk on, say, signal, game events, call stacks, and so on)
  - queues events raised while another event is running (deferred events, capped at `1000` per room drain)
- `WiredEventDispatcher`
  - receives each raised event, looks up the candidate stacks first, and only then applies the room rate limit
  - events that no stack in the room listens to never count toward the rate limit, so pressure plates or rolling dice in a room without matching wired cannot get the room banned
- `WiredEngine`
  - receives `WiredEvent` objects and runs the candidate stacks
  - enforces abuse protection, the delayed queue limit, the recursion limit, the execution budget, and diagnostics
- `WiredStackExecutor`
  - evaluates selectors, conditions, extras, and effects for one stack
  - has a direct mode (`executeDirect`) used by the call-stacks effect, which skips the trigger match and the "requires an actor" check
- `WiredExecutionGuard`
  - owns the per-room recursion depth, event rate trackers (per room and event type), and temporary wired bans
- `RoomWiredStackIndex`
  - caches stack membership so the engine can quickly find candidate stacks for a given event type
- `WiredTickService`
  - runs one global tick loop sharded across worker threads
  - keeps repeaters and other tickables synchronized across rooms
- `WiredHandler`
  - legacy compatibility path that still exists in the codebase
  - useful to understand older stack execution logic and some compatibility behavior

### 2.2 Current execution model

At a high level, the engine processes a stack like this:

1. receive an event
2. find candidate stacks for the event type (no stacks → the event is dropped before rate limiting)
3. check the room ban and the rate limit
4. check whether the trigger matches
5. build a `WiredContext`
6. run selectors first, so the target set is available
7. apply selection-filter extras if present
8. evaluate conditions
9. apply stack-level gates such as the execution limit
10. activate the trigger and extras
11. execute or schedule effects

Important consequences of this model:

- selectors run before conditions
- conditions can inspect the selected targets or even the whole stack
- effects may run immediately or with a delay
- extras can modify selection, condition evaluation, effect ordering, effect subset choice, and movement animation

### 2.3 Tick rules

The centralized tick service is defined in `WiredTickService`.

Current core rules:

- default global tick interval: `50ms`
- hard allowed range: `10ms` to `500ms`
- worker threads: `wired.tick.workers`, default `min(8, max(2, CPU count))`, allowed `1..32`
- repeaters and other tickables use a shared global tick counter
- tickables are registered per room and unregistered when the room unloads

This means:

- repeaters are synchronized with each other
- two repeaters with the same timing do not drift independently per room
- unload/cleanup behavior matters for room-scoped temporary state

### 2.4 Delay rules

The classic wired delay value is stored in half-second steps.

Runtime rule:

- `effective delay in milliseconds = delay * 500`

Examples:

- delay `0` = immediate execution
- delay `1` = `500ms`
- delay `2` = `1000ms`

This rule is used both by the legacy path and by the engine. Delayed effects keep a snapshot of the context (`WiredDelayedExecutionSnapshot`), so they act on the targets that were selected when the stack fired.

### 2.5 Stack ordering rules

There are several separate notions of order:

- **stack candidate order**
  - candidate stacks are found through the index for the specific event type
- **item ordering inside one tile stack**
  - `WiredExecutionOrderUtil` sorts by `z`, then by item id
- **effect subset modifiers**
  - `wf_xtra_random` can choose only part of the effects
  - `wf_xtra_unseen` can rotate through effects without repeats
- **ordered execution**
  - `wf_xtra_exec_in_order` is the explicit "run in stable stack order" modifier

Practical takeaway:

- without an order modifier, execution may depend on the collection order produced by the runtime path
- if exact order matters, use `wf_xtra_exec_in_order`

### 2.6 Selection rules

Selectors build or refine the `WiredTargets` inside `WiredContext`.

In practice:

- target users and target furni are built before conditions are checked
- later effects consume those selected targets through their source setting (trigger, selector, signal, picked furni, and so on)
- several ordinary selectors in one stack are combined as a **union**: each adds its matches to the selection
- a selector with the "filter existing" option narrows the current selection instead; the "invert" option selects everything the selector did not match
- exceptions: the neighborhood selectors (`wf_slc_furni_neighborhood`, `wf_slc_users_neighborhood`) replace earlier picks, and their invert only covers the 9x9 grid around the source; the signal selectors clear the selection on a non-signal event, and the on-furni selectors clear it when no source furni is found
- extras such as `wf_xtra_filter_furni` / `wf_xtra_filter_users` then trim, sort, or limit that selection
- an effect with the selector source and an empty selection does nothing

### 2.7 Condition rules

Conditions are evaluated after selectors.

General behavior:

- if there are no conditions, the stack continues directly to effects
- if there are conditions, all of them must pass according to the current evaluation mode
- `wf_xtra_or_eval` changes how condition results are aggregated
- a condition that checks a set (users or furni from a source) returns `false` when that set is empty; its `NOT` twin returns `true`

The runtime supports both:

- ordinary condition matching
- grouped OR semantics through condition operators and the OR-eval extra

### 2.8 Protection rules

The wired runtime has multiple safety layers:

- maximum steps per stack
- recursion depth protection
- per-room, per-event-type rate limiting (only for events a stack in the room listens to)
- timer firings (repeaters, timers, at-time triggers) are not counted toward the per-event-type rate limit and can never ban a room; instead a room runs at most `200` timer firings per second, all repeaters together, and the rest of that second is skipped (logged once as an execution cap)
- per-player throttle: an event a player raises by their own action (a click, a chat line, a step) is admitted at most `5` times per second per player and event type
- temporary room wired ban after abuse (all wired in the room stops, including direct stack calls). Only floods that wired causes itself (loops, chains, delayed effects) ban the room; when players push a room over its rate limit, their extra events are dropped instead, so visitors cannot switch a room's wired off
- delayed queue cap
- execution budget per room window: each stack run costs its boxes plus one unit per ten selected furni or users, so a stack that moves or toggles half the room is charged for it
- deferred event queue cap (`1000` events per drain)
- call-stacks limits: at most `20` target tiles and `20` stacks per call, and every call consumes `10` units of the room execution budget
- send-signal limits: at most `250` signals per firing, at most `100` forwarded users or furni per signal, each antenna pulses once per firing
- signal depth: `wired.signal.max.depth` (default `100`)
- movement-style hints are batched per movement, so one effect moving many furni sends one packet instead of one per furni
- moving or saving a wired box refreshes the stack index only; it no longer resets the room's execution budget, delayed counters, recursion depth or room log
- the stack index is only rebuilt when a wired box moves; toggling, rolling or moving other furni keeps it
- chat-capture templates (`wf_xtra_text_input_variable`) compile once per template and cannot backtrack exponentially on long lines
- a selector that reads its reference from the selector source cannot re-run itself recursively

Main defaults from runtime/config:

- `wired.engine.maxStepsPerStack = 100`
- `wired.abuse.max.recursion.depth = 10`
- `wired.abuse.max.events.per.window = 100`
- `wired.abuse.rate.limit.window.ms = 10000`
- `wired.abuse.ban.duration.ms = 600000` (`0` disables the ban and only logs)
- `wired.monitor.usage.window.ms = 1000`
- `wired.monitor.usage.limit = 1000`
- `wired.monitor.delayed.events.limit = 100`

Client input is also bounded on save: text params are capped (`hotel.wired.message.max_length`), selections are capped (`hotel.wired.furni.selection.count`), delays are capped (`hotel.wired.max_delay`), numeric params are clamped to their documented range, and variable tokens are normalized to `custom:<id>` or `internal:<key>` (anything else is treated as "no variable").

### 2.9 Monitor and diagnostics rules

The engine tracks room diagnostics through `WiredRoomDiagnostics`.

This is where the `:wired` monitor gets values such as:

- usage in the current window
- delayed events count
- average and peak execution time
- recursion state
- heavy room status
- overload windows

Heavy/overload decisions are based on rolling windows, not on a single event.

The room log keeps the last `200` entries. Each entry has a type and a severity:

- severities: `DEBUG` (0), `INFO` (1), `WARNING` (2), `ERROR` (3)
- engine entries (rate limit, recursion, budget, and so on) are warnings or errors
- `wf_act_log` writes `WIRED_LOG` entries with the level chosen in the box; its line is capped at `1000` characters and lists at most `10` names
- `WIRED_LOG` entries only evict other `WIRED_LOG` entries, so a chatty log box cannot push engine warnings out of the history

Clearing a room's diagnostics also forgets its room-time state (timezone cache and timer resets).

### 2.10 Time rules

- `wf_cnd_match_time`, `wf_cnd_match_date`, and the `@current_time` family use the room's wired timezone (set in the creator tools settings tab), falling back to the server timezone
- the room keeps the millisecond at which its timers were last reset (`wf_act_reset_timers`), so timer triggers and conditions compare at millisecond precision instead of half-second ticks
- `wf_cnd_date_rng_active` treats an end of `0` as open-ended

### 2.11 Legacy compatibility notes

The project still contains `WiredHandler`.

Important practical notes:

- `WiredManager` is the intended modern entrypoint
- `wired.engine.enabled` and `wired.engine.exclusive` are treated as compatibility-only flags by `WiredManager`
- `WiredHandler` still exists and is useful for compatibility and for understanding legacy behavior

So when documenting stacks, think in terms of:

- modern runtime: `WiredManager` + `WiredEngine`
- legacy compatibility surface: `WiredHandler`

### 2.12 Custom variable rules

Custom wired variables are defined by:

- `wf_var_user`
- `wf_var_furni`
- `wf_var_room`
- `wf_var_context`

Shared rules:

- variable names must be unique across the whole room, even across different variable types
- allowed name length: `1..40`
- allowed characters: letters, numbers, `_`

Availability rules:

- `wf_var_user`: room-scoped while the user is in the room, or permanent
- `wf_var_furni`: room-active while the room is loaded, or permanent
- `wf_var_room`: room-active, or permanent
- `wf_var_context`: lives only for one stack execution (and the stacks it calls or signals)

Timestamp rules:

- user variables: creation/update are tied to the assignment on that user
- furni variables: creation/update are tied to the assignment on that furni
- room variables: the meaningful timestamp is mainly the last update time

Value-or-variable settings:

- several boxes (for example `wf_xtra_mov_curve`, `wf_act_change_var_val`, the variable conditions) accept either a typed number or a value read from a variable
- the variable reference picks a target (user, furni, global, context), a variable token, and a source for whose value is read; the first holder in that source that has the variable wins
- internal variables (`@height`, `@current_time`, and so on) can be referenced when the internal variable supports that target

### 2.13 Useful global config keys

| Key | Meaning |
|---|---|
| `wired.engine.enabled` | Compatibility-only legacy flag |
| `wired.engine.exclusive` | Compatibility-only legacy flag |
| `wired.engine.maxStepsPerStack` | Loop/step protection limit |
| `wired.engine.debug` | Verbose engine logging |
| `wired.custom.enabled` | Legacy custom wired compatibility behavior |
| `wired.place.under` | Allow placing furni under wired |
| `hotel.wired.furni.selection.count` | Max furni selection size stored by wired boxes (default `5`) |
| `hotel.wired.max_delay` | Max accepted delay value |
| `hotel.wired.message.max_length` | Max wired/bot text size |
| `wired.effect.teleport.delay` | Teleport effect delay (default `500`) |
| `wired.signal.max.depth` | Max nested signal depth (default `100`) |
| `wired.tick.interval.ms` | Global tick loop interval |
| `wired.tick.workers` | Tick worker thread count |
| `wired.tick.debug` | Tick debug logging |
| `wired.tick.thread.priority` | Tick thread priority |
| `wired.abuse.max.recursion.depth` | Recursion protection |
| `wired.abuse.max.events.per.window` | Event spam protection |
| `wired.abuse.rate.limit.window.ms` | Abuse window size |
| `wired.abuse.ban.duration.ms` | Temporary room wired-ban duration (`0` = log only) |
| `wired.monitor.usage.window.ms` | Usage monitor window size |
| `wired.monitor.usage.limit` | Execution budget per window |
| `wired.monitor.delayed.events.limit` | Delayed queue ceiling |
| `wired.monitor.overload.average.ms` | Overload average threshold (default `50`) |
| `wired.monitor.overload.peak.ms` | Overload peak threshold (default `150`) |
| `wired.monitor.overload.consecutive.windows` | Windows over the threshold before overload (default `2`) |
| `wired.monitor.heavy.usage.percent` | Heavy-room usage threshold (default `70`) |
| `wired.monitor.heavy.delayed.percent` | Heavy-room delayed-queue threshold (default `60`) |
| `wired.monitor.heavy.consecutive.windows` | Windows over the threshold before heavy (default `5`) |
| `wired.gravity.max_items_per_room` | Max furni tracked by the gravity add-on (default `1000`) |
| `wired.gravity.settle_delay_ms` | Gravity settle delay (default `75`) |
| `wired.gravity.retry_delay_ms` | Gravity retry delay (default `50`) |
| `wired.opacity.max_states_per_room` | Max furni opacity states per room |
| `wired.opacity.max_updates_per_packet` | Max opacity updates per packet |
| `wired.api.enabled` | Serve the Variables Web API under `/api/public` (default off; every path answers 404 while off) |
| `wired.api.max_payload_bytes` | Largest request body in bytes (default `16384`) |
| `wired.api.rate_limit.enabled` | Rate limit the Variables Web API (default on) |
| `wired.api.rate_limit.per_ip` / `.per_ip.window_ms` | Requests per client address before authentication (default `120` per `10000` ms) |
| `wired.api.rate_limit.per_key` / `.per_key.window_ms` | Requests per key (default `60` per `10000` ms) |
| `wired.api.rate_limit.room_writes` / `.room_writes.window_ms` | Variable writes per room across all keys (default `200` per `10000` ms) |
| `wired.api.auth_fail.max` / `.window_ms` / `.block_ms` | Failed authentications per address before a block, and the block length (default `10` per `60000` ms, blocked `300000` ms) |
| `wired.api.batch.max` | Operations per batch and variables per profile update (default `100`) |
| `wired.api.bulk_delete.max` | Names per bulk delete (default `20`) |
| `wired.api.page_size.max` | Largest holder page (default `100`) |
| `wired.api.cors.origins` | Browser origins allowed to call the API, comma separated (default `*`; auth is header-only) |
| `hotel.wired.achievements.enabled` | Let `wf_act_progress_achievement` progress achievements (default off) |
| `hotel.wired.achievements.allowed` | Achievement names wired may progress, comma separated (default empty: none) |
| `hotel.wired.achievements.max_per_window` | Progress one user may get per achievement per window, across all rooms (default `50`) |
| `hotel.wired.achievements.window_seconds` | Length of that window (default `3600`, clamped to 1 s .. 7 days) |
| `hotel.wired.reward_tracks.enabled` | Let the reward-track boxes progress and reset tracks (default off) |
| `hotel.wired.reward_tracks.allowed` | Tracks (`track`) or single tasks (`track:task`) wired may move, comma separated (default empty: none) |
| `hotel.wired.reward_tracks.max_per_window` | Task progress one user may get per track per window, across all rooms (default `50`) |
| `hotel.wired.reward_tracks.window_seconds` | Length of that window (default `3600`) |

---

## 3. Triggers

### `wf_trg_walks_on_furni`

- **Class:** `WiredTriggerHabboWalkOnFurni`
- **Behavior:** fires when a room unit steps onto a furni (the furni's walk-on hook, also one-way gates). Triggering user: the unit that moved. Source furni: the furni stepped on. With picked furni it matches the picked furni or any furni on the same tile as one of them, so a picked tile stack works whichever layer is on top.
- **Main settings:** int param 0 = furni source (`0` any furni stepped on, `100` picked furni, `200` furni chosen by the stack's selectors; other values fall back to `0`). With no int params the source is `100` when furni were sent, else `0`. Picked furni are only kept for source `100`. The editor offers up to `hotel.wired.furni.selection.count` furni (default 5).
- **Notes:** source `0` fires for every furni anyone walks onto in the room. Walk events that no stack listens to are not counted toward the room's wired rate limit (100 events of one type per 10 s by default), so busy floors do not get a room's wired banned.

### `wf_trg_walks_off_furni`

- **Class:** `WiredTriggerHabboWalkOffFurni`
- **Behavior:** fires when a room unit leaves a furni's tile (walking, rollers, teleports). Same matching as `wf_trg_walks_on_furni`: picked furni or anything on the same tile as a picked furni.
- **Main settings:** same as `wf_trg_walks_on_furni` (int param 0 = furni source `0`/`100`/`200`, picked furni).
- **Notes:** a user leaving the room while standing on the furni does not fire it (that walk-off call carries no movement data). Source `0` fires for every furni left anywhere in the room.

### `wf_trg_click_furni`

- **Class:** `WiredTriggerHabboClicksFurni`
- **Behavior:** fires when a user clicks a furni (the client's click-furni packet, handled in `ClickFurniEvent`). It runs straight away and is separate from the use/toggle handler. Triggering user: the clicker. Source furni: the clicked furni. Matching is by exact furni id, not by tile.
- **Main settings:** int param 0 = furni source: `0` = the trigger box itself, `100` = picked furni, `200` = selector furni. With no int params the source is `100` when furni were sent, else `0`. Picked furni are only kept for source `100`.
- **Notes:** with source `0` the stack fires only when someone clicks the wired box itself. To react to state changes caused by the click, use `wf_trg_stuff_state` / `wf_trg_state_changed`.

### `wf_trg_click_tile`

- **Class:** `WiredTriggerHabboClicksTile`
- **Behavior:** fires when a user clicks invisible click-tile furni (interaction `room_invisible_click_tile`). Subclass of `WiredTriggerHabboClicksFurni` with its own event, and the clicked furni must be a click tile.
- **Main settings:** as `wf_trg_click_furni` (int param 0 = furni source, picked furni). Picking anything other than click tiles fails with `wiredfurni.error.require_click_tiles`.
- **Notes:** source `0` (the box itself) can never match, because the box is not a click tile. Use picked tiles (`100`) or selectors (`200`).

### `wf_trg_click_user`

- **Class:** `WiredTriggerHabboClicksUser`
- **Behavior:** fires when a user clicks another user's avatar (`ClickUserEvent` or the wired user-selected packet). Triggering user: the clicker. The clicked user is on the event and can be reached with the "clicked user" source (`11`). Only users can be clicked targets. Clicking a bot or pet does not fire it.
- **Main settings:** int params `[blockMenuOpen, doNotRotate]` (each `1` = on, default off).
- **Notes:** the two options only apply when the stack's conditions pass. Block menu tells the client not to open the avatar menu. Do not rotate ignores the clicker's turn-to-look for 500 ms.

### `wf_trg_user_performs_action`

- **Class:** `WiredTriggerHabboPerformsAction`
- **Behavior:** fires when a user performs the chosen avatar action. Actions come from the action packet (wave, blow kiss, laugh, thumbs up, relax/sleep, awake), sit/stand/lay posture changes, the sign packet and the dance packet.
- **Main settings:** int params `[action, signFilter, signId, danceFilter, danceId]`. Action is one of `1` wave, `2` blow kiss, `3` laugh, `4` awake, `5` relax, `6` sit, `7` stand, `8` lay, `9` sign, `10` dance, `11` thumbs up (anything else becomes `1`). Sign id is 0–17 (default 0). Dance id is 1–4 (default 1). The id filters only apply when their flag is `1` and the action is sign or dance.
- **Notes:** pairs with the matching positive and negative action conditions, which read the last action cached on the user.

### `wf_trg_enter_room`

- **Class:** `WiredTriggerHabboEntersRoom`
- **Behavior:** fires when a user (not a bot) enters the room. It also fires when a user who used `:invisible` becomes visible again. Triggering user: the one who entered.
- **Main settings:** string param = optional username filter (case-insensitive exact match). Empty = anyone.
- **Notes:** fires before the room's own `habboEntered` handling.

### `wf_trg_leave_room`

- **Class:** `WiredTriggerHabboLeavesRoom`
- **Behavior:** fires when a user (not a bot) is removed from the room. It fires before the user is taken out, so the stack still sees them in the room at their last tile.
- **Main settings:** string param = optional username filter (case-insensitive). Empty = anyone.
- **Notes:** a user leaving while standing on furni does not also fire `wf_trg_walks_off_furni`.

### `wf_trg_says_something`

- **Class:** `WiredTriggerHabboSaysKeyword`
- **Behavior:** fires when a unit talks or shouts text that matches. Whispers never fire it. Bot speech fires it too (bot chat and the bot talk effects), and so does a chat command line. Text and keyword are compared lower-cased and trimmed.
- **Main settings:** int params `[matchMode, hideMessage, ownerOnly]`. Match mode: `0` contains (default), `1` exact, `2` any message (the keyword is cleared). String param = keyword.
- **Notes:** in modes 0/1 an empty keyword never matches. With owner only, just the room owner can fire it, and bots cannot. Hide message applies only when the stack would actually run (conditions pass): the speaker sees their line as a whisper to themselves and nobody else sees it.

### `wf_trg_clock_counter`

- **Class:** `WiredTriggerClockCounter`
- **Behavior:** fires when an up-counter clock (`InteractionGameUpCounter`) shows exactly the configured time. The counter raises the event on every tick, whole and half seconds, and also when it is adjusted onto a time (for example by `wf_act_adjust_clock`), not only on whole ticked seconds. Source furni: the counter.
- **Main settings:** int params `[minutes, halfSecondSteps, furniSource]`. Minutes are 0–99. Half-second steps are 0–119. Target = minutes × 60 s + steps × 0.5 s. Furni source `0` = any counter, `100` = picked counters, `200` = selector (default `100` when furni were sent, else `0`). Picked furni must be up-counters (`wiredfurni.error.require_counter_furni`), and more than `hotel.wired.furni.selection.count` fails the save.
- **Notes:** a tick only reaches the engine when some clock-counter trigger in the room matches it (right counter, right time), so a running clock does not count towards the room's rate limit. Related: `wf_act_control_clock`, `wf_act_adjust_clock`.

### `wf_trg_var_changed`

- **Class:** `WiredTriggerVariableChanged`
- **Behavior:** fires when a watched variable changes: a user, furni, context or room variable, or an internal (`@…`) variable written by wired. Triggering user: the user whose variable changed (user target) or the stack's user (context). Source furni: the furni (furni target). Checks run in order: target type, variable, change origin, array change (if any), created, deleted, then the value-change kind.
- **Main settings:** int params `[targetType, created, valueChanged, increased, decreased, unchanged, deleted, originMask]`. Target: `0` user, `1` furni, `2` context, `3` room. Missing flags default to on. Origin mask: `-1`/`0` = all; bits `1` wired, `2` web API, `4` creator tools. String param = `token` + optional `\t{"options":…,"fieldId":…}`. Token is `custom:<definition item id>` (a bare number is accepted) or `internal:<key>`.
- **Notes:** saving checks the variable exists for that target (`…validation.missing_variable` / `…invalid_variable`) and that at least one option is on. With value changed off, increased/decreased/unchanged are switched off. Room targets and internal variables cannot use created/deleted. For array variables the options bitmask is used instead: `1` created, `2` changed, then specific kinds `4` appended, `8` inserted, `16` removed, `32` index cleared, `64` replaced, `128` moved, `256` swapped, `512` field changed (optionally only `fieldId`), `1024` length changed, `2048` cleared, `4096` shuffled. Changed with no specific kind = any change. A stack that writes the variable it watches loops until the execution guard stops it.

### `wf_trg_periodically`

- **Class:** `WiredTriggerRepeater`
- **Behavior:** fires every N milliseconds on the global wired tick (50 ms by default). It fires when `tick × tickInterval` is a multiple of the interval, so every repeater with the same interval fires on the same tick, however long ago it was placed. No triggering user.
- **Main settings:** int param 0 = interval in 0.5 s steps. Stored as ms, 500 ms to 24 h. A value below 1 step becomes 500 ms. Default 5 s. Unreadable stored values load as 10 s. Saving without a param fails.
- **Notes:** an execution-limit extra (`WiredExtraExecutionLimit`) on the stack is checked before each firing. The editor lists effects in the stack that need a triggering user.

### `wf_trg_period_short`

- **Class:** `WiredTriggerRepeaterShort`
- **Behavior:** as `wf_trg_periodically` with 50 ms steps and its own event.
- **Main settings:** int param 0 = interval in 50 ms steps, clamped to 50–500 ms. Default 500 ms. A stored value below 50 ms loads as 500 ms. A stored value above 500 ms is clamped to 500 ms.
- **Notes:** with a tick interval other than 50 ms (`wired.tick.interval.ms`), intervals that are not a multiple of it fire less often than set.

### `wf_trg_period_long`

- **Class:** `WiredTriggerRepeaterLong`
- **Behavior:** as `wf_trg_periodically` with 5 s steps and its own event.
- **Main settings:** int param 0 = interval in 5 s steps, 5 s to 24 h. Default 50 s. Unreadable stored values load as 100 s.

### `wf_trg_state_changed`

- **Class:** `WiredTriggerFurniStateUpdated`
- **Behavior:** fires on any state update of a watched furni, whoever or whatever caused it. That includes a user toggling it, the wired toggle / random toggle / match-furni / moodlight effects, pressure plates changing between free and occupied, and dice or colour wheels landing (after a user roll or `wf_act_roll_dice`). On a user click the event is raised after the new state is set, so conditions see the new state. Triggering user: the clicking or stack user, none for plates and dice.
- **Main settings:** same dialog and stored data as `wf_trg_stuff_state`: int params `[mode, furniSource]`. Mode `0` = any state, `1` = only when the furni reaches the state saved for it. Furni source `0` = any furni, `100` = picked, `200` = selector. Picked furni plus their state at save time.
- **Notes:** subclass of `WiredTriggerFurniStateToggled` with its own event (dialog code 32). A stack that toggles a furni it watches fires itself again until the execution guard stops the loop. Updates that no stack listens to (plates, dice) are not counted toward the rate limit.

### `wf_trg_stuff_state`

- **Class:** `WiredTriggerFurniStateToggled`
- **Behavior:** fires when a user toggles a furni by clicking it: multi-state furni (not dice), game timers, wired boxes, highscore boards and multi-height furni. The event is raised after the new state is set, so conditions see the state the click made. Changes made by wired effects never fire it. Triggering user: the clicker.
- **Main settings:** int params `[mode, furniSource]`. Mode `0` = any state change (default), `1` = only when the furni's new state equals the state captured for it at save time. Furni source `0` any furni, `100` picked, `200` selector (default `100` when furni were sent). Picked furni are saved with their current state.
- **Notes:** with mode `1`, only picked furni can match, because a furni with no saved state never matches. Dialog code 4. Use `wf_trg_state_changed` to also react to effect, plate and dice changes.

### `wf_trg_at_given_time`

- **Class:** `WiredTriggerAtSetTime`
- **Behavior:** one-shot timer that fires once after the set time. It counts wired ticks from when the box is registered (room load or placement), saved, or reset by `wf_act_reset_timers`, then waits for the next reset. No triggering user.
- **Main settings:** int param 0 = delay in 0.5 s steps, 500 ms to 24 h. Unreadable stored values load as 10 s. Saving without a param fails. Saving restarts the count.
- **Notes:** when an execution-limit extra refuses the firing, it tries again on every tick until it is allowed.

### `wf_trg_at_time_long`

- **Class:** `WiredTriggerAtTimeLong`
- **Behavior:** long version of `wf_trg_at_given_time`: fires once, a number of 5 s steps after the box is armed or reset, with its own event.
- **Main settings:** int param 0 = delay in 5 s steps (5 s to 24 h). Dialog code 30, with its own window.
- **Notes:** until 2026-09-06 it used the half-second steps and dialog of `wf_trg_at_given_time`. Stored values are milliseconds, so boxes saved before keep their delay, and only the number the dialog shows changed.

### `wf_trg_collision`

- **Class:** `WiredTriggerCollision`
- **Behavior:** fires when furni moved by wired runs into a room unit. That happens when a chasing furni (`wf_act_chase`) ends next to a unit, when a fleeing furni (`wf_act_flee` and its aliases) has a unit within one tile (after 500 ms), or when a `wf_act_move_to_dir` furni is blocked by a user, bot or pet. Triggering user: the unit hit. Source furni: the furni that collided.
- **Main settings:** none (no params are saved).
- **Notes:** it does not filter who collided. Every collision stack in the room fires for every collision. Chase or flee combined with collision loops easily, and the recursion and rate guards cut such loops off.

### `wf_trg_game_starts`

- **Class:** `WiredTriggerGameStarts`
- **Behavior:** fires when a room game starts: a game timer or up-counter is started by a user click, by `wf_act_control_clock`, or by a wired restart. Room-wide, with no triggering user.
- **Main settings:** none.
- **Notes:** the editor lists effects in the stack that need a triggering user.

### `wf_trg_game_ends`

- **Class:** `WiredTriggerGameEnds`
- **Behavior:** fires when a room game ends: the timer runs out, a counter is stopped or reset while active, time is added while paused, or `wf_act_control_clock` ends it. Room-wide, with no triggering user.
- **Main settings:** none.

### `wf_trg_bot_reached_stf`

- **Class:** `WiredTriggerBotReachedFurni`
- **Behavior:** fires when a bot walks onto a furni. It matches when the event's bot fits the bot setting and the reached furni (or its tile stack) is in the furni source.
- **Main settings:** string param = bot name. Int params `[furniSource, botSource]`. Furni source `0`/`100`/`200` as for walk-on. Bot source `100` = by name (default), `200` = bots chosen by the stack's selectors. Picked furni.
- **Notes:** raised from the furni's walk-on handler whenever a bot steps onto it, alongside `wf_trg_walks_on_furni`. The step only reaches the engine when a bot-reached-furni trigger in the room matches it, so roaming bots do not count towards the room's rate limit.

### `wf_trg_bot_reached_avtr`

- **Class:** `WiredTriggerBotReachedHabbo`
- **Behavior:** fires when a bot reaches a user. That happens with the bot follow effect (once when the bot comes within 2 tiles, again only after they separate), the bot give-hand-item effect, and a butler bot serving. The event's actor is the bot. The triggering user for effects and conditions is the user who was reached.
- **Main settings:** string param = bot name. Int param 0 = bot source: `100` by name (default), `200` bots chosen by selectors.
- **Notes:** the same walk-to routine runs when a user walks over to hand an item to someone. Then the walker is a user and does not match a bot name.

### `wf_trg_score_achieved`

- **Class:** `WiredTriggerScoreAchieved`
- **Behavior:** fires when a player's score change makes their team's total go from below the target to the target or above (`total − added < target ≤ total`). A player outside any team counts on their own. The amount added is what really changed, because a score cannot go below 0. Triggering user: the player who scored.
- **Main settings:** int params `[score, team]`. Score is clamped 0–1,000,000, and saving without it fails. Team `0` = any (default), `1`–`4` = red, green, blue, yellow, and only fires when the scoring player is on that team.
- **Notes:** it compares the team total, not the player's own score. A total that drops and rises again past the target fires again.

### `wf_trg_game_team_win`

- **Class:** `WiredTriggerTeamWins`
- **Behavior:** fires at game end for each member of the winning team, once per player, with that player as triggering user. The winner is the team with the highest total. It only fires when the teams scored more than 0 points in total. On a tie the first team found keeps the win.
- **Main settings:** none (dialog code 29, shared with `wf_trg_game_team_lose`).
- **Notes:** subclass of `WiredTriggerGameStarts`. Both team-result boxes share one legacy type, and `matches` accepts only `TEAM_WINS`.

### `wf_trg_game_team_lose`

- **Class:** `WiredTriggerTeamLoses`
- **Behavior:** fires at game end for each member of every team except the winner, once per player, with that player as triggering user. It fires only when a winner was found, that is when some points were scored.
- **Main settings:** none (dialog code 29).
- **Notes:** `matches` accepts only `TEAM_LOSES`.

### `wf_trg_recv_signal`

- **Class:** `WiredTriggerReceiveSignal`
- **Behavior:** fires when `wf_act_send_signal` sends a signal to an antenna this box listens to. The signal channel is the antenna's furni id. The event carries the sender's user and furni, the forwarded user and furni sets, a copy of the context variables and the call-stack depth. The box flashes for 300 ms when it fires.
- **Main settings:** int params `[channel, …, furniSource]`: first = channel, last = furni source (`100` picked antennas, default; `200` selector antennas). Picked furni must have interaction `antenna` ("You can only select antenna furni."). With picked antennas, the channel becomes the first antenna's id.
- **Notes:** not a user trigger (`isTriggeredByRoomUnit` is false), but a triggering user is present when the signal carries one. Picking up an antenna unlinks it and moves the channel to the next picked antenna. The editor shows how many senders target this receiver.

### `wf_trg_starts_dancing`

- **Class:** `WiredTriggerHabboStartsDancing`
- **Behavior:** fires when a room unit goes from not dancing to dancing (`RoomUnitManager.dance`). Triggering user: the dancer.
- **Main settings:** none (dialog code 11).
- **Notes:** switching from one dance to another does not fire it.

### `wf_trg_stops_dancing`

- **Class:** `WiredTriggerHabboStopsDancing`
- **Behavior:** fires when a dancing room unit stops dancing through `RoomUnitManager.dance`. That includes going idle while dancing. Triggering user: that unit.
- **Main settings:** none (dialog code 11).
- **Notes:** sitting or lying on furni clears the dance directly and does not fire it.

### `wf_trg_idles`

- **Class:** `WiredTriggerHabboIdles`
- **Behavior:** fires when a user goes idle: the automatic sleep timeout (only when not dancing), the relax/sleep action, or `:afk`. Users only. Triggering user: that user.
- **Main settings:** none (dialog code 11).

### `wf_trg_unidles`

- **Class:** `WiredTriggerHabboUnidles`
- **Behavior:** fires when an idle user becomes active again, for example by walking, chatting, an action or a wired teleport. It only fires if the user really was idle. Users only.
- **Main settings:** none (dialog code 11).

### `wf_trg_transaction_complete`

- **Class:** `WiredTriggerTransactionComplete`
- **Behavior:** fires when a wired transaction completes. That happens when `wf_act_init_transaction` succeeds (or runs with no contracts), or when a wired trade negotiation settles. It is raised as a new event after the running stack. Every such trigger in the room fires. Triggering user: the transaction's user.
- **Main settings:** none (dialog code 27).
- **Notes:** paired with `wf_trg_transaction_fail`.

### `wf_trg_transaction_fail`

- **Class:** `WiredTriggerTransactionFail`
- **Behavior:** fires when a wired transaction fails. That happens when `wf_act_init_transaction` finds a precondition it cannot meet (it commits nothing), when `wf_act_cancel_transaction` runs, or when a trade negotiation is cancelled or refused. Every such trigger in the room fires. Triggering user: the transaction's user.
- **Main settings:** none (dialog code 28).

### `wf_trg_dice_rolled`

- **Class:** `WiredTriggerDiceRolled`
- **Behavior:** fires when a dice furni (`InteractionDice`) lands on its value, after a user roll or `wf_act_roll_dice`. Colour wheels do not fire it. Source furni: the dice. No triggering user.
- **Main settings:** int param 0 = required value. `0` = any roll (default), negative becomes `0`. Otherwise it fires only when the dice's value equals it.
- **Notes:** dialog code 24. The same landing also raises `wf_trg_state_changed`.

### `wf_trg_press_keybind`

- **Class:** `WiredTriggerPressKeybind`
- **Behavior:** fires when the client reports a key press in the room (packet 9311, `PressKeybindEvent`, rate limit 100). Triggering user: the one who pressed it.
- **Main settings:** int param 0 = key code. `0` = any key (default), negative becomes `0`.
- **Notes:** dialog code 26. The client must send the key-press packet.

### `wf_trg_user_gets_handitem`

- **Class:** `WiredTriggerUserGetsHandItem`
- **Behavior:** fires when a room unit is given a hand item with id > 0 through `RoomUnitManager.giveHandItem`. That covers the give-hand-item effect, `:handitem`, butler bots and so on. Clearing the hand does not fire it. Triggering user: the receiver.
- **Main settings:** int param 0 = hand item id. `0` = any (default), negative becomes `0`.
- **Notes:** dialog code 25.

### `wf_trg_click_bot`

- **Class:** `WiredTriggerHabboClicksUser`
- **Behavior:** alias of `wf_trg_click_user`; same runtime.
- **Notes:** the click handlers only accept users as the clicked target, so clicking a bot does not fire it.

### `wf_trg_double_click_furni`

- **Class:** `WiredTriggerHabboClicksFurni`
- **Behavior:** alias of `wf_trg_click_furni`; same runtime.
- **Notes:** there is no double-click detection, so every single click fires it.

### `wf_trg_anti_afk`

- **Class:** `WiredTriggerHabboUnidles`
- **Behavior:** alias of `wf_trg_unidles`; same runtime.

### `wf_trg_username_as_trigger`

- **Class:** `WiredTriggerUsernameAsTrigger`
- **Behavior:** fires when a user's talk or shout contains their own username (case-insensitive, trimmed). Users only, no whispers. It has its own event, raised from the same chat line as `wf_trg_says_something`.
- **Main settings:** saved like `wf_trg_says_something` (int params `[matchMode, hideMessage, ownerOnly]`, string param). Match mode and keyword are stored but ignored. Hide message and owner only apply.
- **Notes:** subclass of `WiredTriggerHabboSaysKeyword`. Dialog code 31, so the client leaves out the keyword and match controls.

### `wf_trg_cnd_collision`

- **Class:** `WiredTriggerCollision`
- **Behavior:** alias of `wf_trg_collision`; same runtime.

### `wf_trg_user_exits_room`

- **Class:** `WiredTriggerHabboLeavesRoom`
- **Behavior:** alias of `wf_trg_leave_room`; same runtime.

### `wf_trg_exact_keyword`

- **Class:** `WiredTriggerHabboSaysKeyword`
- **Behavior:** alias of `wf_trg_says_something`; same runtime.
- **Notes:** the default match mode is still "contains". Exact matching needs match mode `1` saved in the dialog.

### `wf_trg_says_command`

- **Class:** `WiredTriggerHabboSaysKeyword`
- **Behavior:** alias of `wf_trg_says_something`; same runtime.

### `wf_trg_habbo_says_command`

- **Class:** `WiredTriggerHabboSaysKeyword`
- **Behavior:** alias of `wf_trg_says_something`; same runtime.

### `wf_trg_other_collides_user`

- **Class:** `WiredTriggerCollision`
- **Behavior:** alias of `wf_trg_collision`; same runtime.
- **Notes:** it does not check who collides with whom.

### `wf_trg_user_collides_bot`

- **Class:** `WiredTriggerCollision`
- **Behavior:** alias of `wf_trg_collision`; same runtime.
- **Notes:** it does not check that the unit is a bot.

### `wf_trg_user_collides_other`

- **Class:** `WiredTriggerCollision`
- **Behavior:** alias of `wf_trg_collision`; same runtime.

---

## 4. Effects

### `wf_act_give_credits`

- **Class:** `WiredEffectGiveCredits`
- **Behavior:** gives the configured number of credits to every user the user source resolves (bots and pets are skipped).
- **Main settings:** string param = amount (positive integer, capped at `hotel.wired.reward.max_amount`, default 1000, hard ceiling 100000); int[0] = user source (default triggering user); delay. Uses the shared amount dialog (`EFFECT_AMOUNT`, code 118).
- **Notes:** saving needs `ACC_SUPERWIRED` unless `hotel.wired.reward.require_permission = 0`; the gate is on save, not on firing. An empty, zero or non-numeric amount refuses the save. A stored amount above the current cap is clamped on load.

### `wf_act_give_duckets`

- **Class:** `WiredEffectGiveDuckets`
- **Behavior:** gives duckets (pixels) to every resolved user.
- **Main settings:** same as `wf_act_give_credits`: amount string, int[0] = user source, delay.
- **Notes:** same reward-permission gate and amount cap as `wf_act_give_credits`.

### `wf_act_give_diamonds`

- **Class:** `WiredEffectGiveDiamonds`
- **Behavior:** gives diamonds to every resolved user as points of type `seasonal.currency.diamond` (default 5).
- **Main settings:** same as `wf_act_give_credits`: amount string, int[0] = user source, delay.
- **Notes:** same reward-permission gate and amount cap as `wf_act_give_credits`.

### `wf_act_give_badge`

- **Class:** `WiredEffectGiveBadge`
- **Behavior:** gives a badge code to every resolved user who does not already own it, and tells their client.
- **Main settings:** string param = badge code (trimmed, must not be empty); int[0] = user source; delay. Dialog type `EFFECT_BADGE` (119).
- **Notes:** saving sits behind the same reward-permission gate as the currency boxes (`ACC_SUPERWIRED` unless `hotel.wired.reward.require_permission = 0`), because any badge code, staff badges included, can be granted. Legacy tab-separated rows (`delay<TAB>badge`) still load.

### `wf_act_give_userbadge`

- **Class:** `WiredEffectGiveBadge`
- **Behavior:** alias of `wf_act_give_badge`; same runtime.

### `wf_act_remove_badge`

- **Class:** `WiredEffectRemoveBadge`
- **Behavior:** removes a badge code from every resolved user who owns it (inventory and database), then refreshes their badge inventory and the badges they wear in the room.
- **Main settings:** string param = badge code (trimmed, not empty); int[0] = user source; delay. Dialog type `EFFECT_BADGE`.
- **Notes:** same reward-permission gate as `wf_act_give_badge`.

### `wf_act_give_achievement`

- **Class:** `WiredEffectGiveAchievement`
- **Behavior:** adds one progress step to the named achievement for every resolved user.
- **Main settings:** string param = achievement name (trimmed, not empty); int[0] = user source; delay. Dialog type `EFFECT_TEXT` (123).
- **Notes:** the name is not checked on save; an unknown achievement does nothing at run time. Saving, placing or picking up the box resends the room's `WiredEnvironment` packet, which lists the achievement. Saving sits behind the reward-permission gate (`ACC_SUPERWIRED` unless `hotel.wired.reward.require_permission = 0`); boxes saved before keep working.

### `wf_act_progress_achievement`

- **Class:** `WiredEffectProgressAchievement`
- **Behavior:** Habbo's progress-achievement action. Progresses one achievement for every resolved user: mode 1 adds the amount, mode 0 raises the progress to the amount (only the missing part is added). The work runs on the worker pool, not the wired thread.
- **Main settings:** string param = achievement name (one name, `[A-Za-z0-9_-]`, up to 64); int params `[mode (1 add / 0 raise to, anything else 1), amount (1..1000000), user source (0, 11, 200 or 201; anything else 0)]`; delay. Dialog type `PROGRESS_ACHIEVEMENT` (150).
- **Notes:** hotel-wide rewards, so every gate must pass: `hotel.wired.achievements.enabled` on; the name on `hotel.wired.achievements.allowed`; an achievement enabler (`wf_xtra_achievement_enabler`) in the same room names it; the user is still in the room when the worker runs; and the user has allowance left (`max_per_window` per achievement per `window_seconds`, shared by all rooms). At most 50 users per firing. Disabled, archived and off-season achievements do not move (`AchievementManager`). Saving needs the reward permission.

### `wf_act_progress_reward_track`

- **Class:** `WiredEffectProgressRewardTrack`
- **Behavior:** Habbo's progress-reward-track action. Moves one task of a running reward track for every resolved user: with "add to existing" the amount is added, otherwise the progress becomes the amount (lower is allowed). Task levels pay their points as in play, but a level pays only once: each task keeps its highest count (`users_reward_track_tasks.peak_count`) and only climbing past it pays.
- **Main settings:** string param = `track id<TAB>task id` (each 1..64 of letters, digits, `_`, `-`, `.`); int params `[add to existing (0/1), amount (1..1000000), user source]`; delay. Dialog type `PROGRESS_REWARD_TRACK` (152).
- **Notes:** `hotel.wired.reward_tracks.enabled` must be on and the track (`season_1`) or the task (`season_1:games`) listed in `hotel.wired.reward_tracks.allowed`; the user must still be in the room; units above the current progress count against `max_per_window` per track. A premium task does not move for a user without the pass. Tasks with action type `wired` are moved only by this box, never by play. Saving needs the reward permission; the work runs on the worker pool.

### `wf_act_reset_reward_track`

- **Class:** `WiredEffectResetRewardTrack`
- **Behavior:** Habbo's reset-reward-track action. Puts the resolved users' tasks of a track back to zero: only the tasks the allow-list lets wired move. Points, claimed prizes and the peak counts stay, so a reset never pays a level twice.
- **Main settings:** string param = track id; int params `[user source]`; delay. Dialog type `RESET_REWARD_TRACK` (153).
- **Notes:** same switch and allow-list as `wf_act_progress_reward_track`; nothing happens for a track that is not running. Saving needs the reward permission.

### `wf_act_give_experience`

- **Class:** `WiredEffectGiveExperience`
- **Behavior:** adds the configured amount to the achievement score of every resolved user.
- **Main settings:** amount string, int[0] = user source, delay (same layout as `wf_act_give_credits`).
- **Notes:** same reward-permission gate and amount cap as `wf_act_give_credits`.

### `wf_act_say_command`

- **Class:** `WiredEffectSayCommand`
- **Behavior:** runs a chat command as each resolved user who has a connected client, as if they had typed it (`:` is added when missing).
- **Main settings:** string param = command line (trimmed, not empty); int[0] = user source; delay. Dialog type `EFFECT_TEXT`. The saver's user id is stored as the permission principal.
- **Notes:** the command must exist. The saver needs the command's own permission, and anything other than sit, stand, lay, dance, moonwalk and handitem also needs `ACC_SUPERWIRED`. The same check is repeated at run time against the stored principal (online or offline). A command that fails it is logged and skipped.

### `wf_act_open_habbo_pages`

- **Class:** `WiredEffectOpenHabboPages`
- **Behavior:** pushes an in-client link (for example `habbopages/...` or `catalog/open/...`) to every resolved user's client.
- **Main settings:** string param = link (trimmed, leading `/` removed, not empty); int[0] = user source; delay. Dialog type `EFFECT_TEXT`.
- **Notes:** anyone may use the safe namespaces (catalog, habbopages, navigator, inventory, avatar-editor, games, groups, help and others). Any other namespace needs `ACC_SUPERWIRED` unless `hotel.wired.link.restrict_namespaces = 0`. The check runs on save only; stored links are not re-checked on load.

### `wf_act_make_user_say`

- **Class:** `WiredEffectMakeUserSay`
- **Behavior:** makes each resolved user say the message as their own chat bubble. With visibility "room" it goes through normal room chat (distance, ignore lists and tents apply). With visibility "source users" only the speaker sees the bubble.
- **Main settings:** string param = message; int params `[user source, visibility (0 source users / 1 whole room), bubble style, bubble width (-1 room setting, 0/1/2 wide/normal/thin)]`; delay. Uses the show-message dialog (code 7).
- **Notes:** the message is word-filtered unless the saver has `ACC_SUPERWIRED`, and capped at `hotel.wired.show_message.max_length` (200) characters and `hotel.wired.show_message.max_lines` (8) lines. Placeholders: `%user%`, `%online_count%`, `%room_count%`, plus the username placeholders. Idle speakers are woken up.

### `wf_act_log`

- **Class:** `WiredEffectLog`
- **Behavior:** writes a line into the room's wired log (the room log window) at the configured level. Username placeholders are filled in, and the names of the source users are appended as ` [a, b, ...]`.
- **Main settings:** string param = message (trimmed, not empty, cut to 400 characters); int params `[log level 0 debug / 1 info / 2 warning / 3 error (clamped, default 1), user source (0, 11, 200 or 201; anything else becomes 0)]`; delay. Dialog type `WRITE_TO_LOGS` (137).
- **Notes:** after placeholders the line is capped at 1000 characters. At most 10 users are named, then `+N`. Nothing is appended when the source resolves no one. It never needs a triggering user. Boxes saved before the level existed load as info.

### `wf_act_walk_to_furni`

- **Class:** `WiredEffectWalkToFurni`
- **Behavior:** each resolved user walks to the tile of a randomly chosen furni from the furni source. With the teleport flag on, the user is fast-teleported there instead (see `wf_act_teleport_to`).
- **Main settings:** int params `[teleport 0/1, furni source, user source]` (older two-int form `[furni source, user source]` is also accepted); picked furni up to `hotel.wired.furni.selection.count` (default 5); delay. Dialog type `WALK_TO_FURNI` (115).
- **Notes:** picking furni while the furni source is "triggering furni" switches it to picked furni. Picked furni that have left the room are dropped.

### `wf_act_sit`

- **Class:** `WiredEffectSit`
- **Behavior:** makes every resolved user sit where they stand (walking is stopped first), then optionally whispers them the message.
- **Main settings:** string param = optional message (cut to `hotel.wired.message.max_length`, 100); int[0] = user source; delay. Dialog type `USER_TARGET` (116).
- **Notes:** users who are riding a pet or standing in water are skipped. The whisper supports `%user%`, `%online_count%`, `%room_count%` and the username placeholders.

### `wf_act_lay`

- **Class:** `WiredEffectLay`
- **Behavior:** makes every resolved user lie down like `:lay`: the body turns to the nearest even direction, and the user lies only if the 3 tiles in front are walkable. The optional message is whispered.
- **Main settings:** same as `wf_act_sit`.
- **Notes:** same skips (riding, water) as `wf_act_sit`. A user blocked by the tiles in front gets no lay status and no whisper.

### `wf_act_make_fast_walk`

- **Class:** `WiredEffectMakeFastWalk`
- **Behavior:** turns on fast walking for every resolved unit and whispers the optional message to users.
- **Main settings:** same as `wf_act_sit`.
- **Notes:** this box cannot turn fast walking off again.

### `wf_act_toggle_moodlight`

- **Class:** `WiredEffectToggleMoodlight`
- **Behavior:** switches every moodlight in the room on or off, using the room's enabled preset (or a default preset). It fires the furni-state-changed trigger.
- **Main settings:** delay only. Uses the reset-timers dialog.

### `wf_act_reset_highscores`

- **Class:** `WiredEffectResetHighscores`
- **Behavior:** clears every wired highscore board in the room (memory and `items_highscore_data`) and refreshes them.
- **Main settings:** delay only. Uses the reset-timers dialog.

### `wf_act_move_user_tiles`

- **Class:** `WiredEffectMoveUserTiles`
- **Behavior:** moves each resolved unit up to N tiles in one direction, one tile at a time, and/or turns them. It stops at the first tile it cannot enter. A walking unit is only turned. If nothing moved, the rotation is still applied.
- **Main settings:** int params `[move direction 0-7 or -1 none, rotation 0-7 / 8 clockwise / 9 counter-clockwise / -1 none, user source, tile count 1-10 (default 1)]`; delay. At least 3 ints are required. Dialog type `MOVE_USER_TILES` (117).
- **Notes:** 495 ms cooldown. Uses the stack's user-movement physics and the animation-time / no-animation add-ons.

### `wf_act_all_users_leave_team`

- **Class:** `WiredEffectAllUsersLeaveTeam`
- **Behavior:** with the default source (triggering user), every user in the room leaves their current game team. With any other source, only the users that source resolves leave.
- **Main settings:** int[0] = user source; delay. Dialog type `ALL_USERS_LEAVE_TEAM` (124).

### `wf_act_neg_show_message`

- **Class:** `WiredEffectNegativeShowMessage`
- **Behavior:** same as `wf_act_show_message`, but it runs only when the stack has conditions and they fail (the negative branch).
- **Main settings:** same as `wf_act_show_message`.
- **Notes:** uses the show-message dialog (code 7). The planner puts it in the negative branch because of its `NEG_SHOW_MESSAGE` type.

### `wf_act_neg_log`

- **Class:** `WiredEffectNegativeLog`
- **Behavior:** same as `wf_act_log`, but it runs only when the stack has conditions and they fail.
- **Main settings:** same as `wf_act_log`.
- **Notes:** it shares the `WRITE_TO_LOGS` type with `wf_act_log`. The planner recognises the negative branch by its class.

### `wf_act_give_look`

- **Class:** `WiredEffectGiveLook`
- **Behavior:** sets the figure of every resolved user to the configured look, like `:mimic`. The look is validated for each user's gender, sent to the user, broadcast to the room, and saved on the user's next data save.
- **Main settings:** string param = figure string (trimmed, not empty, cut to 256 characters); int[0] = user source; delay. Dialog type `EFFECT_TEXT`.
- **Notes:** a figure that fails validation for a user's gender is skipped for that user.

### `wf_act_add_tag`

- **Class:** `WiredEffectAddTag`
- **Behavior:** adds a profile tag to every resolved user who does not already have it, saves it, and broadcasts the user's tags to the room.
- **Main settings:** string param = tag (trimmed, not empty, cut to 38 characters); int[0] = user source; delay. Dialog type `EFFECT_TAG` (120).
- **Notes:** profile tags are always permanent.

### `wf_act_add_tag_perm`

- **Class:** `WiredEffectAddTag`
- **Behavior:** alias of `wf_act_add_tag`; same runtime.
- **Notes:** the tag is persisted exactly as with `wf_act_add_tag`.

### `wf_act_remove_tag`

- **Class:** `WiredEffectRemoveTag`
- **Behavior:** removes a profile tag (case-insensitive) from every resolved user who has it, saves the change, and rebroadcasts their tags.
- **Main settings:** same as `wf_act_add_tag`.

### `wf_act_toggle_state`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** moves each resolved furni to its next state (or its previous state), wrapping around, and fires the furni-state-changed trigger. Furni whose state is not a number get a normal click instead (next only).
- **Main settings:** int params `[toggle type 0 next / 1 previous, furni source]` (a single int is read as the furni source); picked furni up to the selection limit; delay. Dialog type `TOGGLE_STATE` (0).
- **Notes:** only furni with more than one state change. Wired boxes, teleports, rollers, gates, game tiles, pet items, gifts, vending machines, switches and similar types are skipped, and are dropped from the picks. Freeze blocks, freeze tiles and crackables are dropped from picks on load.

### `wf_act_reset_timers`

- **Class:** `WiredEffectResetTimers`
- **Behavior:** restarts every wired timer in the room (for example the "at set time" triggers) and records the time of the reset.
- **Main settings:** delay only.

### `wf_act_match_to_sshot`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** restores furni to the snapshot taken on save. Each part is optional: state, direction, position and altitude. With picked furni, each one returns to its own snapshot. With another source, each resolved furni uses the snapshot of a picked furni of the same type, preferring one saved in the target's current state.
- **Main settings:** int params `[state, direction, position, altitude, furni source]` (with only 4 ints: `[state, direction, position, furni source]` and no altitude); picked furni (the snapshot records state, rotation, x, y and z at save time); delay. Dialog type `MATCH_SSHOT` (3).
- **Notes:** altitude without position restores the height, and the direction too when direction is on. Moves go through the wired movement path (physics, carry and animation add-ons). State is restored only for furni that allow a wired state reset. Picking furni while the source is "triggering furni" switches it to picked furni.

### `wf_act_move_rotate`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** moves each resolved furni one tile and/or rotates it. Movement: 1 random N/E/S/W, 2 random east/west, 3 random north/south, 4-7 S/E/N/W, 8-11 diagonals. Rotation: 1 clockwise, 2 counter-clockwise, 3 random.
- **Main settings:** int params `[movement 0-11, rotation 0-3, furni source]`; picked furni up to the selection limit (a save over the limit is refused); delay. Dialog type `MOVE_ROTATE` (4).
- **Notes:** each furni moves at most once per room cycle. Rotating in place is allowed even when a user stands on the furni. Moves go through the wired movement path (physics, carry and animation add-ons).

### `wf_act_give_score`

- **Class:** `WiredEffectGiveScore`
- **Behavior:** adds points to (or removes points from) the game score of each resolved user who is currently playing a game in the room.
- **Main settings:** int params `[score 1-100, operation 0 add / 1 remove, user source]`; delay. Dialog type `GIVE_SCORE` (6).
- **Notes:** users who are not in a game are skipped.

### `wf_act_show_message`

- **Class:** `WiredEffectWhisper`
- **Behavior:** whispers the message in a wired bubble, either to the users the source resolves or to everyone in the room. In "whole room" mode, the placeholders are filled in from the first source user.
- **Main settings:** string param = message; int params `[user source, visibility (0 source users / 1 whole room), bubble style, bubble width (-1 room setting, 0/1/2 wide/normal/thin)]`; delay. Dialog type `SHOW_MESSAGE` (7).
- **Notes:** word-filtered unless the saver has `ACC_SUPERWIRED`. Capped at `hotel.wired.show_message.max_length` (200) characters and `hotel.wired.show_message.max_lines` (8) lines. Placeholders: `%user%`, `%online_count%`, `%room_count%` and the username placeholders. Each recipient gets a message at most once per event. The bubble width travels as an optional trailing int on the chat packet, which older clients ignore.

### `wf_act_teleport_to`

- **Class:** `WiredEffectTeleport`
- **Behavior:** teleports each resolved unit to the tile of a randomly chosen furni from the furni source. Effect 4 is shown, and the unit lands after `wired.effect.teleport.delay` ms (default 500). A rider brings their pet along.
- **Main settings:** int params `[fast teleport 0/1, furni source, user source]` (the two-int form `[furni source, user source]` is also accepted); picked furni up to the selection limit; delay. Dialog type `TELEPORT` (8).
- **Notes:** fast teleport lands after max(75, delay / 5) ms. A blocked or invalid target tile falls back to a free tile next to it. A unit that is already teleporting is skipped. 50 ms box cooldown.

### `wf_act_join_team`

- **Class:** `WiredEffectJoinTeam`
- **Behavior:** puts each resolved user into a team of the chosen game type, creating the game if needed. The join mode picks the team: the chosen one, the one with the fewest members, or a random one of the four. A user already in a different game type or team is removed from it first.
- **Main settings:** int params `[team type 0 wired / 1 Banzai / 2 Freeze, team 1-4, user source, join mode 0 chosen / 1 smallest / 2 random]`. The older forms `[team type, team, user source]` and `[team, user source]` (the wired game) join the chosen team; delay. Dialog type `JOIN_TEAM` (9).
- **Notes:** a team outside 1-4 refuses the save, even in the smallest and random modes; an unknown join mode saves as chosen. "Smallest" counts the other members of each team, so users are spread in turn; a tie keeps a user in their own team, otherwise red, green, blue, yellow is the order. Random re-rolls on every run, like Habbo's.

### `wf_act_leave_team`

- **Class:** `WiredEffectLeaveTeam`
- **Behavior:** removes each resolved user from the game they are in.
- **Main settings:** int[0] = user source; delay. Dialog type `LEAVE_TEAM` (10).

### `wf_act_chase`

- **Class:** `WiredEffectMoveFurniTowards`
- **Behavior:** each resolved furni looks up to 3 tiles in the four straight directions for any unit (user, bot or pet) and steps one tile toward the first one it finds. With no one in sight, it wanders randomly and never turns straight back.
- **Main settings:** int[0] = furni source; picked furni up to the selection limit; delay. Dialog type `CHASE` (11).
- **Notes:** a unit right next to the furni fires the collision trigger, and the furni does not move that time. Only N/E/S/W steps are used. 495 ms cooldown. Moves go through the wired movement path.

### `wf_act_flee`

- **Class:** `WiredEffectMoveFurniAway`
- **Behavior:** each resolved furni steps one tile away from the nearest unit in the room. It tries the axis with the larger distance first, then the other axis when the first is blocked.
- **Main settings:** int[0] = furni source; picked furni up to the selection limit; delay. Dialog type `FLEE` (12).
- **Notes:** a unit within one tile fires the collision trigger (after 500 ms), and the furni still flees. A unit on the furni's own tile gives no direction. 495 ms cooldown.

### `wf_act_move_to_dir`

- **Class:** `WiredEffectChangeFurniDirection`
- **Behavior:** each resolved furni moves one tile per firing in its remembered direction, starting from the configured start direction. When the next tile is blocked, it turns by the blocked action and tries again, up to 8 times.
- **Main settings:** int params `[start direction 0-7, blocked action 0 wait / 1 right 45 / 2 right 90 / 3 left 45 / 4 left 90 / 5 turn back / 6 random, furni source, block on user collision 0/1]`; picked furni up to the selection limit; delay. Dialog type `MOVE_DIRECTION` (13).
- **Notes:** a unit on the target tile fires the collision trigger. With "block on user collision" on, a user in the way does not make the furni turn; it waits. With it off, a user counts as a blocked tile. The direction is remembered per furni (stored for picked furni, kept in memory for other sources). 495 ms cooldown.

### `wf_act_give_score_tm`

- **Class:** `WiredEffectGiveScoreToTeam`
- **Behavior:** adds points to (or removes points from) the chosen team in every running game in the room.
- **Main settings:** int params `[points 1-100, operation 0 add / 1 remove, team 1-4]`; delay. Dialog type `GIVE_SCORE_TEAM` (14).
- **Notes:** removing never takes a team below 0.

### `wf_act_toggle_to_rnd`

- **Class:** `WiredEffectToggleRandom`
- **Behavior:** sets each resolved furni to a random state from 0 to (state count - 1), then fires the furni-state-changed trigger.
- **Main settings:** int[0] = furni source; picked furni up to the selection limit; delay. Dialog type `TOGGLE_RANDOM` (15).
- **Notes:** the same forbidden furni types as `wf_act_toggle_state` are skipped (and removed from the picks). The random state can be the current state.

### `wf_act_move_furni_to`

- **Class:** `WiredEffectMoveFurniTo`
- **Behavior:** moves the event's own furni (the triggering furni) next to a randomly chosen resolved furni, in the configured direction. Repeated firings line furni up `spacing` tiles further along. The line restarts next to the anchor when the next tile is missing or not stackable.
- **Main settings:** int params `[direction 0-7, spacing, furni source]` (at least 3 ints); picked furni = anchors; delay. Dialog type `MOVE_FURNI_TO` (16).
- **Notes:** does nothing on events that carry no furni. The picks are not checked against the selection limit. Moves go through the wired movement path. 495 ms cooldown.

### `wf_act_give_reward`

- **Class:** `WiredEffectGiveReward`
- **Behavior:** gives each resolved user one reward from the list: in list order when rewards are unique, otherwise by probability. Rewards can be a badge, `credits#N`, `diamonds#N`, `pixels#N`, `pointsX#N`, `furni#itemId`, `respect#N` or `cata#catalogItemId`. The user gets the matching reward alert.
- **Main settings:** int params `[frequency 0 once / 1 per N days / 2 per N hours / 3 per N minutes, unique 0/1, total limit (0 = unlimited), interval N, user source]`; string param = rewards as `type,data,probability;...` (type `0` = badge); delay. Dialog type `GIVE_REWARD` (17).
- **Notes:** only `ACC_SUPERWIRED` can save (others get a whisper). A malformed reward row refuses the save. Saving resets the given counter and clears the box's reward history. Limits are checked against `wired_rewards_given`. No box cooldown. Plugins can cancel or change a reward.

### `wf_act_call_stacks`

- **Class:** `WiredEffectTriggerStacks`
- **Behavior:** runs the wired stacks on the tiles of the resolved furni. Each called stack goes through its own selectors, add-ons and conditions. It starts from the caller's selection: the users and furni the caller's selectors picked, or else its triggering user and furni.
- **Main settings:** int[0] = furni source; picked furni up to the selection limit; delay. Dialog type `CALL_STACKS` (18).
- **Notes:** the caller's own tile is skipped. At most 20 tiles and 20 stacks per call, and each called stack is charged to the room's execution budget (calling stops when the budget refuses). Call depth is capped at 10. 250 ms cooldown.

### `wf_act_neg_call_stack`

- **Class:** `WiredEffectNegativeTriggerStacks`
- **Behavior:** a negative call. It runs only when its own stack has conditions and they fail. It then calls the stacks on the resolved furni tiles as if their conditions had come out the other way (their negative branch runs).
- **Main settings:** same as `wf_act_call_stacks`.
- **Notes:** same caps as `wf_act_call_stacks` (own tile skipped, 20 tiles, 20 stacks, execution budget, depth 10, 250 ms). Type `NEG_CALL_STACKS` (86).

### `wf_act_neg_call_stacks`

- **Class:** `WiredEffectNegativeTriggerStacks`
- **Behavior:** alias of `wf_act_neg_call_stack`; same runtime.

### `wf_act_kick_user`

- **Class:** `WiredEffectKickHabbo`
- **Behavior:** gives each resolved user effect 4, whispers the optional message in an alert bubble, and kicks them from the room 2 seconds later.
- **Main settings:** string param = optional message (cut to `hotel.wired.message.max_length`, 100); int[0] = user source; delay. Dialog type `KICK_USER` (19).
- **Notes:** users with `ACC_UNKICKABLE` and the room owner are not kicked; they get a whisper instead.

### `wf_act_mute_triggerer`

- **Class:** `WiredEffectMuteHabbo`
- **Behavior:** mutes each resolved user in the room for the configured number of minutes and whispers them the optional message.
- **Main settings:** int params `[minutes 1-1440, user source]`; string param = message; delay. Dialog type `MUTE_TRIGGER` (20).
- **Notes:** users with rights in the room are skipped. The minutes are clamped to 1-1440 on save and again when the box fires.

### `wf_act_click_conf`

- **Class:** `WiredEffectClickSettings`
- **Behavior:** tells the clients of the resolved users what their clicks do from now on. Avatar clicks: 0 default, 1 click walks behind the avatar, 2 pass through. Furni clicks: 0 default, 1 pass through. The client applies this until the player leaves the room.
- **Main settings:** int params `[user option 0-2, furni option 0-1, user source]` (at least 2 ints; out-of-range values fall back to 0); delay. Dialog type `CLICK_SETTINGS` (129).
- **Notes:** bots, pets and disconnected users are skipped. The server only sends `WiredClickSettings`; it keeps no click state itself.

### `wf_act_bot_teleport`

- **Class:** `WiredEffectBotTeleport`
- **Behavior:** teleports each resolved bot to the tile of a randomly chosen furni from the furni source (effect 4, landing after `wired.effect.teleport.delay` ms).
- **Main settings:** string param = bot name (cut to 100); int params `[furni source, bot source (0 triggering / 100 by name / 200 selector / 201 signal; default 100)]`; picked furni up to the selection limit; delay. Dialog type `BOT_TELEPORT` (21).
- **Notes:** "by name" matches every bot in the room with that name. A blocked target tile falls back to a free tile next to it.

### `wf_act_bot_move`

- **Class:** `WiredEffectBotWalkToFurni`
- **Behavior:** each resolved bot walks to a randomly chosen furni from the furni source, skipping furni the bot is already standing on.
- **Main settings:** same layout as `wf_act_bot_teleport`: bot name, `[furni source, bot source]`, picked furni, delay. Dialog type `BOT_MOVE` (22).

### `wf_act_bot_talk`

- **Class:** `WiredEffectBotTalk`
- **Behavior:** makes each resolved bot say or shout the message.
- **Main settings:** string param = `botName<TAB>message` (name cut to 100, message cut to `hotel.wired.bot.message.max_length`, 100); int params `[mode 0 talk / 1 shout, bot source, bubble width (-1 room setting)]`; delay. Dialog type `BOT_TALK` (23).
- **Notes:** when the actor is a user, `%username%`, `%credits%`, `%pixels%`, `%points%`, `%owner%`, `%item_count%`, `%roomname%` and `%user_count%` are filled in. `%name%` is the bot's name. If the line fires a "user says" trigger that hides it, the bot stays silent. 500 ms cooldown.

### `wf_act_bot_give_handitem`

- **Class:** `WiredEffectBotGiveHandItem`
- **Behavior:** each resolved bot takes the hand item, walks next to each resolved user, and hands it over. On arrival within one tile, it fires the bot-reached-avatar trigger.
- **Main settings:** string param = bot name; int params `[hand item id (at least 0), user source (0, 11, 200, 201), bot source]`; delay. Dialog type `BOT_GIVE_HANDITEM` (24).
- **Notes:** every resolved user is served by every resolved bot.

### `wf_act_bot_follow_avatar`

- **Class:** `WiredEffectBotFollowHabbo`
- **Behavior:** mode 1 makes the resolved bots follow users: each bot is handed one resolved user in turn, so several bots spread over several users. Mode 0 makes the bots stop following.
- **Main settings:** string param = bot name (tabs removed, cut to 100); int params `[mode 0 stop / 1 start, user source (0, 11, 200, 201), bot source]`; delay. Dialog type `BOT_FOLLOW_AVATAR` (25).
- **Notes:** stopping needs no resolved user.

### `wf_act_bot_clothes`

- **Class:** `WiredEffectBotClothes`
- **Behavior:** sets the figure of each resolved bot to the configured look.
- **Main settings:** string param = `botName<TAB>figure`; int[0] = bot source; delay. Dialog type `BOT_CLOTHES` (26).
- **Notes:** the figure is validated on save (an invalid look refuses the save), on load (an invalid stored look is cleared) and before each run.

### `wf_act_bot_talk_to_avatar`

- **Class:** `WiredEffectBotTalkToHabbo`
- **Behavior:** for each resolved user, every resolved bot either says `username: message` aloud (mode 0) or whispers the message to that user (mode 1).
- **Main settings:** string param = `botName<TAB>message` (message cut to 100); int params `[mode 0 talk / 1 whisper, user source, bot source, bubble width (-1 room setting)]`; delay. Dialog type `BOT_TALK_TO_AVATAR` (27).
- **Notes:** placeholders are filled from each target user (same set as `wf_act_bot_talk`). A line swallowed by a "user says" trigger is not spoken.

### `wf_act_give_respect`

- **Class:** `WiredEffectGiveRespect`
- **Behavior:** adds the amount to the respects each resolved user has received, and progresses their `RespectEarned` achievement by the same amount.
- **Main settings:** string param = amount (positive, capped at `hotel.wired.respect.max_amount`, default 100); int[0] = user source; delay. Dialog type `EFFECT_AMOUNT`.
- **Notes:** same reward-permission gate as `wf_act_give_credits`.

### `wf_act_alert`

- **Class:** `WiredEffectAlert`
- **Behavior:** shows the message as a pop-up alert to the source users, or to everyone in the room.
- **Main settings:** same fields as `wf_act_show_message` (message, user source, visibility; bubble style and width are stored but unused). Dialog type `EFFECT_MESSAGE` (122).
- **Notes:** placeholders: `%username%`, `%online%`, `%roomsloaded%` and the username placeholders. Same word filter, length/line caps and per-event de-duplication as `wf_act_show_message`.

### `wf_act_give_handitem`

- **Class:** `WiredEffectGiveHandItem`
- **Behavior:** puts the hand item on every resolved user.
- **Main settings:** string param = hand item id (0 clears it); int[0] = user source (other show-message fields are stored but unused); delay. Dialog type `EFFECT_ID` (121).
- **Notes:** a non-numeric id does nothing (it is recorded as a compatibility failure).

### `wf_act_give_effect`

- **Class:** `WiredEffectGiveEffect`
- **Behavior:** gives every resolved unit (bots and pets included) the avatar effect, with no time limit.
- **Main settings:** string param = effect id (0 or higher; 0 removes the effect); int[0] = user source; delay. Dialog type `EFFECT_ID`.
- **Notes:** a non-numeric or negative id does nothing.

### `wf_act_freeze`

- **Class:** `WiredEffectFreeze`
- **Behavior:** freezes each resolved user: they stop walking, cannot walk, and wear the chosen effect without a time limit.
- **Main settings:** int params `[effect id (218, 12, 11, 53 or 163; others refuse the save), cancel on teleport 0/1, user source]`; delay. Dialog type `FREEZE` (34).
- **Notes:** with "cancel on teleport" on, a teleport unfreezes the user. Bots and pets are skipped.

### `wf_act_unfreeze`

- **Class:** `WiredEffectUnfreeze`
- **Behavior:** unfreezes each resolved user who is frozen by `wf_act_freeze`, so they can walk again.
- **Main settings:** int[0] = user source; delay. Dialog type `UNFREEZE` (35).
- **Notes:** only the freeze's own effect is removed. An effect the user got after the freeze (wired, item or costume) stays. Users who are not frozen are left alone.

### `wf_act_furni_to_user`

- **Class:** `WiredEffectFurniToUser`
- **Behavior:** moves every resolved furni onto the last resolved user's tile, or the tile they are stepping onto. The furni are stacked lowest first on the stack height, falling back to their own z. While the user walks, the furni are registered as followers and move along step by step, in sync with the walk animation.
- **Main settings:** int params `[furni source, user source]`; picked furni up to the selection limit; delay. Dialog type `FURNI_TO_USER` (36).
- **Notes:** moves go through the wired movement path (physics, carry, animation and no-animation add-ons).

### `wf_act_user_to_furni`

- **Class:** `WiredEffectUserToFurni`
- **Behavior:** moves every resolved user onto the last resolved furni, at the height of the furni's top. Then the walk mode decides the user's next goal: 0 resumes the old goal only if the new tile is closer to it, 1 always resumes it, 2 stops.
- **Main settings:** int params `[furni source, user source, walk mode 0-2 (default 1)]`; picked furni up to the selection limit; delay. Dialog type `USER_TO_FURNI` (37).
- **Notes:** uses user-movement physics and the animation / no-animation add-ons.

### `wf_act_furni_to_furni`

- **Class:** `WiredEffectFurniToFurni`
- **Behavior:** moves each furni of the first set onto a furni of the second set, pairing them in turn (round robin). It first tries the target's stack height, then the target's z.
- **Main settings:** int params `[move source, target source (100 or 101 = second picked set)]`; picked furni = the move set; string param = target furni ids (separated by `;`, `,` or tab); each set is capped at the selection limit; delay. Dialog type `FURNI_TO_FURNI` (38).
- **Notes:** a furni is never moved onto itself. 495 ms cooldown. Moves go through the wired movement path.

### `wf_act_set_altitude`

- **Class:** `WiredEffectSetAltitude`
- **Behavior:** raises, lowers or sets the altitude of each resolved furni on its own tile.
- **Main settings:** int params `[operator 0 increase / 1 decrease / 2 set (default), furni source]`; string param = altitude `N` or `N.NN` (empty = 0; any other format refuses the save); picked furni up to the selection limit; delay. Dialog type `SET_ALTITUDE` (39).
- **Notes:** the result is clamped to 0 and `Room.MAXIMUM_FURNI_HEIGHT` (40) and rounded to 2 decimals. Picked furni are stored whatever the source, but only the "picked" source uses them. Moves go through the wired movement path.

### `wf_act_rel_mov`

- **Class:** `WiredEffectRelativeMove`
- **Behavior:** moves each resolved furni by a fixed X/Y offset.
- **Main settings:** int params `[horizontal direction 0 negative / 1 positive, horizontal distance 0-20, vertical direction 0/1, vertical distance 0-20, furni source]` (5 ints required); picked furni up to the selection limit; delay. Dialog type `RELATIVE_MOVE` (40).
- **Notes:** a zero offset does nothing. Moves go through the wired movement path.

### `wf_act_control_clock`

- **Class:** `WiredEffectControlClock`
- **Behavior:** controls game timers and up-counters: 0 start (up-counters restart from zero), 1 stop, 2 reset, 3 pause, 4 resume.
- **Main settings:** int params `[action 0-4, furni source]`; picked furni must be timer/counter furni (otherwise `wiredfurni.error.require_counter_furni`); delay. Dialog type `CONTROL_CLOCK` (41).
- **Notes:** stopping or resetting a running or paused timer fires the game-ends trigger. Resetting also puts the timer back to its base time. Picking clocks while the source is "triggering furni" switches it to picked furni.

### `wf_act_adjust_clock`

- **Class:** `WiredEffectAdjustClock`
- **Behavior:** increases, decreases or sets the value of each resolved up-counter.
- **Main settings:** int params `[operator 0 increase / 1 decrease / 2 set, furni source, minutes 0-99, half-second steps 0-119]`; picked furni must be up-counters; delay. Dialog type `ADJUST_CLOCK` (42).
- **Notes:** other furni are ignored. Picking counters while the source is "triggering furni" switches it to picked furni.

### `wf_act_move_rotate_user`

- **Class:** `WiredEffectMoveRotateUser`
- **Behavior:** moves each resolved unit one tile in the chosen direction and/or turns it. A walking unit, or one that cannot enter the tile, is only turned.
- **Main settings:** int params `[move direction 0-7 or -1, rotation 0-7 / 8 clockwise / 9 counter-clockwise / -1, user source]`; delay. Dialog type `MOVE_ROTATE_USER` (43).
- **Notes:** 495 ms cooldown. Uses user-movement physics and the animation / no-animation add-ons.

### `wf_act_send_signal`

- **Class:** `WiredEffectSendSignal`
- **Behavior:** sends a signal to each resolved antenna, firing `wf_trg_recv_signal` stacks there. By default one signal per antenna carries the whole forwarded user and furni sets. With "per user" or "per furni" on, one signal is sent per user and/or per furni, each carrying only its own user or furni. With "per furni" off, the signal's source item is the first forwarded furni.
- **Main settings:** int params `[antenna source (0 picked / 1 triggering furni), furni forward source (0, 100, 200, 201), user forward source, per furni 0/1, per user 0/1, channel]`; picked furni = antennas (only furni with the `antenna` interaction); string param = forwarded furni ids when the furni forward source is 100; delay. Dialog type `SEND_SIGNAL` (33).
- **Notes:** when no antenna is picked, the stack's selectors can supply antennas (capped at the selection limit). Forwarded users and furni are capped at 100 each, and a firing sends at most 250 signals. Each antenna pulses once per firing. Signal depth is capped at 100. 250 ms cooldown. The channel int is stored but unused: a signal's channel is the antenna's item id. Picked antennas only override the antenna source when it is not "triggering furni".

### `wf_act_neg_send_signal`

- **Class:** `WiredEffectNegativeSendSignal`
- **Behavior:** same as `wf_act_send_signal`, but it runs only when the stack has conditions and they fail.
- **Main settings:** same as `wf_act_send_signal`.
- **Notes:** the receiving stacks evaluate their conditions normally. Type `NEG_SEND_SIGNAL` (87).

### `wf_act_give_var`

- **Class:** `WiredEffectGiveVariable`
- **Behavior:** assigns a user, furni or context variable (array variables included) to the resolved targets, with an initial value when the variable holds one. An existing assignment is overwritten only when "override" is on. The internal targets `@handitem_id` and `@effect_id` write the initial value to users; `@has_rights` gives room rights.
- **Main settings:** int params `[target 0 user / 1 furni / 2 context, override 0/1, initial value, user source (0, 11, 200, 201), furni source (0, 100, 200, 201)]`; string param = variable definition id, or `internal:@handitem_id|@effect_id|@has_rights` (user target only); picked furni when the target is furni and the source is 100; delay. Dialog type `GIVE_VAR` (69).
- **Notes:** a missing, unknown or read-only definition refuses the save. For legacy clients, a picked user-variable definition furni is accepted as the variable.

### `wf_act_remove_var`

- **Class:** `WiredEffectRemoveVariable`
- **Behavior:** removes a user, furni or context variable (array variables included) from the resolved targets. `internal:@has_rights` removes room rights.
- **Main settings:** int params `[target 0 user / 1 furni / 2 context, user source, furni source]`; string param = variable definition id or `internal:@has_rights`; picked furni when the target is furni and the source is 100; delay. Dialog type `REMOVE_VAR` (73).
- **Notes:** read-only or unknown definitions refuse the save. Removing a context variable fires the variable-changed event.

### `wf_act_change_var_val`

- **Class:** `WiredEffectChangeVariableValue`
- **Behavior:** applies an operation to a user, furni, context or room variable (or an array field) on every resolved destination. The operand is a constant or another variable. A reference variable is matched to the same entity when the target types match, otherwise by position in the resolved list, otherwise the first value is used.
- **Main settings:** int params `[destination target 0 user / 1 furni / 2 context / 3 room, operation, reference mode 0 constant / 1 variable, constant, reference target, destination user source, destination furni source, reference user source, reference furni source (101 = second picked set)]`; string param = `destinationToken<TAB>referenceToken<TAB>referenceFurniIds<TAB>arrayData` (tokens are a definition id, `custom:<id>` or `internal:<key>`); picked furni = destination furni when the destination furni source is 100; delay. Dialog type `CHANGE_VAR_VAL` (74).
- **Supported operations:** assign, add, subtract, multiply, divide, power, modulo, min (40), max (41), random 0..operand (50), absolute (60), AND/OR/XOR/NOT (100-103), left/right shift (104-105), bit count (110), get/set/clear/toggle bit (115-118), and the bit scans next/previous low/high bit, inclusive (111-114) and exclusive (119-122).
- **Notes:** values are 32-bit and add/sub/mul saturate. Divide or modulo by 0 leaves the value unchanged. Absolute, NOT and bit count ignore the operand. Writable internal keys are: users `@position_x`, `@position_y`, `@direction`, `@altitude`, `@handitem_id`, `@effect_id`, `@team_score`, `@player_score`; furni `@state`, `@position_x`, `@position_y`, `@rotation`, `@altitude`, `@gravity`, `@opacity`; room `@team_<color>_score`; and writable context captures. Furni placement writes (position, rotation, altitude) move the furni through the wired movement path (physics, carry and animation add-ons). User position and direction writes honour the animation-time and no-animation add-ons. Missing, invalid or read-only destinations refuse the save.

### `wf_act_modify_array`

- **Class:** `WiredEffectModifyArray`
- **Behavior:** applies one structural operation to an array variable (room, user, furni or context array) for every resolved owner: append, insert, set entry, remove, remove first, remove last, swap, move, clear, clear slot or shuffle. Each owner that changes raises an array-changed event; the box flashes if anything changed.
- **Main settings:** ints `[variableType, operation, ownerSource]`. `variableType` is 0 furni, 1 room, 2 user, 3 context. `operation` codes are 0 append, 1 insert, 2 set entry, 3 remove, 4 remove first, 5 remove last, 6 swap, 7 move, 8 clear, 9 clear slot, 10 shuffle. The string param is a JSON document (at most 32,768 chars) holding the array variable id, the first and second index addresses (a constant, or a scalar variable reference) and one value reference per array field. Picked furni are used as furni owners and in references. Standard delay.
- **Notes:** save fails when the chosen variable is not an array, when the operation does not fit the array mode (list or slots), when a constant index is outside the array's max entries, or when a reference does not point to a scalar with a value. Insert, set entry, remove, clear slot, swap and move need a first index; swap and move also need a second one. Only append, insert and set entry read field values, and fields with no input get 0. At most 50 owners per execution (configurable, never above 50), at most 8,192 persistent rows per mutation, and the work is charged to the room's array-work budget. If a limit is hit, nothing is mutated. A user array with the trigger source needs a triggering user.

### `wf_act_give_currency`

- **Class:** `WiredEffectGiveCurrencyFromChest`
- **Behavior:** for each resolved user, takes up to `amount` from the first currency entry stored in the picked wired chest and credits it to the user (credits for a negative type, otherwise that points type). The withdrawal is logged in the chest transaction log, the chest is persisted and a chest notification is sent.
- **Main settings:** ints `[amount, userSource]`, where `amount` is at least 0 and 0 does nothing. Picked furni are the chest(s); the first one that answers wired is used. Standard delay.
- **Notes:** only chests their owner upgraded to answer wired are reachable. It never gives more than the chest holds, and an empty chest gives nothing. Only the first currency entry is tried, even when it is empty.

### `wf_act_give_furni`

- **Class:** `WiredEffectGiveFurniFromChest`
- **Behavior:** for each resolved online user, takes up to `amount` copies of the first furni type stored in the picked wired chest and creates that many fresh items in the user's inventory. The chest is persisted.
- **Main settings:** ints `[amount, userSource]`, where `amount` is at least 1 (default 1). Picked furni are the chest(s); the first one that answers wired is used. Standard delay.
- **Notes:** it only reaches chests that answer wired, and never gives more than the chest holds. Only the first furni entry is tried.

### `wf_act_init_transaction`

- **Class:** `WiredEffectInitTransaction`
- **Behavior:** runs the terms of the picked wired contract furni against the triggering user. A contract that asks the player for something opens the negotiation (trading) window, with a 120-second timeout; the outcome triggers fire when the player confirms or leaves. A contract that asks for nothing runs instantly: every pay and chest-stock precondition is checked first, and only then are balances and chests changed. It then raises `TRANSACTION_COMPLETE`, or `TRANSACTION_FAIL` without changing anything.
- **Main settings:** picked furni = the contract furni. There are no ints or string. Standard delay.
- **Notes:** with no contracts picked it simply raises `TRANSACTION_COMPLETE` (the old signal behaviour). With contracts but no triggering user it raises `TRANSACTION_FAIL`. A receive term with no linked chest mints the currency. Instant mode only handles currency terms (credits or points).

### `wf_act_cancel_transaction`

- **Class:** `WiredEffectCancelTransaction`
- **Behavior:** raises a `TRANSACTION_FAIL` event from this box, which fires every `wf_trg_transaction_fail` in the room and carries the triggering user when there is one.
- **Main settings:** only the delay.

### `wf_act_place_furni`

- **Class:** `WiredEffectPlaceFurni`
- **Behavior:** creates new floor furni of a chosen base item, owned by the room owner, and places them either on this effect's own tile or on a stored x/y. If a copy cannot be placed, it is deleted rather than left without a room.
- **Main settings:** ints `[baseItemId, quantity, placementMode, storedX, storedY, rotation]`. The base item must exist and be a floor item. `quantity` is clamped to 1..10 (default 1). `placementMode` is 0 for this tile or 1 for the stored x/y. The coordinates are at least 0, and `rotation` is taken modulo 8. The delay may be at most `hotel.wired.max_delay` (20).
- **Notes:** saving requires the `acc_superwired` permission unless `hotel.wired.reward.require_permission` is false. It stops once the room holds `hotel.rooms.max.furniture` floor items (default 2500).

### `wf_act_remove_furni`

- **Class:** `WiredEffectRemoveFurni`
- **Behavior:** removes the resolved furni from the room. Mode 0 (return) picks each one up into its owner's inventory. Mode 1 (delete) destroys it permanently.
- **Main settings:** ints `[mode, furniSource]`, defaulting to return and selected furni (100). Picked furni must exist in the room, up to `hotel.wired.furni.selection.count`. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** it never removes itself or any other wired furni. It removes at most 100 furni per firing. Delete mode skips furni not owned by the room owner. Builders Club items are picked up but not added to an inventory.

### `wf_act_bot_start_dance`

- **Class:** `WiredEffectBotDance`
- **Behavior:** sets the dance of every bot in the room with the given name and broadcasts the dance update.
- **Main settings:** string = bot name (tabs stripped, cut to `hotel.wired.message.max_length`, 100). One int, `danceType`, clamped to 0..4: 0 none/stop, 1 Hab Hop, 2 Pogo Mogo, 3 Duck Funk, 4 The Rollie. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** bots are found by name only. There is no bot source selector.

### `wf_act_bot_stop_dance`

- **Class:** `WiredEffectBotDance`
- **Behavior:** alias of `wf_act_bot_start_dance`; same runtime.
- **Notes:** the class does not look at the key, and both keys default to dance 0. The saved dance int decides whether the bot starts or stops.

### `wf_act_give_hotelview_bonus_rare_points`

- **Class:** `WiredEffectGiveHotelviewBonusRarePoints`
- **Behavior:** gives each resolved user `amount` points of the hotel-view bonus-rare currency (`hotelview.promotional.points.type`, default 5) and sends them a refreshed bonus-rare progress.
- **Main settings:** string = amount (a positive integer, capped at `hotel.wired.reward.max_amount`, default 1000). One int, the user source. The delay may be at most `hotel.wired.max_delay`. It uses the amount dialog.
- **Notes:** saving requires the `acc_superwired` permission unless the reward-permission setting is off. An amount of 0 or less, or one that does not parse, rejects the save.

### `wf_act_give_hotelview_hof_points`

- **Class:** `WiredEffectGiveHotelviewHofPoints`
- **Behavior:** adds `amount` to each resolved user's hall-of-fame points (`hofPoints` in their stats) and saves the stats.
- **Main settings:** the same as `wf_act_give_hotelview_bonus_rare_points`: string = amount (positive, capped at `hotel.wired.reward.max_amount`) and one int, the user source.
- **Notes:** saving requires the reward permission, as with bonus-rare points.

### `wf_act_give_or_take_furni`

- **Class:** `WiredEffectGiveOrTakeFurni`
- **Behavior:** for each resolved online user, either creates `quantity` new copies of a base item in their inventory (give) or removes up to `quantity` items of that base item from it and deletes them (take).
- **Main settings:** ints `[baseItemId, quantity, giveOrTake, userSource]`. The base item must exist. `quantity` is clamped to 1..100. `giveOrTake` is 0 for give or 1 for take. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** saving requires the reward permission (`acc_superwired`) unless that setting is off. Take removes only what the user owns. An unconfigured box (base item 0) does nothing.

### `wf_act_give_points_type`

- **Class:** `WiredEffectGivePointsType`
- **Behavior:** gives each resolved user `amount` of a chosen points (currency) type.
- **Main settings:** ints `[pointsType, amount, userSource]`. `pointsType` is clamped to 0..100. `amount` must be at least 1 and is capped at `hotel.wired.reward.max_amount` (default 1000). The delay may be at most `hotel.wired.max_delay`.
- **Notes:** saving requires the reward permission (`acc_superwired`) unless that setting is off.

### `wf_act_give_points_highscore`

- **Class:** `WiredEffectGivePointsHighscore`
- **Behavior:** adds `amount` to the resolved users' row on every wired highscore board in the room, marking it as a win. Rows are matched by the exact set of user ids: an existing row is increased, otherwise a new row is created. The boards are reloaded and refreshed.
- **Main settings:** string = amount (positive, capped at `hotel.wired.reward.max_amount`). One int, the user source. It uses the amount dialog.
- **Notes:** this is not behind the reward permission. If no users are resolved, no row is written. On a "most wins" board each firing counts as one win.

### `wf_act_play_youtube_sound`

- **Class:** `WiredEffectPlayYoutube`
- **Behavior:** sets and broadcasts a YouTube video for the whole room, credited to the room owner as sender. With `autoStart` 0, or with an empty id, it clears the room's video instead.
- **Main settings:** string = YouTube id or URL (tabs stripped, at most 100 chars). One int, `autoStart`: 0 clears, anything else plays (default 1). The delay may be at most `hotel.wired.max_delay`.
- **Notes:** does nothing unless the room has the YouTube feature enabled.

### `wf_act_quick_bopper`

- **Class:** `WiredEffectQuickBopper`
- **Behavior:** starts or stops the room's jukebox (trax) playback. Play starts at the given playlist index.
- **Main settings:** ints `[playOrStop, trackIndex]`. `playOrStop` is 0 for stop, anything else for play (default 1). `trackIndex` is at least 0, and the trax manager wraps it modulo the playlist length. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** play does nothing without a jukebox or with an empty playlist. Stop only acts while music is playing.

### `wf_act_roller_speed`

- **Class:** `WiredEffectSetRollerSpeed`
- **Behavior:** sets the room's roller speed, measured in cycles between roller moves: -1 disables rollers, 0 is fastest, and higher values are slower.
- **Main settings:** one int, `speed`, clamped to -1..10 (default 2). The delay may be at most `hotel.wired.max_delay`.

### `wf_act_set_room_ad`

- **Class:** `WiredEffectSetRoomAd`
- **Behavior:** creates or refreshes the room's promotion (room ad) with the saved caption, description and category. An existing promotion is updated and its end time is pushed forward.
- **Main settings:** string = `caption<TAB>description`. Tabs and CRs are stripped, newlines become spaces, and the text is trimmed. The caption is capped at `hotel.wired.set_room_ad.caption_max_length` (60) and the description at `hotel.wired.set_room_ad.description_max_length` (200). One int, `category`, clamped to 0..50. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** does nothing when both caption and description are empty.

### `wf_act_override_height`

- **Class:** `WiredEffectOverrideHeight`
- **Behavior:** puts each resolved furni at a fixed height (set mode), or back on its tile's stack height (release mode). Furni already at the target height are skipped. Movement goes through `WiredMoveCarryHelper`.
- **Main settings:** ints `[height, mode, furniSource]`. `height` is in thousandths of a tile, clamped to 0..8000 (8.000). `mode` is 0 for set or 1 for release. `furniSource` may be trigger, selected, selector or signal; anything else falls back to trigger. Picking furni while the source is trigger switches it to selected.
- **Notes:** saving more furni than `hotel.wired.furni.selection.count` is rejected. There is no max-delay check on save.

### `wf_act_dont_chase`

- **Class:** `WiredEffectMoveFurniAway`
- **Behavior:** the flee effect. Each resolved furni steps one tile away from the nearest room unit (user, bot or pet), first along the axis with the larger distance and then along the other axis if the first step is blocked. A unit within one tile also raises the collision trigger, 500 ms later.
- **Main settings:** one int, `furniSource`. Picked furni are used when the source is selected; picking furni while the source is trigger switches it to selected. Standard delay, at most `hotel.wired.max_delay`.
- **Notes:** 495 ms cooldown. Uses `WiredMoveCarryHelper`, so movement extras apply. A unit standing on the furni's own tile gives no direction, so the furni does not move.

### `wf_act_dont_chase_top`

- **Class:** `WiredEffectMoveFurniAway`
- **Behavior:** alias of `wf_act_dont_chase`; same runtime.

### `wf_act_give_score_room`

- **Class:** `WiredEffectGiveScore`
- **Behavior:** adds or removes game score for each resolved user who is currently a player in a running game in the room.
- **Main settings:** ints `[score, operation, userSource]`. `score` must be 1..100. `operation` is 0 for add or 1 for remove. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** users who are not in a game are skipped.

### `wf_act_give_score_pp`

- **Class:** `WiredEffectGiveScore`
- **Behavior:** alias of `wf_act_give_score_room`; same runtime.

### `wf_act_bot_give_handitem_or_effect`

- **Class:** `WiredEffectBotGiveHandItem`
- **Behavior:** each resolved bot takes the hand item, walks to a tile next to each resolved user and hands the item over. The user gets the item, the bot's hand is cleared, and a bot-reached-user trigger fires when they are within 2 tiles.
- **Main settings:** string = bot name (cut to `hotel.wired.message.max_length`). Ints `[handItemId, userSource, botSource]`. `handItemId` is at least 0. `userSource` may be trigger, clicked user, selector or signal. `botSource` is 100 (by name, the default), trigger, selector or signal. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** despite the key, only hand items are given, not avatar effects. Every resolved user is served by every resolved bot.

### `wf_act_teleport_all`

- **Class:** `WiredEffectTeleport`
- **Behavior:** teleports each resolved user to a randomly chosen resolved furni, using the same flow as `wf_act_teleport_to`: a teleport effect is shown and the user moves after `wired.effect.teleport.delay` (500 ms). A blocked or invalid target tile falls back to a free tile around it. A pet the user is riding goes along.
- **Main settings:** ints `[fastTeleport, furniSource, userSource]`. With only two ints, they are read as furni source and user source and fast is off. Fast teleport cuts the delay to a fifth, but never below 75 ms. Picked furni up to `hotel.wired.furni.selection.count`. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** a unit already teleporting is skipped. Frozen users with cancel-on-teleport are unfrozen by the teleport.

### `wf_act_teleport_red`

- **Class:** `WiredEffectTeleport`
- **Behavior:** alias of `wf_act_teleport_all`; same runtime.
- **Notes:** there is no team filter in code. Use a team condition or selector to limit it to a team.

### `wf_act_teleport_green`

- **Class:** `WiredEffectTeleport`
- **Behavior:** alias of `wf_act_teleport_all`; same runtime.
- **Notes:** no team filter in code.

### `wf_act_teleport_blue`

- **Class:** `WiredEffectTeleport`
- **Behavior:** alias of `wf_act_teleport_all`; same runtime.
- **Notes:** no team filter in code.

### `wf_act_teleport_yellow`

- **Class:** `WiredEffectTeleport`
- **Behavior:** alias of `wf_act_teleport_all`; same runtime.
- **Notes:** no team filter in code.

### `wf_act_set_state`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** the match-to-snapshot effect. On save it records each picked furni's state, rotation, x/y and z. On run it restores the parts that are switched on. The state change fires the state-changed trigger. Altitude without position restores the height, and the rotation too if direction is on. Direction without position only turns the furni. Position moves it to the saved tile, with the saved rotation and height if those are on. For a non-selected source, each target uses the snapshot of a picked furni with the same base item, preferring one whose state matches.
- **Main settings:** ints `[state, direction, position, altitude, furniSource]`, each flag 1 for on. With four ints the fourth is the furni source and altitude is off. Picked furni up to `hotel.wired.furni.selection.count`; picking furni switches a trigger source to selected. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** the state is only restored on furni that allow wired state reset. Moves go through `WiredMoveCarryHelper`. A move that does not fit is skipped.

### `wf_act_set_trg_state`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** alias of `wf_act_set_state`; same runtime.

### `wf_act_open_gates`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** alias of `wf_act_set_state`; same runtime.

### `wf_act_close_dice`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** the toggle-state effect. It moves each resolved furni to its next state (or its previous state), writes the new state and fires the state-changed trigger. Furni whose state is not numeric are clicked instead (next mode only).
- **Main settings:** ints `[toggleType, furniSource]`. `toggleType` is 0 for next or 1 for previous. With a single int, that int is the furni source and the toggle type is next. Picked furni up to `hotel.wired.furni.selection.count`; picking furni switches a trigger source to selected. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** only furni with more than one state are toggled. A long list of types is refused: wired, teleports, pushables, game tiles, gates and scoreboards, freeze and banzai items, pet items, gifts, vending machines, rollers, switches, one-way gates, water, trophies and more. Freeze tiles, freeze blocks and crackables are dropped from the selection on load.

### `wf_act_roll_dice`

- **Class:** `WiredEffectRollDice`
- **Behavior:** rolls every resolved dice as if a user next to it had clicked it. The dice shows as rolling and gets a random value after 1.5 s, so `wf_trg_dice_rolled` and the dice value follow as usual.
- **Main settings:** one int, the furni source (default selected, 100), plus the picked furni. It uses the toggle-state dialog: a furni picker and nothing else. Standard delay.
- **Notes:** only dice furni (`InteractionDice`) roll. A dice that is already rolling is skipped, and a room where dice are disabled rolls nothing.

### `wf_act_close_gates`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** alias of `wf_act_close_dice`; same runtime.

### `wf_act_color_furni`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** alias of `wf_act_close_dice`; same runtime.

### `wf_act_move_furni_from_stack`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** the move-and-rotate effect. Each resolved furni optionally steps one tile in a direction and optionally rotates, and the move goes through `WiredMoveCarryHelper`. A furni moves at most once per room cycle.
- **Main settings:** ints `[direction, rotation, furniSource]`. `direction`: 0 none, 1 random of the four straight directions, 2 random east/west, 3 random north/south, then fixed directions 4 south, 5 east, 6 north, 7 west, 8 north-east, 9 south-east, 10 south-west and 11 north-west (the direction names used in the code). `rotation`: 0 none, 1 clockwise, 2 counter-clockwise, 3 random. Picked furni up to `hotel.wired.furni.selection.count` (5). Picking furni switches a trigger source to selected.
- **Notes:** a rotation in place is allowed even when a user, bot or pet stands on the tile. A move onto an invalid or blocked tile is skipped. Furni with two rotations flip between 0 and 4.

### `wf_act_move_rotate_no_under`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** alias of `wf_act_move_furni_from_stack`; same runtime.

### `wf_act_allign_furni_stack`

- **Class:** `WiredEffectChangeFurniDirection`
- **Behavior:** the move-in-direction effect. Each resolved furni keeps its own travel direction, starting from the configured start direction, and moves one tile that way on every firing. When the tile ahead is invalid or does not fit, the blocked action turns the direction (up to 8 tries). Hitting a user, bot or pet fires the collision trigger.
- **Main settings:** ints `[startDirection, blockedAction, furniSource, blockOnUserCollision]`. `startDirection` must be 0..7. `blockedAction` must be 0..6: 0 wait, 1 turn right 45, 2 turn right 90, 3 turn left 45, 4 turn left 90, 5 turn back, 6 random. `blockOnUserCollision` is 1 for on and is optional. Picked furni up to `hotel.wired.furni.selection.count`. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** 495 ms cooldown. When `blockOnUserCollision` is off, a user on the tile ahead also counts as blocked and makes the furni turn. For a non-selected source, the direction is remembered per furni while the room runs.

### `wf_act_execute_for_users`

- **Class:** `WiredEffectTriggerStacks`
- **Behavior:** calls the wired stacks standing on the tiles of the resolved furni. The called stacks run through their own selectors, add-ons and conditions. They start from this stack's selection: the users and furni its selectors picked, or else its triggering user and furni.
- **Main settings:** one int, `furniSource`, plus the picked furni (the stacks to call). Picked furni up to `hotel.wired.furni.selection.count`. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** 250 ms cooldown. The caller's own tile is skipped. At most 20 tiles and 20 stacks per call, and each called stack is charged to the room's execution budget, so the call stops when the budget refuses one. The call depth is capped at 10.

### `wf_act_send_bubble`

- **Class:** `WiredEffectMakeUserSay`
- **Behavior:** makes each resolved user say the message in a chat bubble. With the "everyone" visibility, the message goes through the room's normal talk path, so distance, ignore lists and tents apply. Otherwise only the speaker sees it.
- **Main settings:** string = message. Ints `[userSource, visibility, bubbleStyle, bubbleWidth]`: `visibility` 0 means the speaker only and 1 means everyone. `bubbleStyle` defaults to the wired bubble. `bubbleWidth` is -1 (room setting) or 0/1/2 to force wide, normal or thin. The message is word-filtered unless the saver has `acc_superwired`, and is capped at `hotel.wired.show_message.max_length` (200) and `hotel.wired.show_message.max_lines` (8). The delay may be at most `hotel.wired.max_delay`.
- **Notes:** placeholders are `%user%`, `%online_count%` and `%room_count%`, plus username placeholders from text-output extras in the stack. An empty message does nothing. Idle speakers are woken up.

### `wf_act_double_click`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** alias of `wf_act_close_dice`; same runtime.

### `wf_act_give_enable`

- **Class:** `WiredEffectGiveEffect`
- **Behavior:** gives each resolved user the avatar effect whose id is the saved text, with no expiry.
- **Main settings:** saved through the show-message save path. The string is the effect id and the first int is the user source; the other message ints are stored but unused. The dialog is the effect-id dialog.
- **Notes:** text that is not a number, or a negative id, does nothing.

### `wf_act_forward_user_to_room`

- **Class:** `WiredEffectForwardUserToRoom`
- **Behavior:** Habbo's "teleport to room". Sends each resolved online user to another room: the room a furni of the furni source leads to, or else the typed room. A room link (a furni with custom values whose `internalLink` is a room id) leads to that room and marks the entry as a room network. A teleporter leads to the room its pair stands in now, and the user arrives on the pair, facing its way, with the entry marked as a teleport and `@room_entry.teleport_id` set to the pair. The typed room is an ordinary door entry. `@room_entry.method` then reads 1 for the door, 2 for a teleport and 3 for a room network.
- **Main settings:** int params `[user source, furni source]` (the furni source is 100 picked, 0 trigger, 200 selector or 201 signal); string param = the room id, plain digits, which may be empty when furni are picked or come from another source; picked furni must be room links or teleporters (at most the wired furni limit). The delay may be at most `hotel.wired.max_delay`. Dialog type `TELEPORT_TO_ROOM` (138).
- **Notes:** the first of up to 20 resolved furni that leads somewhere wins; a destination that is this room does nothing. Users are only forwarded: their client asks to enter like a navigator visit, so the doorbell, password, bans, full rooms and hidden rooms decide as they always do, and nobody is sent to a room they are banned from or a hidden room they have no rights in. At most 50 users per run, each user at most once every 2 seconds, and the box itself at most every 500 ms. A teleporter's pair is looked up at most once a second per box and kept for 5 seconds. The arrival is kept for 15 seconds; a user who gets in later, or is let in by the doorbell after that, arrives at the door. Boxes saved before the furni source existed keep their typed room (furni source picked, nothing picked); a stored room id that is not plain digits loads as empty.

### `wf_act_teleport_to_room`

- **Class:** `WiredEffectForwardUserToRoom`
- **Behavior:** alias of `wf_act_forward_user_to_room`; same runtime.

### `wf_act_tele_room`

- **Class:** `WiredEffectForwardUserToRoom`
- **Behavior:** alias of `wf_act_forward_user_to_room`; same runtime.

### `wf_act_alert_habbo`

- **Class:** `WiredEffectAlert`
- **Behavior:** shows the message as a pop-up alert to the resolved users, or to every user in the room when visibility is "everyone".
- **Main settings:** the same save as show message: string = message (word-filtered without `acc_superwired`, capped at 200 chars and 8 lines by config). Ints `[userSource, visibility, bubbleStyle, bubbleWidth]`, of which only the source and visibility matter here. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** placeholders are `%username%`, `%online%` and `%roomsloaded%`, plus username placeholders from text-output extras. With "everyone", `%username%` is the first resolved user. Each recipient gets at most one alert per event.

### `wf_act_bot_talk_custom`

- **Class:** `WiredEffectBotTalk`
- **Behavior:** makes each resolved bot talk or shout the message. If the text matches a say trigger, that trigger handles it instead of the chat.
- **Main settings:** string = `botName<TAB>message`. The name is cut to `hotel.wired.message.max_length` (100) and the message to `hotel.wired.bot.message.max_length` (100). Ints `[mode, botSource, bubbleWidth]`. `mode` must be 0 (talk) or 1 (shout). `botSource` is 100 (by name, the default), trigger, selector or signal. `bubbleWidth` is -1 or 0/1/2. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** 500 ms cooldown. With a triggering user, `%username%`, `%credits%`, `%pixels%`, `%points%`, `%owner%`, `%item_count%`, `%roomname%` and `%user_count%` are filled; `%name%` is always the bot's name.

### `wf_act_bot_talk_to_avatar_custom`

- **Class:** `WiredEffectBotTalkToHabbo`
- **Behavior:** for each resolved user, each resolved bot either whispers the message to that user (mode 1) or says `username: message` in public chat (mode 0).
- **Main settings:** string = `botName<TAB>message`, with the same caps as bot talk. Ints `[mode, userSource, botSource, bubbleWidth]`, where `mode` must be 0 or 1. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** the placeholders are the same as bot talk, filled per target user. If the text matches a say trigger, that trigger handles it instead.

### `wf_act_call_stacks_custom`

- **Class:** `WiredEffectTriggerStacks`
- **Behavior:** alias of `wf_act_execute_for_users`; same runtime.

### `wf_act_execute_stack_custom`

- **Class:** `WiredEffectTriggerStacks`
- **Behavior:** alias of `wf_act_execute_for_users`; same runtime.

### `wf_act_cnd_move_furni`

- **Class:** `WiredEffectMoveFurniTo`
- **Behavior:** moves the furni that triggered the event to a tile in front of a random resolved target furni, in the configured direction. Each later move toward the same target goes `spacing` tiles further, and it restarts at distance 0 when the tile is missing or not stackable.
- **Main settings:** ints `[direction, spacing, furniSource]`, where `direction` is 0..7 and the source is for the target furni. Picking furni switches a trigger source to selected. Standard delay.
- **Notes:** 495 ms cooldown. It needs an event that carries a furni, and does nothing otherwise. There is no max-delay or selection-count check on save.

### `wf_act_cnd_move_rotate`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** alias of `wf_act_move_furni_from_stack`; same runtime.

### `wf_act_cnd_toggle_state`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** alias of `wf_act_close_dice`; same runtime.

### `wf_act_freeze_habbo`

- **Class:** `WiredEffectFreeze`
- **Behavior:** freezes each resolved user: they stop walking, cannot walk, and get the chosen freeze effect with no expiry.
- **Main settings:** ints `[effectId, cancelOnTeleport, userSource]`. `effectId` must be one of 218, 12, 11, 53 or 163 (default 218). `cancelOnTeleport` is 1 to unfreeze the user when they are wired-teleported. The delay may be at most `hotel.wired.max_delay`.

### `wf_act_unfreeze_habbo`

- **Class:** `WiredEffectUnfreeze`
- **Behavior:** unfreezes each resolved user who was frozen by wired, so they can walk again. The freeze effect is only removed if it is still the active effect.
- **Main settings:** one int, the user source. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** users who are not frozen are skipped.

### `wf_act_match_to_sshot_new`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** alias of `wf_act_set_state`; same runtime.

### `wf_act_move_furni_to_furni`

- **Class:** `WiredEffectFurniToFurni`
- **Behavior:** moves each resolved "move" furni onto the tile of a target furni. Targets are assigned round-robin. If the normal move fails, it retries at the target's height.
- **Main settings:** ints `[moveSource, targetSource]`. The picked furni are the furni to move. The string holds the target furni ids (separated by `;`, `,` or tab) and is used when `targetSource` is 101 (second selection). Each list is capped at `hotel.wired.furni.selection.count`. Picking furni switches a trigger move source to selected. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** 495 ms cooldown. A furni is never moved onto itself. Moves go through `WiredMoveCarryHelper`.

### `wf_act_teleport_bots_to_furni`

- **Class:** `WiredEffectBotTeleport`
- **Behavior:** teleports each resolved bot to a randomly chosen resolved furni, with a teleport effect and the `wired.effect.teleport.delay` wait. A blocked or invalid tile falls back to a free tile around it.
- **Main settings:** string = bot name (cut to `hotel.wired.message.max_length`). Ints `[furniSource, botSource]`, where the bot source is 100 (by name), trigger, selector or signal. Picked furni up to `hotel.wired.furni.selection.count`. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** a bot that is already teleporting is skipped.

### `wf_act_tp_furni_to_habbo`

- **Class:** `WiredEffectFurniToUser`
- **Behavior:** moves the resolved furni onto the tile of the last resolved user, or onto the tile the user is stepping into. Furni are handled lowest first and stack on the tile. While the user walks, the furni are registered as followers and travel with them.
- **Main settings:** ints `[furniSource, userSource]`. Picked furni up to `hotel.wired.furni.selection.count`; picking furni switches a trigger source to selected. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** if stacking fails, it retries at the furni's original height. The move animation follows the user's step unless a no-animation extra is present.

### `wf_act_give_score_custom`

- **Class:** `WiredEffectGiveScore`
- **Behavior:** alias of `wf_act_give_score_room`; same runtime.

### `wf_act_lower_furni`

- **Class:** `WiredEffectSetAltitude`
- **Behavior:** changes the height of each resolved furni: increase, decrease or set to a value. The result is clamped to 0..40 and rounded to 2 decimals, and the furni moves in place through `WiredMoveCarryHelper`.
- **Main settings:** ints `[operator, furniSource]`. `operator` is 0 for increase, 1 for decrease or 2 for set (the default, also used for invalid values). The string holds the altitude, a number with up to 2 decimals; an empty string means 0, and any other format is rejected. Picked furni up to `hotel.wired.furni.selection.count` are always stored. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** unlike the other furni effects, picking furni does not switch a trigger source to selected.

### `wf_act_raise_furni`

- **Class:** `WiredEffectSetAltitude`
- **Behavior:** alias of `wf_act_lower_furni`; same runtime.
- **Notes:** raising or lowering depends only on the saved operator.

### `wf_act_match_to_sshot_height`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** alias of `wf_act_set_state`; same runtime.

### `wf_act_match_to_sshot_height_instant`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** alias of `wf_act_set_state`; same runtime.

### `wf_act_plus_match_furni_state`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** alias of `wf_act_set_state`; same runtime.

### `wf_act_move_rotate_collide`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** alias of `wf_act_move_furni_from_stack`; same runtime.

### `wf_act_move_rotate_diagonal`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** alias of `wf_act_move_furni_from_stack`; same runtime.
- **Notes:** diagonal moves come from direction codes 8 to 11; the key itself adds nothing.

### `wf_act_rotate_habbo`

- **Class:** `WiredEffectMoveRotateUser`
- **Behavior:** moves each resolved unit one tile in a fixed direction and/or turns it. A walking unit is only turned. When the move is blocked, the unit still turns.
- **Main settings:** ints `[movementDirection, rotationDirection, userSource]`. `movementDirection` is 0..7, or -1 for none. `rotationDirection` is 0..7 (face that way), 8 (clockwise), 9 (counter-clockwise), or -1 for none. The delay may be at most `hotel.wired.max_delay`.
- **Notes:** 495 ms cooldown. The move respects the movement physics and animation extras on the stack, and a no-animation extra makes it instant.

### `wf_act_show_message_room`

- **Class:** `WiredEffectWhisper`
- **Behavior:** the show-message effect. It whispers the message to the resolved users, or to every user in the room when visibility is "everyone".
- **Main settings:** string = message (word-filtered without `acc_superwired`, capped at `hotel.wired.show_message.max_length` (200) and `hotel.wired.show_message.max_lines` (8)). Ints `[userSource, visibility, bubbleStyle, bubbleWidth]`: `visibility` is 0 for the source users or 1 for everyone, and `bubbleWidth` is -1 or 0/1/2 (wide, normal, thin). The delay may be at most `hotel.wired.max_delay`.
- **Notes:** placeholders are `%user%`, `%online_count%` and `%room_count%`, plus username placeholders from text-output extras. With "everyone", `%user%` is the first resolved user. Each recipient gets at most one message per event. An empty message does nothing.

### `wf_act_toggle_state_down`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** alias of `wf_act_close_dice`; same runtime.
- **Notes:** stepping down a state requires saving `toggleType` 1 (previous); the key alone does not change the direction.

### `wf_act_toggle_state_trg`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** alias of `wf_act_close_dice`; same runtime.

### `wf_act_move_furni_as_group`

- **Class:** `WiredEffectMoveFurniAsGroup`
- **Behavior:** shifts all resolved furni one tile in the same direction. The furni at the front of the group move first, so a tightly packed group can move together.
- **Main settings:** ints `[direction, furniSource]`, where `direction` is taken modulo 8. Picked furni up to `hotel.wired.furni.selection.count`; picking furni switches a trigger source to selected. Standard delay.
- **Notes:** 495 ms cooldown. This is best-effort: a member whose target tile is missing or not stackable, or does not fit, stays put and the rest still move. Moves go through `WiredMoveCarryHelper`.

### `wf_act_change_opacity`

- **Class:** `WiredEffectChangeOpacity`
- **Behavior:** changes how transparent the resolved furni look. With "everyone" visibility the value is stored room-wide and every user is updated. Otherwise it is stored and sent only for the resolved users. It can also make the furni click-through.
- **Main settings:** ints `[visibility, opacity, easing, durationSeconds, furniSource, userSource, clickThrough]`, and all seven are required. `visibility` is 0 for the source users or 1 for everyone. `opacity` is clamped to 0..100 (default 100). `easing` is clamped to 0..4, where 0 is instant. `durationSeconds` of 0 or less means the default of 400 ms; otherwise it is clamped to 1..10. `clickThrough` is 1 for on. Picked furni (at most the wired selection limit) must exist; picking furni switches a trigger source to selected (the default source is selected). The delay must be 0..`hotel.wired.max_delay`.
- **Notes:** at most the selection limit of furni are affected per firing. Only clients that report the wired opacity feature receive updates. The trigger user is needed only for source-users visibility with the trigger source.

---

## 5. Selectors

Selector boxes run before the stack's effects and build the furni and/or user targets. Shared rules (from `InteractionWiredEffect.applySelectorModifiers` and `WiredSourceUtil`):

- An ordinary selector ADDS its matches to what earlier selectors in the stack picked (as in Habbo). With `invert`, it adds every selectable furni/user it did NOT match.
- A selector with `filter existing` narrows the current picks instead: it keeps the picks that match (or, with `invert`, the picks that do not match). Filter-mode selectors always run in a second pass, after every ordinary selector, whatever their position in the stack.
- Wired boxes are never picked unless the caller explicitly asks for wired furni (for example the furni-name text placeholder).
- Furni sources used below: `0` trigger furni, `100` picked furni, `200` selector, `201` signal. The limit on stored picks is `hotel.wired.furni.selection.count` (default 5) unless an entry says otherwise.
- `wf_xtra_filter_*` add-ons trim the final selection after all selectors have run (see section 7).

### `wf_slc_furni_area`

- **Class:** `WiredEffectFurniArea`
- **Behavior:** picks every floor furni on the tiles of a rectangle.
- **Main settings:** int params `[rootX, rootY, width, height, filterExisting, invert]`. At least 4 are required or the save fails.
- **Notes:** does nothing while width or height is 0 (the value after pickup).

### `wf_slc_furni_neighborhood`

- **Class:** `WiredEffectFurniNeighborhood`
- **Behavior:** picks furni on a pattern of tile offsets around one or more source positions.
- **Main settings:** int params `[source, filterExisting, invert, targetOffsetX, targetOffsetY, offsetCount, x1, y1, x2, y2, ...]`. Source: `0` trigger user (falls back to the event tile), `1` current user targets (falls back to the trigger user), `2` clicked user (falls back to current user targets), `3` trigger furni, `4` picked furni (up to 20), `5` current furni targets (falls back to the trigger furni). A source outside 0-5 is stored as 0. Up to 64 offsets. Each offset is applied as `source + (offset - targetOffset)`.
- **Notes:** does not use the shared add rule. Without filter mode its result REPLACES earlier furni picks, and an empty match clears them. `invert` returns the furni in the 9x9 grid (±4 tiles) around each source that are not on the pattern, not the whole room. Does nothing when no offsets are saved or no source position resolves.

### `wf_slc_furni_bytype`

- **Class:** `WiredEffectFurniByType`
- **Behavior:** picks every floor furni in the room with the same base item as the source furni. It can also require the same state.
- **Main settings:** int params `[source, matchState, filterExisting, invert]`. At least 4 are required. Source: `0` picked furni (default; up to 20 picks are stored), `1` signal furni, `2` trigger furni. When `matchState = 1`, the base item and the extradata must both match.
- **Notes:** the picked furni are stored whatever source is chosen.

### `wf_slc_furni_altitude`

- **Class:** `WiredEffectFurniAltitude`
- **Behavior:** picks floor furni by their Z height.
- **Main settings:** int params `[comparison, filterExisting, invert]`. At least 3 are required. Comparison: `0` lower than, `1` equal (default), `2` higher than. The string param holds the altitude as a decimal. It is clamped to 0-40 (`Room.MAXIMUM_FURNI_HEIGHT`) and rounded to 2 decimals, and 0 is used when the value does not parse.
- **Notes:** the furni Z is clamped and rounded the same way before the comparison.

### `wf_slc_furni_onfurni`

- **Class:** `WiredEffectFurniOnFurni`
- **Behavior:** for each source furni, picks the furni stacked on its footprint tiles in the chosen height relation.
- **Main settings:** int params `[selectionType, furniSource, filterExisting, invert]`. Selection type: `0` above (default; base at or over the source top), `1` below (top at or under the source base), `2` same base height (includes the source), `3` everything on the tiles (includes the source). Furni source: 0/100/200/201. When furni are picked while the source is 0, the source becomes 100.
- **Notes:** if the room has no layout or no source furni resolves, it clears the furni picks.

### `wf_slc_furni_picks`

- **Class:** `WiredEffectFurniPicks`
- **Behavior:** picks the furni chosen in the editor.
- **Main settings:** int params `[filterExisting, invert]` plus up to 20 picked furni. Its own cap of 20 applies, not the hotel setting.
- **Notes:** picked furni that are no longer in the room are skipped.

### `wf_slc_furni_signal`

- **Class:** `WiredEffectFurniSignal`
- **Behavior:** picks the furni carried by the signal that fired the stack.
- **Main settings:** int params `[filterExisting, invert]`.
- **Notes:** only meaningful under a signal trigger. On any other event it sets an empty furni selection, which clears earlier picks.

### `wf_slc_users_area`

- **Class:** `WiredEffectUsersArea`
- **Behavior:** picks every room unit (habbos, bots and pets) standing inside a rectangle.
- **Main settings:** int params `[rootX, rootY, width, height, filterExisting, invert]`. At least 4 are required.
- **Notes:** does nothing while width or height is 0.

### `wf_slc_users_neighborhood`

- **Class:** `WiredEffectUsersNeighborhood`
- **Behavior:** picks room units on a pattern of tile offsets around the source positions.
- **Main settings:** same layout and sources as `wf_slc_furni_neighborhood`: `[source, filterExisting, invert, targetOffsetX, targetOffsetY, offsetCount, pairs...]`, with 0-5 sources, up to 64 offsets and up to 20 picked furni.
- **Notes:** like the furni version, it replaces earlier user picks (an empty match leaves nobody), and `invert` is limited to the 9x9 grid around the sources.

### `wf_slc_users_signal`

- **Class:** `WiredEffectUsersSignal`
- **Behavior:** picks the users carried by the signal that fired the stack.
- **Main settings:** int params `[filterExisting, invert]`.
- **Notes:** on a non-signal event it sets an empty user selection, which clears earlier picks.

### `wf_slc_users_bytype`

- **Class:** `WiredEffectUsersByType`
- **Behavior:** picks room units of one entity type.
- **Main settings:** int params `[entityType, filterExisting, invert]`. Entity type: `1` habbo (default), `2` pet, `4` bot. Other values are stored as 1.

### `wf_slc_users_team`

- **Class:** `WiredEffectUsersTeam`
- **Behavior:** picks habbos who are on a game team.
- **Main settings:** int params `[team, filterExisting, invert]`. Team: `0` any team (default), `1` red, `2` green, `3` blue, `4` yellow.
- **Notes:** users with no game player or with team NONE never match.

### `wf_slc_users_byaction`

- **Class:** `WiredEffectUsersByAction`
- **Behavior:** picks units that are doing an action. A unit matches if (a) it is the actor of a "user performs action" event with that action, (b) it is currently in the matching state (sit, lay, idle for relax, sign status, dancing), or (c) it performed the action within the last 5 seconds.
- **Main settings:** int params `[action, signFilter, signId, danceFilter, danceId, filterExisting, invert]`. Action: `1` wave (default), `2` blow kiss, `3` laugh, `4` awake, `5` relax, `6` sit, `7` stand, `8` lay, `9` sign, `10` dance, `11` thumbs up. Sign id is 0-17 (else 0) and dance id is 1-4 (else 1). The sign and dance ids are only checked when their filter flag is 1.

### `wf_slc_users_byname`

- **Class:** `WiredEffectUsersByName`
- **Behavior:** picks habbos whose username is in the list.
- **Main settings:** string param with one name per line. Blank lines are dropped, lines are trimmed and duplicates removed. Matching is case-insensitive. Int params `[filterExisting, invert]`.
- **Notes:** habbos only; `invert` picks every other habbo in the room.

### `wf_slc_users_handitem`

- **Class:** `WiredEffectUsersHandItem`
- **Behavior:** picks units holding a hand item.
- **Main settings:** int params `[handItemId, filterExisting, invert]`. The hand item id is clamped to at least 0. `0` means any hand item, as in Habbo.
- **Notes:** `0` with `invert` picks units with empty hands.

### `wf_slc_users_onfurni`

- **Class:** `WiredEffectUsersOnFurni`
- **Behavior:** picks the units standing on the footprint tiles of the source furni.
- **Main settings:** int params `[furniSource, filterExisting, invert]`, with furni source 0/100/200/201. When furni are picked while the source is 0, the source becomes 100. The save fails when more furni are picked than `hotel.wired.furni.selection.count`.
- **Notes:** with no source furni (or no layout) it clears the user picks.

### `wf_slc_users_group`

- **Class:** `WiredEffectUsersGroup`
- **Behavior:** picks habbos who are members of a group.
- **Main settings:** int params `[groupType, groupId, filterExisting, invert]`. Group type: `0` the room's own group (default), `1` the group id in param 1 (clamped to at least 0).
- **Notes:** matches nobody when the room has no group and type 0 is used. Habbos only.

### `wf_slc_furni_with_var`

- **Class:** `WiredEffectFurniWithVariable`
- **Behavior:** picks floor furni that hold a variable, or whose value passes a comparison.
- **Main settings:** int params `[selectByValue, comparison, referenceMode, constant, referenceTarget, referenceUserSource, referenceFurniSource, filterExisting, invert]`. Comparison: `0` >, `1` >=, `2` = (default), `3` <=, `4` <, `5` !=. Reference mode: `0` constant, `1` variable. Reference target: `0` user, `1` furni, `2` context, `3` room. The reference furni source can also be `101` (furni picked in this box; the picks are stored only in that case). The string param is `variableToken<TAB>referenceVariableToken`, where a token is `custom:<definition id>` or `internal:<key>`.
- **Notes:** the save fails when the variable is invalid, or when value mode is on and the variable has no value. With value mode off it only checks that the furni holds the variable. When the reference yields several values, the holder's own value is used if it is in the set, else the first one. It is skipped when no triggering user is available and the reference is the trigger user's variable.

### `wf_slc_users_with_var`

- **Class:** `WiredEffectUsersWithVariable`
- **Behavior:** the user-side version of `wf_slc_furni_with_var`: it picks room units that hold a user variable, or whose value passes the comparison.
- **Main settings:** the same 9 int params and string param as the furni version.
- **Notes:** custom user variables only resolve for habbos. Internal user variables can also match bots and pets.

### `wf_slc_remote`

- **Class:** `WiredEffectRemoteSelector`
- **Behavior:** runs the selector boxes picked in it (not other remote selectors). Each runs in its own empty scratch context, and the combined furni and users they pick become this box's result. When no selector box is picked, it falls back to the signal furni, then to its picked furni.
- **Main settings:** int params `[filterExisting, invert]` plus up to 20 picked furni (selector boxes or plain furni).
- **Notes:** filter and invert are applied to the combined result. A picked selector's own filter mode sees an empty set because of the scratch context. Furni and users are only replaced when a picked selector actually produced that kind of target.

---

## 6. Conditions

Conventions used in the entries below:

- **User sources:** `0` triggering user (when a bot reaches a user, the user it reached), `11` clicked user (only on a "user clicks user" event), `200` selector result, `201` users carried by the signal. Anything else saves as `0`.
- **Furni sources:** `0` triggering furni (the event's furni, or the wired trigger box when the event has none), `100` picked furni, `200` selector result, `201` furni carried by the signal. In most furni boxes, picking furni while the source is `0` saves the source as `100`.
- Resolved users and furni (except the selector source) go through the stack's filter extras before the box sees them.
- **Quantifier:** `0` all, `1` any. Negative boxes usually answer the positive box's result turned around with the same quantifier (`all` = not all match, `any` = none match). Some differ, and each entry says what an empty target set gives.
- **Comparison tables:** three-way `0 <`, `1 =`, `2 >`; six-code (`WiredComparison`) `0 <`, `1 =`, `2 >`, `3 <=`, `4 !=`, `5 >=`; variable table `0 >`, `1 >=`, `2 =`, `3 <=`, `4 <`, `5 !=`.
- "Wired trigger box" means the stack's trigger furni (`ctx.triggerItem()`), not the furni the event happened on.

### `wf_cnd_not_habbo_has_credits`

- **Class:** `WiredConditionHabboLacksCredits`
- **Behavior:** for each resolved user (deduplicated), passes when their credits do NOT compare to the amount with the chosen operator. Uses the `all` or `any` quantifier. An empty user set fails. A unit that is not a Habbo never matches.
- **Main settings:** ints `[teamType (stored, unused; 1-4, default 1), operator (six-code, default 5 >=), amount (0-1,000,000), user source, quantifier (default 0 all)]` (dialog `USER_AMOUNT`, 51).
- **Notes:** with the default `>=` it passes when the user has fewer credits than the amount. Rows saved before the operator existed read as `>=`. The positive twin is `wf_cnd_habbo_has_credits`.

### `wf_cnd_not_habbo_has_diamonds`

- **Class:** `WiredConditionHabboLacksDiamonds`
- **Behavior:** same as `wf_cnd_not_habbo_has_credits`, but reads diamonds (currency type 5): passes when the diamonds do NOT compare to the amount.
- **Main settings:** ints `[teamType (unused), operator (six-code, default >=), amount (0-1,000,000), user source, quantifier]` (dialog 51).
- **Notes:** there is no positive diamonds box.

### `wf_cnd_not_habbo_has_duckets`

- **Class:** `WiredConditionHabboLacksDuckets`
- **Behavior:** same as `wf_cnd_not_habbo_has_credits`, but reads duckets (currency type 0).
- **Main settings:** ints `[teamType (unused), operator (six-code, default >=), amount (0-1,000,000), user source, quantifier]` (dialog 51).
- **Notes:** the positive twin is `wf_cnd_habbo_has_duckets`.

### `wf_cnd_freeze`

- **Class:** `WiredConditionFrozen`
- **Behavior:** passes when the resolved users are frozen by `wf_act_freeze` / `wf_act_freeze_habbo` (the freeze flag on the room unit, not a visual effect). Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[effectId (0-10,000, stored but never read), user source, quantifier (default 1 any)]`. At least one int is needed or the save is refused (dialog `USER_STATE`, 52).

### `wf_cnd_not_freeze`

- **Class:** `WiredConditionNotFrozen`
- **Behavior:** negation of `wf_cnd_freeze`: with `all` it passes when not every resolved user is frozen, with `any` when none is. An empty user set fails.
- **Main settings:** same as `wf_cnd_freeze` (dialog `NOT_USER_STATE`, 53).

### `wf_cnd_furni_in_range`

- **Class:** `WiredConditionFurniInRange`
- **Behavior:** measures the tile distance (straight line) from the wired trigger box to each resolved furni's base tile and tests it against the radius. Uses the `all` or `any` quantifier. Fails when there are no targets, no trigger box, or no tile under the trigger box.
- **Main settings:** ints `[comparison (0 distance < radius, 1 distance <= radius (default), 2 distance > radius), furni source, quantifier (default all)]`. The string is the radius (decimal, at least 0, rounded to 2 places, default 0). Plus the picked furni (dialog `FURNI_RANGE`, 58).
- **Notes:** comparison `1` keeps the old inclusive "within" reading. The negative twin is `wf_cnd_furni_not_in_range`.

### `wf_cnd_furni_not_in_range`

- **Class:** `WiredConditionFurniNotInRange`
- **Behavior:** the per-furni test of `wf_cnd_furni_in_range`, flipped (outside the chosen radius comparison). Uses the same quantifier. It passes when there are no targets, no trigger box, or no trigger tile. A furni whose tile cannot be found counts as outside. It fails only when the room or its layout is missing.
- **Main settings:** same as `wf_cnd_furni_in_range`.

### `wf_cnd_has_same_height`

- **Class:** `WiredConditionSameHeight`
- **Behavior:** compares the Z of the resolved furni exactly. With `all`, every target must share one height. With `any`, at least two targets must share a height. A single target passes with `all`. An empty target set fails.
- **Main settings:** ints `[furni source, quantifier (default all)]`, plus the picked furni (dialog `FURNI_PROPERTY`, 59).

### `wf_cnd_not_has_same_height`

- **Class:** `WiredConditionNotSameHeight`
- **Behavior:** needs at least two resolved furni, otherwise it fails. With `all` it passes when at least one height differs. With `any` it passes when no two targets share a height.
- **Main settings:** same as `wf_cnd_has_same_height`.

### `wf_cnd_habbo_owns_furni`

- **Class:** `WiredConditionHabboOwnsFurni`
- **Behavior:** takes the base types of the resolved furni and checks the triggering user's inventory (furni not placed in a room). With `all` the user must own every type; with `any` (the default) at least one. Always about the triggering user; there is no user source. Fails when no furni type resolves or there is no triggering user.
- **Main settings:** ints `[furni source, quantifier (default 1 any)]`, plus the picked furni, which only supply types (dialog `FURNI_PROPERTY`, 59).

### `wf_cnd_habbo_not_owns_furni`

- **Class:** `WiredConditionHabboNotOwnsFurni`
- **Behavior:** passes when the triggering user fails the `wf_cnd_habbo_owns_furni` check with the same quantifier. Passes when no furni type resolves or there is no triggering user. Fails when the room is missing, or when the triggerer is not a Habbo.
- **Main settings:** same as `wf_cnd_habbo_owns_furni`.

### `wf_cnd_has_tag`

- **Class:** `WiredConditionHasTag`
- **Behavior:** passes when the resolved users have the configured profile tag (whole tag, case-insensitive). Uses the `all` or `any` quantifier. An empty user set fails. An empty tag never matches.
- **Main settings:** the string is the tag (trimmed, max 38 chars). Ints `[user source, quantifier (default 1 any)]` (dialog `USER_TAG`, 54).

### `wf_cnd_not_has_tag`

- **Class:** `WiredConditionNotHasTag`
- **Behavior:** negation of `wf_cnd_has_tag`: with `all` it passes when not every user has the tag, with `any` when none has it. An empty user set passes.
- **Main settings:** same as `wf_cnd_has_tag` (dialog `NOT_USER_TAG`, 55).

### `wf_cnd_motto_contains`

- **Class:** `WiredConditionMottoContains`
- **Behavior:** passes when the resolved users' motto contains the text (case-insensitive substring). Uses the `all` or `any` quantifier. An empty user set fails. An empty text never matches.
- **Main settings:** the string is the text (trimmed, max 64 chars). Ints `[user source, quantifier (default 1 any)]` (dialog `USER_MOTTO`, 56).
- **Notes:** there is no negative twin.

### `wf_cnd_habbo_has_at_least_x_items`

- **Class:** `WiredConditionHabboHasMinItems`
- **Behavior:** compares each resolved user's inventory item count (furni not placed in a room) to the amount. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[teamType (unused), operator (six-code, default 5 >=), amount (0-1,000,000), user source, quantifier (default all)]` (dialog 51).
- **Notes:** rows saved before the operator existed read as `>=`.

### `wf_cnd_habbo_owns_badge`

- **Class:** `WiredConditionHabboOwnsBadge`
- **Behavior:** passes when the resolved users own the badge in their inventory (worn or not; the code is case-insensitive). Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** the string is the badge code (trimmed, max 64 chars). Ints `[user source, quantifier (default 1 any)]` (dialog `ACTOR_WEARS_BADGE`, 11).
- **Notes:** `wf_cnd_wearing_badge` checks worn badges only.

### `wf_cnd_not_habbo_owns_badge`

- **Class:** `WiredConditionNotHabboOwnsBadge`
- **Behavior:** negation of `wf_cnd_habbo_owns_badge`: with `all` it passes when not every user owns the badge, with `any` when none does. An empty user set passes.
- **Main settings:** same as `wf_cnd_habbo_owns_badge` (dialog `NOT_ACTOR_WEARS_BADGE`, 22).

### `wf_cnd_has_furni_on`

- **Class:** `WiredConditionFurniHaveFurni`
- **Behavior:** for each resolved furni, looks for another furni on any tile of its footprint whose Z is at or above the furni's top (Z plus its current height). With "all" every target needs something on it; otherwise at least one does. Fails when there is nothing to check.
- **Main settings:** ints `[all flag (1 = all, anything else = any; required), furni source]`, plus the picked furni (dialog `FURNI_HAS_FURNI`, 7).
- **Notes:** the flag is `1` = all here, not the usual `0` = all quantifier. After pick-up it defaults to any. Legacy `all:ids` data is still read.

### `wf_cnd_furnis_hv_avtrs`

- **Class:** `WiredConditionFurniHaveHabbo`
- **Behavior:** passes when a user, bot or pet currently stands on any tile of the resolved furni's footprint. With "all" every target needs someone on it; otherwise at least one does. Fails when there is nothing to check.
- **Main settings:** ints `[all flag (1 = all, default any), furni source]`, plus the picked furni (dialog `FURNI_HAVE_HABBO`, 1).
- **Notes:** in a one-int save, a first value above 1 is read as the furni source (old client layout). The negative twin is `wf_cnd_not_hv_avtrs`.

### `wf_cnd_stuff_is`

- **Class:** `WiredConditionFurniTypeMatch`
- **Behavior:** passes when the target furni have one of the base types found in a second furni set. Uses the `all` or `any` quantifier. Fails when either set resolves empty.
- **Main settings:** ints `[furni source (targets), compare source (0, 100, 200, 201, or 101 = second picked list), quantifier (default 0 all)]`. The picked furni are the targets. The string holds the second picked list as `;`-separated ids (dialog `STUFF_IS`, 8).
- **Notes:** a legacy save (at most one int and an empty string) treats the picked furni as the types to compare against, with the quantifier `any`. Old `;`-separated `wired_data` loads the same way.

### `wf_cnd_actor_in_group`

- **Class:** `WiredConditionGroupMember`
- **Behavior:** passes when the resolved users are members of the group: the room's group, or a chosen group. Uses the `all` or `any` quantifier. Fails when the group id is 0 (for example a room without a group) or the user set is empty. A unit that is not a Habbo never counts as a member.
- **Main settings:** ints `[user source, group type (0 room's group, 1 selected group), group id (at least 0), quantifier (default all)]` (dialog `ACTOR_IN_GROUP`, 10).

### `wf_cnd_user_count_in`

- **Class:** `WiredConditionHabboCount`
- **Behavior:** passes when the count is between the lower and upper limit, both inclusive. With source `0` the count is the number of Habbos in the room (bots and pets are not counted). With any other source it is the size of the resolved user set.
- **Main settings:** ints `[lower, upper, user source]`. At least two are needed. Limits are clamped to 0-1,000 and swapped if lower > upper. The default is 0-50 (dialog `USER_COUNT`, 5).
- **Notes:** the user source is saved with the box and survives a room reload.

### `wf_cnd_wearing_effect`

- **Class:** `WiredConditionHabboHasEffect`
- **Behavior:** passes when the resolved users' current effect id equals the configured id. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[effect id (0-10,000), user source, quantifier (default 1 any)]`. At least one int is needed (dialog `ACTOR_WEARS_EFFECT`, 12).

### `wf_cnd_wearing_badge`

- **Class:** `WiredConditionHabboWearsBadge`
- **Behavior:** passes when the resolved users wear the badge in a badge slot (the code is case-insensitive). Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** the string is the badge code (trimmed, tabs and newlines become spaces, max 64 chars). Ints `[user source, quantifier (default 1 any)]` (dialog 11).
- **Notes:** to check ownership instead of wearing, use `wf_cnd_habbo_owns_badge`.

### `wf_cnd_time_less_than`

- **Class:** `WiredConditionLessTimeElapsed`
- **Behavior:** passes while the time since the room's wired timers were last reset, counted in milliseconds and divided into half-seconds, is below the configured value.
- **Main settings:** ints `[half-seconds (0-172,800, which is 48 h)]`. At least one is needed (dialog `TIME_LESS_THAN`, 4).
- **Notes:** before any reset in this server run, the room's stored whole-second reset time is used. The opposite box is `wf_cnd_time_more_than`.

### `wf_cnd_match_snapshot`

- **Class:** `WiredConditionMatchStatePosition`
- **Behavior:** on save it records state, rotation, X/Y and Z for each picked furni. With the picked source, each recorded furni that still exists is compared to its record on the ticked fields. With another source, each resolved furni is compared to a record of the same base type, preferring one whose saved state equals its current state; a target with no such record fails. Uses the `all` or `any` quantifier. Fails when the snapshot is empty or no target resolves.
- **Main settings:** ints `[state, direction, position, altitude (0/1 each), furni source, quantifier (default all)]`. At least three are needed. Plus the picked furni (dialog `MATCH_SSHOT`, 0).
- **Notes:** in a four-int save, a fourth value above 1 is read as the source. Removed furni drop out of the snapshot.

### `wf_cnd_time_more_than`

- **Class:** `WiredConditionMoreTimeElapsed`
- **Behavior:** passes once the time since the last timer reset, in milliseconds divided into half-seconds, is above the configured value.
- **Main settings:** ints `[half-seconds (0-172,800)]` (dialog `TIME_MORE_THAN`, 3).
- **Notes:** same reset source as `wf_cnd_time_less_than`.

### `wf_cnd_not_furni_on`

- **Class:** `WiredConditionNotFurniHaveFurni`
- **Behavior:** with "all" it passes when every resolved furni has nothing on top. Otherwise it passes when at least one has nothing on top. It passes when there is nothing to check, when the layout is missing, or for a furni whose tile cannot be found.
- **Main settings:** same as `wf_cnd_has_furni_on` (dialog `NOT_FURNI_HAVE_FURNI`, 18).

### `wf_cnd_not_hv_avtrs`

- **Class:** `WiredConditionNotFurniHaveHabbo`
- **Behavior:** with "all" it passes when no resolved furni has a user, bot or pet on it. Otherwise it passes when at least one furni is free. It passes when there is nothing to check.
- **Main settings:** same as `wf_cnd_furnis_hv_avtrs` (dialog `NOT_FURNI_HAVE_HABBO`, 14).

### `wf_cnd_not_stuff_is`

- **Class:** `WiredConditionNotFurniTypeMatch`
- **Behavior:** the result of `wf_cnd_stuff_is` with the same quantifier, turned around (`all` = not all match, `any` = none matches). It passes when either furni set resolves empty.
- **Main settings:** same as `wf_cnd_stuff_is` (dialog `NOT_STUFF_IS`, 19).

### `wf_cnd_not_user_count`

- **Class:** `WiredConditionNotHabboCount`
- **Behavior:** passes when the count is below the lower limit or above the upper limit. It is counted the same way as `wf_cnd_user_count_in`.
- **Main settings:** ints `[lower, upper, user source]`, clamped the same way. The default is 10-20 (dialog `NOT_USER_COUNT`, 16).
- **Notes:** the user source is saved with the box and survives a room reload.

### `wf_cnd_not_wearing_fx`

- **Class:** `WiredConditionNotHabboHasEffect`
- **Behavior:** negation of `wf_cnd_wearing_effect`: with `all` it passes when not every user wears the effect, with `any` when none does. An empty user set fails.
- **Main settings:** same as `wf_cnd_wearing_effect` (dialog `NOT_ACTOR_WEARS_EFFECT`, 23).

### `wf_cnd_not_wearing_b`

- **Class:** `WiredConditionNotHabboWearsBadge`
- **Behavior:** negation of `wf_cnd_wearing_badge`: with `all` it passes when not every user wears the badge, with `any` when none does. An empty user set passes.
- **Main settings:** same as `wf_cnd_wearing_badge` (dialog 22).

### `wf_cnd_not_in_group`

- **Class:** `WiredConditionNotInGroup`
- **Behavior:** with `any` it passes when at least one resolved user is not a member (non-Habbos count as non-members). With `all` it passes when no resolved user is a member. Fails when the group id is 0 or the user set is empty.
- **Main settings:** same as `wf_cnd_actor_in_group` (dialog `NOT_ACTOR_IN_GROUP`, 21).
- **Notes:** the quantifiers read the other way round from most negative boxes.

### `wf_cnd_not_in_team`

- **Class:** `WiredConditionNotInTeam`
- **Behavior:** negation of `wf_cnd_actor_in_team`: with `all` it passes when not every user is in the team, with `any` when none is. An empty user set passes.
- **Main settings:** same as `wf_cnd_actor_in_team` (dialog `NOT_ACTOR_IN_TEAM`, 17).

### `wf_cnd_not_match_snap`

- **Class:** `WiredConditionNotMatchStatePosition`
- **Behavior:** passes when the snapshot is empty. Otherwise it is the result of `wf_cnd_match_snapshot` with the same quantifier, turned around, so it also passes when no target resolves.
- **Main settings:** same as `wf_cnd_match_snapshot` (dialog `NOT_MATCH_SSHOT`, 13).

### `wf_cnd_not_trggrer_on`

- **Class:** `WiredConditionNotTriggerOnFurni`
- **Behavior:** the result of `wf_cnd_trggrer_on_frn` with the same quantifier, turned around. Fails when no user resolves. Passes when no furni resolves.
- **Main settings:** same as `wf_cnd_trggrer_on_frn` (dialog `NOT_ACTOR_ON_FURNI`, 15).

### `wf_cnd_actor_in_team`

- **Class:** `WiredConditionTeamMember`
- **Behavior:** passes when the resolved users' current game player has the configured team colour. This covers any team game the user is playing. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[team colour type (1 red, 2 green, 3 blue, 4 yellow; any known colour type is kept, unknown falls back to red), user source, quantifier]`. At least one int is needed. The quantifier defaults to `any` when it is not sent or not stored, and to `all` after pick-up (dialog `ACTOR_IN_TEAM`, 6).

### `wf_cnd_trggrer_on_frn`

- **Class:** `WiredConditionTriggerOnFurni`
- **Behavior:** a user counts as on the furni when one of the resolved furni is on the tile the user stands on. With `all` every resolved user must be on one; with `any` at least one user. Fails when no user or no furni resolves.
- **Main settings:** ints `[furni source, user source, quantifier (default all)]`, plus the picked furni (dialog `TRIGGER_ON_FURNI`, 2).
- **Notes:** legacy `wired_data` holding only ids loads as picked furni.

### `wf_cnd_has_handitem`

- **Class:** `WiredConditionHabboHasHandItem`
- **Behavior:** passes when the resolved users hold the configured handitem id. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[handitem id (0-10,000), user source, quantifier (default all)]`. At least one int is needed (dialog `ACTOR_HAS_HANDITEM`, 25).
- **Notes:** id 0 matches empty hands.

### `wf_cnd_not_has_handitem`

- **Class:** `WiredConditionNotHabboHasHandItem`
- **Behavior:** the result of `wf_cnd_has_handitem` with the same quantifier, turned around. An empty user set fails.
- **Main settings:** same as `wf_cnd_has_handitem` (dialog `NOT_ACTOR_HAS_HANDITEM`, 31).

### `wf_cnd_date_rng_active`

- **Class:** `WiredConditionDateRangeActive`
- **Behavior:** passes when the current Unix time (seconds) is at or after the start and at or before the end. An end of 0 is an open range, active from the start on. An unset box (both 0) never passes.
- **Main settings:** ints `[start timestamp, end timestamp]`. Both are needed. Negatives become 0. A start after a non-zero end saves the box as off (0, 0) (dialog `DATE_RANGE`, 24).
- **Notes:** both bounds are absolute instants, so the timezone does not affect the check; the client converts dates to timestamps.

### `wf_cnd_valid_moves`

- **Class:** `WiredConditionMovementValidation`
- **Behavior:** simulates every effect of the stack in order, tracking positions cumulatively, and fails if any simulation fails or throws. Effects that need a user are skipped when there is none. Passes when there is no stack.
- **Main settings:** none (dialog `MOVEMENT_VALIDATION`, 26).
- **Notes:** meant to go before move and rotate stacks, so that either every move happens or none does.

### `wf_cnd_counter_time_matches`

- **Class:** `WiredConditionCounterTimeMatches`
- **Behavior:** compares the current time of up-counter (stopwatch) furni to minutes × 60,000 ms + half-seconds × 500 ms. With `all` every target must be such a counter and match. With `any` other furni are skipped. Fails when no target resolves.
- **Main settings:** ints `[comparison (three-way, default 1 =), minutes (0-99), half-second steps (0-119), furni source, quantifier (default all)]`, plus the picked counters; only up-counters are kept (dialog `COUNTER_TIME_MATCHES`, 27).
- **Notes:** picking furni does not switch the source to `100` by itself; the picked list is only saved when the source is `100`.

### `wf_cnd_match_time`

- **Class:** `WiredConditionMatchTime`
- **Behavior:** checks hour, minute and second of the current time in the room's wired timezone (settings tab), or the hotel's when the room has none. Each part passes when its mode is skip, equals `from` (exact), or lies in `from..to` inclusive (range). A range with from > to wraps around (for example 22-2).
- **Main settings:** ints `[hourMode, hourFrom, hourTo, minuteMode, minuteFrom, minuteTo, secondMode, secondFrom, secondTo]`. Modes are 0 skip, 1 exact, 2 range. Hours are 0-23, minutes and seconds 0-59. A missing `to` equals `from` (dialog `MATCH_TIME`, 36).
- **Notes:** with every part on skip, the box always passes.

### `wf_cnd_match_date`

- **Class:** `WiredConditionMatchDate`
- **Behavior:** checks the current date in the room's wired timezone, or the hotel's when the room has none. The weekday and month must be in their masks. The day of month and year must pass their mode: skip, exact, or inclusive range (no wrap).
- **Main settings:** ints `[weekdayMask (bit n for day n, 1 = Monday ... 7 = Sunday), dayMode, dayFrom, dayTo (1-31), monthMask (bits 1-12), yearMode, yearFrom, yearTo (1-9999)]`. An empty mask means all. The year defaults to the current one (dialog `MATCH_DATE`, 37).

### `wf_cnd_actor_dir`

- **Class:** `WiredConditionActorDir`
- **Behavior:** passes when the resolved users' body rotation is one of the ticked directions. Uses the `all` or `any` quantifier. Fails when no direction is ticked or no user resolves.
- **Main settings:** ints `[direction mask (bit n = rotation n, 0-7), user source, quantifier (default all)]` (dialog `ACTOR_DIR`, 38).

### `wf_cnd_slc_quantity`

- **Class:** `WiredConditionSelectionQuantity`
- **Behavior:** counts the resolved users or furni of one source and compares the count to a number. An empty set counts as 0, so `= 0` passes when nothing resolves.
- **Main settings:** ints `[comparison (three-way, default 1 =), quantity (0-100), source group (0 users, 1 furni), source type]`. User sources are 0, 11, 200, 201; furni sources are 0, 100, 200, 201. Picked furni are saved only for furni source 100 (dialog `SLC_QUANTITY`, 39).
- **Notes:** a three-int save reads the third int as one combined selector: 0 triggering user, 1 signal users, 2 clicked user, 3 triggering furni, 4 picked furni, 5 signal furni.

### `wf_cnd_user_performs_action`

- **Class:** `WiredConditionUserPerformsAction`
- **Behavior:** a user matches when the current "user performs action" event is theirs with the chosen action. It also matches when their current state shows the action (sit or lay status, idle for relax, a sign status, a dance), or when their last recorded action within the past 5 seconds was it. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[action (1 wave (default), 2 blow kiss, 3 laugh, 4 awake, 5 relax, 6 sit, 7 stand, 8 lay, 9 sign, 10 dance, 11 thumb up), sign filter (0/1), sign id (0-17), dance filter (0/1), dance id (1-4), user source, quantifier (default all)]` (dialog `USER_PERFORMS_ACTION`, 28).
- **Notes:** the sign and dance filters only apply to those two actions.

### `wf_cnd_not_user_performs_action`

- **Class:** `WiredConditionNotUserPerformsAction`
- **Behavior:** the result of `wf_cnd_user_performs_action` with the same quantifier, turned around. An empty user set fails.
- **Main settings:** same as `wf_cnd_user_performs_action` (dialog `NOT_USER_PERFORMS_ACTION`, 30).

### `wf_cnd_has_altitude`

- **Class:** `WiredConditionHasAltitude`
- **Behavior:** compares each resolved furni's Z (clamped 0-40, rounded to 2 places) to the altitude. Uses the `all` or `any` quantifier. An empty target set fails.
- **Main settings:** ints `[comparison (three-way, default 1 = exact), furni source, quantifier (default all)]`. The string is the altitude (decimal, 0-40, default 0). Plus the picked furni (dialog `HAS_ALTITUDE`, 29).

### `wf_cnd_triggerer_match`

- **Class:** `WiredConditionTriggererMatch`
- **Behavior:** resolves a "match" user set and a "compare" user set, both deduplicated. It passes when the match users are of the chosen entity type and appear in the compare set. Uses the `all` or `any` quantifier. With the "certain avatar" mode, the compare set is cut to units whose name equals the text (case-insensitive). Fails when either set is empty.
- **Main settings:** ints `[entity type (1 user (default), 2 pet, 4 bot), avatar mode (0 any, 1 certain), match source (0, 11, 200, 201), compare source (0, 11, 200, 201, or 101 = the unit named in the text), quantifier (default all)]`. The string is a username (trimmed, max 64 chars) (dialog `TRIGGERER_MATCH`, 32).

### `wf_cnd_not_triggerer_match`

- **Class:** `WiredConditionNotTriggererMatch`
- **Behavior:** fails when the match set is empty. Otherwise it passes when `wf_cnd_triggerer_match` would not match, including when the compare set is empty or holds no unit of the chosen type.
- **Main settings:** same as `wf_cnd_triggerer_match` (dialog `NOT_TRIGGERER_MATCH`, 33).

### `wf_cnd_team_has_score`

- **Class:** `WiredConditionTeamHasScore`
- **Behavior:** reads the total score of the named team in the Freeze or Battle Banzai game running in the room (any state but idle) and compares it. No user is needed, so it also works from timers. A team missing from the game counts as 0. Fails when no such game is running.
- **Main settings:** ints `[team (1 red (default), 2 green, 3 blue, 4 yellow), comparison (three-way, default 1 =), score (0-1,000,000), user source, quantifier]`. The last two are stored but not used (dialog `TEAM_HAS_SCORE`, 34).

### `wf_cnd_team_has_rank`

- **Class:** `WiredConditionTeamHasRank`
- **Behavior:** a team's rank is 1 plus the number of other teams (red, green, blue, yellow) with a strictly higher total score, so ties share a rank. A named team (1-4) is looked up in the Freeze or Banzai game running in the room, with no user needed; it fails when there is no game or that team is not in it. Team `0` ("triggering user's team") takes the triggering user's team and checks, with the quantifier, that the resolved users are on that team in the same game and that the team has the placement. An empty user set fails in that mode.
- **Main settings:** ints `[team (0 triggering user's team, 1-4 named; default 1), placement (1-4, default 1), user source, quantifier (default all)]`. Source and quantifier are only used in team-0 mode (dialog `TEAM_HAS_RANK`, 35).

### `wf_cnd_has_var`

- **Class:** `WiredConditionHasVariable`
- **Behavior:** passes when the target holds the variable. The target is the resolved users or furni (with the `all` or `any` quantifier), the context, or the room. For user variables, only Habbos can hold one. When the variable is an array, it checks per owner that the array exists (mode 0) or that an entry exists at the given index (mode 1). Fails when no user or furni resolves, or no owner resolves.
- **Main settings:** ints `[target type (0 user, 1 furni, 2 context, 3 room), user source, furni source (0, 100, 200, 201), quantifier (default all)]`. The string is `token` or `token<TAB>{array json}`. The token is `custom:<variable box id>` (a bare number means the same) or `internal:<key>` for built-in values. Picked furni apply for target furni with source 100. An empty token is refused (dialog `HAS_VAR`, 40).
- **Notes:** the negative twin is `wf_cnd_neg_has_var`.

### `wf_cnd_user_level`

- **Class:** `WiredConditionUserLevel`
- **Behavior:** reads the level a user has reached on a user variable, as derived by the level-up extra attached to that variable. It compares that level to the configured level. Uses the `all` or `any` quantifier. A user without the variable, or a variable without a level-up extra, has no level and does not match. Fails when no variable is set or no user resolves.
- **Main settings:** ints `[level (1-10,000, default 1), comparison (variable table, default 1 >=), user source (stored as sent), quantifier (default all)]`. The string is the variable box id (dialog `USER_LEVEL`, 61).
- **Notes:** this is not an account level; the name is borrowed from the official box.

### `wf_cnd_habbo_has_rank`

- **Class:** `WiredConditionHabboHasRank`
- **Behavior:** compares each resolved user's permission rank id (read live) with the configured rank. Uses the `all` or `any` quantifier. An empty user set fails. A unit that is not a Habbo never matches.
- **Main settings:** ints `[rank (1-1,000, default 1), comparison (six-code, default 5 >=), user source (stored as sent), quantifier (default all)]` (dialog `USER_RANK`, 62).
- **Notes:** the negative twin is `wf_cnd_not_habbo_has_rank`.

### `wf_cnd_not_habbo_has_rank`

- **Class:** `WiredConditionNotHabboHasRank`
- **Behavior:** the per-user comparison of `wf_cnd_habbo_has_rank` turned around, under the same quantifier (`all`: every user fails the comparison; `any`: at least one does). An empty user set fails, and non-Habbos never match.
- **Main settings:** same as `wf_cnd_habbo_has_rank` (dialog 62).

### `wf_cnd_user_cooldown`

- **Class:** `WiredConditionUserCooldown`
- **Behavior:** the triggering Habbo passes, and then does not pass again until the configured seconds have gone by. Fails when there is no triggering Habbo, so a timer-driven stack never passes.
- **Main settings:** ints `[seconds (1 to 604,800, which is seven days)]` (dialog `USER_COOLDOWN`, 64).
- **Notes:** the pass is recorded as soon as this box passes, even if another condition then stops the stack. The memory is per box and in RAM only: a restart, a re-save or a pick-up forgets everyone. It tracks the 10,000 most recent users.

### `wf_cnd_first_trg`

- **Class:** `WiredConditionUserFirstTime`
- **Behavior:** the triggering Habbo passes the first time and never again.
- **Main settings:** none (dialog `USER_ONCE`, 65).
- **Notes:** who has passed is stored in `wired_data`, so a restart or re-save keeps it; pick-up forgets everyone. The pass is recorded when this box passes. It keeps the 10,000 most recent users.

### `wf_cnd_daily_trg`

- **Class:** `WiredConditionUserDaily`
- **Behavior:** the triggering Habbo passes once per calendar day.
- **Main settings:** none (dialog `USER_DAILY`, 66).
- **Notes:** the day follows the room's wired timezone (falling back to the hotel timezone). It has the same persistence and pick-up rules as `wf_cnd_first_trg`.

### `wf_cnd_furni_opacity_is`

- **Class:** `WiredConditionFurniOpacityIs`
- **Behavior:** passes when every resolved furni's current opacity compares as configured. It reads the opacity the triggering Habbo sees, since `wf_act_change_opacity` can set a private one; without a triggering Habbo it reads the room-wide value. Fails when no furni resolves or no opacity state comes back.
- **Main settings:** ints `[opacity (0-100, default 100), comparison (six-code, default 1 =), furni source (default 100, stored as sent)]`, plus the picked furni (dialog `FURNI_OPACITY`, 63).

### `wf_cnd_not_furni_opacity_is`

- **Class:** `WiredConditionNotFurniOpacityIs`
- **Behavior:** passes when no resolved furni's opacity compares as configured (the per-furni check turned around, all must fail it). Fails when no furni resolves.
- **Main settings:** same as `wf_cnd_furni_opacity_is`.

### `wf_cnd_x_points_leaderboard`

- **Class:** `WiredConditionHabboHasHighscorePoints`
- **Behavior:** takes each resolved user's best score across the selected highscore boards, including team entries that name them, and compares it with the number. A user with no entry has 0. Uses the `all` or `any` quantifier. Fails when no board is selected or no user resolves. A unit that is not a Habbo fails `all` and is skipped under `any`.
- **Main settings:** ints `[points (at least 0), comparison (six-code, default 5 >=), user source (stored as sent), quantifier (default all)]`, plus the picked scoreboard furni (dialog `USER_HIGHSCORE_POINTS`, 67).

### `wf_cnd_not_x_points_leaderboard`

- **Class:** `WiredConditionNotHabboHasHighscorePoints`
- **Behavior:** the per-user comparison of `wf_cnd_x_points_leaderboard` turned around, under the same quantifier. Fails when no board is selected or no user resolves.
- **Main settings:** same as `wf_cnd_x_points_leaderboard`.

### `wf_cnd_neg_has_var`

- **Class:** `WiredConditionNotHasVariable`
- **Behavior:** the result of `wf_cnd_has_var` with the same quantifier, turned around. An empty user or furni target set still fails. For arrays, no resolved owner gives a pass. Context and room targets are simply turned around.
- **Main settings:** same as `wf_cnd_has_var` (dialog `NOT_HAS_VAR`, 41).

### `wf_cnd_var_val_match`

- **Class:** `WiredConditionVariableValueMatch`
- **Behavior:** compares the variable's value on each target (users or furni with the `all` or `any` quantifier, or the context, or the room) with a constant or with a reference variable. A reference value is paired with a target by the same entity id when the reference is of the same target type, otherwise by position, otherwise the first value is used. A missing value or reference fails that target. Fails when no user or furni resolves. For array variables it compares one cell field per owner.
- **Main settings:** ints `[target type (0 user, 1 furni, 2 context, 3 room), comparison (variable table, default 2 =), reference mode (0 constant, 1 variable), constant (±1,000,000,000), reference target type, user source, furni source, reference user source, reference furni source (0, 200, 201, or 101 = second picked list), quantifier (default all)]`. The string is `token<TAB>referenceToken<TAB>referenceFurniIds(;)<TAB>{array json}`. The picked furni are the targets for furni source 100 (dialog `VAR_VAL_MATCH`, 42).
- **Notes:** saving is refused when the variable, or the reference in variable mode, does not exist or has no value. User, furni, context and room variables, plus `internal:` keys, are supported.

### `wf_cnd_var_age_match`

- **Class:** `WiredConditionVariableAgeMatch`
- **Behavior:** compares how long ago a custom variable was created or last updated on each target (users or furni with the `all` or `any` quantifier, or the context, or the room) against a duration. A target without the variable fails. Fails when no user or furni resolves.
- **Main settings:** ints `[target type (0 user, 1 furni, 2 context, 3 room), compare field (0 created (default), 1 updated), comparison (0 lower than (default), 2 higher than), amount (0-1,000,000), unit (0 ms, 1 s (default), 2 min, 3 h, 4 days, 5 weeks, 6 months = 30 days, 7 years = 365 days), user source, furni source, quantifier]`. The string is the `custom:<id>` token. Plus picked furni (dialog `VAR_AGE_MATCH`, 43).
- **Notes:** room variables only have an update time; a room target with "created" is refused on save. Timestamps are whole seconds.

### `wf_cnd_check_array`

- **Class:** `WiredConditionCheckArray`
- **Behavior:** resolves the owners of an array variable (room, context, or users or furni from the owner source) and checks each owner, with the `all` or `any` quantifier over owners. Match mode counts entries that meet the field criteria (all or any criterion) and tests the count against the result mode, or tests a single entry at a given index. State mode tests empty, full, length, or available indexes against a reference. Fails when the variable is not an array, when no owner resolves, or when there are more owners than the per-execution cap.
- **Main settings:** ints `[variable type (0 furni, 1 room (default), 2 user, 3 context), owner source, mode (0 match, 1 state), scope (0 any index, 1 specific index), criteria mode (0 all, 1 any), result mode (0 all entries, 1 at least one (default), 2 not all, 3 none, 4 fewer than N, 5 exactly N, 6 more than N), (unused), state check (0 empty, 2 full, 3 length, 4 available indexes), state comparison (variable table, default 2 =), quantifier]`. All ten ints are required. The string is JSON with the variable box id, criteria, index, result reference and state reference (max 32,768 chars). Plus picked furni (dialog `CHECK_ARRAY`, 60).
- **Notes:** saving is refused when the definition, a criterion field, the index or a needed reference is invalid. N comes from the result reference and must be at least 0.

### `wf_cnd_chest_has_items`

- **Class:** `WiredConditionChestHasItems`
- **Behavior:** adds up the contents (currency amount plus furni count) of the selected wired chests that are upgraded to answer wired, and compares the total with the amount. Chests that are not upgraded add 0. With no chest selected the total is 0 and is still compared.
- **Main settings:** ints `[amount (at least 0, default 1), comparison (six-code, default 5 >=), amount mode (0 constant, 1 variable), variable target (0 user, 3 room (default))]`. The string is the `custom:<id>` token, required in variable mode. Plus the picked chests; only wired chests are kept (dialog `CHEST_HAS_ITEMS`, 47).
- **Notes:** a user variable is read from the first triggering Habbo that has a value. An unreadable variable fails the box.

### `wf_cnd_chest_has_item_type`

- **Class:** `WiredConditionChestHasItemType`
- **Behavior:** counts furni of one base item id across the selected upgraded wired chests and compares the count with the amount. Fails when the base item id is not set.
- **Main settings:** ints `[base item id, amount (at least 1, default 1), comparison (six-code, default 5 >=)]`, plus the picked chests (dialog `CHEST_HAS_ITEM_TYPE`, 48).

### `wf_cnd_not_battlebanzai`

- **Class:** `WiredConditionNoBattleBanzaiRunning`
- **Behavior:** passes when the room has no Battle Banzai game or its game is not in the RUNNING state.
- **Main settings:** none (dialog `NO_BATTLEBANZAI`, 44).
- **Notes:** the positive twin is `wf_cnd_battlebanzai`.

### `wf_cnd_not_battlebz`

- **Class:** `WiredConditionNoBattleBanzaiRunning`
- **Behavior:** alias of `wf_cnd_not_battlebanzai`; same runtime.

### `wf_cnd_trg_frn_adjacent_state`

- **Class:** `WiredConditionTriggerFurniAdjacentState`
- **Behavior:** passes when any furni on the 8 tiles around the wired trigger box has a state (extradata) exactly equal to the required text. When furni are picked, only furni of the picked base types count. Fails when there is no trigger box or tile.
- **Main settings:** the string is the required state (trimmed, max 256 chars; empty matches an empty state). Optional picked furni scope the types. There are no ints (dialog `TRG_FURNI_ADJACENT_STATE`, 46).
- **Notes:** the match is case-sensitive, and the trigger box itself is ignored.

### `wf_cnd_user_on_furni_with_state`

- **Class:** `WiredConditionUserOnFurniWithState`
- **Behavior:** like `wf_cnd_trggrer_on_frn`, but the resolved furni under the user must also have the required state (exact extradata). An empty state is the plain on-furni check. Fails when no user or no furni resolves.
- **Main settings:** ints `[furni source, user source, quantifier (default all)]`. The string is the required state (trimmed, max 256 chars). Plus picked furni (dialog `USER_ON_FURNI_WITH_STATE`, 45).

### `wf_cnd_habbo_has_credits`

- **Class:** `WiredConditionHabboHasCredits`
- **Behavior:** passes when the resolved users' credits compare to the amount with the chosen operator. Uses the `all` or `any` quantifier. An empty user set fails. Non-Habbos never match.
- **Main settings:** ints `[teamType (unused), operator (six-code, default 5 >=), amount (0-1,000,000), user source, quantifier (default all)]` (dialog 51).
- **Notes:** the negative twin is `wf_cnd_not_habbo_has_credits`.

### `wf_cnd_habbo_has_duckets`

- **Class:** `WiredConditionHabboHasDuckets`
- **Behavior:** same as `wf_cnd_habbo_has_credits`, for duckets (currency type 0).
- **Main settings:** same int layout as `wf_cnd_habbo_has_credits` (dialog 51).
- **Notes:** the negative twin is `wf_cnd_not_habbo_has_duckets`.

### `wf_cnd_battlebanzai`

- **Class:** `WiredConditionBattleBanzaiRunning`
- **Behavior:** passes while the room's Battle Banzai game is in the RUNNING state.
- **Main settings:** none (dialog 44).
- **Notes:** the negative twin is `wf_cnd_not_battlebanzai`.

### `wf_cnd_habbo_is_male`

- **Class:** `WiredConditionHabboIsMale`
- **Behavior:** passes when the resolved users' gender is M. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[user source, quantifier (default 1 any)]`. The badge text slot of the storage it reuses is ignored (dialog `USER_ATTRIBUTE`, 49).

### `wf_cnd_habbo_is_female`

- **Class:** `WiredConditionHabboIsFemale`
- **Behavior:** passes when the resolved users' gender is F. Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** same as `wf_cnd_habbo_is_male` (dialog 49).

### `wf_cnd_habbo_has_rights`

- **Class:** `WiredConditionHabboHasRights`
- **Behavior:** passes when the resolved users have rights in the room (owner, group rights or granted rights). Uses the `all` or `any` quantifier. An empty user set fails.
- **Main settings:** ints `[user source, quantifier (default 1 any)]` (dialog 49).

### `wf_cnd_not_habbo_has_rights`

- **Class:** `WiredConditionHabboNotHasRights`
- **Behavior:** checks per user that they do NOT have rights. With `any` it passes when at least one resolved Habbo lacks rights; with `all` when every one does. An empty user set fails. Non-Habbos never match.
- **Main settings:** same as `wf_cnd_habbo_has_rights` (dialog `NOT_USER_ATTRIBUTE`, 50).

### `wf_cnd_trg_by_user`

- **Class:** `WiredConditionTriggererMatch`
- **Behavior:** alias of `wf_cnd_triggerer_match`; same runtime.

### `wf_cnd_not_trg_by_user`

- **Class:** `WiredConditionNotTriggererMatch`
- **Behavior:** alias of `wf_cnd_not_triggerer_match`; same runtime.

### `wf_cnd_not_bot_is_dancing`

- **Class:** `WiredConditionNotUserPerformsAction`
- **Behavior:** alias of `wf_cnd_not_user_performs_action`; same runtime.
- **Notes:** the action still defaults to wave, and nothing limits it to bots. Set the action to dance (10) and pick a source that resolves the bot.

### `wf_cnd_user_in_range`

- **Class:** `WiredConditionUserInRange`
- **Behavior:** measures the tile distance (straight line) from the wired trigger box to each resolved user's current tile and tests it against the radius. Uses the `all` or `any` quantifier. Fails when no user resolves or there is no trigger box or tile.
- **Main settings:** ints `[comparison (0 <, 1 <= (default), 2 >), user source, quantifier (default all)]`. The string is the radius (decimal, at least 0, 2 places). No furni are picked (dialog `USER_RANGE`, 57).

### `wf_cnd_user_not_in_range`

- **Class:** `WiredConditionUserNotInRange`
- **Behavior:** the per-user test of `wf_cnd_user_in_range`, flipped, under the same quantifier. It passes when the room, layout, trigger box or tile is missing, or when no user resolves. A user with no current tile counts as outside.
- **Main settings:** same as `wf_cnd_user_in_range`.

### `wf_cnd_habbo_in_group`

- **Class:** `WiredConditionGroupMember`
- **Behavior:** alias of `wf_cnd_actor_in_group`; same runtime.

### `wf_cnd_not_habbo_in_group`

- **Class:** `WiredConditionNotInGroup`
- **Behavior:** alias of `wf_cnd_not_in_group`; same runtime.

### `wf_cnd_match_snapshot_new`

- **Class:** `WiredConditionMatchStatePosition`
- **Behavior:** alias of `wf_cnd_match_snapshot`; same runtime.

### `wf_cnd_not_match_snap_new`

- **Class:** `WiredConditionNotMatchStatePosition`
- **Behavior:** alias of `wf_cnd_not_match_snap`; same runtime.

### `wf_cnd_tgr_furni_hv_avtrs`

- **Class:** `WiredConditionFurniHaveHabbo`
- **Behavior:** alias of `wf_cnd_furnis_hv_avtrs`; same runtime.
- **Notes:** the default furni source `0` already checks the triggering furni.

### `wf_cnd_not_tgr_furni_hv_avtrs`

- **Class:** `WiredConditionNotFurniHaveHabbo`
- **Behavior:** alias of `wf_cnd_not_hv_avtrs`; same runtime.

### `wf_cnd_wears_effect`

- **Class:** `WiredConditionHabboHasEffect`
- **Behavior:** alias of `wf_cnd_wearing_effect`; same runtime.

### `wf_cnd_not_wears_effect`

- **Class:** `WiredConditionNotHabboHasEffect`
- **Behavior:** alias of `wf_cnd_not_wearing_fx`; same runtime.

### `wf_cnd_wears_handitem`

- **Class:** `WiredConditionHabboHasHandItem`
- **Behavior:** alias of `wf_cnd_has_handitem`; same runtime.

### `wf_cnd_not_wears_handitem`

- **Class:** `WiredConditionNotHabboHasHandItem`
- **Behavior:** alias of `wf_cnd_not_has_handitem`; same runtime.

### `wf_cnd_trgr_stuff_matches`

- **Class:** `WiredConditionFurniTypeMatch`
- **Behavior:** alias of `wf_cnd_stuff_is`; same runtime.

### `wf_cnd_atleast_one_user_in_team`

- **Class:** `WiredConditionTeamMember`
- **Behavior:** alias of `wf_cnd_actor_in_team`; same runtime.
- **Notes:** nothing forces "at least one"; set the quantifier to `any` (1) for that meaning.

### `wf_cnd_bot_is_dancing`

- **Class:** `WiredConditionUserPerformsAction`
- **Behavior:** alias of `wf_cnd_user_performs_action`; same runtime.
- **Notes:** the action defaults to wave and nothing limits it to bots; set the action to dance (10).

### `wf_cnd_habbo_is_dancing`

- **Class:** `WiredConditionUserPerformsAction`
- **Behavior:** alias of `wf_cnd_user_performs_action`; same runtime.
- **Notes:** the action defaults to wave; set it to dance (10).

### `wf_cnd_not_habbo_is_dancing`

- **Class:** `WiredConditionNotUserPerformsAction`
- **Behavior:** alias of `wf_cnd_not_user_performs_action`; same runtime.
- **Notes:** the action defaults to wave; set it to dance (10).

### `wf_cnd_furni_state_pattern`

- **Class:** `WiredConditionMatchStatePosition`
- **Behavior:** alias of `wf_cnd_match_snapshot`; same runtime.
- **Notes:** tick only "state" to get a pure state check.

### `wf_cnd_is_state`

- **Class:** `WiredConditionMatchStatePosition`
- **Behavior:** alias of `wf_cnd_match_snapshot`; same runtime.
- **Notes:** tick only "state" to get a pure state check.

### `wf_cnd_trg_state_is`

- **Class:** `WiredConditionMatchStatePosition`
- **Behavior:** alias of `wf_cnd_match_snapshot`; same runtime.
- **Notes:** with the triggering-furni source and only "state" ticked, it checks the triggering furni against a snapshot entry of the same type.

### `wf_cnd_furnis_hv_avtrs_custom`

- **Class:** `WiredConditionFurniHaveHabbo`
- **Behavior:** alias of `wf_cnd_furnis_hv_avtrs`; same runtime.

### `wf_cnd_x_habbos_on_furni`

- **Class:** `WiredConditionFurniHaveHabbo`
- **Behavior:** alias of `wf_cnd_furnis_hv_avtrs`; same runtime.
- **Notes:** there is no count setting; it only checks whether someone is on the furni. To count, use `wf_cnd_slc_quantity` with a selector.

---

## 7. Extras

### `wf_xtra_scan_chest_furni_by_type`

- **Class:** `WiredEffectScanChestFurniByType`
- **Behavior:** a read-only selector despite its `wf_xtra` key. It picks every room floor furni whose base type is stored in the picked wired chest(s).
- **Main settings:** int params `[filterExisting, invert]` plus up to 20 picked chests.
- **Notes:** follows the section 5 selector rules. Chests whose owner has not enabled wired access (`answersWired`) are ignored.

### `wf_xtra_custom_contract`

- **Class:** `InteractionWiredCustomContract`
- **Behavior:** a contract config-holder (dialog code 113). It never runs by itself: `wf_act_init_transaction` reads the contracts picked in it. A contract with a give side opens a negotiation window for the player (120 s timeout). A contract that asks for nothing runs instantly and atomically: it pre-checks balances and chest stock, then pays and credits, and fires transaction-complete or transaction-fail.
- **Main settings:** int params in the rules format `[-1, ruleCount, (nodeCount, node...)..., rewardCount, node...]`, where a node is `kind (0 currency / 1 furni), currencyType, wallItem, baseItemId, amount`. Up to 8 give alternatives with up to 8 nodes each, and up to 8 reward nodes. Nodes with an amount of 0 or less are dropped. The legacy format `[count, (dir, type, amount)...]` is still read as one required alternative. The string param holds `index=posterId` pairs for wall posters. Picked furni are the linked chests: the first chest with wired access is the deposit sink for pay terms and the source for receive terms.
- **Notes:** currency type `-1` is credits and `>= 0` is a points type. A receive term without a linked chest is minted. The player pays with the first give alternative they can afford. The instant path handles every term as currency, so a furni term in a reward-only contract is treated as currency type 0 there.

### `wf_xtra_random`

- **Class:** `WiredExtraRandom`
- **Behavior:** each firing runs a random subset of the stack's effects instead of all of them. It can avoid effects used in recent firings.
- **Main settings:** int params `[pickAmount, skipExecutions]`. Pick amount 1-1000 (default 1). Skip executions 0-1000 (default 0): effects chosen in the last N firings are avoided while enough others remain.
- **Notes:** the history is cleared on save, move and pickup. It takes priority over `wf_xtra_unseen` and `wf_xtra_exec_in_order`.

### `wf_xtra_unseen`

- **Class:** `WiredExtraUnseen`
- **Behavior:** each firing runs one effect: the first one in stack order not yet run in this cycle. When all have run, the memory resets.
- **Main settings:** none (it saves nothing).
- **Notes:** the memory holds up to 1000 ids and is cleared on move and pickup. Ignored when `wf_xtra_random` is also present.

### `wf_xtra_or_eval`

- **Class:** `WiredExtraOrEval`
- **Behavior:** sets how the stack's condition results are combined. Conditions marked OR are grouped by type, and each group counts as one requirement.
- **Main settings:** int params `[mode, furniSource, compareValue]`. Mode: `0` all (default), `1` at least one, `2` not all (at least one but not all), `3` none, `4` fewer than N, `5` exactly N, `6` more than N. N is 0-100 (the minimum is 1 for "fewer than"), default 1. The furni source (0/100/200/201) and picked `wf_cnd_*`/`wf_xtra_*` boxes are saved.
- **Notes:** at runtime only the mode and N are used. The source and picked boxes have no effect. A stack with no requirements always passes.

### `wf_xtra_filter_furni`

- **Class:** `WiredExtraFilterFurni`
- **Behavior:** after all selectors have run, it limits the selected furni to N, chosen at random.
- **Main settings:** int param `[amount]`, 0-10000. If no int is sent, the string param is parsed instead.
- **Notes:** 0 (the unconfigured value) means no limit. With several filter boxes the smallest amount wins. It only applies when a selector changed the furni targets. Variable filters run first.

### `wf_xtra_filter_user`

- **Class:** `WiredExtraFilterUser`
- **Behavior:** after all selectors have run, it limits the selected users to N, chosen at random.
- **Main settings:** int param `[amount]`, 0-10000, with the same string fallback as the furni filter.
- **Notes:** 0 means no limit. With several boxes the smallest amount wins. Variable filters run first.

### `wf_xtra_filter_users`

- **Class:** `WiredExtraFilterUser`
- **Behavior:** alias of `wf_xtra_filter_user`; same runtime.

### `wf_xtra_filter_furni_by_var`

- **Class:** `WiredExtraFilterFurniByVariable`
- **Behavior:** after the selectors have run, it keeps the top N selected furni ranked by a variable. Furni without the variable are dropped.
- **Main settings:** int params `[sortBy, amountMode, amount, referenceTarget, referenceUserSource, referenceFurniSource]`. Sort: `0` highest value, `1` lowest value, `2` oldest creation, `3` latest creation, `4` oldest update, `5` latest update. Amount mode: `0` constant, `1` variable. The amount is 0-10000 (default 1). The reference target is 0 user, 1 furni, 2 context or 3 room, and the furni source can be `101` (picked in this box). The string param is `variableToken<TAB>referenceVariableToken`.
- **Notes:** an amount of 0, or a variable amount that cannot be read, empties the selection. Several variable filters run in item-id order, before the plain quantity filter.

### `wf_xtra_filter_users_by_var`

- **Class:** `WiredExtraFilterUsersByVariable`
- **Behavior:** the user-side version of `wf_xtra_filter_furni_by_var`: it keeps the top N selected users ranked by a user variable.
- **Main settings:** the same 6 int params and string param.

### `wf_xtra_mov_carry_users`

- **Class:** `WiredExtraMoveCarryUsers`
- **Behavior:** when the stack moves furni, the users on them move along.
- **Main settings:** int params `[carryMode, userSource]`. Carry mode: `0` only users standing directly on the moving furni (it is their top item), `1` any user on its tiles. User source: `0` trigger, `200` selector, `201` signal, `900` everyone in the room.

### `wf_xtra_mov_no_animation`

- **Class:** `WiredExtraMoveNoAnimation`
- **Behavior:** furni and user moves from this stack are sent without the wired slide animation (instant).
- **Main settings:** none.

### `wf_xtra_anim_time`

- **Class:** `WiredExtraAnimationTime`
- **Behavior:** sets the duration of the wired move animation for this stack.
- **Main settings:** int param `[durationMs]`, 50-2000 (default 500). If no int is sent, the string param is used.
- **Notes:** `wf_xtra_mov_no_animation` takes precedence.

### `wf_xtra_mov_physics`

- **Class:** `WiredExtraMovePhysics`
- **Behavior:** changes the collision rules for this stack's furni moves. It can keep the furni's altitude, let it pass through chosen furni or users, and make chosen furni block it.
- **Main settings:** int params `[keepAltitude, moveThroughFurni, moveThroughUsers, blockByFurni, moveThroughFurniSource, blockByFurniSource, moveThroughUsersSource]`. The flags are 0/1. Each source is `0` trigger, `200` selector, `201` signal or `900` whole room.
- **Notes:** pass-through users only apply to habbos (unit type USER). The moving furni itself is never in its own lists.

### `wf_xtra_exec_in_order`

- **Class:** `WiredExtraExecuteInOrder`
- **Behavior:** runs the effects in stack order, grouped by delay: each delay batch runs in order, and delayed batches are scheduled.
- **Main settings:** none.
- **Notes:** ignored when `wf_xtra_random` or `wf_xtra_unseen` is present.

### `wf_xtra_execution_limit`

- **Class:** `WiredExtraExecutionLimit`
- **Behavior:** lets the stack run at most N times in a sliding time window. A slot is used only after the conditions pass.
- **Main settings:** int params `[maxExecutions, timeWindowMs]`. Max executions 1-100 (default 1). The window is 1000-10000 ms (default 1000), rounded to a 500 ms step.
- **Notes:** the history is cleared on save, move and pickup.

### `wf_xtra_text_output_username`

- **Class:** `WiredExtraTextOutputUsername`
- **Behavior:** defines a `$(name)` placeholder that wired text (for example chat and bot talk) replaces with the names of the source users.
- **Main settings:** int params `[placeholderType, userSource]`. Type: `1` single (first name, default), `2` multiple (joined with the delimiter). User source: `0` trigger, `11` clicked user, `200` selector, `201` signal. The string param is `name<TAB>delimiter`: the name is up to 32 characters (a wrapping `$( )` is stripped), and the delimiter is up to 16 characters (default `, `).
- **Notes:** an empty name disables the placeholder. Bots and pets use their own names. With source 0 or 11 the stack needs an actor.

### `wf_xtra_text_output_furni_name`

- **Class:** `WiredExtraTextOutputFurniName`
- **Behavior:** defines a `$(name)` placeholder that is replaced with the display names of the source furni (falling back to the item name).
- **Main settings:** int params `[placeholderType, furniSource]`, with type 1 single / 2 multiple and furni source 0/100/200/201. The string param is `name<TAB>delimiter`, with the same limits as the username version. Picked furni are stored only for source 100, capped by `hotel.wired.furni.selection.count`.
- **Notes:** with the selector source, wired boxes picked by the selectors are included.

### `wf_xtra_text_output_variable`

- **Class:** `WiredExtraTextOutputVariable`
- **Behavior:** defines a `$(name)` placeholder that is replaced with a variable's value for the source holders, either as a number or through the variable's text connector. It also works on array variables (one cell per holder).
- **Main settings:** int params `[target, displayType, placeholderType, userSource, furniSource]`. Target: `0` user, `1` furni, `2` context, `3` room. Display: `1` numeric, `2` textual. Type: `1` single, `2` multiple. The string param is `variableToken<TAB>name<TAB>delimiter[<TAB>arrayAddressJson]`. Picked furni are stored for target furni with source 100.
- **Notes:** the save fails without a valid variable, or with an array address that cannot be reached. Textual display falls back to numeric unless a `wf_xtra_var_text_connector` is attached to the variable (for arrays, to the field). A user target with source 0 or 11 needs an actor.

### `wf_xtra_text_output_global`

- **Class:** `WiredExtraTextOutputGlobal`
- **Behavior:** a global placeholder (code 2000). It defines a `$(name)` placeholder with a fixed text that every wired text in the room is run through, whichever stack sends it. A stack's own placeholders are applied first, then the room's global ones in item id order (at most 64).
- **Main settings:** int params `[mode, 0, sourceRoomId]`. Mode `0` uses a typed value, and the string param is `name<TAB>text`. Mode `1` takes the text of a typed global placeholder in another room of the same owner, and the string param is `name<TAB>sourcePlaceholderName`. The name is up to 32 characters (a wrapping `$( )` is stripped) and the text up to 100, without tabs or line breaks. An empty name in mode 1 takes the source's name.
- **Notes:** the save fails in mode 1 unless the source room belongs to the same owner and holds a typed global placeholder of that name. The editor gets a third tab-separated field with the owner's shared placeholders as JSON, read from the database when the editor opens (at most 100). At firing time a linked placeholder reads its source only when that room is loaded, and otherwise uses the text stored at the last save. Output stays within the usual 16 KB and 512-replacement limits.

### `wf_xtra_text_input_variable`

- **Class:** `WiredExtraTextInputVariable`
- **Behavior:** on a "user says keyword" stack, it turns a `#name#` slot in the keyword template into a capture. When the chat matches, the captured text is parsed and stored in a context variable for this firing. When a capture does not parse, the trigger does not match.
- **Main settings:** int param `[displayType]`: `1` numeric (default; the text must be an integer), `2` textual (the text is mapped back through the variable's text connector). The string param is `variableToken<TAB>capturerName`: the token must point to a `wf_var_context` definition with a value, and the name is up to 32 characters (a wrapping `#` is stripped).
- **Notes:** textual mode falls back to numeric when the context variable has no text connector. The owner-only setting of the keyword trigger is respected. Capturers with an empty name are ignored.

### `wf_xtra_var_text_connector`

- **Class:** `WiredExtraVariableTextConnector`
- **Behavior:** maps numeric values to text labels for the variable definition on its tile (or one array field). Text output shows the label, text input and echo read it back, and unmapped values show as numbers.
- **Main settings:** string param with lines `value=text` (a `,` separator is also accepted), or JSON `{mappingsText, fieldId}` for arrays. Keys are integers (64-bit), and text matching when reading back is case-insensitive.
- **Notes:** up to 1000 characters and 30 lines, otherwise the save fails. A field id other than 0 must be a field of an array definition on the same tile. Spaces in labels are sent as non-breaking spaces.

### `wf_xtra_varfx_hp`

- **Class:** `WiredExtraVariableFxHealthPoints`
- **Behavior:** a variable fx (code 130, health category): it draws hearts or a health bar over every avatar or furni that holds the user/furni variable defined on the same tile. The box keeps no state. The fx service works out per viewer what to show and sends it to the client.
- **Main settings:** 16 int params: `[source (0 user / 1 furni), visibility, showMode, showDurationMs, style, color, width, segments, defaultMin, defaultMax, overrideMinOn, overrideMaxOn, overrideMinTarget, overrideMaxTarget, audienceValue, categoryExtra]`. Visibility: `0` only the holder, `1` holder's game team, `2` everyone (default), `3` viewers who have the audience variable, `4` viewers whose audience variable equals the audience value. Show mode: `0` always, `1` when it changes (for the show duration, 1500-20000 ms, default 3000), `2` never. The style is clamped to the category's style table (health: hearts, cross, thermometer, bar). Color is `-1` (none) to 1000, or `1001` dynamic levelling / `1002` team color. Width is -1..100 (default 2). Segments 0-100. Override target: `0` holder's own value, `1` room variable. The string param is `overrideMinToken<TAB>overrideMaxToken<TAB>audienceToken<TAB>icon`, where tokens are `custom:<id>` and the icon (up to 32 characters) must be a known icon.
- **Notes:** the save fails when max is not greater than min. For furni, visibility 0/1 reads as everyone. With visibility 3/4 and no audience variable chosen, nobody sees it. The shown variable is the first `wf_var_user` (user source) or `wf_var_furni` (furni source) on the tile.

### `wf_xtra_var_fx_health`

- **Class:** `WiredExtraVariableFxHealthPoints`
- **Behavior:** alias of `wf_xtra_varfx_hp`; same runtime.

### `wf_xtra_varfx_prog`

- **Class:** `WiredExtraVariableFxProgressBar`
- **Behavior:** a variable fx (code 131): a plain progress bar of the variable between min and max. It has the same runtime and 16 params as `wf_xtra_varfx_hp`.
- **Main settings:** as `wf_xtra_varfx_hp`. Styles: plain, block, striped, arrow, classic mini.

### `wf_xtra_var_fx_progress`

- **Class:** `WiredExtraVariableFxProgressBar`
- **Behavior:** alias of `wf_xtra_varfx_prog`; same runtime.

### `wf_xtra_varfx_levelling`

- **Class:** `WiredExtraVariableFxLevellingProgress`
- **Behavior:** a variable fx (code 132): a level badge with a progress bar for an experience variable. The level, level cap, maxed flag and range of the current level come from the `wf_xtra_var_lvlup_system` on the variable's tile. Without one, the holder shows as level 1 of 1.
- **Main settings:** the same 16 params as `wf_xtra_varfx_hp`, but min/max and the overrides are not used (no value range). Category extra: the bar drawn next to the badge (block, striped or arrow; block by default).
- **Notes:** at the level cap the bar is drawn full.

### `wf_xtra_var_fx_level`

- **Class:** `WiredExtraVariableFxLevellingProgress`
- **Behavior:** alias of `wf_xtra_varfx_levelling`; same runtime.

### `wf_xtra_varfx_status`

- **Class:** `WiredExtraVariableFxStatusBar`
- **Behavior:** a variable fx (code 133): a themed bar where the style brings the icon and color. Styles: energy, shield, magic, food, stamina, poison, mana, health, gold, gems, honor, reputation, cooldown, timeleft, burning, freezing, battery, repairing, stealth, upgrading, star_power, droplet.
- **Main settings:** as `wf_xtra_varfx_hp`.

### `wf_xtra_var_fx_status`

- **Class:** `WiredExtraVariableFxStatusBar`
- **Behavior:** alias of `wf_xtra_varfx_status`; same runtime.

### `wf_xtra_varfx_boss`

- **Class:** `WiredExtraVariableFxBossBar`
- **Behavior:** a variable fx (code 134): the wide boss health bar (skull icon) for the variable on its tile.
- **Main settings:** as `wf_xtra_varfx_hp`.

### `wf_xtra_var_fx_boss`

- **Class:** `WiredExtraVariableFxBossBar`
- **Behavior:** alias of `wf_xtra_varfx_boss`; same runtime.

### `wf_xtra_varfx_number`

- **Class:** `WiredExtraVariableFxNumberDisplay`
- **Behavior:** a variable fx (code 135): the variable drawn as a plain number, with an optional icon.
- **Main settings:** the same 16 params as `wf_xtra_varfx_hp`, without a value range. The icon comes from the 4th string field. Category extra: icon alignment `0` left, `1` right, `2` double.

### `wf_xtra_var_fx_number`

- **Class:** `WiredExtraVariableFxNumberDisplay`
- **Behavior:** alias of `wf_xtra_varfx_number`; same runtime.

### `wf_xtra_var_lvlup_system`

- **Class:** `WiredExtraVariableLevelUpSystem`
- **Behavior:** placed on the tile of a variable definition, it treats the variable as experience and exposes read-only derived sub-variables: `current_level`, `current_xp`, `level_progress`, `level_progress_percent`, `total_xp_required`, `xp_remaining`, `is_at_max` and `max_level`.
- **Main settings:** JSON string param `{mode, stepSize, maxLevel, firstLevelXp, increaseFactor, interpolationText, subvariables}`. Mode: `1` linear (level n starts at `(n-1)*stepSize`), `2` exponential (the first step is `firstLevelXp`, and each next step grows by `increaseFactor` %), `3` manual (`level=xp` anchor lines, interpolated between anchors). Defaults: step 100, max level 10 (capped at 10000), first level XP 100, factor 100. The manual text is up to 4096 characters. `subvariables` lists the exposed sub-variable indexes (0-7; default 0 and 1).
- **Notes:** plain non-JSON text is read as manual anchors. Anchors above level 10000 are ignored. It feeds the levelling fx.

### `wf_xtra_array_capture_variable`

- **Class:** `WiredExtraArrayCaptureVariable`
- **Behavior:** before the conditions run, it captures one entry of an array variable into a run-scoped `@array.<alias>` namespace. The alias is the name of the chosen scalar context variable, and that variable receives the matched index (-1 when nothing is found). This exposes `@array.<alias>.found`, `.index`, `<alias>.index` and `<alias>.<field>` to other boxes. `<alias>.<field>` is writable when the array is writable.
- **Main settings:** int params `[arrayType (0 furni / 1 room / 2 user / 3 context), ownerSource, captureMode (0 index / 1 find), findDirection (0 first / 1 last / 2 random), criteriaMode (0 all / 1 any)]`. At least 5 are required. The string param is JSON `{variableItemId, contextVariableItemId, index, criteria, ...}` of up to 32768 characters. Picked furni serve as owners and references.
- **Notes:** find mode needs 1-8 valid criteria on the array's fields. The save fails when the context variable is an array, has no value, or its alias is already used by another capturer on the stack. A duplicate alias at runtime publishes "missing". A failed capture does not stop the stack.

### `wf_xtra_condition_change`

- **Class:** `WiredExtraOrEval`
- **Behavior:** alias of `wf_xtra_or_eval`; same runtime.

### `wf_xtra_one_condition`

- **Class:** `WiredExtraOrEval`
- **Behavior:** alias of `wf_xtra_or_eval`; same runtime.

### `wf_xtra_mov_curve`

- **Class:** `WiredExtraMovementCurve`
- **Behavior:** sets the motion curve of this stack's furni moves. Style `7` is Habbo's jump strength: the furni hops to its tile in an arc (100 is one tile high; a negative value dips). Styles `0`-`6` are our easing curves: linear, ease-in, ease-out, ease-in-out, bounce, elastic, drop. A variable strength is read once per firing, not once per moved furni. The jump also applies to users the stack moves (move user, move/rotate user, user to furni) and to users carried along by a jumping furni. Move-style hints are batched into one packet per style per firing and sent only to clients that announced the move-style feature; avatar jumps only to clients that also announced the trajectory feature. Other clients keep the linear animation.
- **Main settings:** int params `[curve (0-7; default 7 for a new box), intensity (0-100, default 100, easing only), strength (-1000..1000, default 80), strengthFromVariable (0/1), variableTarget, variableUserSource, variableFurniSource]`. The string param is the variable token. Picked furni are the furni the variable is read from when the furni source is the box's own picks.
- **Notes:** when the variable cannot be read, the typed strength is used. Old numeric payloads load as a curve id.

### `wf_xtra_var_time_util`

- **Class:** `WiredExtraTimeUtilities`
- **Behavior:** placed on the tile of a variable definition, it reads the variable as a point in time and exposes read-only derived sub-variables named `<variable>.<part>`. They can be read in conditions, selectors, placeholders, echoes and as change-variable references. Calendar parts use the room's wired timezone: `millisecond_of_second` (always 0, timestamps are whole seconds), `seconds_of_minute`, `minute_of_hour`, `hour_of_day`, `day_of_week` (1 Monday to 7 Sunday), `day_of_month`, `day_of_year`, `week_of_year` (ISO), `month_of_year`, `year`. Advanced parts count whole units since 1970 (UTC): `millisecond`, `second`, `minute`, `hour`, `day`, `week`, `month`.
- **Main settings:** int params `[mask, mode]`. Bit `id` of the mask creates a sub-variable: ids 1-10 are the calendar parts in the order above, ids 20-26 the advanced ones. Other bits are dropped. Mode: `0` the value as unix seconds (negative reads as 0), `1` creation time, `2` last update time; anything else becomes `0`. A new box creates nothing.
- **Notes:** modes 1 and 2 also work on variables without a value; mode 0 needs one. It can share a tile with a level-up or quest box. Values that do not fit an int are clamped, so `millisecond` is capped for any date after late January 1970. A change event fires for a sub-variable when it is created or removed, or, in mode 0, when the value changes it; timestamp changes alone fire none. Sub-variables are only exposed for definitions with an item id below 6,250,000. Old saves (`{timeUnit}` or a plain number) load with nothing selected and keep the unit.

### `wf_xtra_var_web_api`

- **Class:** `WiredExtraVariableWebApi`
- **Behavior:** the Variables Web API add-on (code 128). It holds a read key and a write key for the room's HTTP API (`/api/public/rooms/{roomId}/...`, contract in `docs/wired/web-api.md`, OpenAPI at `GET /api/public/api-docs`). The API reads and writes the room's permanent custom user, furni and global variables through the same code as the `:wired` creator tools, with change origin "Variable Web API".
- **Main settings:** string param `readKey \t writeKey`, int params `[bulkDelete]`. The owner asks for a key with packet 2819 (`int itemId, boolean isReadKey`); the server mints 32 random bytes (base64url, 43 chars), stores it at once and answers with packet 59 to the owner only. On save a key is kept when sent back and cleared when sent empty; anything else is ignored. Bulk delete needs a write key. Saves by anyone but the owner change nothing.
- **Notes:** only the box owner sees the keys; everyone else, staff included, gets empty keys, and keys appear in no other packet (debug packet logging prints no body for them). Keys are compared as SHA-256 hashes and never logged. A key only opens the room its box stands in, and only while that room belongs to the box owner. One generate request per box per 2 s. Picking the box up clears both keys and the permission. Stored keys are bound to the item id, so rows copied by room bundles, templates or the marketplace load without keys. Rows saved by the first design (`variableToken`, `writeEnabled`) load without keys; the owner generates new ones. The box cannot be traded, sold, gifted, recycled, put in a chest or wired trade, or given by gift commands; a user owns at most one (catalog purchases and housekeeping grants that would break this are refused) and a room holds at most one. Room bundles and room templates leave it out.
### `wf_xtra_rotate_to_dir`

- **Class:** `WiredExtraProjectile`
- **Behavior:** a projectile add-on (code 136). It changes how the stack's moves look for the furni it treats as projectiles (the picked furni, or every furni the stack moves when none are picked):
  - turns the projectile to face its direction of travel (directional system plus a rotation offset);
  - bends the flight with the curve strength (sent as the jump style of the move-style hint; a `wf_xtra_mov_curve` in the same stack wins);
  - lets the animation fly past the target (overshoot, N tiles) or always fly the same distance (fixed: N minus the tiles actually moved; negative falls short). The client lands the furni on its real tile when the animation ends;
  - scales the animation time with the distance when asked: the distance is the largest measured axis (x and y when none is ticked, height in tiles); each tile takes the time per tile minus the speed increase for every tile already flown (at least 1 ms), a part tile its share; the flight is kept between 50 ms and 60 s. Without scaling the stack's own animation time is used;
  - turns the shooter (a user source, default the triggering user) to face the flight, once per firing, using the directional system; with bunny hop on, a shooter who has to turn hops on the spot instead (strength 30, 300 ms);
  - records the flight for the `@projectile.*` furni variables (see below).
- **Main settings:** 24 int params, clamped on save. The first 19 are Habbo's editor order: `[rotate (0/1, default 1), directionalSystem (0 eight straight, 1 eight diffuse, 2 four prefer vertical, 3 four prefer horizontal), scaleTimeWithDistance, timePerTileIsVariable, timePerTileMs (1-100000, default 500), timePerTileVarTarget, distanceByX, distanceByY, distanceByHeight, speedIncreaseMs (0-100000), rotationOffset (0-7 eighth turns), internalVarMask (0-127), turnShooter, bunnyHop, distanceMode (0 normal / 1 overshoot / 2 fixed), distanceTilesIsVariable, distanceTiles (-64..64), distanceTilesVarTarget, curveStrength (-1000..1000)]`. Then `[timeVarUserSource, timeVarFurniSource, distanceVarUserSource, distanceVarFurniSource, shooterUserSource]`. Variable targets use the shared numbering (0 user, 1 furni, 2 context, 3 room); furni source `101` reads the variable from the picked projectiles. The string param is `timeToken<TAB>distanceToken`.
- **Variables:** `@projectile.animation.position.x`, `.position.y`, `.position.altitude` (hundredths of a tile), `.is_traveling` (a flag without a value, held only mid-flight), `.tiles_traveled`, `.furni_collisions`, `.user_collisions`. Mask bits 0-6 in that order enable them; a furni never launched holds none. The flight follows the client's animation from the start tile to the target: position and altitude are interpolated, tiles traveled is `floor(progress x (path length - 1))`, and a collision is whatever stood on a path tile (the start tile excluded) when the flight began, counted once the animation reaches it. Each room keeps the last flight per furni (at most 1024, oldest dropped first); a furni leaving the room or the room unloading forgets it.
- **Notes:** boxes saved before these params were applied carry a time per tile of 0 and only 19 params; they load with the default 500 ms and default sources. A variable nobody holds falls back to the typed time, and leaves the flight as long as the move for the distance. Variables are read once per firing. A move with no displacement keeps the rotation from the move effect.

### `wf_xtra_achievement_enabler`

- **Class:** `WiredExtraAchievementEnabler`
- **Behavior:** Habbo's achievement enabler add-on (code 151). Names the achievements the room's `wf_act_progress_achievement` boxes may progress. The names it lists that the hotel allows go to visitors in `WiredEnvironment.enabledAchievements` (with the give-achievement boxes' names), which fills the progress box's dropdown and the room-tools achievements button.
- **Main settings:** string param = names separated by commas, semicolons or white space, at most 2000 characters (a large-payload box); up to 50 distinct names of `[A-Za-z0-9_-]` (1..64), others are dropped. No int params.
- **Notes:** declares only; nothing is granted unless the hotel allows the name too. Saving needs the reward permission. Saving, placing and picking it up resend `WiredEnvironment` to the room.

---

## 8. Variable Definitions

Variable names are 1-40 characters of `A-Z a-z 0-9 _`, with whitespace turned into `_`. A name must be unique among all variable definitions in the room. Definitions can be scalars or arrays: the string param is either the plain name or JSON with array metadata, with up to 8 fields and 128 entries by default (2048 at most).

### `wf_var_quest`

- **Class:** `WiredExtraQuest`
- **Behavior:** placed on the tile of a counter variable, it exposes read-only derived sub-variables: `progress`, `target`, `is_complete` (progress >= target > 0), `percent` (0-100; 100 when the target is 0) and `remaining`.
- **Main settings:** int param `[targetValue]`, at least 0.
- **Notes:** all sub-variables are always exposed. Completion can be detected through the counter's variable-changed event.

### `wf_var_quest_chain`

- **Class:** `WiredExtraQuestChain`
- **Behavior:** placed on the tile of a step counter, it exposes read-only `current_step` (capped at the total), `total_steps`, `is_complete` and `percent`.
- **Main settings:** int param `[targetValue]` (the total number of steps), at least 0.
- **Notes:** steps advance manually, for example by a change-variable effect when a sub-quest completes. It does not read member quests.

### `wf_var_daily_task`

- **Class:** `WiredExtraDailyTask`
- **Behavior:** a daily task (code 2008). Placed on the tile of a user counter with a value, it makes the counter daily: a value last written before the current day began in the room's wired timezone reads as 0, so every holder starts each day at 0. It exposes the same read-only sub-variables as `wf_var_quest`: `progress`, `target`, `is_complete`, `percent` and `remaining`.
- **Main settings:** int param `[targetValue]`, at least 0. The string param is the task name, up to 100 characters. When the string holds a tab, only the text after the last tab is kept.
- **Notes:** nothing runs at midnight. The reset is applied when a value is read or written, using the value's stored update time, so it also holds for users who were away and across restarts, and no variable-changed event fires at the day change. Shared (`wf_var_reference`) readers in other rooms see the stored value. Only user counters are affected, and array variables are not.

### `wf_var_user`

- **Class:** `WiredExtraUserVariable`
- **Behavior:** defines a custom user variable that effects can give to users, change and remove.
- **Main settings:** int params `[hasValue, availability]`. Availability: `0` while the user is in the room (default), `10` permanent, `11` shared (permanent and readable from the owner's other rooms through `wf_var_reference`). The string param is the name or the array JSON.
- **Notes:** arrays always have a value. Permanent values are stored per user. Assignment is done by `wf_act_give_var`.

### `wf_var_furni`

- **Class:** `WiredExtraFurniVariable`
- **Behavior:** defines a custom furni variable.
- **Main settings:** int params `[hasValue, availability]`. Availability: `1` while the room is active (default), `10` permanent. The string param is the name or the array JSON.
- **Notes:** non-permanent values are lost when the room unloads. Permanent rows for furni that are gone are removed on load.

### `wf_var_room`

- **Class:** `WiredExtraRoomVariable`
- **Behavior:** defines a single room-wide variable, which always has a value.
- **Main settings:** int param `[availability]`: `1` while the room is active (default), `10` permanent, `11` shared. The string param is the name or the array JSON.
- **Notes:** there is no "has value" option.

### `wf_var_context`

- **Class:** `WiredExtraContextVariable`
- **Behavior:** defines a variable that lives only for one wired execution context. Signals sent from the stack carry it along. Text input capture, array capture and effects write it.
- **Main settings:** int param `[hasValue]`. The string param is the name or the array JSON.
- **Notes:** never persisted. Arrays always have a value.

### `wf_var_reference`

- **Class:** `WiredExtraVariableReference`
- **Behavior:** makes a shared (availability 11) user or room variable from another room of the same owner usable in this room under a local name.
- **Main settings:** JSON string param `{variableName, sourceRoomId, sourceVariableItemId, sourceTargetType (0 user / 3 room), readOnly}`. The name is validated like any definition name. The save fails when the source is not a shared definition in one of the owner's other rooms.
- **Notes:** read-only by default. Array references are writable only when `readOnly` is off.

### `wf_var_echo`

- **Class:** `WiredExtraVariableEcho`
- **Behavior:** a read-only mirror of another variable under its own name, so that the value can be shown as text. The source can be an internal variable or a derived sub-variable (a custom name containing a `.`, for example from a level-up or quest box).
- **Main settings:** JSON string param `{variableName, sourceTargetType (0 user / 1 furni / 3 room), sourceVariableToken, sourceVariableItemId}`. An empty name is derived from the source name.
- **Notes:** the save fails when the source has no value, is not an allowed echo source, would create an echo loop, or when no `wf_xtra_var_text_connector` is attached to the echo.

---

## 9. Special Wired Items

These furni are not trigger, condition, effect or add-on boxes. Each `wf_conf_*` controller is a switch: it is on while its state is `1`. Rights holders (or renters of the space) toggle it, and so does the wired toggle-state effect. One active controller applies to the whole room.

### `wf_conf_invis_control`

- **Class:** `InteractionConfInvisControl`
- **Behavior:** while on, every floor furni whose base custom params contain the `is_invisible` token is reported to clients as hidden.
- **Main settings:** none; only the on/off state.

### `wf_conf_area_hide`

- **Class:** `InteractionAreaHideControl`
- **Behavior:** while on, the client hides furni in a configured area. The server stores the values and sends them to everyone in the room.
- **Main settings:** custom values `state`, `rootX`, `rootY`, `width`, `length` (all whole numbers, at least 0), and the flags `invisibility`, `wallItems`, `invert`.
- **Notes:** several controllers can be active at once, each with its own area.

### `wf_conf_handitem_block`

- **Class:** `InteractionHanditemBlockControl`
- **Behavior:** while on, users cannot give hand items to other users or to pets.
- **Main settings:** none; on/off.

### `wf_conf_queue_speed`

- **Class:** `InteractionQueueSpeedControl`
- **Behavior:** overrides the room's roller speed. Clicking cycles four modes (states 0/3/6/9 map to roller speed 0-3), with an interval of `(speed + 1) * 500` ms. The furni animates at that speed.
- **Main settings:** none; only the mode state.
- **Notes:** the first controller found applies whether or not it is "on". A click from a user without rights is passed on as a normal use.

### `wf_conf_wired_disable`

- **Class:** `InteractionWiredDisableControl`
- **Behavior:** while on, all wired events in the room are ignored.
- **Main settings:** none; on/off.

### `wf_conf_hidewired`

- **Class:** `InteractionHideWiredControl`
- **Behavior:** while on, wired boxes are left out of the floor items sent to users entering the room, like the room's hide-wired setting.
- **Main settings:** none; on/off.
- **Notes:** only affects what is sent on room entry.

### `wf_conf_dice_disable`

- **Class:** `InteractionDiceDisableControl`
- **Behavior:** while on, dice cannot be rolled by clicking or by the wired roll-dice effect.
- **Main settings:** none; on/off.

### `wf_conf_doorkick_disable`

- **Class:** `InteractionDoorkickDisableControl`
- **Behavior:** while on, users who walk onto the door tile are not kicked out of the room.
- **Main settings:** none; on/off.

### `wf_storage_coins1`

- **Class:** `InteractionWiredChestCurrency`
- **Behavior:** a currency chest. Players open it to deposit and withdraw (anyone when "everyone can open" is set, otherwise rights holders). The wired give-from-chest effects, chest conditions, contracts and the chest scanner use its pool only after the owner has enabled wired access.
- **Main settings:** the wired config dialog (code 100) sends `[currencyType, amount]`: `-1` credits, `>= 0` a points type. Saving it replaces the whole chest storage with that single pool.
- **Notes:** the sprite shows closed, or open with fullness 1-4 according to the "appearance" setting. Default capacity is 5000, raised in steps of 5000 up to 1,000,000.

### `wf_storage_coins2`

- **Class:** `InteractionWiredChestCurrency`
- **Behavior:** alias of `wf_storage_coins1`; same runtime.

### `wf_storage_furni1`

- **Class:** `InteractionWiredChestFurni`
- **Behavior:** a furni chest. Players open it to see and withdraw stored furni. The wired give-furni-from-chest effect, chest conditions, contracts and the chest scanner use it only after the owner has enabled wired access.
- **Main settings:** the wired config dialog (code 101) sends `[baseItemId, quantity]`. Saving replaces the storage with that one pool, and only when the base item exists and the quantity is above 0.
- **Notes:** the lid is open or closed according to the appearance setting (by default, open while someone looks inside). Up to 4 distinct stored items can be shown above it when preview is on.

### `wf_storage_furni2`

- **Class:** `InteractionWiredChestFurni`
- **Behavior:** alias of `wf_storage_furni1`; same runtime.

### `wf_storage_furni_starter`

- **Class:** `InteractionWiredChestFurni`
- **Behavior:** alias of `wf_storage_furni1`; same runtime.
- **Notes:** a starter chest (base name ending in `_starter`) is capped at 500 and cannot be upgraded.

### `wf_contract_payment`

- **Class:** `InteractionWiredContractPayment`
- **Behavior:** a payment contract (dialog code 110): the terms of what the player pays. The same storage, format and runtime as `wf_xtra_custom_contract`, executed by `wf_act_init_transaction`.
- **Main settings:** as `wf_xtra_custom_contract`.
- **Notes:** negotiated as a payment.

### `wf_contract_reward`

- **Class:** `InteractionWiredContractReward`
- **Behavior:** a reward contract (dialog code 111): what the player receives, sourced from the linked chest or minted when no chest is linked. The same format and runtime as `wf_xtra_custom_contract`.
- **Main settings:** as `wf_xtra_custom_contract`.
- **Notes:** a reward with no give side runs instantly, without a negotiation window.

### `wf_contract_trade`

- **Class:** `InteractionWiredContractTrade`
- **Behavior:** a trade contract (dialog code 112): the player pays one side and receives the other, both applied together or not at all. The same format and runtime as `wf_xtra_custom_contract`.
- **Main settings:** as `wf_xtra_custom_contract`.
- **Notes:** negotiated as a trade.

### `wf_blob`

- **Class:** `WiredBlob`
- **Behavior:** a game pickup. When an active blob (state `0`) is walked on by a game player, it adds points to that player and turns used (state `1`). Banzai counters are refreshed.
- **Main settings:** none in the editor. The base item custom params are `points,resetsWithGame`.
- **Notes:** placed and ended games leave it used. With `resetsWithGame = true` it becomes active at game start. Otherwise only wired toggle-state flips it, and only while a game is running or paused. When the custom params are missing or malformed, it gives 0 points.

### `wf_highscore`

- **Class:** `InteractionWiredHighscore`
- **Behavior:** a scoreboard that shows the top rows (at most 50) recorded when a game ends in the room.
- **Main settings:** none in the editor. The score type (`perteam`, `mostwin`, `classic`, `fastesttime`, `longesttime`) and the reset period (`*1` all time, `*2` daily, `*3` weekly, `*4` monthly) come from the base item name, for example `highscore_classic*1`.
- **Notes:** rights holders or wired toggle its on/off display state. A board in a room without a game timer or up-counter is flagged as unreachable, because nothing can end a game there. The old `wired.highscores.displaycount` setting is not read.

---

## 10. Practical Design Notes

### 10.1 If exact order matters

Use `wf_xtra_exec_in_order`. Do not rely on "it seems to run in that order" once the stack becomes more complex.

### 10.2 If the stack performs movement

Think about:

- movement validation
- movement physics extras
- carry-users extra
- animation/no-animation extras and `wf_xtra_mov_curve` (easing styles or a jump arc)
- snapshot restore effects

Movement stacks are where most subtle runtime interactions appear. One effect moving many furni sends one move-style hint per style, not one per furni.

The move-style hint (header 5110) is `int count, int[count] ids, int style, int intensity, int overshootTiles, int kind`. The last two were appended later: a client that stops after the intensity still reads it correctly. `overshootTiles` (-64..64) lets the animation fly past its target; `kind` 0 names floor furni and 1 names room units (avatar jumps). Kind 1 is sent only to clients announcing wired feature bit 4 (trajectory) next to bit 2 (move style). The hint for a user moved by an effect is sent right before that user's movement packet; hints for furni and carried users go out with the firing's batch.

### 10.3 If the stack uses variables

Remember:

- variable names must be room-unique
- the target type matters (user, furni, room/global, context)
- room/global variables are definition-driven
- context variables only live for one execution and the stacks it calls or signals
- textual rendering requires the text connector
- a value-or-variable setting reads the first holder in its source that has the variable

### 10.4 If the stack uses repeaters/timers

Remember:

- repeaters are synchronized on the global tick loop
- delay units are half-seconds
- counters, repeaters, and timer-style triggers often need explicit reset/control logic
- time and date conditions use the room's wired timezone

### 10.5 If the stack is heavy

Check:

- selection size
- number of delayed effects
- recursion or self-trigger chains
- call-stack and signal fan-out (both are capped, see 2.8)
- random/unseen subsets
- execution limits
- room diagnostics and the room log in `:wired`

---

## 11. Quick Alias / Shared Runtime Notes

Keys registered on the same class share one runtime. The class decides the behavior; the key only picks the default dialog and, for some classes, a preset (for example the teleport team colors or the move/rotate variants).

- `InteractionWiredChestCurrency`: `wf_storage_coins1`, `wf_storage_coins2`
- `InteractionWiredChestFurni`: `wf_storage_furni1`, `wf_storage_furni2`, `wf_storage_furni_starter`
- `WiredConditionFurniHaveHabbo`: `wf_cnd_furnis_hv_avtrs`, `wf_cnd_tgr_furni_hv_avtrs`, `wf_cnd_furnis_hv_avtrs_custom`, `wf_cnd_x_habbos_on_furni`
- `WiredConditionFurniTypeMatch`: `wf_cnd_stuff_is`, `wf_cnd_trgr_stuff_matches`
- `WiredConditionGroupMember`: `wf_cnd_actor_in_group`, `wf_cnd_habbo_in_group`
- `WiredConditionHabboHasEffect`: `wf_cnd_wearing_effect`, `wf_cnd_wears_effect`
- `WiredConditionHabboHasHandItem`: `wf_cnd_has_handitem`, `wf_cnd_wears_handitem`
- `WiredConditionMatchStatePosition`: `wf_cnd_match_snapshot`, `wf_cnd_match_snapshot_new`, `wf_cnd_furni_state_pattern`, `wf_cnd_is_state`, `wf_cnd_trg_state_is`
- `WiredConditionNoBattleBanzaiRunning`: `wf_cnd_not_battlebanzai`, `wf_cnd_not_battlebz`
- `WiredConditionNotFurniHaveHabbo`: `wf_cnd_not_hv_avtrs`, `wf_cnd_not_tgr_furni_hv_avtrs`
- `WiredConditionNotHabboHasEffect`: `wf_cnd_not_wearing_fx`, `wf_cnd_not_wears_effect`
- `WiredConditionNotHabboHasHandItem`: `wf_cnd_not_has_handitem`, `wf_cnd_not_wears_handitem`
- `WiredConditionNotInGroup`: `wf_cnd_not_in_group`, `wf_cnd_not_habbo_in_group`
- `WiredConditionNotMatchStatePosition`: `wf_cnd_not_match_snap`, `wf_cnd_not_match_snap_new`
- `WiredConditionNotTriggererMatch`: `wf_cnd_not_triggerer_match`, `wf_cnd_not_trg_by_user`
- `WiredConditionNotUserPerformsAction`: `wf_cnd_not_user_performs_action`, `wf_cnd_not_bot_is_dancing`, `wf_cnd_not_habbo_is_dancing`
- `WiredConditionTeamMember`: `wf_cnd_actor_in_team`, `wf_cnd_atleast_one_user_in_team`
- `WiredConditionTriggererMatch`: `wf_cnd_triggerer_match`, `wf_cnd_trg_by_user`
- `WiredConditionUserPerformsAction`: `wf_cnd_user_performs_action`, `wf_cnd_bot_is_dancing`, `wf_cnd_habbo_is_dancing`
- `WiredEffectAddTag`: `wf_act_add_tag`, `wf_act_add_tag_perm`
- `WiredEffectAlert`: `wf_act_alert`, `wf_act_alert_habbo`
- `WiredEffectBotDance`: `wf_act_bot_start_dance`, `wf_act_bot_stop_dance`
- `WiredEffectBotGiveHandItem`: `wf_act_bot_give_handitem`, `wf_act_bot_give_handitem_or_effect`
- `WiredEffectBotTalkToHabbo`: `wf_act_bot_talk_to_avatar`, `wf_act_bot_talk_to_avatar_custom`
- `WiredEffectBotTalk`: `wf_act_bot_talk`, `wf_act_bot_talk_custom`
- `WiredEffectBotTeleport`: `wf_act_bot_teleport`, `wf_act_teleport_bots_to_furni`
- `WiredEffectChangeFurniDirection`: `wf_act_move_to_dir`, `wf_act_allign_furni_stack`
- `WiredEffectForwardUserToRoom`: `wf_act_forward_user_to_room`, `wf_act_teleport_to_room`, `wf_act_tele_room`
- `WiredEffectFreeze`: `wf_act_freeze`, `wf_act_freeze_habbo`
- `WiredEffectFurniToFurni`: `wf_act_furni_to_furni`, `wf_act_move_furni_to_furni`
- `WiredEffectFurniToUser`: `wf_act_furni_to_user`, `wf_act_tp_furni_to_habbo`
- `WiredEffectGiveBadge`: `wf_act_give_badge`, `wf_act_give_userbadge`
- `WiredEffectGiveEffect`: `wf_act_give_effect`, `wf_act_give_enable`
- `WiredEffectGiveScore`: `wf_act_give_score`, `wf_act_give_score_room`, `wf_act_give_score_pp`, `wf_act_give_score_custom`
- `WiredEffectMakeUserSay`: `wf_act_make_user_say`, `wf_act_send_bubble`
- `WiredEffectMatchFurni`: `wf_act_match_to_sshot`, `wf_act_set_state`, `wf_act_set_trg_state`, `wf_act_open_gates`, `wf_act_match_to_sshot_new`, `wf_act_match_to_sshot_height`, `wf_act_match_to_sshot_height_instant`, `wf_act_plus_match_furni_state`
- `WiredEffectMoveFurniAway`: `wf_act_flee`, `wf_act_dont_chase`, `wf_act_dont_chase_top`
- `WiredEffectMoveFurniTo`: `wf_act_move_furni_to`, `wf_act_cnd_move_furni`
- `WiredEffectMoveRotateFurni`: `wf_act_move_rotate`, `wf_act_move_furni_from_stack`, `wf_act_move_rotate_no_under`, `wf_act_cnd_move_rotate`, `wf_act_move_rotate_collide`, `wf_act_move_rotate_diagonal`
- `WiredEffectMoveRotateUser`: `wf_act_move_rotate_user`, `wf_act_rotate_habbo`
- `WiredEffectNegativeTriggerStacks`: `wf_act_neg_call_stack`, `wf_act_neg_call_stacks`
- `WiredEffectSetAltitude`: `wf_act_set_altitude`, `wf_act_lower_furni`, `wf_act_raise_furni`
- `WiredEffectTeleport`: `wf_act_teleport_to`, `wf_act_teleport_all`, `wf_act_teleport_red`, `wf_act_teleport_green`, `wf_act_teleport_blue`, `wf_act_teleport_yellow`
- `WiredEffectToggleFurni`: `wf_act_toggle_state`, `wf_act_close_dice`, `wf_act_close_gates`, `wf_act_color_furni`, `wf_act_double_click`, `wf_act_cnd_toggle_state`, `wf_act_toggle_state_down`, `wf_act_toggle_state_trg`
- `WiredEffectTriggerStacks`: `wf_act_call_stacks`, `wf_act_execute_for_users`, `wf_act_call_stacks_custom`, `wf_act_execute_stack_custom`
- `WiredEffectUnfreeze`: `wf_act_unfreeze`, `wf_act_unfreeze_habbo`
- `WiredEffectWhisper`: `wf_act_show_message`, `wf_act_show_message_room`
- `WiredExtraFilterUser`: `wf_xtra_filter_user`, `wf_xtra_filter_users`
- `WiredExtraOrEval`: `wf_xtra_or_eval`, `wf_xtra_condition_change`, `wf_xtra_one_condition`
- `WiredExtraVariableFxBossBar`: `wf_xtra_varfx_boss`, `wf_xtra_var_fx_boss`
- `WiredExtraVariableFxHealthPoints`: `wf_xtra_varfx_hp`, `wf_xtra_var_fx_health`
- `WiredExtraVariableFxLevellingProgress`: `wf_xtra_varfx_levelling`, `wf_xtra_var_fx_level`
- `WiredExtraVariableFxNumberDisplay`: `wf_xtra_varfx_number`, `wf_xtra_var_fx_number`
- `WiredExtraVariableFxProgressBar`: `wf_xtra_varfx_prog`, `wf_xtra_var_fx_progress`
- `WiredExtraVariableFxStatusBar`: `wf_xtra_varfx_status`, `wf_xtra_var_fx_status`
- `WiredTriggerCollision`: `wf_trg_collision`, `wf_trg_cnd_collision`, `wf_trg_other_collides_user`, `wf_trg_user_collides_bot`, `wf_trg_user_collides_other`
- `WiredTriggerHabboClicksFurni`: `wf_trg_click_furni`, `wf_trg_double_click_furni`
- `WiredTriggerHabboClicksUser`: `wf_trg_click_user`, `wf_trg_click_bot`
- `WiredTriggerHabboLeavesRoom`: `wf_trg_leave_room`, `wf_trg_user_exits_room`
- `WiredTriggerHabboSaysKeyword`: `wf_trg_says_something`, `wf_trg_exact_keyword`, `wf_trg_says_command`, `wf_trg_habbo_says_command`
- `WiredTriggerHabboUnidles`: `wf_trg_unidles`, `wf_trg_anti_afk`

Other shared runtime notes:

- several positive/negative conditions are simple logical counterparts; each entry in section 6 says how its empty-set case behaves
- `wf_act_give_var`, `wf_act_remove_var`, `wf_act_change_var_val`, the variable selectors, and the variable conditions all work on the custom variable system defined by `wf_var_*`
