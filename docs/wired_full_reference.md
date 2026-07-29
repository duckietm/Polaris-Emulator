# Wired Full Reference

## 1. Scope

This document is a code-based reference for the current wired runtime in `Arcturus-Morningstar-Extended`.

It covers:

- general wired engine rules
- tick and delay rules
- protection and monitor rules
- custom variable rules
- every registered wired trigger, effect, selector, condition, extra, and variable definition

Primary runtime sources used for this reference:

- `Emulator/src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredManager.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/core/WiredEngine.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/tick/WiredTickService.java`
- `Emulator/src/main/java/com/eu/habbo/habbohotel/wired/WiredHandler.java`

This file is meant to describe the runtime behavior and configuration surface, not the Nitro UI layout in detail. For `:wired` monitor and inspection tooling, also see `Arcturus-Morningstar-Extended/docs/wired_tools_reference.md`.

---

## 2. Wired Engine, Tick, and General Runtime Rules

### 2.1 Main runtime architecture

The modern wired runtime is centered around these components:

- `WiredManager`
  - initializes the wired runtime
  - loads config
  - owns the centralized engine and stack index
  - exposes high-level trigger methods such as user click, walk on, say, signal, game events, and so on
- `WiredEngine`
  - receives `WiredEvent` objects
  - finds candidate stacks through the room stack index
  - evaluates selectors, conditions, extras, and effects
  - enforces abuse protection, delayed queue limits, recursion limits, and diagnostics
- `RoomWiredStackIndex`
  - caches stack membership so the engine can quickly find candidate stacks for a given event type
- `WiredTickService`
  - runs a single global tick loop
  - keeps repeaters and other tickables synchronized across rooms
- `WiredHandler`
  - legacy compatibility path that still exists in the codebase
  - still useful to understand older stack execution logic and some compatibility behavior

### 2.2 Current execution model

At a high level, the engine processes a stack like this:

1. receive an event
2. find candidate stacks for the event type
3. check whether the trigger matches
4. build a `WiredContext`
5. run selectors first, so the target set is available
6. apply selection-filter extras if present
7. evaluate conditions
8. apply stack-level gates such as execution limit
9. activate the trigger and extras
10. execute or schedule effects

Important consequences of this model:

- selectors run before conditions in the new engine
- conditions can inspect the selected targets or even the whole stack
- effects may run immediately or with delay
- extras can modify selection, condition evaluation, effect ordering, and effect subset choice

### 2.3 Tick rules

The centralized tick service is defined in `WiredTickService`.

Current core rules:

- default global tick interval: `50ms`
- hard allowed range: `10ms` to `500ms`
- repeaters and other tickables use a shared global tick counter
- tickables are registered per room
- when a room is unloaded, tickables should be unregistered

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

This rule is used both by the legacy path and by the new engine.

### 2.5 Stack ordering rules

There are several separate notions of order:

- **stack candidate order**
  - candidate stacks are found through the index for the specific event type
- **item ordering inside one tile stack**
  - `WiredExecutionOrderUtil` sorts by:
    - `z`
    - then `item id`
- **effect subset modifiers**
  - `wf_xtra_random` can choose only part of the effects
  - `wf_xtra_unseen` can rotate through effects without repeats
- **ordered execution**
  - `wf_xtra_exec_in_order` is the explicit “run in stable stack order” modifier

Practical takeaway:

- if there is no order modifier, execution may depend on the collection/order produced by the runtime path
- if exact order matters, `wf_xtra_exec_in_order` is the intended box to use

### 2.6 Selection rules

Selectors build or refine the `WiredTargets` inside `WiredContext`.

In practice:

- target users and target furni are built before conditions are checked
- later effects consume those selected targets
- some selectors are pure “build a selection” tools
- some extras then trim, sort, or invert that selection

### 2.7 Condition rules

Conditions are evaluated after selectors.

General behavior:

- if there are no conditions, the stack can continue directly to effects
- if there are conditions, all configured condition logic must pass according to the current evaluation mode
- `wf_xtra_or_eval` changes how the condition results are aggregated

The runtime supports both:

- ordinary condition matching
- grouped OR semantics through condition operators and the OR-eval extra

### 2.8 Protection rules

The wired runtime has multiple safety layers:

- maximum steps per stack
- recursion depth protection
- per-room event rate limiting
- room temporary wired ban after abuse
- delayed queue cap
- execution budget / usage cap per room window

Main defaults from runtime/config:

- `wired.engine.maxStepsPerStack = 100`
- `wired.abuse.max.recursion.depth = 10`
- `wired.abuse.max.events.per.window = 100`
- `wired.abuse.rate.limit.window.ms = 10000`
- `wired.abuse.ban.duration.ms = 600000`
- `wired.monitor.usage.window.ms = 1000`
- `wired.monitor.usage.limit = 1000`
- `wired.monitor.delayed.events.limit = 100`

### 2.9 Monitor and diagnostics rules

The new engine tracks room diagnostics through `WiredRoomDiagnostics`.

This is where `:wired` monitor gets values such as:

- usage in current window
- delayed events count
- average execution time
- peak execution time
- recursion state
- heavy room status
- overload windows

Heavy/overload decisions are based on rolling windows, not on a single event.

### 2.10 Legacy compatibility notes

The project still contains `WiredHandler`.

Important practical notes:

- `WiredManager` is the intended modern entrypoint
- `wired.engine.enabled` and `wired.engine.exclusive` are treated as compatibility-only flags by `WiredManager`
- `WiredHandler` still exists and is useful for compatibility and for understanding legacy behavior

So when documenting stacks, it is best to think in terms of:

- modern runtime: `WiredManager` + `WiredEngine`
- legacy compatibility surface: `WiredHandler`

### 2.11 Custom variable rules

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

- `wf_var_user`
  - room-scoped while the user is in the room
  - or permanent
- `wf_var_furni`
  - room-active while the room is active/loaded
  - or permanent
- `wf_var_room`
  - room-active
  - or permanent

Timestamp rules:

- user variables: creation/update are tied to the assignment on that user
- furni variables: creation/update are tied to the assignment on that furni
- room variables: practically meaningful timestamp is mainly the last update time

Array rules:

- user, furni, room, and context definition boxes can define a scalar, simple array, or record array
- arrays use `list` or sparse `slots` indexing and support up to eight stable-ID record fields
- permanent arrays use optimistic versioning and delta persistence; context arrays remain execution-scoped
- captured entries expose read-only `alias.field` context values plus `@array.alias.found` and
  `@array.alias.index` metadata throughout ordinary variable reference pickers
- the configured entry, populated-cell, and owner limits are enforced on both save and execution paths

### 2.12 Useful global config keys

| Key | Meaning |
|---|---|
| `wired.engine.enabled` | Compatibility-only legacy flag |
| `wired.engine.exclusive` | Compatibility-only legacy flag |
| `wired.engine.maxStepsPerStack` | Loop/step protection limit |
| `wired.engine.debug` | Verbose engine logging |
| `wired.custom.enabled` | Legacy custom wired compatibility behavior |
| `hotel.wired.furni.selection.count` | Max furni selection size stored by wired boxes |
| `hotel.wired.max_delay` | Max accepted delay value |
| `hotel.wired.message.max_length` | Max wired/bot text size |
| `hotel.wired.arrays.max_entries` | Maximum logical capacity accepted for one array definition |
| `hotel.wired.arrays.max_populated_cells_per_owner` | Maximum populated field cells for one owner and array |
| `hotel.wired.arrays.max_owners_per_execution` | Maximum owners processed by one array box execution |
| `hotel.wired.arrays.metrics_log_interval_ms` | Minimum interval between aggregate array persistence metric log entries |
| `wired.effect.teleport.delay` | Teleport effect delay |
| `wired.tick.interval.ms` | Global tick loop interval |
| `wired.tick.debug` | Tick debug logging |
| `wired.tick.thread.priority` | Tick thread priority |
| `wired.abuse.max.recursion.depth` | Recursion protection |
| `wired.abuse.max.events.per.window` | Event spam protection |
| `wired.abuse.rate.limit.window.ms` | Abuse window size |
| `wired.abuse.ban.duration.ms` | Temporary room wired-ban duration |
| `wired.monitor.usage.window.ms` | Usage monitor window size |
| `wired.monitor.usage.limit` | Execution budget per window |
| `wired.monitor.delayed.events.limit` | Delayed queue ceiling |
| `wired.monitor.overload.average.ms` | Overload average threshold |
| `wired.monitor.overload.peak.ms` | Overload peak threshold |
| `wired.monitor.heavy.usage.percent` | Heavy-room usage threshold |
| `wired.highscores.displaycount` | Wired highscore rows shown to users |

---

## 3. Triggers

### `wf_trg_walks_on_furni`

- **Class:** `WiredTriggerHabboWalkOnFurni`
- **Behavior:** fires when a user walks onto the selected furni/tile stack.
- **Main settings:** selected furni, standard trigger cooldown.
- **Notes:** commonly used as the first event in movement or pressure-style stacks.

### `wf_trg_walks_off_furni`

- **Class:** `WiredTriggerHabboWalkOffFurni`
- **Behavior:** fires when a user leaves the selected furni/tile stack.
- **Main settings:** selected furni, standard trigger cooldown.
- **Notes:** useful for exit logic, cleanup logic, and delayed “leave area” patterns.

### `wf_trg_click_furni`

- **Class:** `WiredTriggerHabboClicksFurni`
- **Behavior:** fires when a user clicks a furni.
- **Main settings:** selected furni.
- **Notes:** click-based stacks often combine this with selectors or trigger-user conditions.

### `wf_trg_click_tile`

- **Class:** `WiredTriggerHabboClicksTile`
- **Behavior:** fires when a user clicks a tile.
- **Main settings:** selected click-tile furni / trigger area depending on setup.
- **Notes:** often used for invisible tile-style interactions.

### `wf_trg_click_user`

- **Class:** `WiredTriggerHabboClicksUser`
- **Behavior:** fires when one avatar clicks another avatar.
- **Main settings:** runtime flags such as menu blocking and rotation behavior.
- **Notes:** the event carries both the clicking user and the clicked user.

### `wf_trg_user_performs_action`

- **Class:** `WiredTriggerHabboPerformsAction`
- **Behavior:** fires when a user performs a configured avatar action.
- **Main settings:** action id and action parameter.
- **Notes:** pairs naturally with the matching positive/negative action conditions.

### `wf_trg_enter_room`

- **Class:** `WiredTriggerHabboEntersRoom`
- **Behavior:** fires when a user enters the room.
- **Main settings:** none beyond default cooldown.
- **Notes:** common for welcome logic, spawn logic, variable assignment, and snapshot restore.

### `wf_trg_leave_room`

- **Class:** `WiredTriggerHabboLeavesRoom`
- **Behavior:** fires when a user leaves the room.
- **Main settings:** none beyond default cooldown.
- **Notes:** common for cleanup and last-known-state stacks.

### `wf_trg_says_something`

- **Class:** `WiredTriggerHabboSaysKeyword`
- **Behavior:** fires when a user says the configured text/keyword.
- **Main settings:** text/keyword, message hiding mode.
- **Notes:** can optionally suppress the visible chat output when configured to hide the message.

### `wf_trg_clock_counter`

- **Class:** `WiredTriggerClockCounter`
- **Behavior:** fires when a selected counter reaches its configured match point.
- **Main settings:** target counter(s), counter matching behavior.
- **Notes:** often combined with `wf_act_control_clock` and `wf_act_adjust_clock`.

### `wf_trg_periodically`

- **Class:** `WiredTriggerRepeater`
- **Behavior:** fires on a repeating interval.
- **Main settings:** repeat interval.
- **Notes:** synchronized through the global tick service.

### `wf_trg_period_short`

- **Class:** `WiredTriggerRepeaterShort`
- **Behavior:** faster repeating trigger with short cadence.
- **Main settings:** short repeater timing.
- **Notes:** aligned to the global `50ms` tick service.

### `wf_trg_period_long`

- **Class:** `WiredTriggerRepeaterLong`
- **Behavior:** repeating trigger with longer cadence.
- **Main settings:** long repeater timing.
- **Notes:** intended for lower-frequency repeating behavior.

### `wf_trg_state_changed`

- **Class:** `WiredTriggerFurniStateToggled`
- **Behavior:** fires when the state of the selected furni changes.
- **Main settings:** selected furni.
- **Notes:** runtime is shared with `wf_trg_stuff_state`.

### `wf_trg_stuff_state`

- **Class:** `WiredTriggerFurniStateToggled`
- **Behavior:** same runtime behavior as `wf_trg_state_changed`.
- **Main settings:** selected furni.
- **Notes:** kept as a second key/alias for compatibility/content mapping.

### `wf_trg_at_given_time`

- **Class:** `WiredTriggerAtSetTime`
- **Behavior:** fires once after the configured time target is reached.
- **Main settings:** time value.
- **Notes:** behaves like a one-shot timer rather than a repeater.

### `wf_trg_at_time_long`

- **Class:** `WiredTriggerAtTimeLong`
- **Behavior:** long-duration variant of the set-time trigger.
- **Main settings:** time value.
- **Notes:** used when the short version is not sufficient for the desired range.

### `wf_trg_collision`

- **Class:** `WiredTriggerCollision`
- **Behavior:** fires when the configured collision case is detected.
- **Main settings:** collision participants / collision mode.
- **Notes:** can easily produce loops when combined with chase/flee unless protections are configured.

### `wf_trg_game_starts`

- **Class:** `WiredTriggerGameStarts`
- **Behavior:** fires when the room game starts.
- **Main settings:** none beyond default cooldown.
- **Notes:** useful for score resets, timers, and spawn setup.

### `wf_trg_game_ends`

- **Class:** `WiredTriggerGameEnds`
- **Behavior:** fires when the room game ends.
- **Main settings:** none beyond default cooldown.
- **Notes:** useful for rewards, cleanup, and reset logic.

### `wf_trg_bot_reached_stf`

- **Class:** `WiredTriggerBotReachedFurni`
- **Behavior:** fires when a bot reaches the selected furni.
- **Main settings:** bot path target furni.
- **Notes:** typically paired with bot movement effects.

### `wf_trg_bot_reached_avtr`

- **Class:** `WiredTriggerBotReachedHabbo`
- **Behavior:** fires when a bot reaches an avatar.
- **Main settings:** target avatar/source mode.
- **Notes:** useful for escort, interaction, or story-style flows.

### `wf_trg_score_achieved`

- **Class:** `WiredTriggerScoreAchieved`
- **Behavior:** fires when the configured score threshold is reached.
- **Main settings:** score threshold.
- **Notes:** usually tied to game or team score flows.

### `wf_trg_game_team_win`

- **Class:** `WiredTriggerTeamWins`
- **Behavior:** fires when a team wins the current room game.
- **Main settings:** target team.
- **Notes:** can be used for reward or celebration logic.

### `wf_trg_game_team_lose`

- **Class:** `WiredTriggerTeamLoses`
- **Behavior:** fires when a team loses the current room game.
- **Main settings:** target team.
- **Notes:** often paired with reset or consolation logic.

### `wf_trg_recv_signal`

- **Class:** `WiredTriggerReceiveSignal`
- **Behavior:** fires when a matching signal is received from `wf_act_send_signal`.
- **Main settings:** selected antenna(s), signal/channel matching.
- **Notes:** can receive user/furni payload carried by the signal event.

---

## 4. Effects

### `wf_act_toggle_state`

- **Class:** `WiredEffectToggleFurni`
- **Behavior:** toggles the state of the selected furni.
- **Main settings:** selected furni, effect delay.
- **Notes:** one of the most common state-manipulation effects.

### `wf_act_reset_timers`

- **Class:** `WiredEffectResetTimers`
- **Behavior:** resets compatible timer/repeater-style boxes.
- **Main settings:** selected timer/counter/repeater items.
- **Notes:** used to restart timing flows cleanly.

### `wf_act_match_to_sshot`

- **Class:** `WiredEffectMatchFurni`
- **Behavior:** restores furni to a saved snapshot of state/position/rotation settings.
- **Main settings:** selected furni, snapshot match mode/settings.
- **Notes:** usually paired with move/rotate or state-change effects.

### `wf_act_move_rotate`

- **Class:** `WiredEffectMoveRotateFurni`
- **Behavior:** moves and/or rotates furni according to the configured pattern.
- **Main settings:** selected furni, movement direction, rotation behavior, effect delay.
- **Notes:** obeys move physics extras when present.

### `wf_act_give_score`

- **Class:** `WiredEffectGiveScore`
- **Behavior:** gives score to the target user/player.
- **Main settings:** score amount.
- **Notes:** room/game scoring effect.

### `wf_act_show_message`

- **Class:** `WiredEffectWhisper`
- **Behavior:** sends the configured message text.
- **Main settings:** message text, effect delay.
- **Notes:** text length is limited by wired message config.

### `wf_act_teleport_to`

- **Class:** `WiredEffectTeleport`
- **Behavior:** teleports the target user to the configured destination.
- **Main settings:** target furni/tile, effect delay.
- **Notes:** also respects `wired.effect.teleport.delay`.

### `wf_act_join_team`

- **Class:** `WiredEffectJoinTeam`
- **Behavior:** moves the target user into the selected team.
- **Main settings:** team id/color.
- **Notes:** game-specific utility effect.

### `wf_act_leave_team`

- **Class:** `WiredEffectLeaveTeam`
- **Behavior:** removes the target user from their team.
- **Main settings:** effect delay.
- **Notes:** typically used in game cleanup.

### `wf_act_chase`

- **Class:** `WiredEffectMoveFurniTowards`
- **Behavior:** moves furni toward the configured target.
- **Main settings:** selected furni, target source, movement distance/direction rules.
- **Notes:** can interact strongly with collision and movement validation.

### `wf_act_flee`

- **Class:** `WiredEffectMoveFurniAway`
- **Behavior:** moves furni away from the configured target.
- **Main settings:** selected furni, target source, movement rules.
- **Notes:** often paired with collision or proximity triggers.

### `wf_act_move_to_dir`

- **Class:** `WiredEffectChangeFurniDirection`
- **Behavior:** changes furni direction/rotation.
- **Main settings:** selected furni, new direction or direction mode.
- **Notes:** pure direction-change effect without full movement pathing.

### `wf_act_give_score_tm`

- **Class:** `WiredEffectGiveScoreToTeam`
- **Behavior:** gives score directly to a team.
- **Main settings:** team id and score amount.
- **Notes:** separate from single-user score.

### `wf_act_toggle_to_rnd`

- **Class:** `WiredEffectToggleRandom`
- **Behavior:** toggles a random compatible furni among the selected set.
- **Main settings:** selected furni.
- **Notes:** randomness is per execution.

### `wf_act_move_furni_to`

- **Class:** `WiredEffectMoveFurniTo`
- **Behavior:** moves furni to a configured target position.
- **Main settings:** selected furni, destination tile/furni, effect delay.
- **Notes:** works with movement physics and animation extras.

### `wf_act_give_reward`

- **Class:** `WiredEffectGiveReward`
- **Behavior:** gives a configured reward.
- **Main settings:** reward type, reward content, amount, inventory/catalog parameters depending on reward mode.
- **Notes:** may generate inventory items, badges, or related reward outputs depending on configuration.

### `wf_act_call_stacks`

- **Class:** `WiredEffectTriggerStacks`
- **Behavior:** triggers other stacks indirectly.
- **Main settings:** selected furni/tile sources.
- **Notes:** recursion protection is important here.

### `wf_act_kick_user`

- **Class:** `WiredEffectKickHabbo`
- **Behavior:** kicks the target user from the room.
- **Main settings:** target source, effect delay.
- **Notes:** administrative/gameplay removal effect.

### `wf_act_mute_triggerer`

- **Class:** `WiredEffectMuteHabbo`
- **Behavior:** mutes the target user.
- **Main settings:** mute duration / target source depending on layout.
- **Notes:** often used in moderation or mini-game penalties.

### `wf_act_bot_teleport`

- **Class:** `WiredEffectBotTeleport`
- **Behavior:** teleports the selected bot.
- **Main settings:** bot source and destination.
- **Notes:** bot-only effect.

### `wf_act_bot_move`

- **Class:** `WiredEffectBotWalkToFurni`
- **Behavior:** makes a bot walk toward the selected furni.
- **Main settings:** bot source, target furni.
- **Notes:** commonly paired with bot reached triggers.

### `wf_act_bot_talk`

- **Class:** `WiredEffectBotTalk`
- **Behavior:** makes a bot say configured text.
- **Main settings:** bot source, message text.
- **Notes:** subject to wired/bot text size limits.

### `wf_act_bot_give_handitem`

- **Class:** `WiredEffectBotGiveHandItem`
- **Behavior:** gives a handitem to a bot.
- **Main settings:** bot source, handitem id.
- **Notes:** bot cosmetic / state effect.

### `wf_act_bot_follow_avatar`

- **Class:** `WiredEffectBotFollowHabbo`
- **Behavior:** makes a bot follow an avatar.
- **Main settings:** bot source, avatar source.
- **Notes:** useful for escort or scripted behaviors.

### `wf_act_bot_clothes`

- **Class:** `WiredEffectBotClothes`
- **Behavior:** changes a bot’s clothes/look.
- **Main settings:** bot source, look string.
- **Notes:** bot appearance effect.

### `wf_act_bot_talk_to_avatar`

- **Class:** `WiredEffectBotTalkToHabbo`
- **Behavior:** makes a bot talk toward an avatar/target.
- **Main settings:** bot source, avatar target, text.
- **Notes:** dialogue-oriented bot effect.

### `wf_act_give_respect`

- **Class:** `WiredEffectGiveRespect`
- **Behavior:** gives respect to the target user.
- **Main settings:** respect amount / target source.
- **Notes:** social reward effect.

### `wf_act_alert`

- **Class:** `WiredEffectAlert`
- **Behavior:** sends an alert window/message.
- **Main settings:** alert text.
- **Notes:** distinct from whisper-style chat output.

### `wf_act_give_handitem`

- **Class:** `WiredEffectGiveHandItem`
- **Behavior:** gives a handitem to the target user.
- **Main settings:** handitem id.
- **Notes:** user state/cosmetic effect.

### `wf_act_give_effect`

- **Class:** `WiredEffectGiveEffect`
- **Behavior:** gives an avatar effect to the target user.
- **Main settings:** effect id.
- **Notes:** visual avatar effect.

### `wf_act_freeze`

- **Class:** `WiredEffectFreeze`
- **Behavior:** freezes the selected user targets.
- **Main settings:** target source, effect delay.
- **Notes:** mainly game/control utility.

### `wf_act_unfreeze`

- **Class:** `WiredEffectUnfreeze`
- **Behavior:** unfreezes the selected user targets.
- **Main settings:** target source, effect delay.
- **Notes:** counterpart to `wf_act_freeze`.

### `wf_act_furni_to_user`

- **Class:** `WiredEffectFurniToUser`
- **Behavior:** moves furni toward/on a user target.
- **Main settings:** furni source, user source, effect delay.
- **Notes:** movement batching/physics extras may change the visible result.

### `wf_act_user_to_furni`

- **Class:** `WiredEffectUserToFurni`
- **Behavior:** moves a user toward a furni target.
- **Main settings:** user source, furni target.
- **Notes:** a user-targeted movement effect.

### `wf_act_furni_to_furni`

- **Class:** `WiredEffectFurniToFurni`
- **Behavior:** moves one furni set onto another furni set.
- **Main settings:** primary furni source, secondary furni source.
- **Notes:** supports double-selection source flow.

### `wf_act_set_altitude`

- **Class:** `WiredEffectSetAltitude`
- **Behavior:** sets furni altitude.
- **Main settings:** selected furni, altitude value or altitude mode.
- **Notes:** used in advanced movement / stacking setups.

### `wf_act_rel_mov`

- **Class:** `WiredEffectRelativeMove`
- **Behavior:** moves furni using relative X/Y offsets.
- **Main settings:** selected furni, X offset, Y offset.
- **Notes:** easier to reason about than absolute destination when building movement loops.

### `wf_act_control_clock`

- **Class:** `WiredEffectControlClock`
- **Behavior:** controls counter boxes.
- **Main settings:** selected counter(s), action mode such as start/stop/reset/pause/resume.
- **Notes:** works directly with counter-based trigger/condition flows.

### `wf_act_adjust_clock`

- **Class:** `WiredEffectAdjustClock`
- **Behavior:** adjusts a counter’s current value.
- **Main settings:** selected counter(s), operation mode, amount.
- **Notes:** intended for dynamic counter manipulation.

### `wf_act_move_rotate_user`

- **Class:** `WiredEffectMoveRotateUser`
- **Behavior:** moves and/or rotates user targets.
- **Main settings:** user source, movement mode, direction/rotation settings.
- **Notes:** user-side analogue of furni move/rotate logic.

### `wf_act_send_signal`

- **Class:** `WiredEffectSendSignal`
- **Behavior:** sends a signal through antenna-based wiring.
- **Main settings:** selected antenna furni, signal payload/source options.
- **Notes:** can carry user/furni payload to `wf_trg_recv_signal`.

### `wf_act_give_var`

- **Class:** `WiredEffectGiveVariable`
- **Behavior:** assigns a custom variable to a compatible target.
- **Main settings:** variable definition, target type/source, overwrite flag, initial value if the variable has value.
- **Notes:** works with `wf_var_user` and `wf_var_furni`; room/global variables are definition-driven and do not need this assigner.

### `wf_act_remove_var`

- **Class:** `WiredEffectRemoveVariable`
- **Behavior:** removes a custom variable assignment from the selected target.
- **Main settings:** variable definition, target type/source.
- **Notes:** counterpart to `wf_act_give_var`.

### `wf_act_change_var_val`

- **Class:** `WiredEffectChangeVariableValue`
- **Behavior:** changes the value of a variable by applying an operation.
- **Main settings:** variable selection, operation, reference mode, constant or reference variable, reference source, target source.
- **Supported operations:** assign, add, subtract, multiply, divide, power, modulo, min, max, random, absolute, bitwise AND/OR/XOR/NOT, left shift, right shift.
- **Notes:** one of the most flexible variable effects; textual rendering is separate and handled by extras.

---

### `wf_act_modify_array`

- **Class:** `WiredEffectModifyArray`
- **Behavior:** modifies a selected array with append, insert, replace, remove, remove-first/last,
  swap, move, clear, or unbiased shuffle operations.
- **Main settings:** array definition, owner source, operation, index references, and per-field values.
- **Notes:** input references may be constants, scalar variables, captured fields, or addressed array cells;
  failed mutations are atomic and do not partially persist.

## 5. Selectors

### General selector notes

Selectors typically do one or both of these:

- build a new target set
- filter/transform an existing target set

When the UI exposes classic selector options, those usually include:

- filter the existing selection
- invert the result

### `wf_slc_furni_area`

- **Class:** `WiredEffectFurniArea`
- **Behavior:** selects furni in a configured area.
- **Main settings:** area size/position.
- **Notes:** foundational room-space selector.

### `wf_slc_furni_neighborhood`

- **Class:** `WiredEffectFurniNeighborhood`
- **Behavior:** selects furni in a local neighborhood around the source point.
- **Main settings:** neighborhood/radius.
- **Notes:** useful for adjacency-based logic.

### `wf_slc_furni_bytype`

- **Class:** `WiredEffectFurniByType`
- **Behavior:** selects furni by base furni type.
- **Main settings:** furni type.
- **Notes:** good for “all chairs”, “all switches”, and similar patterns.

### `wf_slc_furni_altitude`

- **Class:** `WiredEffectFurniAltitude`
- **Behavior:** selects furni by altitude relation/value.
- **Main settings:** compare mode and altitude target.
- **Notes:** useful in stacked build logic.

### `wf_slc_furni_onfurni`

- **Class:** `WiredEffectFurniOnFurni`
- **Behavior:** selects furni that are on top of other furni.
- **Main settings:** base furni selection.
- **Notes:** stack-inspection selector.

### `wf_slc_furni_picks`

- **Class:** `WiredEffectFurniPicks`
- **Behavior:** selects a hand-picked list of furni.
- **Main settings:** selected furni list.
- **Notes:** capped by `hotel.wired.furni.selection.count`.

### `wf_slc_furni_signal`

- **Class:** `WiredEffectFurniSignal`
- **Behavior:** selects furni carried by a signal event.
- **Main settings:** signal source mode.
- **Notes:** meaningful only in signal-driven stacks.

### `wf_slc_users_area`

- **Class:** `WiredEffectUsersArea`
- **Behavior:** selects users in a configured area.
- **Main settings:** area size/position.
- **Notes:** area equivalent of the furni selector.

### `wf_slc_users_neighborhood`

- **Class:** `WiredEffectUsersNeighborhood`
- **Behavior:** selects users in a nearby neighborhood.
- **Main settings:** neighborhood/radius.
- **Notes:** good for local interaction logic.

### `wf_slc_users_signal`

- **Class:** `WiredEffectUsersSignal`
- **Behavior:** selects users carried by a signal event.
- **Main settings:** signal source mode.
- **Notes:** signal-only context.

### `wf_slc_users_bytype`

- **Class:** `WiredEffectUsersByType`
- **Behavior:** selects users by runtime category.
- **Main settings:** user type such as habbo, bot, pet.
- **Notes:** useful for mixed rooms with bots and pets.

### `wf_slc_users_team`

- **Class:** `WiredEffectUsersTeam`
- **Behavior:** selects users by team membership.
- **Main settings:** team id/color.
- **Notes:** game-centric selector.

### `wf_slc_users_byaction`

- **Class:** `WiredEffectUsersByAction`
- **Behavior:** selects users by current action/state.
- **Main settings:** action type / action parameter.
- **Notes:** complements the action trigger/conditions.

### `wf_slc_users_byname`

- **Class:** `WiredEffectUsersByName`
- **Behavior:** selects users whose names are listed in the text area.
- **Main settings:** multiline list of usernames.
- **Notes:** direct name-driven selector.

### `wf_slc_users_handitem`

- **Class:** `WiredEffectUsersHandItem`
- **Behavior:** selects users holding a specific handitem.
- **Main settings:** handitem id.
- **Notes:** useful for role/item possession flows.

### `wf_slc_users_onfurni`

- **Class:** `WiredEffectUsersOnFurni`
- **Behavior:** selects users standing on selected furni.
- **Main settings:** base furni selection.
- **Notes:** common in pressure/tile gameplay.

### `wf_slc_users_group`

- **Class:** `WiredEffectUsersGroup`
- **Behavior:** selects users by group relationship in the room.
- **Main settings:** group relation/mode.
- **Notes:** useful for rights/group-room logic.

### `wf_slc_furni_with_var`

- **Class:** `WiredEffectFurniWithVariable`
- **Behavior:** selects furni that hold a chosen custom variable.
- **Main settings:** variable selection, optional value filter, comparison operator, constant or variable reference, reference source, selector options.
- **Notes:** if value filtering is disabled, it behaves as a presence-only selector.

### `wf_slc_users_with_var`

- **Class:** `WiredEffectUsersWithVariable`
- **Behavior:** selects users that hold a chosen custom variable.
- **Main settings:** variable selection, optional value filter, comparison operator, constant or variable reference, reference source, selector options.
- **Notes:** user-side analogue of the furni variable selector.

---

## 6. Conditions

### General condition notes

Conditions can be thought of as gates for the stack.

Common patterns:

- positive/negative counterpart pairs
- threshold checks
- “match the current selection”
- variable-based checks
- time/date checks

### `wf_cnd_has_furni_on`

- **Class:** `WiredConditionFurniHaveFurni`
- **Behavior:** true if the configured furni have other furni on top.
- **Main settings:** target furni selection.

### `wf_cnd_furnis_hv_avtrs`

- **Class:** `WiredConditionFurniHaveHabbo`
- **Behavior:** true if the configured furni currently have avatars on top.
- **Main settings:** target furni selection.

### `wf_cnd_stuff_is`

- **Class:** `WiredConditionFurniTypeMatch`
- **Behavior:** true if the furni match the configured type.
- **Main settings:** furni type.

### `wf_cnd_actor_in_group`

- **Class:** `WiredConditionGroupMember`
- **Behavior:** true if the acting user is in the required group relation.
- **Main settings:** group relation.

### `wf_cnd_user_count_in`

- **Class:** `WiredConditionHabboCount`
- **Behavior:** true if room user count satisfies the configured threshold.
- **Main settings:** comparison and count value.

### `wf_cnd_wearing_effect`

- **Class:** `WiredConditionHabboHasEffect`
- **Behavior:** true if the target user is wearing the configured effect.
- **Main settings:** effect id.

### `wf_cnd_wearing_badge`

- **Class:** `WiredConditionHabboWearsBadge`
- **Behavior:** true if the target user wears the configured badge.
- **Main settings:** badge code.

### `wf_cnd_time_less_than`

- **Class:** `WiredConditionLessTimeElapsed`
- **Behavior:** true if less than the configured time has elapsed.
- **Main settings:** duration.

### `wf_cnd_match_snapshot`

- **Class:** `WiredConditionMatchStatePosition`
- **Behavior:** true if the current furni state/position matches the stored snapshot.
- **Main settings:** selected furni, snapshot fields to compare.

### `wf_cnd_time_more_than`

- **Class:** `WiredConditionMoreTimeElapsed`
- **Behavior:** true if more than the configured time has elapsed.
- **Main settings:** duration.

### `wf_cnd_not_furni_on`

- **Class:** `WiredConditionNotFurniHaveFurni`
- **Behavior:** logical negation of `wf_cnd_has_furni_on`.
- **Main settings:** target furni selection.

### `wf_cnd_not_hv_avtrs`

- **Class:** `WiredConditionNotFurniHaveHabbo`
- **Behavior:** logical negation of `wf_cnd_furnis_hv_avtrs`.
- **Main settings:** target furni selection.

### `wf_cnd_not_stuff_is`

- **Class:** `WiredConditionNotFurniTypeMatch`
- **Behavior:** logical negation of `wf_cnd_stuff_is`.
- **Main settings:** furni type.

### `wf_cnd_not_user_count`

- **Class:** `WiredConditionNotHabboCount`
- **Behavior:** logical negation of the user-count match.
- **Main settings:** comparison and count value.

### `wf_cnd_not_wearing_fx`

- **Class:** `WiredConditionNotHabboHasEffect`
- **Behavior:** true if the user is not wearing the configured effect.
- **Main settings:** effect id.

### `wf_cnd_not_wearing_b`

- **Class:** `WiredConditionNotHabboWearsBadge`
- **Behavior:** true if the user is not wearing the configured badge.
- **Main settings:** badge code.

### `wf_cnd_not_in_group`

- **Class:** `WiredConditionNotInGroup`
- **Behavior:** true if the user is not in the configured group relation.
- **Main settings:** group relation.

### `wf_cnd_not_in_team`

- **Class:** `WiredConditionNotInTeam`
- **Behavior:** true if the user is not in the configured team.
- **Main settings:** team id/color.

### `wf_cnd_not_match_snap`

- **Class:** `WiredConditionNotMatchStatePosition`
- **Behavior:** logical negation of snapshot match.
- **Main settings:** selected furni, snapshot fields to compare.

### `wf_cnd_not_trggrer_on`

- **Class:** `WiredConditionNotTriggerOnFurni`
- **Behavior:** true if the triggerer is not on the selected furni.
- **Main settings:** selected furni.

### `wf_cnd_actor_in_team`

- **Class:** `WiredConditionTeamMember`
- **Behavior:** true if the actor belongs to the required team.
- **Main settings:** team id/color.

### `wf_cnd_trggrer_on_frn`

- **Class:** `WiredConditionTriggerOnFurni`
- **Behavior:** true if the triggerer is on the selected furni.
- **Main settings:** selected furni.

### `wf_cnd_has_handitem`

- **Class:** `WiredConditionHabboHasHandItem`
- **Behavior:** true if the user currently holds the configured handitem.
- **Main settings:** handitem id.

### `wf_cnd_not_has_handitem`

- **Class:** `WiredConditionNotHabboHasHandItem`
- **Behavior:** logical negation of the handitem condition.
- **Main settings:** handitem id.

### `wf_cnd_date_rng_active`

- **Class:** `WiredConditionDateRangeActive`
- **Behavior:** true if current server time is between the configured absolute date/time bounds.
- **Main settings:** start timestamp, end timestamp.

### `wf_cnd_valid_moves`

- **Class:** `WiredConditionMovementValidation`
- **Behavior:** simulates movement-related effects in the current stack and fails if a movement would be invalid.
- **Main settings:** no major user-facing setting besides stack composition.
- **Notes:** especially useful before move/rotate stacks.

### `wf_cnd_counter_time_matches`

- **Class:** `WiredConditionCounterTimeMatches`
- **Behavior:** true if the selected counter(s) match the configured time value.
- **Main settings:** counter selection, compare mode, target value, quantifier.

### `wf_cnd_match_time`

- **Class:** `WiredConditionMatchTime`
- **Behavior:** true if server local time matches the configured clock rule.
- **Main settings:** hour/minute/second or related time fields.

### `wf_cnd_match_date`

- **Class:** `WiredConditionMatchDate`
- **Behavior:** true if server local date matches the configured date rule.
- **Main settings:** weekday/day/month/year.

### `wf_cnd_actor_dir`

- **Class:** `WiredConditionActorDir`
- **Behavior:** true if the actor faces the configured direction.
- **Main settings:** direction.

### `wf_cnd_slc_quantity`

- **Class:** `WiredConditionSelectionQuantity`
- **Behavior:** true if the current selection size matches the configured threshold.
- **Main settings:** compare mode and amount.

### `wf_cnd_user_performs_action`

- **Class:** `WiredConditionUserPerformsAction`
- **Behavior:** true if the tracked user action matches.
- **Main settings:** action id / action parameter.

### `wf_cnd_not_user_performs_action`

- **Class:** `WiredConditionNotUserPerformsAction`
- **Behavior:** logical negation of the user action condition.
- **Main settings:** action id / action parameter.

### `wf_cnd_has_altitude`

- **Class:** `WiredConditionHasAltitude`
- **Behavior:** true if the selected furni satisfy the altitude comparison.
- **Main settings:** compare mode and altitude value.

### `wf_cnd_triggerer_match`

- **Class:** `WiredConditionTriggererMatch`
- **Behavior:** true if the triggerer matches the required target/source rule.
- **Main settings:** target source/match mode.

### `wf_cnd_not_triggerer_match`

- **Class:** `WiredConditionNotTriggererMatch`
- **Behavior:** logical negation of triggerer match.
- **Main settings:** target source/match mode.

### `wf_cnd_team_has_score`

- **Class:** `WiredConditionTeamHasScore`
- **Behavior:** true if the selected team score satisfies the configured comparison.
- **Main settings:** team id, comparison mode, score threshold.

### `wf_cnd_team_has_rank`

- **Class:** `WiredConditionTeamHasRank`
- **Behavior:** true if the selected team currently has the configured rank/placement.
- **Main settings:** team id, rank target.

### `wf_cnd_has_var`

- **Class:** `WiredConditionHasVariable`
- **Behavior:** true if the target entity holds the chosen variable.
- **Main settings:** variable selection, quantifier (`all` / `any`), variable source target.
- **Notes:** current layout/runtime is centered on user and furni variables; context exists as future placeholder.

### `wf_cnd_neg_has_var`

- **Class:** `WiredConditionNotHasVariable`
- **Behavior:** logical negation of `wf_cnd_has_var`.
- **Main settings:** variable selection, quantifier, source target.

### `wf_cnd_var_val_match`

- **Class:** `WiredConditionVariableValueMatch`
- **Behavior:** compares a variable value against a constant or another variable.
- **Main settings:** variable selection, compare type (`>`, `≥`, `=`, `≤`, `<`, `≠`), reference mode, reference variable/source, quantifier.
- **Notes:** room/global variables are supported here; context remains partial.

### `wf_cnd_var_age_match`

- **Class:** `WiredConditionVariableAgeMatch`
- **Behavior:** compares variable age against a duration.
- **Main settings:** variable selection, compare field (`creation` or `update` time), compare type (`lower than` / `higher than`), duration value + unit, quantifier, source.
- **Notes:** room/global variables are mostly meaningful for update time.

### `wf_cnd_check_array`

- **Class:** `WiredConditionCheckArray`
- **Behavior:** checks array state, one addressed entry, or the number of entries matching up to eight criteria.
- **Main settings:** owner source, all/any owner quantifier, empty/full/length/available state checks,
  first-class comparisons, criteria mode, and optional index.
- **Notes:** references accept signed 64-bit constants, scalar variables, captured fields, and array cells.

---

## 7. Extras

### `wf_xtra_random`

- **Class:** `WiredExtraRandom`
- **Behavior:** executes only a random subset of effects instead of all effects.
- **Main settings:** number of effects to choose, optional recent-history protection.
- **Notes:** effect subset changes at each execution.

### `wf_xtra_unseen`

- **Class:** `WiredExtraUnseen`
- **Behavior:** rotates through effects without repeating one until the full cycle is exhausted.
- **Main settings:** hidden runtime state / no-repeat cycle.
- **Notes:** useful when true round-robin behavior is preferred over randomness.

### `wf_blob`

- **Class:** `WiredBlob`
- **Behavior:** special wired/game helper item.
- **Main settings:** blob-specific gameplay/runtime state.
- **Notes:** not a normal logic extra in the same sense as the others, but it is registered in the wired extra family.

### `wf_xtra_or_eval`

- **Class:** `WiredExtraOrEval`
- **Behavior:** changes how condition results are aggregated.
- **Main settings:** evaluation mode and compare value.
- **Notes:** lets stacks use modes beyond plain “all conditions must pass”.

### `wf_xtra_filter_furni`

- **Class:** `WiredExtraFilterFurni`
- **Behavior:** trims the current furni selection to a limited quantity.
- **Main settings:** quantity.
- **Notes:** selection-filter extra, not a normal selector.

### `wf_xtra_filter_user`

- **Class:** `WiredExtraFilterUser`
- **Behavior:** trims the current user selection to a limited quantity.
- **Main settings:** quantity.
- **Notes:** same runtime family as `wf_xtra_filter_users`.

### `wf_xtra_filter_users`

- **Class:** `WiredExtraFilterUser`
- **Behavior:** same runtime behavior as `wf_xtra_filter_user`.
- **Main settings:** quantity.
- **Notes:** alias key kept for content compatibility.

### `wf_xtra_filter_furni_by_var`

- **Class:** `WiredExtraFilterFurniByVariable`
- **Behavior:** sorts furni by variable metric and keeps only the top N.
- **Main settings:** variable selection, sort mode, quantity mode, constant quantity or variable reference, reference source.
- **Supported sort modes:** highest value, lowest value, oldest creation, latest creation, oldest update, latest update.

### `wf_xtra_filter_users_by_var`

- **Class:** `WiredExtraFilterUsersByVariable`
- **Behavior:** sorts users by variable metric and keeps only the top N.
- **Main settings:** variable selection, sort mode, quantity mode, constant quantity or variable reference, reference source.
- **Supported sort modes:** highest value, lowest value, oldest creation, latest creation, oldest update, latest update.

### `wf_xtra_mov_carry_users`

- **Class:** `WiredExtraMoveCarryUsers`
- **Behavior:** carries users together with moved furni.
- **Main settings:** carry mode.
- **Notes:** affects how movement results are applied when furni move.

### `wf_xtra_mov_no_animation`

- **Class:** `WiredExtraMoveNoAnimation`
- **Behavior:** suppresses movement animation.
- **Main settings:** none besides presence in stack.
- **Notes:** intended for instant or hidden movement behavior.

### `wf_xtra_anim_time`

- **Class:** `WiredExtraAnimationTime`
- **Behavior:** overrides movement animation time.
- **Main settings:** animation duration.
- **Notes:** influences visual pacing, not core selection logic.

### `wf_xtra_mov_physics`

- **Class:** `WiredExtraMovePhysics`
- **Behavior:** changes the physics rules applied during movement.
- **Main settings:** physics flags such as collision/pass-through/stack behavior depending on layout.
- **Notes:** important for advanced furni movement setups.

### `wf_xtra_exec_in_order`

- **Class:** `WiredExtraExecuteInOrder`
- **Behavior:** forces ordered effect execution.
- **Main settings:** none besides presence in stack.
- **Notes:** the explicit “do not rely on arbitrary order” extra.

### `wf_xtra_execution_limit`

- **Class:** `WiredExtraExecutionLimit`
- **Behavior:** allows the stack to execute only a configured number of times per window.
- **Main settings:** max executions, time window.
- **Notes:** stack-level throttle.

### `wf_xtra_text_output_username`

- **Class:** `WiredExtraTextOutputUsername`
- **Behavior:** exposes one or more usernames as a text placeholder for other wired text.
- **Main settings:** placeholder name, placeholder type (single/multiple), delimiter, user source.
- **Notes:** works like a text injector for later wired text output.

### `wf_xtra_text_output_furni_name`

- **Class:** `WiredExtraTextOutputFurniName`
- **Behavior:** exposes furni names as a text placeholder.
- **Main settings:** placeholder name, placeholder type (single/multiple), delimiter, furni source.
- **Notes:** furni-name counterpart to username output.

### `wf_xtra_text_output_variable`

- **Class:** `WiredExtraTextOutputVariable`
- **Behavior:** exposes a variable value as a text placeholder.
- **Main settings:** placeholder name, variable selection, display type (`numeric` / `textual`), placeholder type (`single` / `multiple`), delimiter, dynamic variable source.
- **Notes:** textual display works only when the selected variable is connected through `wf_xtra_var_text_connector`.

### `wf_xtra_var_text_connector`

- **Class:** `WiredExtraVariableTextConnector`
- **Behavior:** maps numeric values to text labels for a variable.
- **Main settings:** text area mapping in the form `0=text`, `1=text`, and so on.
- **Notes:** must live in the same stack context as the corresponding `wf_var_*` definition to be meaningful.

### `wf_xtra_array_capture_variable`

- **Class:** `WiredExtraArrayCaptureVariable`
- **Behavior:** captures an entry by index or by first/last/random criteria match for the current execution.
- **Main settings:** array definition, owner source, context alias variable, capture mode, direction, and criteria.
- **Notes:** publishes read-only field projections as `alias.field`; metadata is available through
  `@array.alias.found` and `@array.alias.index`. Duplicate aliases in one stack are rejected.

---

## 8. Variable Definitions

### `wf_var_user`

- **Class:** `WiredExtraUserVariable`
- **Behavior:** defines a custom variable that can be assigned to users.
- **Main settings:** variable name, `has value` flag, availability (`while user is in room` / `permanent`).
- **Notes:** assignment is done through `wf_act_give_var`; timestamps belong to the assignment on the individual user.

### `wf_var_furni`

- **Class:** `WiredExtraFurniVariable`
- **Behavior:** defines a custom variable that can be assigned to furni.
- **Main settings:** variable name, `has value` flag, availability (`while room is active` / `permanent`).
- **Notes:** non-permanent assignments are cleaned when the room is unloaded/unregistered from room tickables.

### `wf_var_room`

- **Class:** `WiredExtraRoomVariable`
- **Behavior:** defines a room/global variable.
- **Main settings:** variable name, availability (`while room is active` / `permanent`).
- **Notes:** always has a value; there is no separate “has value” checkbox for room variables.

### `wf_var_context`

- **Class:** `WiredExtraContextVariable`
- **Behavior:** defines an execution-scoped scalar or array shared by the current Wired context.
- **Main settings:** variable name, value flag for scalars, or array format/mode/capacity/fields.
- **Notes:** context arrays never persist to the room database and can trigger Variable Changed events.

---

## 9. Special Wired Items

These are part of the wired ecosystem, even if they are not regular trigger/effect/selector/condition/extra boxes.

### `wf_highscore`

- **Class:** `InteractionWiredHighscore`
- **Behavior:** wired highscore furniture that stores and displays ranked score data.
- **Main settings:** score type, clear/reset policy, display behavior depending on furniture configuration.
- **Notes:** governed also by `wired.highscores.displaycount`.

---

## 10. Practical Design Notes

### 10.1 If exact order matters

Use:

- `wf_xtra_exec_in_order`

Do not rely on “it seems to run in that order” when the stack becomes more complex.

### 10.2 If the stack performs movement

Prefer to think about:

- movement validation
- movement physics extras
- carry-users extra
- animation/no-animation extras
- snapshot restore effects

Movement stacks are where most subtle runtime interactions appear.

### 10.3 If the stack uses variables

Remember:

- variable name must be room-unique
- target type matters
- room/global variables are definition-driven
- textual rendering requires the text connector
- `context` is not yet fully implemented everywhere

### 10.4 If the stack uses repeaters/timers

Remember:

- repeaters are synchronized on the global tick loop
- delay units are half-seconds
- counters, repeaters, and timer-style triggers often need explicit reset/control logic

### 10.5 If the stack is heavy

Check:

- selection size
- number of delayed effects
- recursion or self-trigger chains
- random/unseen subsets
- execution limits
- room diagnostics in `:wired`

---

## 11. Quick Alias / Shared Runtime Notes

- `wf_trg_state_changed` and `wf_trg_stuff_state` share the same runtime.
- `wf_xtra_filter_user` and `wf_xtra_filter_users` share the same runtime.
- Several positive/negative conditions are simple logical counterparts.
- `wf_act_give_var`, `wf_act_remove_var`, `wf_act_change_var_val`, variable selectors, and variable conditions all operate on top of the same custom variable system defined by `wf_var_*`.
