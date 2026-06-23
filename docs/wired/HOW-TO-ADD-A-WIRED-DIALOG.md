# How to Add a New WIRED Dialog (Arcturus server + Nitro client)

A WIRED dialog is the editor popup that opens when a player clicks a wired furni box. It exists in
two halves that must agree **byte-for-byte**: the **Arcturus emulator** (Java, server) and the
**Nitro client** (React/TS UI + `@nitrots/nitro-renderer` networking). This guide covers adding a
brand-new wired type end-to-end. (Produced 2026-06-23 by tracing the live roundtrip in both repos.)

> Key roots
> - Server: `Emulator/src/main/java/com/eu/habbo/`
> - Client UI: `Nitro-V3/src`
> - Client renderer (networking primitives): `renderer/packages/communication/src` (only touched for non-generic shapes)

---

## 1. The roundtrip in one diagram

```
   user clicks box  ─► InteractionWired{Trigger,Effect,Condition}.onClick
                          builds Wired{...}DataComposer; composer.init(header); box.serializeWiredData(msg, room)
        S──►C OPEN DIALOG   header 383 (trigger) | 1434 (effect/action) | 1108 (condition)
   client: NitroMessages id ─► WiredFurni{Action,Condition,Trigger}Event ─► Parser
                          Triggerable(wrapper) reads shared prefix; *Definition reads suffix; useWired.setTrigger(def)
                          WiredView ─instanceof─► Wired{Action,Trigger,Condition}LayoutView(code) ─switch─► <WiredXxxView/>
                          useEffect([trigger]): hydrate UI from trigger.intData[]/stringData/selectedItems
   user edits ─► save() seeds setIntParams([...])/setStringParam(...)/furniIds ─► WiredBaseView.onSave ─► saveWired()
        C──►S SAVE          header 1520 (trigger) | 2281 (action) | 3203 (condition)
                          [id, ints.len,...ints, string, stuffs.len,...stuffs, (delay), selCode]
   server: PacketManager ─► Wired{...}SaveDataEvent; readInt() itemId; InteractionWired.readSettings(packet,isEffect)
                          ─► WiredSettings; box.saveData(settings[,client]) (validate; may throw); UPDATE items SET wired_data=?
        S──►C RESULT        WiredSavedComposer 1155 (ok, closes dialog) | UpdateFailedComposer 156 (validation alert)
```

Secondary open handshake (double-click): server `WiredOpenComposer` (**1830**) → client `WiredOpenEvent`
replies `OpenMessageComposer(stuffId)` (**768**) → server sends the OPEN DIALOG packet above.

### Header IDs (identical in both repos — you do NOT change these to add a new *type*)

| Direction | Category | id |
|---|---|---|
| S→C open | Trigger / Effect / Condition | **383 / 1434 / 1108** |
| C→S save | Trigger / Effect / Condition | **1520 / 2281 / 3203** |
| S→C open-request | — | **1830** ; client replies **768** |
| S→C save ok / fail | — | **1155 / 156** |

A new wired *type* reuses all of these generically. Only a brand-new *packet* requires touching `Incoming`/`Outgoing`/`PacketManager`.

---

## 2. Server checklist (Arcturus)

**2.0 Wire primitives** (`messages/ServerMessage.java`): `appendInt` = 4 bytes BE; `appendBoolean` = 1 byte;
`appendString` = 2-byte length prefix + UTF-8. The byte order is literally the call order.

**2.1 Add the enum constant** (the `.code` the client uses to pick the dialog):
- Effect → `habbohotel/wired/WiredEffectType.java`  (e.g. `SET_ROLLER_SPEED(88)`)
- Condition → `habbohotel/wired/WiredConditionType.java`
- Trigger → `habbohotel/wired/WiredTriggerType.java`

Multiple server constants may share one client code (e.g. `NEG_*` reuse the positive dialog).

**2.2 `serializeWiredData` — the OPEN-dialog payload. Canonical field order (all categories):**
```
appendBoolean(stuffTypeSelectionEnabled)   // furni-selecting flag
appendInt(maxFurniSelection)               // furniLimit (0=none, else WiredManager.MAXIMUM_FURNI_SELECTION)
appendInt(selectedFurniCount) + ids        // selected furni
appendInt(getBaseItem().getSpriteId())
appendInt(getId())
appendString(stringParam)                  // "" if none; pack multi-strings with '\t'
appendInt(intParamCount) + ...intParams... // EXACT count + order the dialog edits + saveData reads
appendInt(0)                               // reserved pad (stuffTypeSelectionCode slot)
appendInt(getType().code)                  // THE DIALOG CODE
--- EFFECT ONLY ---  appendInt(getDelay()); then appendInt(conflictingTriggers) + sprite ids
--- TRIGGER ONLY --- appendInt(conflictingActions) + sprite ids
--- CONDITION --- (no delay, no conflicting tail)
```
Models to clone: effect `WiredEffectWhisper`, condition `WiredConditionDateRangeActive`, trigger `WiredTriggerAtSetTime`.

**2.3 `saveData(WiredSettings[, GameClient])`** — read params back in the SAME order/count you serialized:
`settings.getIntParams()` / `getStringParam()` / `getFurniIds()` / `getDelay()` / `getStuffTypeSelectionCode()`.
Throw `new WiredSaveException("...")` on bad input. (Effect signature takes `GameClient`; trigger/condition don't.)

**2.4 Also implement:** both ctors `(ResultSet,Item)` and `(int,int,Item,String,int,int)`; `getWiredData()` (JSON via
`WiredManager.getGson()`) + `loadWiredData` + `onPickUp`; `execute(WiredContext)` / `evaluate(WiredContext)`;
keep deprecated `execute(RoomUnit,Room,Object[])` returning false.

**2.5 Register** in `habbohotel/items/ItemManager.java`:
`interactionsList.add(new ItemInteraction("wf_act_my_thing", WiredEffectMyThing.class));`
The string must equal `items_base.interaction_type` of the furni rows. (effects/conditions wildcard-imported;
triggers & extra wildcard-imported too — verified.)

**2.6 No packet registration needed** for a new type (reuses 1520/2281/3203 + 383/1434/1108 generically).

---

## 3. Client checklist (Nitro)

**3.1 Pick the LayoutCode number (MUST equal the server `getType().code`):**

| Enum file (`src/api/wired/`) | Class name | Next free (as of 2026-06-23) |
|---|---|---|
| `WiredActionLayoutCode.ts` | `WiredActionLayoutCode` | **88** |
| `WiredConditionLayoutCode.ts` | `WiredConditionlayout` (lowercase "l"!) | **44** |
| `WiredTriggerLayoutCode.ts` | `WiredTriggerLayout` | **24** |

`public static MY_NEW_ACTION: number = 88;`

**3.2 Add the switch case** in the matching LayoutView (+ import at top):
- Action → `components/wired/views/actions/WiredActionLayoutView.tsx`
- Condition → `.../conditions/WiredConditionLayoutView.tsx`
- Trigger → `.../triggers/WiredTriggerLayoutView.tsx`
```ts
case WiredActionLayoutCode.MY_NEW_ACTION: return <WiredActionMyNewActionView />;
```

**3.3 Create the component.** Clone `WiredActionToggleFurniStateView.tsx` (ints only, simplest) or
`WiredActionChatView.tsx` (string+ints). Pattern:
```tsx
export const WiredActionMyNewActionView: FC = () => {
    const [ myValue, setMyValue ] = useState(0);
    const { trigger, setIntParams } = useWired();
    useEffect(() => { setMyValue(trigger.intData.length > 0 ? trigger.intData[0] : 0); }, [ trigger ]);
    const save = () => setIntParams([ myValue ]);          // EXACT order the server reads
    return (<WiredActionBaseView hasSpecialInput requiresFurni={ WiredFurniType.STUFF_SELECTION_OPTION_NONE } save={ save }>
        { /* controls bound to myValue */ }
    </WiredActionBaseView>);
}
```
- `trigger.stringData` = the one string; `trigger.intData[i]` = each int; `trigger.selectedItems` = furni ids;
  `trigger.spriteId` / `maximumItemSelectionCount` / (actions) `trigger.delayInPulses`.
- `requiresFurni` from `WiredFurniType` (`NONE=0, BY_ID=1, BY_ID_OR_BY_TYPE=2, BY_ID_BY_TYPE_OR_FROM_CONTEXT=3`);
  when `>0`, `WiredBaseView` renders the furni picker and furniIds flow automatically.
- `save()` only seeds hook state; `WiredBaseView.onSave → needsSave effect → saveWired()` does the send.
- Conditions/triggers wrap `WiredConditionBaseView` / `WiredTriggerBaseView` (only actions carry the delay slider).

**3.4 Nothing else for the common case.** No parser/composer changes as long as the data fits the generic shape:
one `stringParam`, one `int[] intParams`, `int[] furniIds`, plus action `delay` + `selectionCode`.
> Renderer changes ARE needed only for shapes the generic format can't carry (multiple independent strings,
> nested arrays). Then edit `renderer/.../Triggerable.ts` + `UpdateActionMessageComposer.ts` in lockstep with the
> emulator. Avoid this: pack values into `intParams` slots and delimit compound strings inside the one `stringParam`
> (see `api/wired/WiredStringDelimeter.ts`).

---

## 4. The golden rule (all three must hold)

1. **Code match:** server `getType().code` == client `WiredXxxLayoutCode.YOUR_CONST`.
2. **Open order:** server `serializeWiredData` append order == client `Triggerable` + `*Definition` read order.
3. **Save order:** client `save()` intParams/stringParam order == server `saveData` read order (and the serialized
   `intParamCount` == the number of ints the dialog edits).

Mnemonic: the save array is the mirror of the `Triggerable` read order, and both mirror `serializeWiredData`.

---

## 5. Worked mini-example — new EFFECT "set roller speed" (one signed int), code 88

**Server enum** `WiredEffectType.java`: `SET_ROLLER_SPEED(88),`

**Server class** `wired/effects/WiredEffectSetRollerSpeed.java` — `getType()` returns `SET_ROLLER_SPEED`;
`serializeWiredData` appends `intCount=1, speed` then pad/code/delay/0; `saveData` reads `ints[0] → speed`.

**Register** `ItemManager.java`: `new ItemInteraction("wf_act_set_roller_speed", WiredEffectSetRollerSpeed.class)`.

**Client enum** `WiredActionLayoutCode.ts`: `public static SET_ROLLER_SPEED: number = 88;`
**Switch** `WiredActionLayoutView.tsx`: `case WiredActionLayoutCode.SET_ROLLER_SPEED: return <WiredActionSetRollerSpeedView/>;`
**Component** `WiredActionSetRollerSpeedView.tsx`: one `<input type="number">` bound to `speed`; `save = () => setIntParams([speed])`.

The three equalities: code 88==88 ✅; open `intData=[speed]` ✅; save `setIntParams([speed]) → ints[0]` ✅.
No renderer/composer/parser/packet changes — the generic Action path carries it.
