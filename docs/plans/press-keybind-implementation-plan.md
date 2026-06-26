# press_keybind — implementation plan (cross-repo wired trigger)

**Goal:** a new wired **trigger** that fires when a user presses a configured key while in the room.
Greenfield: no keybind packet/composer exists anywhere today. Touches 3 repos.

## Allocated codes / headers (verified free)
| Thing | Value | Where |
|---|---|---|
| Server `WiredTriggerType` code | **26** | `WiredTriggerType.java` (24=dice_rolled, 25=user_gets_handitem) |
| Client `WiredTriggerLayout` code | **26** | `src/api/wired/WiredTriggerLayoutCode.ts` — **MUST equal** server `getType().code` |
| New outgoing packet header (client→server keypress) | **9311** | renderer `OutgoingHeader.ts` (custom-features block ends at 9310). Alt: 10034–10039 |
| Furni `interaction_type` | `wf_trg_press_keybind` | registered in `ItemManager` + a DB row |

## Architecture
Two independent flows:
1. **The dialog** (configure which key) — reuses the existing wired-save packet, **no new packet**.
2. **The runtime keypress** — a **new** client→server packet that fires the trigger.

```
[config]  WiredTriggerPressKeybindView  --save-->  UpdateTriggerMessageComposer (1520, existing)
                                                     -> server saveData() stores the key
[runtime] in-room keydown (client)  --new packet 9311-->  IncomingPressKeybind (server)
                                                           -> WiredManager.triggerKeybind(room, habbo, key)
                                                           -> WiredTriggerPressKeybind.matches() (key == configured)
```

## Server (Emulator) — I implement, commit to `java25-migration`
Mirrors the dice_rolled/user_gets_handitem additive-trigger pattern (no WiredEngine edit):
1. `WiredTriggerType.java` — add `PRESS_KEYBIND(26)`.
2. `WiredEvent.java` (core) — add `Type.PRESS_KEYBIND`.
3. `WiredEvents.java` (migrate) — add `pressKeybind(room, habbo, key)` factory.
4. `WiredManager.java` — add `triggerKeybind(room, habbo, key)` helper (mirror `triggerDiceRolled`).
5. **New** `WiredTriggerPressKeybind.java` — `InteractionWiredTrigger` subclass (template: `WiredTriggerHabboSaysKeyword` for the string/key field + `WiredTriggerDiceRolled` for the no-furni shape). `saveData` stores the key; `matches(event)` checks `event.key == this.key`; `serializeWiredData` sends the key string + code 26.
6. **New** `IncomingPressKeybindEvent` (messages/incoming) — reads the key, resolves `client.getHabbo()` + `currentRoom`, calls `WiredManager.triggerKeybind`. Register header **9311** in `Incoming.java` + `PacketManager` (auth-required; pattern of `TriggerDiceEvent`/`SoundboardPlayEvent`).
7. `ItemManager.java:~627` — register `wf_trg_press_keybind` → `WiredTriggerPressKeybind`.
8. **DB migration** — insert/point a furni row with `interaction_type='wf_trg_press_keybind'` (the furni must exist in `items_base`; the client renders the dialog only if a furni with code-26 trigger exists).

## Client app (Nitro-V3, your repo → branch `wired-extra-dialogs`)
**Dialog (no new packet):**
- `src/api/wired/WiredTriggerLayoutCode.ts` — add `PRESS_KEYBIND = 26`.
- **New** `src/components/wired/views/triggers/WiredTriggerPressKeybindView.tsx` — copy `WiredTriggerDiceRolledView`; a key-capture input that stores the chosen key; `save()` → `setIntParams/​setStringParam` with the key. Reuses `WiredTriggerBaseView` → save sends `UpdateTriggerMessageComposer` (1520).
- `src/components/wired/views/triggers/WiredTriggerLayoutView.tsx` — import + `case WiredTriggerLayout.PRESS_KEYBIND: return <WiredTriggerPressKeybindView/>`.

**Runtime keypress capture (new in-room widget):**
- **New** `src/components/room/widgets/RoomKeybindView.tsx` — mounts a `document.body` `keydown` listener (copy the pattern at `ChatInputView.tsx:297`), only while a `RoomSession` is active. On the configured key (and only when **no input/chat has focus** — reuse `anotherInputHasFocus()` logic, `ChatInputView.tsx:28-39`), call `SendMessageComposer(new PressKeybindComposer(key))`.
- Mount it in `src/components/room/widgets/RoomWidgetsView.tsx` (lines 161–179).

## Renderer (Nitro_Render_V3) — ONLY the outgoing packet, NO keyboard code
The renderer has **zero** keyboard handling and needs none (capture lives in the React client). Add the composer (template `RedeemItemClothingComposer.ts`):
1. `packages/communication/src/messages/outgoing/OutgoingHeader.ts` — add `PRESS_KEYBIND = 9311`.
2. **New** `…/outgoing/room/PressKeybindComposer.ts` — `implements IMessageComposer`, `getMessageArray()` returns `[key]`.
3. export it from the dir `index.ts`.
4. `packages/communication/src/NitroMessages.ts` — `this._composers.set(OutgoingHeader.PRESS_KEYBIND, PressKeybindComposer)`.
> The app consumes the renderer via vite aliases → `../Nitro_Render_V3/packages/*/src` (`vite.config.mjs:143-156`), so editing renderer **src** is picked up by `vite build` (no dist rebuild needed for the app). Verify `src/nitro-renderer.mock.ts` doesn't shadow it in tests.

## Risks / decisions to make
- **Key vs chat conflict (main one):** `ChatInputView` grabs *every* keystroke into the chat box. The keybind handler must bail when chat/any input is focused, and likely register on the **capture phase** or call `stopImmediatePropagation()` to win the race. Needs an in-room smoke test.
- **Keybind storage / config UX:** where the user picks the key — the *trigger dialog* stores it server-side (so it's per-furni, sent back via the trigger definition). The client widget must learn the active key(s) for the current room (from the wired furni in the room) to know what to listen for. Cleanest: the room's wired triggers expose their configured key; the widget listens for those. (Greenfield — design choice.)
- **Furni asset:** a `wf_trg_press_keybind` furni must exist in furnidata+items_base; if none exists client-side, decide whether to add one or alias an existing sprite.

## Recommended order
1. Server side (1–8) — I can do + verify (`mvn test`) + commit, behind the furni existing.
2. Renderer composer + client dialog — straightforward, your build.
3. Runtime capture widget — the part needing the in-room smoke test (chat conflict).

**Effort:** medium. Server ~half a day; client/renderer ~half a day + smoke-testing the capture. All additive, no risk to existing features.
