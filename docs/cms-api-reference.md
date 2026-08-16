# CMS Command Reference

Every administrative command the emulator exposes to external CMS software, with
its key, payload fields, validation rules and response codes.

These commands are **transport-neutral**: the `data` object documented here is
identical whether the command is sent over the legacy **RCON** TCP listener or the
**CMS HTTP API** (see [`rcon-api-migration.md`](./rcon-api-migration.md)). Only the
request framing and authentication differ between transports.

> Source of truth: `com.eu.habbo.messages.rcon.*` and the registrations in
> `RCONServer`. The machine-readable wire envelope is
> [`protocol/rcon-contract.json`](../protocol/rcon-contract.json).

## Request envelope

```json
{ "key": "givecredits", "data": { "user_id": 1, "credits": 500 } }
```

- `key` — command key. Matched case-insensitively with underscores removed, so
  `giveCredits`, `give_credits` and `GIVECREDITS` all resolve to the same command.
- `data` — the command-specific payload object documented below. Unknown fields
  are ignored; omitted optional fields fall back to their defaults.

### RCON transport
Raw JSON, one request per TCP connection, to `rcon.host:rcon.port`. Admission is
by IP allow-list (`rcon.allowed`) plus a per-address rate limit. The connection
receives the response and closes.

### HTTP transport
`POST /api/cms/command` on the game server port, with the same JSON body,
authenticated with an HMAC-signed request. Enable with `cms.api.enabled=true`.

**Routes** (all under `/api/cms`, all behind the Tier-1 IP allow-list):

| Method | Path | Needs key? | Purpose |
| ------ | ---- | ---------- | ------- |
| GET | `/api/cms` | no | Discovery index — lists these routes and which need a key |
| GET | `/api/cms/ping` | no | Liveness probe; returns `{"status":0,"message":"pong"}` |
| GET | `/api/cms/commands` | yes | Lists the commands the calling key is scoped for |
| POST | `/api/cms/command` | yes | Dispatch a command (`{key, data}`) |

The two no-key routes are still gated by `cms.api.allowed`, and the index only
reveals route names — never the command list (that stays behind auth + scope).

**Access control**
- **Tier 1 (network):** the direct peer must match `cms.api.allowed` (loopback by
  default; exact IPs or CIDR blocks, `;`-separated). Behind a reverse proxy,
  allow-list the proxy IP.
- **Tier 2 (request):** an HMAC-SHA256 signature over the request. Keys and all
  API tuning live in the **`emulator_api` database table** (not `config.ini`), so
  they can be managed and rotated from the CMS without a restart. Add a
  `cms.api.keys` row as `keyId|secret|scopes` (records `;`-separated). Scopes:
  `*`, `group:*` (e.g. `economy:*`), `group:command`, or a bare command key. The
  stress commands can never be called over HTTP regardless of scope.

**Settings (`emulator_api` table)** — `cms.api.enabled` and `cms.api.allowed` stay
in `config.ini`; everything below is a row in `emulator_api` (seeded by
migration), read live with a short cache:

| Key | Default | Meaning |
| --- | ------- | ------- |
| `cms.api.keys` | *(empty)* | `keyId|secret|scopes` records, `;`-separated |
| `cms.api.timestamp.skew.seconds` | `300` | max accepted timestamp skew |
| `cms.api.nonce.ttl.seconds` | `600` | replay-protection nonce window |
| `cms.api.max_payload_bytes` | `65536` | max request body size |
| `cms.api.require_tls` | `0` | require TLS for non-loopback callers |
| `cms.api.rate_limit.enabled` | `1` | per-IP rate limiting toggle |
| `cms.api.rate_limit.limit_for_period` | `120` | requests per refresh period |
| `cms.api.rate_limit.refresh_period_ms` | `1000` | refresh period (ms) |

**Required headers**

| Header | Value |
| ------ | ----- |
| `X-Cms-Key` | the key id |
| `X-Cms-Timestamp` | unix seconds; must be within `cms.api.timestamp.skew.seconds` (default 300) |
| `X-Cms-Nonce` | unique per request; a repeat within the window is rejected (409) |
| `X-Cms-Signature` | lowercase hex of `HMAC-SHA256(secret, keyId + "\n" + timestamp + "\n" + nonce + "\n" + rawBody)` |

The signature is computed over the **exact raw request body**, so sign the same
bytes you send.

**Example (PHP CMS)**

```php
$keyId = 'cms-main';
$secret = '...';                         // matches cms.api.keys
$body = json_encode(['key' => 'givecredits', 'data' => ['user_id' => 1, 'credits' => 500]]);
$ts = (string) time();
$nonce = bin2hex(random_bytes(16));
$sig = hash_hmac('sha256', "$keyId\n$ts\n$nonce\n$body", $secret); // lowercase hex

$ch = curl_init('http://127.0.0.1:30000/api/cms/command');
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        'Content-Type: application/json',
        "X-Cms-Key: $keyId",
        "X-Cms-Timestamp: $ts",
        "X-Cms-Nonce: $nonce",
        "X-Cms-Signature: $sig",
    ],
    CURLOPT_POSTFIELDS => $body,
    CURLOPT_RETURNTRANSFER => true,
]);
$response = curl_exec($ch); // -> {"status":0,"message":""}
```

The machine-readable envelope lives in
[`protocol/cms-api-contract.json`](../protocol/cms-api-contract.json).

## Response envelope

Always exactly two fields:

```json
{ "status": 0, "message": "" }
```

| `status` | Constant          | Meaning |
| -------- | ----------------- | ------- |
| `0`      | `STATUS_OK`       | Handled successfully. `message` may carry a note (e.g. `"offline"`). |
| `1`      | `STATUS_ERROR`    | Validation failed, bad input, or the command could not be applied. `message` describes why. |
| `2`      | `HABBO_NOT_FOUND` | Target user does not exist. |
| `3`      | `ROOM_NOT_FOUND`  | Target room does not exist. |
| `4`      | `SYSTEM_ERROR`    | Internal error while handling (e.g. SQL failure). |

Transport-level errors reuse the same envelope: `rate limited`, `payload too
large`, `invalid request`, `unknown command`, `command failed`.

Validation `message` values are the annotation messages listed per command below
(e.g. `"invalid user"`, `"invalid credits"`). When several fields are invalid, the
message for the alphabetically-first field path is returned.

## Global limits

- **Payload size:** capped at `rcon.max_payload_bytes` (default 65536).
- **Currency ceiling:** `givecredits`, `givepixels`, `givepoints` amounts are
  additionally capped at `rcon.grant.max_amount` (default 1,000,000). Exceeding it
  returns `"<field> exceeds rcon grant ceiling"`.

---

## Commands

Legend for **Field** column: `type name` — `constraint`. `*` marks a required
field (must be present and pass validation); others are optional with the default
shown.

### Economy & currency

#### `givecredits` — grant credits
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int credits` * | > 0, ≤ grant ceiling |

Grants to online or offline users; offline grants go through the economy ledger
and return `message: "offline"`. `HABBO_NOT_FOUND` if the user does not exist.

#### `givepixels` — grant pixels (duckets)
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int pixels` * | > 0, ≤ grant ceiling |

#### `givepoints` — grant a seasonal/points currency
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int points` * | > 0, ≤ grant ceiling |
| `int type` | ≥ 0 — currency/points type id (default 0) |

#### `sendgift` — deliver a catalog item as a gift
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int itemid` * | > 0 — catalog item id |
| `String message` | gift note (default `""`) |

#### `sendroombundle` — send a room bundle from a catalog page
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int catalog_page` * | > 0 — catalog page id |

#### `giveuserclothing` — unlock a clothing item
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int clothing_id` * | > 0 |

#### `modifysubscription` — add/remove a subscription (e.g. HABBO_CLUB)
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `String type` * | non-blank, ≤ 64, `[A-Za-z0-9_]+` — e.g. `HABBO_CLUB` |
| `String action` * | non-blank, ≤ 16, one of `add`/`remove`/`a`/`r`/`+`/`-` (case-insensitive) |
| `int duration` | seconds to add/remove; `-1` removes the subscription entirely (default `-1`) |

### Users & profile

#### `updateuser` — update account/profile flags and reload the user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int achievement_score` | ≥ 0 (default 0) |
| `int block_following` | `-1`/`0`/`1`; `-1` = leave unchanged (default `-1`) |
| `int block_friendrequests` | `-1`/`0`/`1` (default `-1`) |
| `int block_roominvites` | `-1`/`0`/`1` (default `-1`) |
| `int old_chat` | `-1`/`0`/`1` (default `-1`) |
| `int block_camera_follow` | `-1`/`0`/`1` (default `-1`) |
| `String look` | figure string, bounded length (default `""`) |
| `boolean strip_unredeemed_clothing` | default `false` |

#### `setmotto` — set a user's motto
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `String motto` * | non-null, ≤ 127 |

#### `setrank` — set a user's rank
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int rank` * | > 0 — rank id |

#### `changeusername` — allow/deny a username change for a user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `boolean canChange` | grant the user a username change |

#### `givebadge` — award a badge
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `String badge` * | non-blank, ≤ 512 — badge code |

#### `progressachievement` — add achievement progress
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int achievement_id` * | > 0 |
| `int progress` * | > 0 |

#### `giverespect` — adjust respect counters
| Field | Constraint |
| ----- | ---------- |
| `int user_id` | user id |
| `int respect_given` | default 0 |
| `int respect_received` | default 0 |
| `int daily_respects` | default 0 |

### Moderation

#### `muteuser` — mute a user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int duration` * | ≥ 0 — seconds |

#### `disconnect` — force-disconnect a user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` | target by id (default `-1`) |
| `String username` | or target by username |

Provide `user_id` **or** `username`.

#### `modticket` — open a moderation ticket
| Field | Constraint |
| ----- | ---------- |
| `int sender_id` * | > 0 |
| `String sender_username` * | non-blank, ≤ 64 |
| `int reported_id` * | > 0 |
| `String reported_username` * | non-blank, ≤ 64 |
| `int reported_room_id` | ≥ 0 (default 0) |
| `String message` * | non-blank, ≤ 4096 |

#### `executecommand` — run an in-game command as a user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `String command` * | non-blank, ≤ 512 |

> **High privilege.** Runs an arbitrary chat command with the target user's
> permissions. On the HTTP API this should require a dedicated scope and be
> excluded from general-purpose CMS keys.

### Alerts & messaging

#### `alertuser` — send a plain alert to one user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `String message` * | non-blank, ≤ 4096 |

#### `hotelalert` — broadcast a plain alert to the whole hotel
| Field | Constraint |
| ----- | ---------- |
| `String message` * | non-blank, ≤ 4096 |
| `String url` | optional link, ≤ 2048 (default `""`) |

#### `imagealertuser` — rich (bubble/image) alert to one user
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `String bubble_key` * | non-blank, ≤ 64 |
| `String message` | ≤ 4096 (default `""`) |
| `String url` | ≤ 2048 (default `""`) |
| `String url_message` | ≤ 256 (default `""`) |
| `String title` | ≤ 256 (default `""`) |
| `String display_type` | ≤ 32 (default `""`) |
| `String image` | ≤ 2048 (default `""`) |

#### `imagehotelalert` — rich (bubble/image) alert to the whole hotel
| Field | Constraint |
| ----- | ---------- |
| `String bubble_key` * | non-blank, ≤ 64 |
| `String message` | ≤ 4096 (default `""`) |
| `String url` | ≤ 2048 (default `""`) |
| `String url_message` | ≤ 256 (default `""`) |
| `String title` | ≤ 256 (default `""`) |
| `String display_type` | ≤ 32 (default `""`) |
| `String image` | ≤ 2048 (default `""`) |

#### `staffalert` — send an alert to online staff
| Field | Constraint |
| ----- | ---------- |
| `String message` * | non-blank, ≤ 4096 |

#### `talkuser` — make a user "say" a message in their room
| Field | Constraint |
| ----- | ---------- |
| `String type` * | non-blank, ≤ 16 — e.g. `say`/`shout`/`whisper` |
| `int user_id` * | > 0 |
| `int bubble_id` | chat bubble id (default `-1`) |
| `String message` * | non-blank, ≤ 512 |

### Social & rooms

#### `friendrequest` — create a friend request between two users
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int target_id` * | > 0 |

#### `ignoreuser` — make one user ignore another
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int target_id` * | > 0 |

#### `stalkuser` — make one user follow (stalk) another
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int follow_id` * | > 0 |

#### `forwarduser` — teleport a user into a room
| Field | Constraint |
| ----- | ---------- |
| `int user_id` * | > 0 |
| `int room_id` * | > 0 |

#### `changeroomowner` — reassign room ownership
| Field | Constraint |
| ----- | ---------- |
| `int room_id` * | > 0 |
| `int user_id` * | > 0 — new owner |
| `String username` | new owner username |

### Cache / data reloads

These reload server-side data after the CMS changes it in the database. Their
`data` object is **empty** (`{}`).

| Key | Effect |
| --- | ------ |
| `updatecatalog` | Reload the catalog. |
| `updateitems` | Reload item (furni) definitions. |
| `updatewordfilter` | Reload the word filter. |
| `updatewheel` | Reload the "wheel of fortune" / lucky wheel config. |
| `updatesoundboard` | Reload soundboard data. |

---

## Load-testing commands (not for CMS)

These are registered **only** when Polaris stress controls are enabled
(`stress.enabled`) and exist for load testing, not CMS operations:

| Key | Data | Purpose |
| --- | ---- | ------- |
| `stressstart` | `room_id`*, optional `profile`, `bots`, `items`, `rollers`, `wired_stacks`, `wired_events_per_second`, `item_id`, `chat_per_second`, `duration_seconds`, `seed`, `movement` | Start a bounded synthetic load scenario in a room. |
| `stressstatus` | `room_id`* | Query a running scenario. |
| `stressstop` | `room_id`* | Stop a scenario. |

The optional profile presets are intentionally bounded and never run automatically:

| Profile | Bots | Items | Rollers | Wired stacks | Wired/s | Chat/s | Duration |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `small` | 25 | 500 | 25 | 25 | 5 | 20 | 60 s |
| `medium` | 100 | 2,500 | 100 | 100 | 20 | 100 | 120 s |
| `large` | 300 | 10,000 | 500 | 500 | 50 | 300 | 180 s |

Set `runtime.operational.profile` to `small`, `medium`, or `large` to select
conservative persistence sizing. The default `custom` value preserves the
existing adaptive behavior. Explicit positive values in
`persistence.executor.threads` and `db.persistence.queue.capacity` always take
precedence. A zero queue capacity selects the profile default. `stress.enabled`
remains off by default, and a load
scenario still requires a manual allowlisted RCON request.

They are documented for completeness; a CMS API key should never be granted a
scope that includes them.
