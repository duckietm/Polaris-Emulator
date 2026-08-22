# Wired Creator Tools Implementation Summary

## 1. Purpose

This document summarizes the `:wired` work completed in this development cycle.

It is intended as a project-facing summary of:

- what was added
- where it lives
- what is already working
- what is still intentionally left as future work

---

## 2. Main goals completed

The current `:wired` implementation now provides:

1. a dedicated Nitro UI window
2. monitor and inspection tooling
3. room/user/furni/global variable views
4. inline editing for selected values
5. live wired diagnostics from the server
6. error/warning history with details
7. server-side diagnostics configuration through DB settings

---

## 3. Octane-UI work

Main file:

- `Octane-UI/src/components/wired-tools/WiredCreatorToolsView.tsx`

### 3.1 UI window

The `:wired` tool now has these main tabs:

- `Monitor`
- `Variables`
- `Inspection`
- `Chests`
- `Settings`

Current active work is mainly in:

- `Monitor`
- `Inspection`

`Chests` and `Settings` are currently placeholder/future-facing areas.

### 3.2 Inspection

Implemented:

- element type switcher (`furni`, `user`, `global`)
- preview area
- variable table
- `Keep selected`
- inline editing

#### Furni

Added support for:

- detailed furni variables
- live preview
- wall/floor-specific handling
- teleport metadata
- inline edits for state/position/rotation/altitude/wall offset

#### User

Added support for:

- user/bot/pet identity
- rights / owner / group admin flags
- mute / trading / frozen flags
- team / sign / dance / idle / hand item / effect display
- room entry method and teleport entry id
- inline edits for position and direction

#### Global

Added support for:

- room counts
- wired timer
- team scores and sizes
- room/group ids
- server/client timezone
- current server time breakdown

### 3.3 Monitor

Implemented:

- live stats table
- log summary list
- full log history
- info/documentation popup
- error information popup

The auxiliary monitor windows now use proper Nitro card windows, so they are:

- draggable
- resizable
- closed through the normal Nitro close button

---

## 4. Octane-Renderer work

Renderer-side work was focused on making Nitro receive enough metadata for the new UI.

Main areas:

- new wired monitor packet parsing
- room/session metadata extensions
- furni metadata extensions
- user metadata extensions

### 4.1 Monitor data

The renderer now parses and exposes:

- usage budget values
- delayed queue values
- execution timing values
- heavy/overload thresholds
- current logs
- history rows

### 4.2 Room metadata

The renderer/session flow was extended to expose values used by Nitro:

- room furni limit
- room group id
- hotel timezone / hotel time snapshot

### 4.3 Furni metadata

The furni info path now exposes values used by the inspector, including:

- dimensions
- `items_base`-driven flags such as sit/lay/stand/stack
- teleport target metadata

### 4.4 User metadata

The user/unit data path now exposes values used by the inspector, including:

- room entry method
- room entry teleport id
- identity data for user/bot/pet

---

## 5. Emulator work

Main areas:

- wired diagnostics engine
- monitor request/response packet
- room/user/furni metadata support
- configuration migration to `wired_emulator_settings`

### 5.1 Wired diagnostics

Added server-side room diagnostics with:

- usage budget tracking
- delayed event queue tracking
- average/peak execution timing
- overload detection
- heavy-room detection
- recursion protection logging
- killed-room protection logging

### 5.2 Diagnostics logs

Logs now carry:

- type
- severity
- count
- reason
- source label
- source id
- history entries with occurrence timestamps

### 5.3 Trigger/runtime fixes

Important behaviour fixes added during this work:

- empty repeater stacks no longer count as executable work
- monitor usage is consumed later in the execution path, closer to real execution
- timer/repeater behaviour is less noisy in diagnostics

### 5.4 Monitor packet

A dedicated request/response path was added so Nitro can poll live room diagnostics.

### 5.5 Configuration migration

All wired config is being moved out of `emulator_settings` and into:

- `wired_emulator_settings`

This now includes both:

- existing wired runtime settings
- the new `:wired` monitor threshold settings

Migration file:

- `Database Updates/002_move_wired_settings_to_wired_emulator_settings.sql`

---

## 6. What the monitor currently measures

The monitor currently measures:

- execution budget consumed in the current server window
- delayed events currently pending
- average execution time inside the current window
- peak execution time inside the current window
- recursion depth
- remaining killed-room cooldown
- room heavy state
- room furni counts
- renderer custom variable counts on room items

---

## 7. What is configurable now

Current DB-configurable areas include:

- engine enable/debug/exclusive/max-steps
- custom wired compatibility mode
- furni selection limit
- max delay / max text length
- teleport delay
- tick interval/debug/priority
- abuse protection thresholds
- monitor usage/delayed/heavy/overload thresholds

All of these are documented in:

- `docs/wired_tools_reference.md`

---

## 8. Known limitations / future work

Current known limitations:

- `Permanent furni vars` uses a fixed UI denominator (`60`)
- `@wired_timer` is still client-side time since room entry
- `Chests` and `Settings` are not fully implemented yet
- legacy wired configuration keys are still present for database compatibility, but runtime execution now goes only through the new engine

Good future tasks:

- make `Permanent furni vars` fully server-driven
- add export/copy actions for monitor history
- add more detailed filtering/search in history
- document chest/settings once implemented
- optionally remove the compatibility keys entirely once old database defaults are no longer needed

---

## 9. Recommended rollout order

1. run the wired settings migration SQL
2. restart the emulator
3. refresh renderer/client
4. verify monitor values in a real room
5. tune `wired.monitor.*` thresholds using the new DB table
