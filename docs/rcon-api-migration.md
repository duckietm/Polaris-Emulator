# RCON → CMS API Migration

Status: **steps 1 & 2 landed** — the transport-neutral command registry and the
HMAC-authenticated CMS HTTP API both exist and run side by side with RCON. The API
ships **disabled by default** (`cms.api.enabled=false`). Remaining work is the CMS
adopting it command-by-command and, eventually, retiring the RCON listener.

## Why

External CMS software drives hotel changes (give credits, alerts, rank changes,
catalog reloads, …) by opening a TCP socket to the emulator's **RCON** listener
and sending `{"key": "...", "data": {...}}`. This has worked for ~15 years, but
RCON has structural limits as a control plane:

- **Authentication is network-only.** The sole gate is an IP allow-list
  (`rcon.allowed`). Anything that can reach the port from an allowed address is
  fully trusted. There is no per-caller identity, no secret, and no way to scope
  what a given caller may do.
- **Off-box use is unsafe.** Because there is no request-level secret, running the
  CMS on a different server means trusting the network path completely.
- **No audit trail or per-caller limits.** Rate limiting is per remote address
  only, and there is no record of who issued which command.

The goal is to expose the same administrative surface over an **HTTP API** that is
safe to call both loopback (CMS on the same box) and remotely, while keeping RCON
running unchanged so the CMS can migrate command-by-command.

## Principles

1. **Side-by-side, not a cutover.** RCON and the API stay live together until the
   CMS is fully migrated. RCON behavior does not change at all during the
   migration.
2. **One command implementation, two transports.** Command logic, payload types,
   validation and the `{status, message}` response envelope are shared. A command
   added once is reachable from both transports; there is no second copy to keep
   in sync.
3. **Defense in depth (two-tier access control).** Network admission first, then
   cryptographic request authentication with per-key scopes.
4. **Reuse existing infrastructure.** The gameserver already runs a hardened HTTP
   stack (`networking/gameserver/auth/`) with an off-event-loop worker pool,
   rate limiting, CORS gating, Cloudflare-aware client-IP resolution and
   HMAC-SHA256 signing/verification (`AccessTokenService`). The API is a new
   handler on that pipeline, not a new server.

## Architecture

```
                     ┌─────────────────────────────────────────┐
   RCON TCP client ──┤ RCONServer                               │
   (legacy CMS)      │  • IP allow-list (rcon.allowed)          │
                     │  • per-IP rate limit (Resilience4j)      │
                     │  • payload size cap                      │
                     └───────────────┬─────────────────────────┘
                                     │  dispatch(key, body)
                                     ▼
                     ┌─────────────────────────────────────────┐
                     │ CommandRegistry (transport-neutral)      │
                     │  • key normalization                     │
                     │  • payload parse (Gson)                  │
                     │  • Jakarta Bean Validation               │
                     │  • RCONMessage.handle(...)               │
                     │  • {status, message} envelope            │
                     └───────────────▲─────────────────────────┘
                                     │  dispatch(key, body)
                     ┌───────────────┴─────────────────────────┐
   HTTPS/HTTP     ───┤ CmsApiHandler   (Netty HTTP, planned)    │
   (new CMS)         │  • Tier 1: IP / CIDR allow-list          │
                     │  • Tier 2: HMAC request auth + scopes    │
                     │  • per-key rate limit + audit log        │
                     │  • status → HTTP code mapping            │
                     └──────────────────────────────────────────┘
```

### Step 1 — shared registry (implemented)

`com.eu.habbo.messages.command.CommandRegistry` now owns command lookup, payload
parsing, validation and dispatch, returning a transport-neutral
`CommandResult(known, status, message)`.

`RCONServer` keeps everything transport-specific — the IP allow-list, the
Resilience4j per-address rate limiter, the payload cap and the command
registrations — and simply calls `registry.dispatch(key, body)` once a request is
admitted. `CommandResult.toResponseJson()` emits the exact `{status, message}`
bytes RCON has always returned, so the wire contract in
[`protocol/rcon-contract.json`](../protocol/rcon-contract.json) is unchanged and
all existing RCON contract tests pass untouched.

The command classes themselves (`com.eu.habbo.messages.rcon.*`) are **not** moved
or renamed in this step, to keep the diff small and reviewable. A later,
optional cleanup can rename the package to something transport-neutral.

### Step 2 — CMS API handler (implemented)

`CmsApiHandler` (`networking/gameserver/cms/`) is mounted in
`WebSocketChannelInitializer` next to `AuthHttpHandler`, on the blocking HTTP
executor group so command dispatch never runs on the event loop. Routes:

- `POST /api/cms/command` — body `{ "key": "...", "data": {...} }`, a 1:1 mirror of
  the RCON envelope. The CMS migration is a *transport swap, not a payload
  rewrite*: every existing command works on day one, and there is a single shared
  contract to maintain. REST-style routes can be layered on later.
- `GET /api/cms/commands` — authenticated; lists the command keys the calling key
  is scoped for.
- `GET /api/cms/ping` — network-gated liveness probe (no auth), returns
  `{status:0,message:"pong"}`.

Dispatch parses the JSON → `registry.dispatch(key, data)` → maps `CommandResult`
to HTTP while keeping the `{status, message}` body identical to RCON:

| CommandResult                         | HTTP status |
| ------------------------------------- | ----------- |
| `known=false`                         | 404 Not Found |
| `status = STATUS_OK (0)`              | 200 OK |
| `status = STATUS_ERROR (1)` (validation / domain) | 400 Bad Request |
| `status = HABBO_NOT_FOUND (2)` / `ROOM_NOT_FOUND (3)` | 404 Not Found |
| `status = SYSTEM_ERROR (4)`           | 500 Internal Server Error |

Auth/transport failures map to 401 (bad/missing signature, unknown key, stale
timestamp), 409 (nonce replay), 403 (network denied / out of scope / TLS
required), 429 (rate limited) and 413 (payload too large).

The handler reuses the shared registry via `RCONServer.getRegistry()`, so command
registrations live in exactly one place.

**Settings source.** `cms.api.enabled` and `cms.api.allowed` are read from
`config.ini` (bootstrap toggle + network gate). Everything else — keys, timestamp
skew, nonce TTL, payload cap, TLS requirement and rate limits — lives in the
**`emulator_api` database table** (a `key`/`value` store seeded by migration
`V20260803120000__cms_api_settings.sql`). `CmsApiSettings` reads it with a short
cache and falls back to the built-in config defaults when a row, the table, or the
database is unavailable — so the API is safe before the migration runs, and the
CMS can rotate keys or retune limits live without editing files or restarting.

## Two-tier access control

**Tier 1 — network admission.** Reuse the CIDR/allow-list machinery already in
`networking/gameserver/auth` (`CidrRange`, Cloudflare-aware `resolveClientIp`).
A configurable allow-list (default loopback) drops disallowed peers before any
parsing or auth work. This is a superset of today's `rcon.allowed`.

**Tier 2 — request authentication (HMAC, recommended).** Each CMS instance holds
a **key id + shared secret**. Every request carries:

- `X-Cms-Key` — the key id (selects the secret + scopes server-side)
- `X-Cms-Timestamp` — unix seconds; rejected if outside a small skew window
- `X-Cms-Nonce` — random per request; cached briefly to reject replays
- `X-Cms-Signature` — `HMAC-SHA256(secret, keyId + "\n" + timestamp + "\n" + nonce + "\n" + rawBody)`,
  compared in constant time

Why HMAC rather than a static bearer key: the secret never travels on the wire,
so the API is **safe over plaintext HTTP** when the CMS runs off-box; the
timestamp + nonce window defeats replay; and signing the body defeats tampering.
The building blocks already exist in `AccessTokenService` (HMAC-SHA256,
constant-time compare, auto-generated + persisted secret) and can be factored out
for reuse.

**Scopes.** Each key id maps to a set of allowed command scopes (e.g.
`economy:*`, `alerts:*`, `users:rank`). A key without the scope for a command is
rejected with 403 before dispatch — so, for example, a CMS key can be prevented
from calling `executecommand`. Starting with a single all-scope key is acceptable
for a first cut; the scope check is where least-privilege is enforced later.

**TLS.** When the CMS is remote, terminate TLS (the pipeline already supports
`SslHandler`) or place the API behind a TLS-terminating reverse proxy. HMAC keeps
the request authentic even without TLS, but TLS still protects payload
confidentiality (e.g. alert text, usernames).

## Validate & sanitize inputs

This is inherited for free by sharing the registry: every command runs the same
Jakarta Bean Validation constraints (`@Positive`, `@NotBlank`, `@Size`,
`@Pattern`, `@Min`/`@Max`) plus domain guards (`RconGrantGuard` currency ceilings,
`RconUserLookup` existence checks) regardless of transport. The API additionally
enforces a request body size cap (as RCON already does) and should add structured
**audit logging** — key id, command, target, source IP, outcome — which RCON
currently lacks.

## Rollout plan

1. **Step 1 (done):** extract dispatch into `CommandRegistry`; RCON delegates to
   it. Zero behavior change; all contract tests green.
2. **Step 2:** add `CmsApiHandler` + HMAC auth + scopes + per-key rate limit +
   audit log; publish `protocol/cms-api-contract.json` and mirror the RCON
   contract tests for the HTTP envelope.
3. **Ship both live.** Migrate CMS calls one command at a time; RCON remains the
   fallback throughout.
4. **Wind down RCON.** Once the CMS is fully on the API, disable the RCON listener
   via config (`rcon.enabled=false`). No command code is deleted — only the
   transport is turned off — so RCON can be re-enabled if needed.

## Reference

The full list of available commands, their payloads, validation rules and
response codes lives in [`cms-api-reference.md`](./cms-api-reference.md). It
applies to **both** transports today (the `data` object is identical); the RCON
column and the API column differ only in how the request is framed and
authenticated.
