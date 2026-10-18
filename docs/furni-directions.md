# Furni directions

In Habbo a floor furni can only face the directions its asset lists. A bed faces 0 or 2,
`bling_bed` faces 2 or 4, a colour tile only 0. Polaris reads each furni type's directions from the
hotel's own furniture bundles, the files the client loads. There is no generated table to keep up
to date, and a new furni works as soon as its bundle is in the folder.

## What the client does

The official client (AIR) reads `logic.model.directions` from the furni's asset library. The
values are degrees: `[0, 90]` means rotations 0 and 2. It sorts them and keeps the list as
`furniture_allowed_directions`.

- **Rotate**: the client steps to the next direction in that list (or the previous one). A
  rotation that is not in the list counts as the first direction at or after it, and the list wraps
  around. A furni with one direction does not turn.
- **Move**: when a move starts on a direction that is not in the list, the client starts the furni
  on the first direction in the list.
- The visualization's own direction list is only used to draw the furni. Each size has one, and
  the client draws the nearest direction it has.

The Octane renderer reads the same `logic.model.directions` the same way.

## What the server does with them

Directions are in rotation units (0..7). "defaultdir" is the furnidata `defaultdir` of the
classname.

| Where | Rule |
|---|---|
| A user moves, rotates or places a furni (`RotateMoveItemEvent`, `UpdateFurniturePositionEvent`, `RoomPlaceItemEvent`) | A rotation the furni has is stored as it is. Any other rotation is stored as the defaultdir when the furni has that direction, else as its first direction. |
| Wired **Move/Rotate furni** | Clockwise and counterclockwise step to the next direction the furni has, in 45 degree order, as the client's rotate does. Random picks another direction the furni has. A furni with one direction stays put. |
| Wired **projectile** add-on | A heading the furni has is taken as it is. Otherwise the furni lands on its defaultdir when it has that direction, and keeps its rotation if not. A furni with a single direction leaves the shooter as is. |
| `@rotation` variable write | The value when the furni has it. Otherwise the nearest direction within 45 degrees, clockwise first. Anything further away is refused and nothing changes. |
| Wired **Match to Snapshot**, and applying a snapshot from the wired menu | The same rule as `@rotation` for the snapshot's rotation. When that is refused, the furni keeps its rotation; position and altitude still apply. |
| Wired **Place furni** | The configured rotation follows the user placement rule. |

The server rules come from PlusEMU commits 8aef04aa, 0aa757d5 and 494c829c. Their authors watched
an AIR build do this. Polaris reimplements the behaviour; it copies no code or data from them.

## Where the directions come from

1. `furni.directions.assets_dir` if it is set, else `camera.render.assets.furniture_dir`: the
   furniture bundle folder the camera renderer uses (the client's `furni.asset.url` folder).
2. In that folder, `<classname>.nitro`, or else `<classname>.hab` (Habbo's bundle with the same
   json). A colour variant `name*3` uses `name`.
3. In the asset json, `logic.model.directions` (in degrees). If it has none, the server uses the
   direction keys of the size-64 visualization.

Only the json is read. The sheet image is not decoded. Each bundle is read once, on a background
thread named `FurniDirections-1`. The answer is kept in a memory cache, and so is "no data", so a
missing bundle is not looked up again and again.

When a room loads, the server starts reading the bundles of the furni in it. Wired and rotations
then find the answers in the cache. If a lookup misses the cache, it waits up to
`furni.directions.wait_ms` for the read. After that it uses the fallback below, and the next lookup
finds the answer cached.

## Fallback

A furni keeps today's rules when:

- `furni.directions.enabled` is 0;
- no bundle folder is set, or the folder does not exist;
- its bundle is missing, cannot be read, is larger than `camera.render.assets.max_bundle_mb`, or
  lists no directions;
- the read did not finish within `furni.directions.wait_ms`.

Today's rules are: any rotation for user moves and `@rotation`; Move/Rotate by the
`items_base` rotation count; even projectile headings only. A missing defaultdir only changes
which listed direction is used.

Up to 20 unreadable bundles are named in the log. The server does not reload a bundle that changes
on disk while it runs. Restart the emulator after replacing bundles.

## Settings (`emulator_settings`)

| Key | Default | What |
|---|---|---|
| `furni.directions.enabled` | `1` | Read furni directions from the furniture bundles. |
| `furni.directions.assets_dir` | empty | Folder of furniture `.nitro`/`.hab` bundles. Empty uses `camera.render.assets.furniture_dir`. |
| `furni.directions.cache_size` | `8192` | Furni types whose directions stay in memory (LRU). |
| `furni.directions.wait_ms` | `50` | Longest wait for a bundle read before the fallback applies; 0 never waits. |

These settings are read when the emulator starts. The defaultdir comes from the furnidata that the
furni names use (`items.furnidata.path` and related settings). It updates when the furnidata
changes.
