# Camera: server-side photo rendering

The official (AIR) client does not upload a picture when a photo is bought. It sends one
RenderRoom packet (header 3226) with a description of the scene, and the server draws the photo.
The Octane client today renders the photo itself and uploads the PNG in the same packet.
Polaris accepts both. It tells them apart by the first bytes of the payload, and
`camera.render.mode` decides which ones it accepts.

The room thumbnail packet (RenderRoomThumbnail, header 1982) works the same way. A thumbnail
is rendered at 110x110 and written to the thumbnail folder as `<roomId>.png`.

## Modes

| `camera.render.mode` | PNG upload (Octane today) | Scene JSON (official client) |
|---|---|---|
| `upload` | stored, as before | refused |
| `server` (default) | refused | rendered by the server; without a configured asset source every photo is refused, and the log says what to set |
| `auto` | stored | rendered, but only when an asset source is configured; otherwise refused, and the log says what to set |

Server rendering is the default, so a client can no longer upload any picture as a photo. Set the
`camera.render.assets.*` paths below before players buy photos, or set `upload` to keep the old
client-rendered photos.

A refused photo gets the usual `camera.error.creation` alert. When the render queue is full the
user gets `camera.render.busy` and can try again. A refusal does not use up the photo cooldown.

How the payload is recognised: a PNG starts with `89 50 4E 47`. Scene JSON is zlib data,
starting with `78 xx` and a valid zlib header check. Anything else is refused.

## What the server accepts (the official client's format)

The packet body is a byte array (`int length` followed by the bytes). The bytes are the
zlib-deflated UTF-8 string that the official client builds:

```
{ "planes" : [...],"sprites" : [...],"modifiers" : {},"filters" : [...],"roomid" : N[,"zoom" : N],"status" : N,"timestamp" : N,"checksum" : N }
```

- **planes**: `{z, cornerPoints:[{x,y} x4], color (RGB int), bottomAligned, texCols:[{assetNames:[...]}], masks?:[{name, location:{x,y}, flipH, flipV}]}`.
  The first plane is the full-viewport background. Texture and mask names come from the `room` library.
  A landscape (the view through a window) arrives as one plane per landscape layer: a colour
  layer, then bottom-aligned texture columns of `landscape_*` assets. The layers have the corner
  points of the wall they lie on and a depth just behind it. The server draws them behind the wall,
  and they show through the holes the wall's masks cut.
- **sprites**: `{name, x, y, z}`, plus these fields only when they differ from the default:
  - `alpha` (0..255; 255 when omitted)
  - `flipH`, `skew`, `frame`
  - `color` (RGB int)
  - `blendMode`: `add`, `subtract` and `darken` (what furniture layer `ink` gives), and also
    `multiply`, `screen`, `lighten`, `difference`, `overlay` and `hardlight`. Any other value draws normally.
  - `width`/`height` (for `http...` images and `group_badge:` badges)
  - `posture`, `paletteSourceName`

  Names are asset names (furniture, figure parts, pets, room), `http(s)://...` external images
  (badges, photos), or `group_badge:<badge code>` (see [Group badges](#group-badges)). `avatar_<id>`
  anchor sprites are skipped. External images and badges are drawn at the `width`/`height` the
  client sent.

  For skewed thumbnails and for small external images and badges (up to 200x200), the client sends
  the picture's average colour as `color`, as AIR does. That colour is not a tint. The server draws
  the picture as it is. If the picture cannot be loaded (fetching off, refused, a missing badge
  part), the server draws a block of that colour at the sprite's size instead.
- **filters**: `[{name, alpha 0..255}]` with frames last. These are colour-matrix effects
  (`dark_sepia`, `increase_saturation`, `x_ray`, ...) and overlays (`hearts_hardlight_02`,
  `frame_gold`, ...). The overlay images come from `camera.render.assets.effects_dir`.
- **roomid** must be the room the user is in. **zoom** 2 enlarges the centre 2x.
- **status / timestamp / checksum** are checked the way the client computes them, when
  `camera.render.verify_checksum` is 1 (the default):
  - `status = clock/100 % 23 + securityLevel`
  - `check = (length + clock/100*17) % 1493`
  - `timestamp = clock + fletcher100(text, check, roomId)`
  - `checksum = (lastDigits + 13) * (check + 29)`

  The server cannot know the user's security level or the two clock digits the client dropped,
  so it only checks that they are in range.
  The keys, the spaces around ` : ` and the closing ` }` must be exactly as above, because the
  checksum covers the raw text.

The photo is written as `<userId>_<timestamp>.png` (320x320) and `<userId>_<timestamp>_small.png`
(110x110) in `imager.location.output.camera`. The URL is `camera.url` + file name, the same scheme
as uploads, so the viewer allow-list keeps working. The pending photo is set exactly as for an
upload. Purchase, publish and competition then work unchanged: the photo stays bound to its room,
and buying it twice is still refused.

## Asset sources

Server rendering needs at least one of these. Paths are read when the emulator starts.

| Key | What |
|---|---|
| `camera.render.assets.furniture_dir` | Folder of furniture `.nitro` bundles (the client's `furni.asset.url` folder). |
| `camera.render.assets.figure_dir` | Folder of figure `.nitro` bundles (`avatar.asset.url`). |
| `camera.render.assets.figuremap` | The hotel's `FigureMap.json` (gamedata). |
| `camera.render.assets.effect_dir` | Folder of avatar effect `.nitro` bundles (`avatar.asset.effect.url`). |
| `camera.render.assets.effectmap` | The hotel's `EffectMap.json`. |
| `camera.render.assets.pet_dir` | Folder of pet `.nitro` bundles (`pet.asset.url`). |
| `camera.render.assets.room_library` | `room.nitro` (or `room.hab`), or the client's room asset folder (`Octane-Renderer/packages/assets/src/assets/room`: `room.asset.json` plus `images/room_*.png`). |
| `camera.render.assets.effects_dir` | Folder of camera effect and frame overlays as `<name>.png` (`frame_gold.png`, `hearts_hardlight_02.png`, ...), the same images the client's camera effects use. |
| `camera.render.assets.sprites_dir` | Optional folder of loose `<asset>.png`, checked before the bundles (the flash-era export). |
| `camera.render.assets.wall_colors` | Optional `.properties` file `wallpaint=0xRRGGBB` overriding wall colours. |
| `camera.render.assets.max_bundle_mb` | Largest bundle read (default 64). |

How a sprite name is matched to a bundle (the same libraries the client drew it from):

1. **Room**: wall and floor textures, masks such as `door_64`, and landscapes are looked up in the room library first.
2. **Effect parts** (`h_std_fx29_...`): the library that `EffectMap.json` lists for effect 29, in `effect_dir`.
3. **Figure parts** (`h_std_hd_180_2_0`, which is size, action, part type, part id, direction, frame):
   the library that `FigureMap.json` lists for `hd:180`, in `figure_dir`.
4. **Furniture** (`<classname>_<size>_<layer>_<direction>_<frame>`): `<classname>.nitro`, or
   `<classname>.hab` when there is no `.nitro`. The server tries the name's underscore prefixes,
   longest first. A colour variant `name*3` uses its base library.
5. **Pets**: the same prefix rule in `pet_dir`.

Bundles are read as the client reads them. A bundle is either a `.nitro` file or a Habbo `.hab`
file. The server accepts a `.hab` only when it holds the same files as a `.nitro`: the asset json
and one sheet. A flash-style `.hab` (xml and loose images) is refused, as the client refuses it.
Convert it first. The `.json` asset library and the `.png` sheet are cut into images by asset
name. A `source` alias resolves to its source's frame. A frame stored rotated
(TexturePacker `rotated: true`, turned 90 degrees clockwise) is turned back upright, as Pixi does.
A trimmed frame is padded back to its `sourceSize`, so the image lines up with the sprite's x/y.
Mirroring follows the sprite's `flipH`, which the client copies from the asset. Pet palettes come
from the library's `palettes`, matched by `paletteSourceName`.

## Group badges

The client builds group badges itself from the badge parts. When the client has a
`group.badge.url` with `%imagerdata%` in it, the scene names the badge by that URL, and the server
fetches it like any other external image. Without one, the scene names it
`group_badge:<badge code>`, for example `group_badge:b05114s06114`.

The server draws such a badge in memory from the code. It uses the same composition as the guild
badge imager (`BadgeImager`), and it writes no file. Each badge is drawn once and then kept in a
memory cache (1/32 of `camera.render.cache_mb`). A badge already in the cache stays as drawn
after the guild parts are reloaded, until the cache drops it. The badge is scaled to the sprite's
`width` and `height`.

What the badge needs:

| Source | What |
|---|---|
| Guild parts (database `guilds_elements`) | Which images make up each base and symbol, and the colour hex of each colour id. The emulator loads them at start; the `cmd_update_guildparts` command reloads them. |
| `imager.location.badgeparts` | Folder of the part images, named `badgepart_<part>.png`. A `.gif` in the part name is read as `.png`. |
| `imager.internal.enabled` | When 1, every part image is read at start (as for the guild badge files). When 0, the camera reads each part image the first time a badge needs it. |

A badge code is one to five parts of seven characters: `b` (base) or `s` (symbol), a three-digit
part id, a two-digit colour id and a one-digit position. Any other code is refused. A badge whose
parts cannot be found is drawn as a block of its average colour.

## Performance

Renders never run on game, room or network threads. The packet thread only reads the room id,
background toner and wall paint. It then queues the job and returns at once.

| Key | Default | What |
|---|---|---|
| `camera.render.workers` | 2 | Render threads (read at start-up). |
| `camera.render.queue` | 16 | Renders that may wait. When the queue is full, a request is refused immediately with `camera.render.busy` (read at start-up). |
| `camera.render.timeout_ms` | 5000 | A render is abandoned after this long. |
| `camera.render.cache_mb` | 128 | Memory for decoded bundles and sliced images (LRU by decoded bytes). Nothing is loaded at boot; a library loads the first time a photo needs it. |
| `camera.render.limits.compressed_bytes` | 65536 | Largest compressed scene. |
| `camera.render.limits.inflated_bytes` | 1048576 | Largest scene after inflating. A bigger one is refused while inflating. |
| `camera.render.limits.planes` / `.sprites` / `.filters` | 64 / 1024 / 16 | Counts per scene. Also fixed caps: 32 masks and 64 texture columns per plane, 16 assets per column. |
| `camera.render.limits.plane_texture_pixels` | 1048576 | Largest plane texture built. |
| `camera.render.fetch.enabled` | 1 | Fetch `http...` sprite images. |
| `camera.render.fetch.max_per_render` | 30 | External images per render. The client itself sends at most 30 and turns the rest into `box`. |
| `camera.render.fetch.budget_ms` | 3000 | Total fetch time per render. |
| `camera.render.fetch.connect_timeout_ms` / `.read_timeout_ms` | 1500 / 2000 | Per fetch. |
| `camera.render.fetch.max_bytes` / `.max_pixels` | 2 MB / 4 MP | Per external image. |
| `camera.render.fetch.allowed_hosts` | empty | Hosts (or `*.suffix`) that may resolve to private addresses. By default, loopback, private and link-local addresses are refused, and redirects are not followed. The host is resolved once, and the connection goes to that checked address. The request keeps the original `Host` header, and https uses the original name for SNI and for the certificate check. |
| `camera.render.verify_checksum` | 1 | Check the client checksum (see above). |
| `camera.render.max_clock_skew_seconds` | 0 (off) | Refuse a scene whose client clock is further than this from the server clock. |

The photo cooldown (15 s), the daily limit and the packet handling stay as they are for uploads.

## Not supported yet

- Flash-style libraries (`*_assets.xml` with loose images, `.bin` palettes, or a `.hab` that
  holds them) are not read. Convert them to `.nitro` (or a `.hab` with a json and one sheet), or
  use the loose `sprites_dir` folder.
- Landscapes: AIR sends neither the landscape's animated layer (clouds) nor its offset along
  the walls. The server draws the colour and texture layers, starting each wall's texture columns
  at that wall's edge.
- `posture` and `frame` are accepted but not used (the sprite name already names the frame).
