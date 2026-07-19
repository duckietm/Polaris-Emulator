# Polaris Codebase Modernization Plan

**Status:** Approved — v4, audit baseline pinned and test-first gated
**Scope:** Developer experience, readability, maintainability, testability, and
safe performance work
**Compatibility target:** Existing Polaris/Arcturus plugins, clients, CMSs,
proxies, packet formats, schemas, and production deployments
**Assessment date:** 2026-07-19
**Assessment baseline:** `origin/dev` as observed during the audit at
`4323cd845f3d08268fc9bc6bd1c04f1d52181764` (PR #397 merge). The local
checkout used to edit this document is `dbf5255700a3811eb2df14e9643244a07db2419a`
and is 17 commits behind that baseline; statistics and current-work status below
refer to the upstream tree, not the older checkout.
**Deep audit:** 2026-07-19 — source review covered networking/packet handling,
database/SQL safety, concurrency/shared state, build/dependencies/CI, core
runtime/plugin behavior, and game-loop hot paths. Tier-1 items were traced
through source, but source-confirmed risk is distinguished below from dynamic
reproduction, conditional operator semantics, and work already resolved
upstream.
**Compatibility revalidation:** 2026-07-19 — recommendations were checked
against public fields, live collection semantics, event dispatch, economy
write paths, time helpers, manager lifecycle contracts, bundled dependencies,
and the assembled runnable jar. Recommendations that would have changed
plugin-visible behavior were corrected below.

## Executive summary

Polaris can be modernized safely without breaking its existing plugin system or
external integrations. The recommended approach is to freeze the existing
public surface and progressively replace its internal implementations.

The intended architecture is:

```text
Plugins and existing Polaris code
                |
                v
Emulator / GameEnvironment / Room
       unchanged compatibility facades
                |
                v
Runtime services / room components / repositories
                |
                v
Database / scheduler / event system / packet transport
```

`Emulator`, `GameEnvironment`, `Room`, and other established public classes
should remain available with their current signatures. Their methods can
delegate to smaller internal services. This allows new code to use explicit,
testable dependencies while old plugins continue to call the same APIs.

Modernization should not be measured only by reducing line counts or adopting
new Java syntax. For Polaris, a modern codebase means:

- Explicit ownership of services and lifecycle
- Narrow, testable dependencies
- Bounded and observable concurrency
- Clear separation between protocol, domain, and persistence code
- Compatibility tests guarding the legacy public API
- Incremental tooling that prevents new debt without rewriting the repository
- Evidence-led performance work using production-like measurements

**What the audits changed.** The audits confirmed the plan's structural
direction and most of its problem inventory, corrected the current-upstream
statistics and several impact statements, and surfaced a set of
**source-confirmed correctness risks** that no structural refactor addresses —
including a lock-order deadlock shape on the room dispose path, a
plugin-facing event contract (`@EventHandler` priority and cancellation) that
has never been honored, a success exit code on port-bind failure, and no
application policy for non-writable outbound channels. It also identified two
workstreams the v1 plan lacked entirely: **networking/transport** and the
**release pipeline**.
These are captured as a new **Phase −1 (test-first correctness triage)** plus
additions to the guardrails and vertical-slice phases. Correctness findings are
exempt from the "profile before changing" rule, but never from the mandatory
test-first rule.

## Compatibility requirements

The following are hard constraints for all modernization work:

- Existing plugin jars must continue to load without recompilation.
- Public and protected classes, constructors, methods, and fields must not be
  removed or have their signatures changed.
- Compatibility includes more than descriptors: preserve public object and
  collection identity, mutability, iteration/live-view behavior, return and
  exception behavior, lifecycle timing, event delivery/cancellation, and
  classloader/resource behavior unless an opt-in mode explicitly selects a
  corrected contract.
- Existing packet layouts, field order, types, event ordering, and externally
  observable side effects must remain compatible.
- CMSs, clients, proxies, and plugins must not be required to adopt a new API,
  schema, or payload.
- Existing `Emulator`, `GameEnvironment`, manager, and `Room` entry points must
  remain callable.
- Existing database bridges used by legacy plugins must remain available.
- Polaris-owned database migrations are allowed, but external systems must not
  need coordinated changes.

New internal implementations may be introduced behind the public surface.
Legacy methods may be deprecated after a replacement exists, but they should
remain as forwarders while compatibility is required.

Public mutable fields need special treatment. Unlike methods, fields cannot
transparently delegate to another object. They should remain canonical
compatibility storage until plugin read/write behavior has been audited. Avoid
creating mirrored fields whose values can diverge.

Zero breakage is the release criterion, but tests cannot prove the behavior of
plugins that are not available to the project. Confidence therefore requires
all of: ABI comparison, behavioral characterization, a representative corpus
of precompiled production/legacy plugins, assembled-jar tests, a canary
rollout, monitoring, and a rollback path. A change that fails any known plugin
fixture is breaking even if japicmp passes.

**The frozen surface is broader than `com.eu.habbo.*`.** The fat jar bundles
third-party libraries (`netty-all`, Gson, fastutil, HikariCP, commons-lang3,
commons-math3, …) on the classpath plugins load against, so slimming or
upgrading bundled libraries is itself an ABI-relevant change and must be gated
by the plugin smoke tests. The japicmp gate only guards `com.eu.habbo.*` —
see the `gnu.trove` finding (T1.22) for a concrete regression this blind spot
already allowed.

## Repository observations

All figures were re-measured on the current-upstream assessment baseline. Keep
the measurement commands with future plan revisions so approximate text-search
counts remain reproducible:

- 2,511 Java production files
- 226,238 production lines of Java
- 235 `*Test.java` classes; 249 test Java files including bases/helpers
- 3 `*IT.java` integration-test classes (`MigrationRunnerIT`,
  `DatabaseIntegrityAuditorIT`, and `PackagedJarContractIT`)
- 58 production classes of at least 500 lines
- 13 production classes of at least 1,000 lines
- 1,017 production classes importing `Emulator`
- 600 files calling `Emulator.getGameEnvironment()`
- 1,468 `getGameEnvironment()` call occurrences (1,469 textual occurrences
  including the method declaration)
- 182 production files acquiring a connection through the global database
- 472 `habbohotel` classes importing packet/message classes
- 396 distinct literal configuration keys across 640 direct accessor call
  sites
- 499 broad `catch (Exception)` / `catch (Throwable)` /
  `catch (RuntimeException)` occurrences
- 431 production-source uses of `synchronized`
- 220 wildcard imports

Key search-derived figures above use these conventions:

```bash
# Production files and physical lines
rg --files Emulator/src/main/java -g '*.java' | wc -l
rg --files Emulator/src/main/java -g '*.java' -0 | xargs -0 wc -l

# Direct global/config coupling
rg -l '^import com\.eu\.habbo\.Emulator;' \
  Emulator/src/main/java -g '*.java' | wc -l
rg -l 'Emulator\.getGameEnvironment\(\)' \
  Emulator/src/main/java -g '*.java' | wc -l
# Textual result is 1,469; subtract the one method declaration for 1,468 calls.
rg -o 'getGameEnvironment\(\)' Emulator/src/main/java -g '*.java' | wc -l
rg -l 'Emulator\.getDatabase\(\)\.getDataSource\(\)\.getConnection' \
  Emulator/src/main/java -g '*.java' | wc -l
rg -o 'Emulator\.getConfig\(\)\.get(?:Value|Int|Boolean|Double)\("[^"]+"' \
  Emulator/src/main/java -g '*.java' | wc -l
rg -o 'Emulator\.getConfig\(\)\.get(?:Value|Int|Boolean|Double)\("[^"]+"' \
  Emulator/src/main/java -g '*.java' |
  sed -E 's/.*\("([^"]+)"/\1/' | sort -u | wc -l

# Textual inventory counts (comments included where stated)
rg -o '\bsynchronized\b' Emulator/src/main/java -g '*.java' | wc -l
rg -o 'catch\s*\(\s*(?:final\s+)?(?:Exception|Throwable|RuntimeException)\b' \
  Emulator/src/main/java -g '*.java' | wc -l
rg -n '^import .*\*;' Emulator/src/main/java -g '*.java' | wc -l
rg -i -o 'select\s+\*' Emulator/src/main/java -g '*.java' | wc -l
```

These numbers do not individually prove that code is incorrect. They identify
where coupling, hidden dependencies, concurrency, and review cost are
concentrated.

Some of the largest classes are:

| Class | Approximate lines | Main concerns |
|---|---:|---|
| `Room` | 2,890 | State, loading, persistence, lifecycle, serialization, rights, wired settings, media, and compatibility methods |
| `RoomItemManager` | 2,486 | Indexing, loading, persistence, placement, movement, physics, special-type registration, and ownership |
| `CatalogManager` | 1,973 | Repository loading, cache management, purchases, vouchers, and orchestration |
| `WiredEngine` | 1,900 | Wired evaluation and execution |
| `RoomManager` | 1,694 | Cache, database access, search, room entry, moderation, model loading, and lifecycle |
| `WiredManager` | 1,441 | Wired global state and configuration |
| `RoomSpecialTypes` | 1,216 | Special-item classification and lookup |
| `ItemManager` | 1,218 | Item definitions and item-related orchestration |
| `RoomUnitManager` | 1,168 | Room-unit indexes, positioning, updates, and lifecycle |
| `RoomUserVariableManager` | 1,153 | Wired user-variable state |

The goal should not be a blanket rule that every class must be short. The goal
is for each class to have a coherent responsibility, a clear owner, and
testable behavior.

## Priority findings

Priority here means modernization sequencing and potential blast radius, not a
CVSS-style severity claim. Source-confirmed risks retain conservative language
until focused runtime tests establish their actual failure rate and impact.

| Priority | Area | Finding | Recommended action |
|---|---|---|---|
| P0 | Room lifecycle | Source-confirmed lock-order deadlock shape: cycle-driven self-dispose takes `loadLock` → Room monitor while external disposers take Room monitor → `loadLock` (T1.1) | Add a timeout-guarded reproduction and characterize lifecycle timing first; implement one canonical state machine/lock order without changing the public monitor contract |
| P0 | Plugin events | `@EventHandler` `priority`/`ignoreCancelled` are inert; dispatch order is `HashSet` iteration; `reload()` races `fireEvent()` (T1.3) | Preserve legacy dispatch by default; fix reload with immutable snapshots; offer corrected ordering/cancellation only as opt-in |
| P0 | Process lifecycle | Raw game/RCON bind failure exits successfully and can defeat failure-based supervisor policies; console REPL busy-spins at 100% CPU on EOF stdin (T1.4, T1.5) | Phase −1 Wave A |
| P0 | Networking | Code ignores Netty channel writability, so writes continue queuing for slow clients; blocking JDBC/16 MB file reads run on the shared 5-thread event loop (T1.6, T1.7) | Characterize packet delivery first; add explicit sustained-unwritable handling and offload blocking work |
| P0 | Release pipeline | Releases build with `-DskipTests` and are not gated on CI success (T1.24) | Phase −1 Wave C |
| Completed prerequisite | Plugin compatibility | Dual-baseline japicmp gate merged through PR #393 (`5a3d92ea`, gate commit `70f5c842`) | Keep green; supplement with behavioral plugin fixtures and bundled-classpath coverage |
| P0 | Room loading | Parent room-load task blocks on child tasks scheduled on the same bounded executor; unused outer connection held throughout; promotion load is fire-and-forget with an observable `promoted=false` window | `RoomLoader` extraction (Phase 2); unused connection + promotion fix pulled into Phase −1 |
| P0 | ABI blind spot | Hand-vendored partial `gnu.trove.THashMap` shadows the Trove namespace legacy plugins expect; japicmp does not guard `gnu.trove.*` (T1.22) | Compatibility design spike and dual-baseline plugin corpus before changing the shim |
| P1 | Database config | Operator `db.pool.maxsize`/`minsize` are silently overwritten by `runtime.threads * 2`; shipped migrations currently supply `runtime.threads=8`, but startup still needs a defensive fallback (T1.8) | Phase −1 Wave A |
| P1 | Economy | Many first-party callers bypass `EconomyLedger`; naively delegating public `addCredits`/`setCredits` would recurse or change absolute-vs-delta semantics (T1.11) | Characterize legacy methods; add a non-persisting internal balance-apply path; migrate first-party callers |
| P1 | Global state | `Emulator` is a composition root, lifecycle controller, service locator, configuration registry, and utility class | Introduce `PolarisRuntime` while preserving existing static methods as facades |
| P1 | Environment ownership | `GameEnvironment.dispose()` can abort partial-startup cleanup and omits at least the concrete `BotManager.dispose()` contract; other managers require an ownership audit rather than assumed disposal (T1.10) | Null-safe per-step cleanup plus a resource-ownership registry |
| P1 | Room design | `Room`/`RoomItemManager` duplicated operations: manager copies are **drifted and dead** (missing area-hide/invisibility/handitem hooks, zero production callers) | Consolidate onto Room's implementations as canonical |
| P1 | Layer boundaries | Domain classes import packets and many handlers perform persistence and business logic directly | Introduce application services and repositories one vertical slice at a time |
| P1 | Configuration | Config reload partially applies on malformed values, publishes arrays before filling them, and writes ~60 non-volatile statics; shutdown save-back may clobber manual DB edits only if live direct-SQL edits are part of the supported operator model (T1.9, T1.10) | Typed keys + subsystem binders that fix apply/publication semantics; decide and document configuration ownership before changing save-back |
| P2 | Testing | Room behavior is largely protected by source-text contract tests; no test directly constructs a `Room`; the three-class Failsafe layer remains narrow, and the index-migration integration test is not executed in CI | Package-private constructor seam, behavioral tests, broader IT coverage, and explicit CI execution of the index-migration test |
| P2 | Tooling | Formatting, dependency boundaries, warnings, and static analysis are not ratcheted | Compatibility-aware incremental guardrails |

## Audited defect and risk inventory (2026-07-19)

Every Tier-1 item below was source-traced on the pinned assessment baseline,
but that does not mean every failure was dynamically reproduced. Labels are
used deliberately:

- **Confirmed defect:** the incorrect behavior follows directly from a
  reachable source path.
- **Source-confirmed risk:** a credible availability/concurrency failure shape
  exists, but a deterministic runtime reproduction has not yet been added.
- **Conditional:** whether behavior is defective depends on an operator or
  compatibility contract that still needs an explicit decision.
- **Resolved upstream:** the v3 finding was valid for its older checkout but
  has since landed on `origin/dev`.

Tier 1 items are correctness/availability work — add the mandatory focused
reproduction or characterization test before fixing them, but do not wait for
performance profiling. Tier 2 items are performance headroom — visible in
code, but changes should be validated with JFR/metrics before and after.
Tier 3 items are build/DX. Efforts: S (hours), M (days), L (week+).

### Tier 1 — correctness findings, source-confirmed risks, and resolved work

#### Room lifecycle and locking

**T1.1 — Lock-order deadlock shape on room dispose (HIGH,
source-confirmed availability risk).**
`Room.run()` acquires `loadLock` (`Room.java:1148`) then runs `cycle()`
(`:1156`); when a room has been empty long enough, `RoomCycleManager.cycle()`
calls `room.dispose()` inline (`RoomCycleManager.java:122`), and `dispose()` is
`public synchronized` (Room monitor, `Room.java:963`) then takes `loadLock`
(`:964`) — so the cycle thread acquires **loadLock → Room monitor**.
RoomManager's external disposal paths do the opposite:
`RoomManager.clearInactiveRooms()`
(`RoomManager.java:439`, CleanerThread) and `unloadRoomsForHabbo()` (`:418`,
logout via `Habbo.java:251`) call `dispose()` directly — **Room monitor →
loadLock**. Two threads disposing the same room deadlock permanently, consume
two workers from the default 8-thread pool, and hold both monitors forever, so
every later touch of that room blocks behind them (cascading pool exhaustion).
Plugins can legally synchronize on the public `Room` object, although this
audit has no external-plugin corpus proving that they do. *Fix:* first capture
the existing idle-unload timing and callbacks. Then establish one lifecycle
state machine and canonical lock order. Enqueuing idle disposal to a sweeper is
one candidate, but do not prescribe it until parity tests show that its timing
and events remain compatible. Moving internal lifecycle work to a private lock
is desirable only if the public monitor's observable synchronization behavior
remains unchanged. Effort: M.

**T1.2 — `roomCycleTask` rescheduled outside `loadLock` → zombie cycle.**
The reschedule (`Room.java:651-656`) runs during `loadDataInternal` while
`loaded` is still false (flips at `:678`); `dispose()` only cancels inside
`if (this.loaded)` (`:973`, `:982-985`), and the field is `public` non-volatile
(`:136`). A dispose interleaving mid-load skips the cancel; the load then
installs a fresh periodic task on a room already torn down and removed from
`activeRooms` — a leaked 500 ms tick and use-after-dispose. *Fix:* move
cancel+reschedule inside the `loadLock` block that sets `loaded=true`,
re-checking disposed state; make the field volatile. Effort: S.

**T1.3 — Plugin event contract has never worked; reload races dispatch.**
`fireEvent` iterates `HashSet` fields (`PluginManager.java:74-75`) and invokes
every handler unconditionally (`:358-389`): it never reads
`EventHandler.priority()`, never reads `ignoreCancelled()`, and never checks
`event.isCancelled()` between handlers — the entire `EventPriority` enum
shipped to plugin authors is inert, and dispatch order is nondeterministic
hash-bucket order. Cancellation only works post-hoc (callers check
`isCancelled()` after all handlers ran — 89 call sites). Additionally,
`reload()` (`:433-443`) does `methods.clear()` then repopulates — reachable at
runtime via `:update_plugins` — while Netty/pool threads iterate the same
non-synchronized sets in `fireEvent` (CME / events dispatched against a
half-cleared table). *Fix:* build an immutable per-event-class handler list
sorted by priority slot, honor `ignoreCancelled`, swap atomically on reload
(volatile reference). **This changes observable ordering for existing plugins —
land only after the behavioral fixtures exist, behind a
`polaris.events.honor_priority` opt-in mode. Legacy dispatch remains the
default for the lifetime of this no-breaking plan; do not remove the mode or
flip the default without a separately approved breaking-release policy.
Reload safety can be fixed independently by atomically swapping immutable
handler snapshots while preserving the legacy all-handlers semantics.**
Effort: M.

**T1.4 — `System.exit(0)` on port-bind failure; busy-wait binds.**
The base raw-game/RCON `Server.connect()` logs and calls `System.exit(0)` on
bind failure (`networking/Server.java:84-93`). Exit code **0** can suppress
failure-based restart and alerting under supervisors configured to restart only
on failure; Docker, systemd, and Kubernetes behavior depends on their restart
policy and must not be generalized as "never restart". The WebSocket
`GameServer` variant (`GameServer.java:97-104`) also busy-waits and logs a bind
failure, but it **does not call `System.exit`**. Both bind paths busy-spin
(`while (!channelFuture.isDone()) {}`), burning a core until the bind resolves.
The raw-server exit also occurs inside networking code rather than surfacing to
`main()`'s catch blocks (`Emulator.java:266-278`). Partial-startup case: game
port binds, RCON bind fails → `System.exit(0)` while the game channel is
already accepting clients. *Fix:* throw a typed startup exception; composition
root decides the non-zero exit code; use `sync()`/`await()`. Effort: M.

**T1.5 — Console REPL busy-spins at 100% CPU on EOF stdin.**
With `console.mode` default true, the main thread loops on `readLine()`
(`Emulator.java:245-264`). When stdin is EOF (Docker without `-it`, systemd,
`nohup`), `readLine()` returns null immediately and the loop re-prints and
re-loops — an unthrottled hot spin pinning a core and flooding logs for the
life of every headless deployment. *Fix:* on EOF, break out (or park) instead
of re-looping. Effort: S.

#### Networking availability

**T1.6 — Outbound writes ignore Netty writability.**
`GameClient.sendResponse` guards only `channel.isOpen()`, writes with
`voidPromise()`, never checks `isWritable()` (`GameClient.java:110-130`); no
`WRITE_BUFFER_WATER_MARK` is configured anywhere (zero repo hits for
`isWritable`/`WriteBufferWaterMark`/`channelWritabilityChanged`), and the
allocator is unpooled (`networking/Server.java:42`). Netty has default
high/low water marks and toggles `Channel.isWritable()`, but writes made while
false are still queued; Polaris never reacts to that signal. A slow connection
can therefore retain growing outbound data and create availability pressure.
*Fix:* first characterize packet/event ordering and delivery. Configure
explicit water marks and disconnect on sustained unwritability, or apply only
coalescing proven safe for the affected packet type. Do not silently drop
arbitrary packets. Cover the policy with `EmbeddedChannel` and soak tests.
Effort: M.

**T1.7 — Blocking HTTP handlers run on the shared 5-thread I/O event loop.**
The WS listener and raw game server share one 5-thread worker group
(`WebSocketChannelInitializer.java:64-69`, `GameServer.java:87`; default
`io.workergroup.threads=5`). On that loop: `BadgeLeaderboardHttpHandler`
(JDBC GROUP BY/JOIN, `:118`, `:177-269`), `BadgeHttpHandler` (JDBC + PNG
decode, `:52`), `NitroSecureAssetHandler` (synchronous `Files.readAllBytes` up
to 16 MB + ECDH/AES, `:71`, `:160`), `EmuStatsHttpHandler` (`:37`, `:66`), and
`AccessTokenService.verify()` JDBC (`:87`). A handful of cache-cold requests
can occupy all five threads and freeze socket I/O for every connected player —
while `AuthHttpHandler.java:97` right next to them demonstrates the correct
dedicated-executor offload. *Fix:* route these handlers through the existing
`AUTH_EXECUTOR` or a dedicated offload group. Effort: M.

**T1.8 — Operator pool config silently discarded; fallback is incomplete.**
`DatabasePool` reads `db.pool.maxsize`/`db.pool.minsize`
(`DatabasePool.java:44-45`), then `Emulator.java:185-186` overwrites both with
`runtime.threads * 2` and a hard-coded `minimumIdle(10)` — the documented
knobs are inert. The shipped base migration currently supplies
`runtime.threads=8`, so a normal fresh or recognized migrated database does
not take a zero-size path. However, the key is not in
`registerStartupConfigDefaults()` (`Emulator.java:436`) or the example config;
an absent/corrupt database setting can still make `getInt` return 0 and Hikari
reject `setMaximumPoolSize(0)`. Also
`config example/config.ini.example:46` carries an unparseable value
(`db.pool.leak_detection_ms = 20000 set to 0 to disable`) that logs a parse
error every boot, and the example JWT secret at `:71` has a typo
(`hange-me-…`).
*Fix:* delete the override (or clamp `max(dbPoolMax, threads*2)`), register a
`runtime.threads` default, fix the example file. Effort: S.

#### Configuration and shutdown lifecycle

**T1.9 — Config reload partially applies and publishes unsafely.**
One malformed value in `globalOnConfigurationUpdated`
(`mapToInt(Integer::parseInt)` at `PluginManager.java:98`, `:244`, `:245`)
throws, `fireEvent` swallows it (`:363-366`), and **every assignment after the
throw is silently skipped** — dozens of settings left stale with one buried
stack trace. `RoomChatMessage.BANNED_BUBBLES` is published as a fresh array
and then filled in place (`PluginManager.java:150-157`) while chat threads
read it (`RoomChatMessage.java:60`, `:183`) — a window where bubble 0 is
wrongly banned/filtered. `RoomLayout.MAXIMUM_STEP_HEIGHT` is a non-volatile
`double` (torn-read-eligible under the JMM, `RoomLayout.java:21`,
written `PluginManager.java:89`). All ~60 reload-written statics are
non-volatile (full inventory in "Configuration improvements"). *Fix:* the
config binders must apply each key in its own try/catch and publish
fully-built immutable snapshots through volatile holders — this is now a
correctness requirement of that workstream, not a style preference. Immediate
minimal fix: build `BANNED_BUBBLES` locally and assign once; guard the three
parse sites. Effort: S (minimal) / M (binders).

**T1.10 — Shutdown teardown is fragile; lifecycle ownership is incomplete;
config save-back is conditional on the supported ownership model.**
`GameEnvironment.dispose()` (`GameEnvironment.java:134-153`) has no per-step
try/catch and dereferences `pointsScheduler` without a null guard (`:135`) —
if startup failed partway, the shutdown hook's dispose throws immediately and
**skips the remaining `GameEnvironment` teardown including
`habboManager.dispose()` (online-user saves)**. The outer composition root
continues later shutdown steps through `tryShutdown`, but cannot recover the
skipped manager disposals. Dispose order is not reverse-of-construction.
`BotManager` has a concrete `dispose()` method that is not called. Several
other constructed managers are not mentioned by teardown, but many appear to
own only in-memory state and do not expose a disposal contract; omission alone
is not a verified resource leak. Audit ownership of threads, connections,
files, caches, and scheduled tasks manager by manager before adding lifecycle
methods.
Separately, `ConfigurationManager.saveToDatabase()`
(`ConfigurationManager.java:264-281`, called at `Emulator.java:508-509`)
writes **every** in-memory property back with per-key `UPDATE … LIMIT 1`: under
an operator model that permits live direct SQL edits, a row changed while the
server runs can be overwritten with stale in-memory state, and keys without
existing rows no-op silently. Confirm the intended operator semantics before
calling this a defect; dirty tracking or compare-and-set is the compatible
design if such live edits are supported.
Also: rooms are disposed (`Emulator.java:504` →
`GameEnvironment.java:145` → `RoomManager.java:1571-1573`) **before** the pool
shuts down (`Emulator.java:511`), so cycle tasks still fire during the save
pass; and during shutdown `ThreadPooling.run(task)` executes inline while
`run(task, delay)` returns null and **silently drops** the task
(`ThreadPooling.java:30-60`) — late saves can vanish. *Fix:* null-guard +
per-step try/catch + registry-driven reverse-order disposal for resources with
an actual lifecycle contract; dirty-set tracking for config save-back if live
edits are supported; quiesce cycles before the room-save pass; log (don't
swallow) dropped shutdown tasks. Effort: M.

#### Database and economy integrity

**T1.11 — First-party credit mutations bypass the `EconomyLedger`; direct
public-method delegation is unsafe.**
The ledger (`EconomyLedger.apply`, `economy/EconomyLedger.java:45-53`) is an
idempotent single-writer (balance lock + `operation_id` dedup against
`logs_economy` + audit row), and `Habbo.giveCredits()` uses it
(`Habbo.java:296`). But `HabboInfo.addCredits()` (`HabboInfo.java:506-519`)
and `setCredits()` (`:500-504`) still mutate the balance and fire a direct
synchronous `UPDATE users SET … credits = ?` (`:733-745`) with no operation
id, no dedup, no audit row. Callers span redemption, catalog/marketplace,
trading, commands, and wired/furniture paths; this is broader than the three
initial examples. These writes are not idempotent or audited and can block the
calling thread.

Do **not** make the public methods simply call the ledger: `setCredits` is an
absolute assignment, `addCredits` is a delta, and the current ledger result
calls `HabboInfo.setCredits`, whose `run()` path performs another full
`users` update. The current `giveCredits`/ledger path therefore already
double-persists the resulting balance, while naive delegation from
`setCredits` would recurse or further change behavior. *Fix:* first
characterize the public methods' clamping, synchronous persistence, events,
exceptions, and return timing. Add a private or package-private method that
applies an already-persisted ledger balance to memory without saving again.
Migrate first-party mutations to explicit ledger operations with stable
operation IDs. Keep the public legacy method behavior until a non-recursive
compatibility bridge is proven by plugin fixtures. Effort: M.

**T1.12 — Catalog cache reload races unlocked readers.**
Reload mutates `giftFurnis` under `synchronized(this.giftFurnis)`
(`CatalogManager.java:651-664`) — but `CatalogBuyItemAsGiftEvent.java:108/123/137-138`,
`GiftConfigurationComposer.java:37-39`, `GiftCommand.java:67`,
`MassGiftCommand.java:68`, `RoomGiftCommand.java:54` read it with no lock
(only `SendGift.java:80` locks correctly). Same pattern for `clothing`
(`:676`), `prizes` (`:627`), `targetOffers` (`:594`), and
`catalogFeaturedPages` (reloaded under `synchronized(this)` at `:450`,
iterated lock-free in `CatalogPageComposer.java:67`,
`FrontPageFeaturedLayout.java:41`). `:update_catalog` during purchases →
CME/corrupt reads. These caches are exposed as `public final` mutable maps, so
replacing them with swapped references would break plugins that retain or
mutate the objects. *Fix:* keep every public map object and its live semantics.
Add internal snapshot/read methods that copy under the same lock and migrate
first-party readers, or take the corresponding lock at those reads. Build
reload data locally, then update the stable public map under its lock. Add
fixtures for public identity and mutability before changing internals.
Effort: M.

**T1.13 — LTD last-unit race resolves as `SERVER_ERROR`, not "sold out".**
Two buyers pass the `available()==0` check (`CatalogManager.java:1072`); one
pops the last serial, the other's `getNumber()` → `pop()` on an empty list
throws `NoSuchElementException` (`CatalogLimitedConfiguration.java:37-41`),
caught generically (`CatalogManager.java:1464-1467`) → wrong error to the
buyer. (`available()` at `:144-146` is also an unsynchronized read.) No
oversell — the atomic pop + conditional `WHERE user_id = 0` UPDATE
(`:87-95`) + finally-restore (`CatalogManager.java:1482-1490`) are correct.
*Fix:* `pollFirst()` returning empty-as-sold-out; synchronized/volatile
`available()`. Effort: S.

#### Shared-state and utility correctness

**T1.14 — First-party roller iteration races mutation; dispose uses a
different monitor.**
`RoomSpecialTypes.getRollers()` returns the raw `HashMap` (`RoomSpecialTypes.java:324-326`),
iterated every cycle (`RoomRollerManager.java:54`) while packet threads mutate
it under `synchronized(this.rollers)` (`:311-319`) — CME in the cycle. And
`dispose()` clears it under `synchronized(this)` (`:1182`, `:1189`), a
different monitor, so dispose isn't mutually excluded with add/remove either.
The public getter is part of the compatibility surface and may be used to
mutate or retain the live map; changing it to a defensive copy would be
breaking. *Fix:* preserve `getRollers()` exactly. Add an internal
`rollerSnapshot()` that copies while holding the rollers monitor, use that in
first-party cycle code, and clear under the same rollers monitor. Add a plugin
fixture that locks down public map identity and mutability. Effort: S.

**T1.15 — Static `SimpleDateFormat` instances shared across threads.**
`ModToolUserChatlogComposer.java:13/:43`, `ModToolIssueChatlogComposer.java:15/:91`,
`ModToolUserInfoComposer.java:21/:170`, public
`ModToolBan.dateFormat` (`ModToolBan.java:16`), and the private static
`VisitorBot.DATE_FORMAT` (`VisitorBot.java:16/:29`) use mutable
`SimpleDateFormat` instances from potentially concurrent callbacks.
`SimpleDateFormat` is not thread-safe; concurrent use can throw or produce
wrong timestamps. *Fix:* use immutable `DateTimeFormatter` internally. Keep
public fields such as `ModToolBan.dateFormat` for ABI/source compatibility,
while first-party code stops relying on them. Effort: S.

**T1.16 — Periodic-task failure observability is incomplete; `IOException` is
swallowed and the rejection handler is unused.**
`HabboExecutorService.guardPeriodicTask` catches `Exception`
(`HabboExecutorService.java:33-42`) and logs through `logFailure`, but
`logFailure` silently swallows an `IOException` from its own reporting path
(`:67-71`). The custom `RejectedExecutionHandlerImpl` is never installed
(`HabboExecutorService.java:19-21` never calls
`setRejectedExecutionHandler`). An `Error` escaping a periodic task will cause
`ScheduledThreadPoolExecutor` to suppress later executions, but the earlier
`StackOverflowError` narrative is not a reproduced Polaris defect. Catching
arbitrary `Throwable` and continuing is not a safe remedy: fatal VM,
thread-death, and linkage errors should still propagate, which also terminates
that periodic future. *Fix:* make the existing exception-reporting path
observable even when file logging fails; wire or delete the rejection handler;
add a focused test for the intended policy on recoverable exceptions. Only add
an `Error` classification boundary if a specific recoverable case and safe
continuation policy are demonstrated. Effort: S.

**T1.17 — Non-volatile lifecycle flags and room fields read across threads.**
The visibility-sensitive lifecycle fields
`Emulator.isReady/isShuttingDown/stopped` are plain `public static` mutable
fields (`Emulator.java:86-89`) — plugin-writable (anyone can set
`Emulator.isShuttingDown = true`) and read as control-flow gates in many places
(`ThreadPooling.java:33`, `CleanerThread.java:156`, …) with no visibility
guarantee. Other public mutable fields in the same area (`build`,
`buildTimestamp`, `debugging`) are compatibility concerns but are not all
equally strong control-flow defects and should not inherit the same severity
without a traced cross-thread consumer. `Room.layout` (`Room.java:142`; set
`:689`, nulled `:1064`) and `Room.roomSpecialTypes` (`:192`, reassigned `:548`)
are likewise non-volatile but read lock-free from cycle/pathfinding threads →
stale reads / transient NPE after dispose. *Fix:* characterize reflection and
direct-field behavior, then mark only visibility-sensitive fields volatile.
Adding `volatile` keeps the field descriptor but changes reflection and memory
semantics. Public fields must remain directly writable by plugins; internal
writers may use package-private helpers, but those helpers cannot prevent
external writes. Effort: S.

**T1.18 — RCON responses can truncate; heavy commands block a 2-thread loop.**
`RCONServerHandler.writeAndClose` writes with a void promise, double-flushes,
then closes immediately (`RCONServerHandler.java:103-108`) — larger JSON
responses truncate on a slow socket. Command bodies (catalog reload, mass
gives) execute inline on the 2-thread RCON loop (`RCONServer.java:38`,
`:110-144`). *Fix:* real promise + `ChannelFutureListener.CLOSE`; optionally
offload command execution. Effort: S.

**T1.19 — `WebSocketCodec` forwards any frame type as game bytes.**
`WebSocketCodec.java:18-20` retains and forwards any `WebSocketFrame`'s
payload — a `TextWebSocketFrame` is injected into the raw byte decoder as if
it were game protocol. Low impact, but an unnecessary fuzzing surface. *Fix:*
the frame aggregator precedes this codec, so forward only aggregated
`BinaryWebSocketFrame` payloads and reject text or any unexpected
continuation/control frame reaching this boundary. Effort: S.

**T1.20 — Time/duration utility defects.**
`Emulator.timeStringToSeconds` (`Emulator.java:649`) multiplies in `int` —
`"100 year"` overflows negative. Current callers feed subscription
add/remove durations and subscription payday intervals, not mute/ban
durations. It also rebuilds a HashMap + regex `Pattern` per call (same in
`modifyDate`, `:677`). `stringToDate` (`:716`) returns null on parse failure
(caller NPE trap). `getIntUnixTimestamp` (`:739`) and the `int` epoch fields
(`timeStarted`, online-time math) roll over in 2038. `SimpleDateFormat` is
allocated per call at `:480`/`:717` (safe but hot-path garbage). The public
signatures and legacy invalid-input behavior are compatibility contracts.
*Fix:* use `long` intermediates but keep `timeStringToSeconds(String): int`;
characterize and explicitly choose compatible saturation for out-of-range
values. Keep `stringToDate` returning null on invalid input and add a strict
internal parser instead. Keep `getIntUnixTimestamp(): int`; add a new/internal
long epoch API and migrate first-party calculations without changing the old
method's descriptor or behavior. Hoist immutable parsers and route internals
through `java.time`. Effort: S.

**T1.21 — `Room.updateItemState` NPE ordering.**
`Room.java:2592` null-checks `item` in the area-hide branch, then calls
`item.isLimited()` unguarded — a null item that isn't a controller item NPEs.
Fold into whichever PR touches the method. Effort: S.

#### Build, packaging, and release correctness

**T1.22 — Vendored partial `gnu.trove` shim is an ABI trap japicmp cannot see.**
`Emulator/src/main/java/gnu/trove/map/hash/THashMap.java` is a hand-written
shim (added 2026-07-06, commit `d5b85b88`), consumed only by
`HabboStats.cache`. The fat jar now ships a **partial** `gnu.trove.*`
namespace: legacy Arcturus plugins compiled against real bundled Trove resolve
`THashMap` but `NoClassDefFoundError` on `TIntObjectHashMap` and everything
else — and the ABI gate only guards `com.eu.habbo.*`. *Fix (decide
deliberately):* treat this as a compatibility design spike, not a quick
dependency cleanup. `HabboStats.cache` is a public final `THashMap` field, so
changing its descriptor to `HashMap`/fastutil is binary-breaking. Replacing
the shim with real Trove changes superclass and method behavior for plugins
compiled against released Polaris, even if it helps Morningstar plugins.
Preserve the current descriptor and released behavior until precompiled
fixtures against both Morningstar and Polaris exercise hierarchy, methods,
and additional Trove classes. Add a fat-jar namespace manifest and select a
bridge only after both baselines pass. Effort: M.

**T1.23 — RESOLVED UPSTREAM: assembled-jar `ServiceLoader` discovery.**
The v3 finding was valid for the older `jar-with-dependencies` build:
`META-INF/services` entries could be overwritten and the runnable jar omitted
the Flyway MySQL/MariaDB provider. PR #397, merged at `4323cd84` (implementation
commit `365ceb86`), replaced assembly with `maven-shade-plugin` **without
relocation**, added `ServicesResourceTransformer` and manifest handling, and
added `PackagedJarContractIT` plus its isolated probe. Current verification
discovers the MariaDB provider from the assembled runnable jar. Residual P2
hardening remains worthwhile: compare a golden duplicate-resource/classloading
manifest and exercise representative plugin resources, but do not list the
provider defect as open. Completed upstream.

**T1.24 — Releases ship untested and ungated.**
`build-release.yml:44` runs `mvn clean package -DskipTests`, triggers on push
to `main` independently of `ci.yml` (no `needs`/status gate), auto-bumps and
publishes. A change that compiles but fails `verify` can reach every hotel.
Also: no job `timeout-minutes` anywhere, no `concurrency` cancellation, no
CodeQL, no Failsafe `forkedProcessTimeoutInSeconds`; a stale `.gitlab-ci.yml`
pins JDK 21 (cannot compile `release=25`) and a private runner tag. *Fix:*
gate release on CI (or single workflow with `needs`), build releases with
tests (or promote the CI-built artifact), add timeouts/concurrency/CodeQL,
delete or fix the GitLab file; optionally add provenance attestation + SBOM.
Effort: S–M.

### Tier 2 — performance headroom (validate with JFR/metrics)

These are code-visible costs on hot paths. They are strong candidates, but land
them with before/after evidence per the concurrency-and-performance policy.

- **T2.1 Broadcast path re-encodes per recipient.** `ServerMessage.get()`
  returns `channelBuffer.copy()` per call (`ServerMessage.java:180-184`,
  unpooled heap `:36`); the encoder then copies again into the outbound buffer
  (`GameServerMessageEncoder.java:17-24`). A 50-user room broadcast = 50
  allocations + 100 memcopies. Concurrent `get()` also races `setInt(0, …)` on
  the shared buffer (benign today). *Fix:* frame length once at compose time;
  broadcast via `retainedDuplicate()`/`retainedSlice()`; keep `get()`'s
  copying contract for external ABI callers. Effort: M.
- **T2.2 `OutgoingPacketEvent` + full reflective scan per packet per
  recipient.** `sendResponse`/`sendResponses` allocate and fire the event
  unconditionally (`GameClient.java:110-155`); `fireEvent` linearly scans ~14
  handler `Method`s calling `getParameterTypes()` (defensive clone) each time
  (`PluginManager.java:358-368`, `:447-458`) — even with zero subscribers.
  *Fix:* registration-gated fast path (`isRegistered(...)`) + per-event-class
  dispatch table with cached `MethodHandle`s (same work as T1.3). Effort: M.
- **T2.3 One `write`+`flush` per composer.** `GameClient.java:127-128` flushes
  every packet; `sendResponses` already batches (`:150-153`). *Fix:* internal
  buffered-send + one flush per tick/batch. Effort: S/M.
- **T2.4 Reflective `newInstance` per incoming packet.**
  `PacketManager.java:207` does constructor lookup + instantiation per packet.
  Keep instance-per-packet (plugin handlers subclass `MessageHandler` with
  per-invocation fields) but cache the `Constructor`/`MethodHandle`.
  Effort: M.
- **T2.5 Channel-pinned dispatch = head-of-line blocking.**
  `GamePacketExecutionGroup` (`:14-16`, `DefaultEventExecutorGroup`,
  max(16, cores×2)) pins each channel to one thread for life; one slow query
  (catalog purchase runs all validation + inserts inline —
  `CatalogBuyItemEvent.java:46-296` → `CatalogManager.purchaseItem`
  `:1030-1498`) stalls every co-pinned client. *Fix:* offload durable writes
  after synchronous validation/reservation (keep `isPurchasingFurniture`
  guard); monitor queue latency. Effort: L.
- **T2.6 Disconnect performs 4–5 sequential synchronous DB saves under the
  `Habbo` monitor** (`Habbo.java:210-256`), on the pinned packet thread; a
  disconnect wave serializes DB latency. *Fix:* in-memory teardown under the
  monitor, coalesced persistence offloaded. Effort: M.
- **T2.7 Room-item persistence is connection-per-item under the items lock.**
  `HabboItem.run()` borrows a connection per single-row UPDATE/DELETE
  (`HabboItem.java:274-308`); `saveAllPendingItems()` loops items **while
  holding `synchronized(roomItems)`** (`RoomItemManager.java:1164-1172`);
  bundles likewise (`RoomBundleLayout.java:150,154`). Defeats
  `rewriteBatchedStatements=true` (`DatabasePool.java:78`); inventory already
  shows the correct batched pattern (`ItemsComponent.java:154-181`,
  `QueryDeleteHabboItems.java:27-35`). *Fix:* snapshot dirty items under the
  lock, release, batch on one connection. Effort: M.
- **T2.8 No dedicated JDBC executor.** ~393 `Emulator.getThreading().run(...)`
  sites put blocking saves on the same 8-thread scheduler as room/wired ticks;
  a stalled MariaDB (`socketTimeout=30s`, `DatabasePool.java:90`) freezes game
  logic. *Fix:* bounded I/O executor for persistence. Effort: M.
- **T2.9 Room join writes the DB user count under `roomUnitLock`**
  (`RoomUnitManager.java:157-164` → blocking UPDATE `Room.java:1237-1247`),
  serializing concurrent joins behind DB latency. *Fix:* bump in memory, flush
  async/coalesced. Effort: S.
- **T2.10 `unloadRoomsForHabbo` scans all active rooms per disconnect**
  (`RoomManager.java:406-421`) although the `roomsByOwner` index exists and is
  maintained by first-party insertion paths (`:424-436`). The public active
  rooms map can still be mutated by plugins without updating the secondary
  index. *Fix:* use the owner index as a fast path plus the legacy scan as
  fallback/reconciliation until externally inserted rooms are impossible by
  contract. Add parity coverage for rooms inserted through the public map.
  Effort: S.
- **T2.11 Navigator popular/public/tags recompute full scans per request**
  (`RoomManager.java:1092-1147`, per-room `String.split` allocations). *Fix:*
  short-TTL cache or incremental lists. Effort: S/M.
- **T2.12 `GameClientManager` lookups are O(n)** (`GameClientManager.java:123-133`
  and peers) despite the `authenticatedClients` userId index (`:18`, `:87-100`).
  The map is released before every teardown/session-resume state has completed,
  while the scan may still find a `Habbo`. *Fix:* use the map as a fast path
  and retain the existing scan as fallback until disposal, parking, and resume
  semantics are characterized. Add a username index only with the same
  lifecycle coverage. Effort: S.
- **T2.13 `SELECT *` on the hottest queries** (124 case-insensitive textual
  occurrences including comments; worst:
  `MarketPlace.java:274,278` per-buy, `ItemManager.java:731`,
  `RoomManager.java:158,284,390`) — schema-drift fragility + wasted transfer.
  *Fix:* pin columns on the top ~5. Effort: S–M.
- **T2.14 `sendComposers` copies the message list per recipient**
  (`RoomMessagingManager.java:55-61`, used by the cycle batch
  `RoomCycleManager.java:106-109`). Effort: S.
- **T2.15 `WiredEngine` allocates `new Random()` per random-effect selection**
  (`WiredEngine.java:923`) → `ThreadLocalRandom.current()`. Effort: S.
- **T2.16 Auth pool geometry throttles login bursts.**
  `AuthHttpHandler.java:37-49`: core=min(4,max), max=16, queue 512 — the pool
  only grows past 4 threads once 512 BCrypt logins are queued. *Fix:* eager
  core threads sized to BCrypt cost. Effort: S.
- **T2.17 No `SO_BACKLOG`; fixed 4 KB receive buffer**
  (`Server.java:76-81`, `GameServer.java:92-93`) — camera uploads (~408 KB,
  `GameByteFrameDecoder.java:15`) require roughly 102 4 KB buffer fills at the
  maximum frame size; those are read operations, not necessarily 102 distinct
  event-loop cycles. *Fix:* explicit backlog +
  `AdaptiveRecvByteBufAllocator`. Effort: S.
- **T2.18 Per-connection ECDH keygen/agreement on the shared event loop**
  (`WsHandshakeHandler.java:34-63`, `:109-116`) — reconnect storms concentrate
  crypto CPU on I/O threads. *Fix:* offload or pre-generate. Effort: S/M.
- **T2.19 Logback appenders are synchronous** (no `AsyncAppender`;
  `logback.xml`) — file I/O on game/Netty threads under load; only SlowQueries
  has `totalSizeCap`. `FileDebug` is **not dead**: when `debug.mode` is
  enabled, startup changes the root logger to DEBUG
  (`Emulator.java:220-225`). *Fix:* async-wrap with a deliberate queue/drop
  policy, preserve the runtime debug-mode behavior, and cap sizes. Effort:
  S–M.
- **T2.20 SMTP sends run on the shared game pool** (`SessionEndpoints.java:470`)
  with a 10 s timeout competing with game tasks → move to the auth pool.
  Effort: S.
- Note: `Emulator.getRandom()` is already `ThreadLocalRandom` (correct); the
  shared `secureRandom` for dice (`Emulator.java:630-631`) is thread-safe but a
  contention point under heavy gambling load — measure before touching.

### Tier 3 — build, dependency, and DX gaps

- **BOM/dependencyManagement absent:** import `netty-bom`,
  `testcontainers-bom`, `junit-bom`, `mockito-bom`; drop hand-synced
  per-artifact versions. Effort: S–M.
- **Non-reproducible builds:** no `project.build.outputTimestamp`, no Maven
  wrapper, no toolchains — pair with the planned Enforcer work. Effort: S.
- **Apparently unused dependencies:** test-scope `datafaker` has zero usages
  and can be removed after the test classpath is checked.
  `resilience4j-circuitbreaker` has zero first-party imports, but it is a
  runtime dependency on the plugin-visible fat-jar classpath; do not remove it
  merely because Polaris source does not import it. Require the bundled-class
  manifest and plugin corpus first. Effort: S.
- **`commons-math3` 3.6.1 is EOL (2016),** used in 10 files — inventory and
  migrate opportunistically; not urgent, not vulnerable. Effort: M (deferred).
- **`netty-all` aggregator** ships the entire Netty surface to plugins —
  slimming is possible but ABI-risky; treat as a documented trade-off gated by
  plugin smoke tests.
- **Hibernate-Validator EL is deliberately absent** — `RconPayloadValidator`
  uses `ParameterMessageInterpolator` (`RconPayloadValidator.java:16-18`).
  Watch-out: any future `Validation.buildDefaultValidatorFactory()` call will
  fail at bootstrap. Do not "fix" by adding an EL implementation.
- **`<parameters>true</parameters>` not set** — parameter names are dropped
  from bytecode (affects reflection diagnostics/debuggers). Effort: S.
- **Repo hygiene:** no `.editorconfig`, `CONTRIBUTING.md`, or PR/issue
  templates (add a PR template that repeats the ABI-freeze rule);
  `.gitattributes` normalizes exactly one file (add `* text=auto eol=lf` +
  binary rules); `.gitignore` globally ignores `*.txt`/`*.log`/`*.zip`
  (silently drops fixtures — scope to `logging/`) and contains a dead
  root-anchored `src/test/` line. Effort: S.
- **Build scripts are PowerShell-only** (`scripts/*.ps1`; CI runs a pwsh step
  to test a jar-copy helper) — provide `.sh` equivalents as `e2e/` already
  does, or fold into Maven. Effort: S.
- **`config.ini.example` contains 46 assignment lines while first-party code
  directly accesses 396 distinct literal keys;** `e2e/config.ini.template`
  contains 30 assignments and is a second, diverging example. These are raw
  inventories, not a valid coverage percentage: database-backed,
  environment-mapped, plugin-owned, and startup-file keys have different
  ownership. Generate an annotated reference from the typed registry once it
  exists (the registry design below makes this nearly free), and converge the
  two example files for the keys valid in each. Effort: M.
- **No coverage floor:** JaCoCo is report-only behind the opt-in profile — add
  a modest `check` ratchet. There are now 3 IT classes, but domain coverage
  remains narrow. `DatabaseIndexMigrationIntegrationTest` skips without
  `MIGRATION_TEST_DB_HOST` and no current CI job invokes it with that
  environment; add explicit executable MariaDB coverage rather than counting
  the skipped Surefire class. Effort: S–M.
- **Docs gaps:** no top-level architecture overview, no plugin-author guide
  (`POLARIS.md` covers the DB bridge specifically), no versioning/release
  policy (relevant given auto-patch-bump releases). Effort: M.

### Audited strengths — preserve unless new evidence contradicts them

Source review found these designs sound in their audited paths. This is not a
proof of absence of defects; preserve them unless tests, production evidence,
or a more complete trace shows a concrete problem:

- **Game-packet dispatch is off the event loop** with per-channel ordering
  preserved and the reasoning documented (`GameMessageHandler.java:58-65`).
- **The auth stack:** dedicated bounded pool with 503-on-reject
  (`AuthHttpHandler`), JWT with alg-confusion immunity, constant-time
  comparison, password-hash binding, token-version revocation, refresh-replay
  detection; Turnstile with timeouts; asset path-traversal protection;
  bounded auth rate limiting.
- **Money paths:** `EconomyLedger` (idempotent, audited, deduped);
  `CatalogPurchaseTransaction`, `MarketPlacePurchaseTransaction`,
  `RoomTradeTransaction` with correct autocommit/commit/rollback and row-count
  guards; LTD allocation cannot oversell and compensates on failure.
- **No confirmed player-input SQL injection was found in this audit** —
  parameterization discipline is strong (`SqlLikeEscaper`, whitelists, regex
  guards). The two reviewed string-assembled statements use admin/config-owned text
  (`ModToolManager.java:629`, `HallOfFame.java:33`) — pin them for
  defense-in-depth, but they are not vulnerabilities today, and the
  `HallOfFame` config-query contract is admin-trust-only by design.
- **Wired ticking** (`WiredTickService`): global 50 ms clock, per-room shard
  affinity, empty-room skip, lag skip-ahead, tiered slow-tick logging — this
  is the architectural model the room cycle should converge toward.
- **Pathfinding:** computed once per walk request, bounded A* (25 ms timeout),
  cycle polls a precomputed deque; tile lookups are cached O(1).
- **Migration/schema machinery:** `MigrationRunner`, `SchemaPreflight`
  (fail-closed adoption), `RuntimeSchemaValidator` + contract regeneration;
  `SlowQueryMonitor` (sanitized, fingerprinted, config-gated).
- **`ThreadPooling.shutDown()`** graceful-then-forced with instrumented
  results; `HabboExecutorService.afterExecute` + periodic guard (modulo the
  `Error` gap, T1.16).
- **`loadLock` discipline inside `Room`:** volatile load-state flags, futures
  captured under the lock and joined outside it.
- **Plugin classloader:** parent-first delegation (plugins cannot shadow
  emulator classes), closed on rejection, validated before instantiation.
- **Concurrent collections** are used correctly for the online-user maps,
  active-room maps, client registry, and wired collections;
  `getItemsOfType()` copies under lock.
- **Build/CI:** `release=25`, every dependency and plugin version-pinned,
  modern dependency set with no duplicate JSON/logging stacks, clean
  Surefire/Failsafe split, digest-pinned MariaDB images on the exact supported
  versions (10.11 + 11.4), least-privilege workflow permissions, and
  **dependabot is present** (weekly, maven + actions).

## Global state and runtime ownership

### Current shape

`Emulator` currently performs several unrelated jobs:

- Starts the application
- Constructs runtime services
- Owns database, configuration, threading, plugins, and servers
- Exposes services through static getters
- Coordinates shutdown
- Stores public lifecycle state
- Provides date, time, random, and formatting utilities

`GameEnvironment` similarly owns:

- Manager construction order
- Manager startup order
- Manager disposal order
- Several public schedulers
- Public accessors for hotel-domain managers

This makes dependencies invisible. A class that appears to need only a `Room`
can access the database, scheduler, plugin manager, packet layer, and every
hotel manager through global calls.

### Target design

Introduce a manually wired internal runtime object:

```java
final class PolarisRuntime {
    private final Database database;
    private final TaskScheduler scheduler;
    private final PluginManager plugins;
    private final HotelServices hotel;
    private final Clock clock;
}
```

The public API remains intact:

```java
public static GameEnvironment getGameEnvironment() {
    return runtime.hotel().legacyEnvironment();
}

public static Database getDatabase() {
    return runtime.database();
}
```

The exact implementation will depend on compatibility constraints, but the
important rule is that new internal code receives only what it needs:

```java
final class RoomLoader {
    RoomLoader(
        RoomRepository rooms,
        RoomItemRepository items,
        TaskScheduler scheduler,
        Clock clock
    ) {
        // ...
    }
}
```

### Migration strategy

1. Introduce `PolarisRuntime` without changing existing getters.
2. Move construction from `Emulator.main()` into a bootstrap/composition class.
3. Make one existing getter at a time delegate to the runtime.
4. Give newly extracted services explicit dependencies.
5. Convert existing internal classes opportunistically when they are already
   being changed.
6. Add architecture tests preventing new global dependencies in internal code.

Do not attempt to replace all 1,017 `Emulator` imports in one change.

### Lifecycle

Startup and shutdown should be represented as explicit ordered phases:

```text
Configuration
    -> Database and migrations
    -> Plugin compatibility layer
    -> Hotel services
    -> Network servers
```

Shutdown should happen in reverse order and isolate failures. If one manager
throws during disposal, remaining managers, schedulers, servers, and database
resources must still receive their shutdown calls.

Partially completed startup should also unwind already-created resources before
the process exits.

The audit made this concrete (T1.4, T1.10): today a failed port bind calls
`System.exit(0)` from inside a networking class, a partially started
environment NPEs out of `dispose()` and skips online-user saves, at least the
concrete `BotManager.dispose()` contract is omitted, lifecycle ownership of
the remaining managers is undocumented, and rooms save while their cycle
tasks still fire.
The lifecycle/registry work must fix these specific behaviors, not just
reorganize the code around them. The lifecycle flags themselves
(`isReady`/`isShuttingDown`/`stopped`) should become volatile only after
reflection/direct-field characterization, with internal writes going through
package-private helpers (T1.17). The public fields remain directly writable
for ABI and behavioral compatibility.

## Room modernization

### Current shape

`Room` is not merely a domain entity. It currently owns or coordinates:

- Room metadata and mutable public fields
- Database-backed construction
- Room loading and unloading
- Room-cycle scheduling
- Persistence
- Unit, item, trade, rights, chat, promotion, badge, wired, and cycle managers
- Packet serialization
- Guild lookup
- Ban and rights persistence
- Wired configuration
- Media and Jukebox state
- Item updates and ownership
- Posture calculations
- Plugin events

It already exposes approximately 15 manager components. This is the beginning
of a facade architecture, but it has not yet reached a single-source-of-truth
design.

The duplicated operations between `Room` and `RoomItemManager` —
`pickUpItem`, `updateItem`, `updateItemState` — were characterized during the
audit, and the situation is now settled rather than open:

- The manager copies are **stale drifted duplicates**:
  `RoomItemManager.updateItem` (`RoomItemManager.java:860`) lacks the
  `RoomAreaHideSupport` handling present in `Room.updateItem`
  (`Room.java:2570`), and `RoomItemManager.updateItemState` (`:881`) lacks the
  `RoomAreaHideSupport` / `RoomConfInvisSupport` / `RoomHanditemBlockSupport`
  hooks present in `Room.updateItemState` (`Room.java:2592`).
- The manager variants have **zero production callers** — every live call site
  uses the `Room` versions.
- Therefore the canonical direction is fixed: **Room's implementations are
  canonical**; the manager (or an extracted internal service) must receive
  Room's logic, and the manager entry points delegate to it. The "obvious"
  cleanup of pointing callers at the manager would silently break the
  area-hide/invisibility/handitem-block features. Characterization tests
  should still be written first, but the risk is now one-directional.

### Room loading correctness risk

`Room.startBackgroundLoad()` submits `loadDataInternal()` to the shared
scheduled executor (`Room.java:461-472`).

`loadDataInternal()` then submits item, rights, word-filter, bot, pet,
heightmap, and wired operations to that same executor and joins them from the
parent task (`Room.java:620`, `:644`). The pool is a single bounded
`ScheduledThreadPoolExecutor` (default `runtime.threads` = 8), so enough
concurrent parent loads occupy all workers with parents waiting on children
that can never be scheduled. This is a source-confirmed starvation risk. It
was not dynamically reproduced during this assessment.

The method also opens an outer database connection (`Room.java:543`) that is
**verified never used** inside the block — every child opens its own — while
being held for the entire load, reducing pool capacity exactly during
room-load bursts.

The promotion load (`Room.java:557-575`) is fire-and-forget outside the final
`allOf()` **and** sets `this.promoted = false` before conditionally restoring
it — an observable window where an entering user sees a promoted room as
unpromoted.

Two further verified lifecycle defects interact with loading and are inventory
items: the cycle-task reschedule happens outside `loadLock` mid-load (T1.2,
zombie cycle on disposed rooms) and the cycle/dispose lock-order inversion
(T1.1). The `RoomLoader`/`RoomLifecycle` designs below must resolve both, and
the Phase −1 triage fixes the acute cases first.

### Target Room structure

`Room` should remain the public compatibility facade. Its existing public
constructors, fields, methods, and manager getters should stay available.

Recommended internal components:

#### `RoomSnapshot`

An immutable representation of room metadata loaded from persistence.

```java
record RoomSnapshot(
    int id,
    int ownerId,
    String name,
    String description
    // ...
) {}
```

This record must remain internal. Java records are final and are not suitable
replacements for extensible plugin-facing classes.

#### `RoomRepository`

Owns persistence for:

- Room metadata
- Room bans
- Room rights
- Word filters
- Wired configuration
- Room save operations

Queries should not be hidden in getters such as `Room.getGuildId()`.

#### `RoomLoader`

Owns the room-load dependency graph:

```text
Room snapshot
    -> Layout
        -> Items
            -> Heightmap
            -> Wired data
        -> Bots
        -> Pets
    -> Rights
    -> Word filter
    -> Promotion
```

The implementation should:

- Avoid blocking an executor worker while waiting for children submitted to the
  same bounded pool
- Make promotion completion semantics explicit (and never publish a transient
  `promoted=false`)
- Propagate or aggregate failures predictably
- Avoid holding unnecessary database connections
- Expose timing and failure metrics
- Prevent a disposed room from publishing late load results (including never
  installing a cycle task for a room that was disposed mid-load)

#### `RoomLifecycle`

Owns:

- Load state
- Cycle scheduling
- Idle-cycle behavior
- Unload decisions
- Cancellation
- Disposal

`RoomLifecycle` inherits two hard requirements from the audit: a single
documented lock order for load/cycle/dispose (T1.1), and cycle-task
installation/cancellation that is atomic with the load/dispose state machine
(T1.2). The room cycle should also stop holding `loadLock` across packet
broadcast I/O — today the lock is held for the entire tick while the wired
path mutates the same room without taking it at all, so the coarse lock buys
contention without buying atomicity (`Room.java:1147-1162`,
`WiredTickService.java:475`).

#### `RoomPersistence`

Owns the large room update statement and related persistence decisions —
including converting the connection-per-item save loop into batched writes
that do not hold the items lock across JDBC round-trips (T2.7).

#### Additional focused components

- `RoomMediaSession`: YouTube, Jukebox, and media state
- `RoomWiredAccessPolicy`: wired configuration permissions
- `RoomPresentation`: packet serialization and presentation decisions
- `RoomPostureService`: sitting and laying calculations

### Constructor compatibility

Keep the current public `Room(ResultSet)` constructor:

```java
public Room(ResultSet row) {
    this(RoomSnapshot.from(row), LegacyRuntime.roomDependencies());
}

Room(RoomSnapshot snapshot, RoomDependencies dependencies) {
    // Package-private constructor for runtime wiring and tests.
}
```

This provides a test seam without removing or changing the plugin-facing
constructor.

### RoomItemManager decomposition

`RoomItemManager` should also remain as a public compatibility facade.

Its internal responsibilities can be divided into:

- `RoomItemIndex`: item collections, coordinate indexes, caches, and lookups
- `RoomItemRegistry`: special furniture-type registration
- `RoomItemPlacementService`: placement validation and placement effects
- `RoomItemMovementService`: movement, stacking, collision, and physics
- `RoomItemOwnershipService`: pickup, ejection, and ownership decisions
- `RoomItemPersistence`: item updates and removal persistence (batched — see
  T2.7)

After behavioral tests are in place, consolidate each duplicated
`Room`/`RoomItemManager` operation onto **Room's current logic** (the verified
canonical implementation — see "Current shape") and make both legacy entry
points delegate to it. While in `updateItemState`, fix the null-ordering NPE
(T1.21).

Do not expose every new component through a public getter. Public getters become
part of the compatibility surface and are difficult to remove later.

### Realistic outcome

`Room.java` may remain over 1,000 lines because hundreds of compatibility
methods and fields must remain. That is acceptable if most methods become
small, clear forwarders and the actual behavior lives in cohesive, tested
components.

## Networking and transport workstream (new)

The v1 plan had no networking coverage; the audit found the layer is mostly
well-architected at the dispatch level but has one availability-critical gap
(backpressure, T1.6), one misplaced-work gap (HTTP on the event loop, T1.7),
and a family of per-recipient hot-path costs (T2.1–T2.5, T2.14). Treat this as
its own vertical slice with the same measure-first discipline as other
performance work:

1. **Safety first (Tier 1):** write-buffer water marks + `isWritable()`
   handling with a tested sustained-unwritable policy (prefer disconnect over
   silently dropping arbitrary packets); offload the badge/asset/stats HTTP
   handlers to the existing executor pattern; RCON drain-before-close;
   WebSocket frame-type filtering.
2. **Broadcast efficiency (Tier 2, JFR-gated):** serialize-once +
   `retainedDuplicate()` per recipient; registration-gated
   `OutgoingPacketEvent`; per-tick flush coalescing; cached handler
   constructors.
3. **Topology (Tier 2, measured):** dispatch-queue latency metrics for the
   channel-pinned executor; offloading blocking handler work (catalog
   purchase, disconnect persistence) so pinned channels stop head-of-line
   blocking each other; auth pool geometry; adaptive receive buffers +
   explicit `SO_BACKLOG`; handshake crypto off the I/O loop.

All of this is internal to `com.eu.habbo.networking` / `GameClient` /
`ServerMessage` internals; the public composer/`sendResponse` contracts do not
change. `ServerMessage.get()` must keep returning a defensive copy for
external callers — add an internal broadcast path instead of changing its
semantics.

## Other recommended vertical slices

### Catalog purchases

Some incoming catalog handlers combine:

- Packet parsing
- Validation
- Catalog lookup
- Product rules
- Currency calculations
- Locking
- Database access
- Plugin events
- Packet output

The audit verified this end-to-end: `CatalogBuyItemEvent` (`:46-296`) →
`CatalogManager.purchaseItem` (`:1030-1498`) executes parsing, validation,
currency math, item INSERTs, LTD serial allocation, plugin events, and packet
composition inline on the channel-pinned packet thread.

Introduce command/result-oriented application services:

```java
CatalogPurchaseResult purchase(CatalogPurchaseCommand command);
```

The incoming handler should:

1. Parse the packet.
2. Build the command.
3. Invoke the service.
4. Translate the result to existing outgoing packets.

Preserve the existing order of validation, payment, item creation, plugin
events, and outgoing packets. The existing `CatalogPaymentService` is a useful
small-facade precedent, although it can later receive explicit dependencies.
The LTD sold-out race (T1.13) and the ledger-bypass callers (T1.11) should be
fixed as part of — or before — this slice. The stable-public-map/internal-read
snapshot work (T1.12) belongs to the same code area; do not swap out the
plugin-visible public map objects.

### RoomManager

Split internal responsibilities into:

- `RoomDirectory`: loaded-room cache and indexes
- `RoomRepository`: room persistence
- `RoomSearchService`: navigator/search queries (add the short-TTL caching,
  T2.11)
- `RoomEntryService`: entry checks and policy
- `RoomLifecycleService`: load/unload ownership (owner-index disposal, T2.10;
  the dispose-ordering rules from T1.1)
- `RoomModerationService`: room moderation operations
- `RoomModelRepository`: room-model loading

Keep `RoomManager` as the plugin-facing facade.

### Persistence write-path (new)

A persistence slice distinct from the repository extraction:

1. Batched room-item and bundle saves on a single connection (T2.7), following
   the in-tree inventory precedent.
2. A dedicated bounded JDBC executor so persistence stops competing with
   room/wired ticks for the same 8 threads (T2.8).
3. Complete the `EconomyLedger` migration without recursive public delegation:
   characterize `addCredits`/`setCredits`, add a non-persisting internal
   balance-apply method for ledger results, and move first-party callers to
   explicit ledger operations with stable operation IDs (T1.11).
4. Coalesced/async user-count and disconnect persistence (T2.6, T2.9).
5. Column-pinned queries on the hottest paths (T2.13).

### PluginManager

`PluginManager.globalOnConfigurationUpdated()` currently writes configuration
values into mutable static fields owned by many unrelated subsystems.

Introduce subsystem-specific binders:

```text
ConfigurationUpdated
    -> RoomConfigurationBinder
    -> WiredConfigurationBinder
    -> NetworkConfigurationBinder
    -> CatalogConfigurationBinder
```

Initially, these binders should continue setting the same legacy static fields.
Only replace those fields with immutable configuration objects after confirming
that plugins do not write them.

The binders inherit three correctness requirements from T1.9: per-key failure
isolation (one malformed value must not skip the remaining assignments),
publish-fully-built values only (no in-place array fills after publication),
and volatile/atomic holders for cross-thread visibility (including the
64-bit `MAXIMUM_STEP_HEIGHT`).

The audit produced the full inventory of reload-written statics the binder
work needs (~40 classes; the notable set): `ItemManager.RECYCLER_ENABLED`;
`MarketPlace.MARKETPLACE_ENABLED/_CURRENCY`;
`Messenger.SAVE_PRIVATE_CHATS/MAXIMUM_FRIENDS/_HC`;
`PacketManager.DEBUG_SHOW_PACKETS/MULTI_THREADED_PACKET_HANDLING`;
`Room.HABBO_CHAT_DELAY/MUTEAREA_CAN_WHISPER/MAXIMUM_BOTS/_PETS/_FURNI/
_POSTITNOTES/HAND_ITEM_TIME/IDLE_CYCLES/_KICK/ROLLERS_MAXIMUM_ROLL_AVATARS/
PREFIX_FORMAT`; `RoomChatMessage.SAVE_ROOM_CHATS/MAXIMUM_LENGTH/BANNED_BUBBLES`;
`RoomLayout.MAXIMUM_STEP_HEIGHT/ALLOW_FALLING`;
`RoomTrade.TRADING_ENABLED/_REQUIRES_PERK`;
`WordFilter.ENABLED_FRIENDCHAT/DEFAULT_REPLACEMENT`; `DiscountComposer.*`;
`BotManager.*` + `Bot.*`; `HabboInventory.MAXIMUM_ITEMS`;
`RoomManager.MAXIMUM_ROOMS_USER/_HC/HOME_ROOM_ID/SHOW_PUBLIC_IN_POPULAR_TAB`;
`WiredManager.*`; `WiredEffectSendSignal.MAX_SIGNAL_DEPTH`; `WiredEngine.*`
(~15 abuse/monitor knobs);
`NavigatorManager.MAXIMUM_RESULTS_PER_PAGE/CATEGORY_SORT_USING_ORDER_NUM`;
`TraxManager.LARGE/NORMAL_JUKEBOX_LIMIT`; `HabboManager.WELCOME_MESSAGE`;
`FloorPlanEditorSaveEvent.*`; `HotelViewRequestLTDAvailabilityEvent.*`;
`InteractionPostIt.STICKYPOLE_PREFIX_TEXT`; `TargetOffer.ACTIVE_TARGET_OFFER_ID`;
`CatalogManager.PURCHASE_COOLDOWN/SORT_USING_ORDERNUM`;
`AchievementManager.TALENTTRACK_ENABLED`; `InteractionRoller.NO_RULES`;
`CheckPetNameEvent.PET_NAME_LENGTH_MIN/MAX`;
`ChangeNameCheckUsernameEvent.VALID_CHARACTERS`;
`BuyRoomPromotionEvent.ROOM_PROMOTION_BADGE`;
`PetManager.MAXIMUM_PET_INVENTORY_SIZE`; `SubscriptionHabboClub.*` (~12);
`ClothingValidationManager.*` (7, incl. the `FIGUREDATA_URL` reload
side-effect); `NewNavigatorEventCategoriesComposer.CATEGORIES`;
`GiftConfigurationComposer.BOX_TYPES/RIBBON_TYPES`.

The plugin loader and event system also need behavioral compatibility tests for:

- Legacy plugin jar loading
- `plugin.json` interpretation
- Classloader and resource access
- Event registration
- Event ordering
- Cancellation
- Plugin enable/disable/disposal
- Exceptions thrown from plugin callbacks

Binary compatibility checks alone cannot protect these semantics — and the
event-dispatch finding (T1.3) proves the point: japicmp and the source-text
tests would both pass while `priority`/`ignoreCancelled` silently do nothing.
These fixtures are a **prerequisite** for the dispatcher fix, not optional
polish.

### Incoming handlers

Large incoming handlers should become protocol adapters:

```text
Parse packet
    -> Validate protocol-level shape
    -> Invoke application service
    -> Compose established response packets
```

Business services should return domain/application results rather than packet
composer instances. This keeps the domain testable without a network client.

### Persistence

Introduce repositories around high-change aggregates rather than attempting an
ORM rewrite:

- Room
- Catalog purchases
- User/session state
- Inventory
- Guilds
- Marketplace

Repositories should receive a data source or transaction context explicitly.
Existing plugin access through `Emulator.getDatabase()` must remain.

## Configuration improvements

Configuration currently has 396 distinct literal string keys across 640 direct
accessor call sites. The shipped example contains 46 assignments and the E2E
template contains 30, but that raw ratio is not configuration coverage because
not every direct key belongs in `config.ini`.

Introduce typed definitions:

```java
final class ConfigKeys {
    static final ConfigKey<Integer> ROOM_CYCLE_MS =
        ConfigKey.integer("hotel.room.cycle.ms", 500);
}
```

A typed configuration registry can provide:

- Key name
- Type
- Default value
- Validation
- Environment alias
- Deprecated aliases
- Documentation
- Whether a restart is required
- Whether live reload is supported

This can generate complete configuration documentation, distinguish
startup-file, environment, database-backed, and plugin-owned keys, and detect
malformed first-party values during startup. It should generate the applicable
parts of `config.ini.example` and converge the two example files without
pretending every runtime key belongs in the startup file. Unknown keys must
remain allowed because plugins can own configuration outside the first-party
registry; warn with provenance where useful, but never reject startup merely
because a key is unknown to Polaris.

Existing string-based configuration access should remain available to plugins.

Audited work this stream must absorb (details in T1.8, T1.9, T1.10):

- The Hikari pool-size override that makes `db.pool.*` inert, plus a defensive
  startup fallback for `runtime.threads` even though the shipped base migration
  currently supplies `8`.
- Partial application of reload on malformed values; unsafe publication of
  reload-written statics.
- The shutdown save-back that writes all keys blindly. Decide first whether
  live direct-SQL edits are part of the supported operator model; use dirty
  tracking/compare-and-set only if they are.
- The two example-file bugs (unparseable `leak_detection_ms` comment, typo'd
  JWT secret placeholder).

## Time and date handling

New internal code should use `java.time` and an injectable `Clock`.

Benefits include:

- Deterministic tests
- Explicit time zones
- Fewer formatting/parsing inconsistencies
- Removal of shared mutable date-format concerns

Existing `Emulator` date/time methods should remain and forward to the new
implementation. They may be deprecated, but not removed.

Audited defects and risks to address during this work (T1.15, T1.20): the
three shared
static `SimpleDateFormat` instances in mod-tool composers plus
`ModToolBan.dateFormat` and `VisitorBot.DATE_FORMAT` (thread-unsafe — replace
first-party internal use with `DateTimeFormatter` while preserving public
fields), `timeStringToSeconds` integer overflow in subscription/payday
durations, the 2038 limitation of `int` epoch helpers, and per-call
HashMap/Pattern/SimpleDateFormat allocations. Preserve the existing public
descriptors and `stringToDate` null-on-failure behavior; add strict/long
internal alternatives and migrate first-party callers.

## Concurrency and performance

Polaris has substantial concurrency-sensitive code, including room cycles,
wired ticks, schedulers, synchronized state, database loading, and plugin
callbacks.

Do not optimize this by intuition alone — **but distinguish two categories.**
The Tier-1 defects and source-confirmed risks (deadlock shape, races,
visibility gaps) have reachable file:line evidence; add their focused
reproductions/characterization and fix them without waiting for a JFR profile.
The "measure first" policy below governs *performance* work (Tier 2) and any
speculative lock restructuring beyond the inventoried items.

### First establish observability

Use Java Flight Recorder in representative hotel workloads to measure:

- Room-load latency
- Room-cycle duration
- Wired-tick duration
- Monitor contention
- Database-pool wait time
- Allocation pressure
- Garbage collection
- Scheduler queue delay
- Slow plugin callbacks
- Slow database queries

JFR is designed to capture CPU, allocation, I/O, and monitor-lock information
with production-appropriate overhead.

### Executor ownership

Avoid one shared executor for unrelated workloads.

Today a single 8-thread (default) `ScheduledThreadPoolExecutor` services room
cycles, wired support tasks, persistence, SMTP sends, and ad-hoc delayed work,
and the database pool is (incorrectly — T1.8) sized from the same
`runtime.threads` knob, so both saturate together.

Potential workload categories are:

- Ordered room/wired ticks
- Scheduled timers
- Blocking database work (see the dedicated-JDBC-executor item, T2.8)
- Background cache refresh
- Network-event execution
- Plugin callbacks where isolation is required

Each executor should have:

- An explicit owner
- A defined workload
- A shutdown policy
- Queue/backpressure behavior
- Metrics
- Named threads
- Error handling that logs recoverable task exceptions and lets fatal
  VM/thread/linkage errors propagate; do not claim a generic `Throwable`
  boundary can safely keep periodic work alive (T1.16)

### Virtual threads

Virtual threads may be useful for isolated blocking database operations after
measurement. They should not be applied wholesale to:

- Room cycles
- Wired ticks
- CPU-heavy calculations
- Strictly ordered state mutation
- Every task currently submitted to `ThreadPooling`

Database-pool capacity still limits useful database concurrency. Virtual
threads do not make an undersized database pool unlimited, and they should not
be pooled as though they were platform threads.

### Synchronization

The 431 production-source `synchronized` occurrences indicate that lock
behavior should be measured before changes.

For each high-contention area:

1. Identify the protected invariant.
2. Document lock ownership and ordering.
3. Measure actual contention.
4. Reduce lock scope or change the data flow only when evidence supports it.
5. Add concurrency tests for the protected invariant.

Do not mechanically replace synchronized blocks with concurrent collections or
atomic variables. Those constructs do not automatically preserve multi-field
invariants.

One audit-verified exception where test-first correctness work precedes
performance measurement is the Room dispose lock-order inversion (T1.1 — a
deadlock shape, not a contention question). Plugins can legally lock the
public `Room` monitor, so moving
lifecycle work to private locks is not automatically compatible: characterize
the monitor behavior and preserve synchronization semantics while establishing
one canonical internal state machine.

## Testing strategy

### Establish a green baseline first

The baseline was executed against a clean tree identical to current
`origin/dev` during the 2026-07-19 v4 revalidation:

- `mvn -B -f Emulator/pom.xml verify`: **683 Surefire tests, 0
  failures/errors, 2 skipped; 9 Failsafe integration tests, 0
  failures/errors/skips**
- Failsafe coverage: 7 `MigrationRunnerIT` tests, 1
  `DatabaseIntegrityAuditorIT` test, and 1 `PackagedJarContractIT` test
- Environment: Maven 3.9.11, JDK 26 compiling with `release=25`, Docker
  MariaDB 11.4
- Skipped Surefire tests:
  `MariaDbMigrationBackupIntegrationTest` and
  `DatabaseIndexMigrationIntegrationTest` require
  `MIGRATION_TEST_DB_HOST`. Current CI separately runs the backup test against
  a MariaDB service, but does not invoke the index-migration test with the
  required environment.

CI covers the supported MariaDB 10.11/11.4 migration matrix and currently also
tests MariaDB 12.3 compatibility. The dual-baseline ABI gate and packaged-jar
contract are merged and are part of current-upstream verification.

### Mandatory test-first parity gate

Every refactor starts with tests against the unchanged implementation. No
production behavior moves until the required tests exist and their pre-change
result is recorded.

1. **Behavior-preserving refactor:** add characterization tests and make them
   pass on current code. Run the same tests unchanged after the extraction.
2. **Intentional bug fix:** add the smallest reproduction that fails before
   the fix while all existing characterization tests stay green. Make only
   that reproduction pass; do not combine the fix with unrelated refactoring.
3. **Untestable legacy code:** first add the smallest package-private/internal
   seam with no observable behavior change. Prove that seam with parity tests,
   then perform the larger move in a later change.
4. **Validation ladder:** run the focused test, then the full unit suite.
   Run `verify` whenever database, concurrency, lifecycle, networking,
   packaging, or integration behavior is affected. Run assembled-jar tests for
   packaging/classloader/resource changes.
5. **Review rule:** do not delete, weaken, or rewrite a characterization test
   merely to fit the new implementation. A required layer that cannot be run
   makes the refactor non-merge-ready unless a release owner explicitly
   documents and accepts the gap.

Separate test-only and production changes into independently reviewable
commits/PRs where practical. This makes the before/after evidence auditable and
keeps bug fixes distinct from structural cleanup.

### Compatibility tests

Use binary, source/reflection, behavioral, packaged-runtime, and real-plugin
protection together.

Binary checks should prevent accidental removal or incompatible modification of:

- Public/protected classes
- Constructors
- Methods
- Fields
- Inheritance relationships

Also keep a reflection/signature manifest for public field modifiers and types;
the normal `com.eu.habbo.*` ABI comparison does not protect bundled namespaces
or all reflective expectations.

Behavioral fixtures should compile or load representative legacy plugins and
exercise:

- `Emulator` getters
- `GameEnvironment` managers
- `Room` methods and fields
- Plugin events — first capture the current legacy all-handlers behavior;
  separately test priority/`ignoreCancelled`/cancellation in the opt-in
  corrected mode. Do not assert a particular `HashSet` iteration order as a
  stable promise.
- Resource loading
- Database bridge access
- Plugin lifecycle
- **Bundled third-party classpath** (the `gnu.trove` regression class — a
  smoke plugin that references the bundled libs legacy plugins actually use)
- Public collection/field behavior, including identity, retained references,
  mutability, live views, and direct writes
- The assembled fat jar in an isolated classloader, including
  `ServiceLoader`, duplicate resources, plugin resources, and `plugin.json`

Maintain a representative corpus of precompiled Morningstar, released Polaris,
and real production plugins. CI fixtures reduce risk; canary deployment,
monitoring, and rollback remain required for plugins outside that corpus.

### Room characterization tests

Before moving Room behavior, capture:

- Loading success and failure
- Concurrent `startBackgroundLoad()` calls
- Disposal during loading (the T1.2 zombie-cycle interleaving specifically)
- Concurrent cycle-driven and external dispose (the T1.1 deadlock shape,
  as a timeout-guarded test)
- Item pickup ownership
- Item updates and packet broadcasts (including the area-hide/invisibility/
  handitem hooks that distinguish the canonical implementations)
- Rights checks
- Guild rights
- Plugin-event ordering and cancellation
- Save behavior
- Room-cycle scheduling
- Wired settings
- Media state

No test in the assessed tree directly constructs a `Room`. Several protections
read Java source text and check for specific expressions. Those tests can be
useful temporary tripwires, but they do not prove runtime behavior and make
refactoring unnecessarily fragile.

Replace source-text assertions with behavioral tests as test seams become
available. Keep intentional protocol-contract parsing tests where the source
shape itself is the contract.

### Repository integration tests

Use disposable MariaDB containers for:

- Repository query correctness
- Transaction behavior
- Migration compatibility
- Constraint and engine behavior
- Concurrent updates

Do not describe source-text tests, mocked repository tests, or
environment-skipped Surefire tests as executable database integration
coverage. The Failsafe layer now has three classes and 9 tests, but its domain
coverage remains narrow. Add a CI-backed MariaDB invocation for
`DatabaseIndexMigrationIntegrationTest`, or convert it to a self-contained
Testcontainers-backed `*IT`, so its idempotency/equivalent-index assertions
actually run.

## Developer-experience guardrails

Introduce guardrails gradually. Existing debt should be baselined so new
violations fail without forcing one giant cleanup.

### Plugin ABI gate

The `feature/plugin-abi-gate` prerequisite is complete: PR #393 merged the
dual japicmp baselines (Arcturus Morningstar 3.5.5 and released Polaris
v4.2.60) into `dev`. The accepted-divergence files are substantial and must be
reviewed semantically rather than described as empty. Keep the checks green on
every `mvn test`/`verify`.

Review the accepted-divergence lists whenever they change. The gate protects
binary compatibility of `com.eu.habbo.*` only — not event semantics, not
runtime behavior, and **not the bundled third-party namespace** (see T1.22).

### Architecture tests

Use ArchUnit frozen rules to prevent new violations while existing coupling is
removed incrementally.

Candidate rules:

- New `habbohotel` domain classes must not depend on outgoing packet classes.
- New incoming handlers must not acquire database connections directly.
- New internal services must not import `Emulator`.
- New mutable global state is forbidden outside documented compatibility
  facades.
- Package cycles must not increase.
- Persistence packages must not depend on packet packages.
- No new code under `src/main/java` outside `com.eu.habbo.*` and the
  documented `db.migration` package (prevents another vendored-namespace
  surprise).

### Release pipeline and CI hygiene (new)

- Gate the release workflow on CI success and stop building releases with
  `-DskipTests` (promote the CI-verified artifact, or run `verify` in the
  release job). (T1.24)
- Add `timeout-minutes` and a `concurrency` group with cancel-in-progress to
  both CI jobs; set Failsafe `forkedProcessTimeoutInSeconds`.
- Add a CodeQL workflow (the project maintains a substantial security-audit
  document but has no automated SAST).
- Delete or fix the stale `.gitlab-ci.yml` (JDK 21 cannot compile
  `release=25`).
- Optionally: build provenance attestation and a CycloneDX SBOM for release
  artifacts.

### Packaging and dependencies (new)

- Treat the vendored `gnu.trove` shim as a dual-baseline compatibility design
  spike; do not change `HabboStats.cache`'s descriptor or replace the shim
  until Morningstar and released-Polaris plugin fixtures pass (T1.22).
- **Completed through PR #397:** fat-jar packaging now uses shade **without
  relocation** plus `ServicesResourceTransformer`, and
  `PackagedJarContractIT` validates the assembled jar's Flyway MariaDB
  provider (T1.23). Retain residual hardening for a golden
  duplicate-resource/classloading manifest and representative plugin
  resources.
- Import BOMs (`netty`, `testcontainers`, `junit`, `mockito`) under
  `dependencyManagement`, but review the resolved dependency tree because BOMs
  can alter transitive versions. Remove test-only `datafaker` after validation;
  retain runtime `resilience4j-circuitbreaker` until the plugin-visible
  classpath corpus proves removal safe. Track `commons-math3` for eventual
  replacement; document the
  `netty-all`-is-plugin-visible trade-off; keep the deliberate
  Hibernate-Validator-without-EL configuration documented.
- Add `project.build.outputTimestamp` for reproducible jars; add the Maven
  wrapper alongside the planned Enforcer rules; consider
  `<parameters>true</parameters>`.

### Logging (new)

Wrap the four synchronous rolling file appenders in `AsyncAppender` (bounded
queue, with an explicit loss policy rather than assuming every event may be
dropped), preserve `FileDebug`'s runtime DEBUG behavior when `debug.mode` is
enabled, and add `totalSizeCap` to the three uncapped appenders. Keep the
`SlowQueries` appender as the model.

### Formatting

Use Spotless with a ratchet from a selected Git reference so only changed lines
or files are formatted.

Do not run a whole-repository formatter during architectural changes. Large
formatting diffs obscure behavior changes, complicate review, and increase
merge conflicts.

### Build environment

Use Maven Enforcer to make supported build requirements explicit:

- Required JDK version
- Minimum Maven version
- Dependency convergence, initially in report or controlled mode if necessary
- Banned dependencies where appropriate

Consider adding a Maven wrapper so contributors and automation can run the
expected Maven version consistently.

### Static analysis

Introduce the following in report/baseline mode:

- Compiler `-Xlint`
- Error Prone or SpotBugs
- Duplicate-code reporting where useful
- Dependency/cycle reporting

After the baseline is reviewed, make newly introduced violations fail CI.

### Error handling

Incrementally replace broad or empty catches (499 today) with:

- Specific exception types
- Context-rich logging
- Explicit failure results
- Propagation to the lifecycle owner
- Metrics where failures can repeat silently

Avoid a mechanical replacement that changes recovery behavior.

### Repository hygiene (new)

- `.editorconfig` (UTF-8, LF, indent rules) and `* text=auto eol=lf` in
  `.gitattributes` with binary rules for assets.
- Scope the global `*.txt`/`*.log`/`*.zip` ignores to `logging/` and build
  output; delete the dead root-anchored `src/test/` ignore line.
- `CONTRIBUTING.md` (JDK 25, `mvn verify`, Docker-for-IT, ABI rule) and a PR
  template that repeats the `com.eu.habbo.*` freeze.
- Shell equivalents (or Maven replacements) for the PowerShell-only build
  scripts, as `e2e/` already provides.
- Docs: a one-page architecture overview, a plugin-author guide (ABI rules,
  bundled libraries plugins may rely on, `plugin.json`), and a short
  versioning/release policy.

### Imports and style

Remove wildcard imports in touched files and let the formatter enforce that
rule for new changes. Avoid a repository-wide style-only diff unless it is a
dedicated, coordinated change.

## Safe Java modernization

Good uses of newer Java features include:

- Internal immutable snapshots represented as records
- Internal command/result types represented as records
- Pattern matching where it makes existing branches clearer
- `Comparator.comparingInt()` instead of subtraction-based comparisons
- `java.time`
- Text blocks for readable internal SQL where query ownership is clear
- Try-with-resources for all owned database resources

Unsafe or inappropriate conversions include:

- Converting plugin-facing classes to records
- Sealing legacy classes that plugins may extend
- Making plugin-facing classes or methods final
- Changing public collection types or mutability semantics
- Changing a return type even when the new type appears source-compatible
- Replacing public fields with getters
- Preview features (e.g. structured concurrency remains preview on JDK 25) —
  production hotels must not require `--enable-preview`

## Changes to avoid

The following would create high risk with limited near-term value:

- A big-bang dependency-injection framework migration
- Replacing all global calls in one PR
- A full ORM rewrite
- Renaming public packages
- Java module-system encapsulation of plugin-facing packages
- Whole-repository formatting mixed with behavior changes
- Wholesale conversion to virtual threads
- Replacing every manager with a new abstraction at once
- Changing packet contracts to make internal cleanup easier
- Requiring coordinated CMS, client, proxy, or plugin changes
- Removing legacy methods because a cleaner API now exists
- Replacing a public live collection/map with a defensive copy or a swapped
  backing object (`getRollers()` and CatalogManager caches are concrete risks)
- Exposing all new internal components through public getters
- Shade **relocation** of bundled libraries (breaks the plugin-visible
  classpath)
- Reworking the audited strengths (auth stack, wired sharding, transaction
  helpers, HV-without-EL, dispatch-off-event-loop) without new evidence
- Redirecting callers to the drifted `RoomItemManager` duplicates (they are
  missing feature hooks — Room's implementations are canonical)

## Recommended delivery roadmap

Phase numbers describe workstreams, not permission to bypass gates. Execution
starts with the recorded green baseline and the already-merged ABI and
packaged-jar protections. No Phase −1 production fix lands until its focused
reproduction, affected behavioral fixtures, and applicable plugin/package
checks exist.

### Phase −1: Test-first correctness triage

Small, independently shippable fixes for open Tier-1 defects and risks. Each
intentional fix starts with a failing reproduction plus passing parity tests;
where a failure is still only a source-confirmed shape, first add a
deterministic focused reproduction or record that it could not be reproduced
before assigning final severity. “Small” or descriptor-compatible does not by
itself mean behavior-neutral. No production fix starts without the applicable
evidence.

**Wave A — independent small fixes (one focused PR each or small batches):**

1. Console EOF spin (T1.5)
2. Bind-failure exit code + `sync()` instead of busy-wait (T1.4)
3. Remove the Hikari pool-size override; add a defensive `runtime.threads`
   fallback while preserving the shipped migration value; fix the two
   example-file bugs (T1.8)
4. Remove the unused outer connection in `loadDataInternal`; stop publishing
   the transient `promoted=false` (from the v1 room-loading finding)
5. Make periodic-task exception reporting observable even when its file write
   fails; wire or delete `RejectedExecutionHandlerImpl`; add a focused
   recoverable-exception policy test. Do not catch arbitrary `Error` merely to
   continue scheduling (T1.16)
6. Reflection/direct-field fixtures, then volatile lifecycle flags +
   `Room.layout`/`roomSpecialTypes` if compatible (T1.17)
7. Preserve public live `getRollers()`; add an internal locked snapshot and
   use one monitor for first-party iteration/mutation/dispose (T1.14)
8. Replace first-party use of all five audited shared
   `SimpleDateFormat` instances with `DateTimeFormatter`, preserving public
   compatibility fields (T1.15)
9. `BANNED_BUBBLES` publish-once + guard the three parse sites (T1.9 minimal)
10. LTD `pollFirst()` sold-out semantics (T1.13)
11. Preserve stable public catalog map objects; add locked internal snapshots
    or locked first-party reads (T1.12)
12. `unloadRoomsForHabbo` owner-index fast path with legacy-scan fallback and
    plugin-mutation parity coverage (T2.10)
13. RCON drain-before-close (T1.18); WebSocket frame filtering (T1.19)
14. `GameClientManager.getHabbo` authenticated-map fast path with the existing
    teardown/resume scan fallback (T2.12)
15. Outbound water marks + a tested sustained-unwritable policy; do not
    silently drop arbitrary packets (T1.6)
16. Offload the badge/asset/stats HTTP handlers (T1.7)
17. `timeStringToSeconds` long intermediate with compatible `int` return,
    legacy-null parser wrapper + strict internal parser, hoisted parsers; and
    the separately reproduced `updateItemState` NPE (T1.20, T1.21)
18. CI timeouts + concurrency groups; delete/fix `.gitlab-ci.yml` (part of
    T1.24)
19. Execute `DatabaseIndexMigrationIntegrationTest` against disposable
    MariaDB in CI, or convert it to a self-contained Testcontainers-backed
    `*IT`; assert it is not skipped

**Wave B — room lifecycle locking cluster (one coherent PR):**

1. Characterize idle-unload timing/events; implement one canonical lifecycle
   state machine and lock order. Use a sweeper queue/private lock only if those
   tests prove the observable monitor and timing behavior remains compatible
   (T1.1)
2. Cycle-task install/cancel atomic with load/dispose state (T1.2)
3. Shutdown quiesces cycles before the room-save pass; dropped shutdown tasks
   logged, not swallowed (T1.10 partial)
4. `GameEnvironment.dispose()` null-guards + per-step isolation; call the
   concrete omitted `BotManager.dispose()` and add other managers only after a
   resource-ownership audit proves they need lifecycle contracts (T1.10)
5. Timeout-guarded concurrent dispose/load/cycle tests

**Wave C — release pipeline (T1.24):** gate releases on CI, build with tests,
optional provenance/SBOM.

**Exit condition:** no known deadlock shape, no success-exit-on-failure, a
tested policy bounds sustained non-writable channels, releases cannot ship
unverified, and the reproduced small defects are closed without parity
regressions.

### Phase 0: Freeze compatibility

1. Keep the already-merged dual-baseline japicmp gate green.
2. Review accepted divergences and require semantic review for additions.
3. Add the dual-baseline Trove design-spike fixtures (T1.22). Extend the
   already-merged packaged-jar contract with golden duplicate-resource and
   representative plugin-resource/classloading coverage (T1.23 residual
   hardening).
4. Add representative legacy-plugin fixtures (including bundled-classpath
   smoke coverage).
5. Record event ordering and cancellation behavior as executable fixtures.
6. Make reload atomic while preserving legacy dispatch. Add corrected
   priority/cancellation behavior behind opt-in
   `polaris.events.honor_priority`; keep legacy as the default under this
   no-breaking plan.

**Exit condition:** Structural refactors cannot silently introduce new binary
plugin incompatibilities; critical plugin behaviors have executable fixtures;
legacy event behavior is frozen and corrected annotation semantics are
available only through an explicit opt-in.

### Phase 1: Make Room testable

1. Add only the smallest package-private constructor/test-builder seam needed
   to construct the current `Room` without global bootstrap; prove that the
   existing public constructor behavior is unchanged.
2. Characterize loading, disposal, item ownership, rights, events, saves,
   public monitor behavior, and public fields/manager getters.
3. Add `RoomSnapshot` behind the unchanged public `Room(ResultSet)` facade and
   rerun the same characterization suite unchanged.
4. Add Room dependency interfaces and fakes one boundary at a time, with the
   same parity requirement.

**Exit condition:** Core Room behavior can be exercised without constructing a
global emulator or reading Java source text.

### Phase 2: Correct Room loading

1. On the existing loader, add passing completion/order/failure fixtures and
   focused failing reproductions for nested-executor starvation, promotion
   publication, and dispose-during-load.
2. Extract `RoomLoader` without changing those results.
3. Remove same-executor parent/child blocking and satisfy the pre-existing
   reproductions.
4. Define promotion-load completion semantics within the established external
   behavior.
5. Add load timing and failure metrics.

(The unused-connection and transient-`promoted` fixes land earlier, in
Phase −1 Wave A.)

**Exit condition:** Room loading has explicit dependencies, bounded database
use, deterministic completion, and concurrency coverage.

### Phase 3: Extract Room persistence and lifecycle

1. Characterize save SQL effects/order, hidden getter queries, cycle timing,
   cancellation, and public dispose behavior on the current implementation.
2. Introduce `RoomRepository`.
3. Move save operations to `RoomPersistence` (batched writes — T2.7).
4. Remove hidden queries from getters.
5. Introduce `RoomLifecycle` (absorbing the Wave B lock-order rules).
6. Keep all public `Room` methods as forwarders and rerun the same suite after
   each move.

**Exit condition:** `Room` no longer directly owns most database or scheduling
logic.

### Phase 4: Consolidate item behavior

1. Characterize duplicated Room/item-manager operations (the audit already
   established direction: Room's implementations are canonical and the manager
   copies are dead).
2. Move Room's logic into the canonical internal implementation.
3. Split indexes, registry, ownership, movement, placement, and persistence.
4. Make both legacy entry points delegate to the canonical implementation.

**Exit condition:** Item behavior has one implementation and both public
facades remain compatible.

### Phase 5: Introduce runtime ownership

1. Characterize every `Emulator`/`GameEnvironment` getter and mutable field
   touched by the first slice, plus startup/shutdown order and partial unwind.
2. Add `PolarisRuntime`.
3. Extract bootstrap and lifecycle coordination (absorbing the T1.4/T1.10
   startup-unwind and shutdown-ordering fixes as explicit phase behavior).
4. Delegate only the characterized `Emulator` getters to the runtime.
5. Keep `GameEnvironment` as the legacy hotel-services facade over a service
   registry that owns construction and reverse-order, failure-isolated
   disposal of every resource that has a verified lifecycle contract.
6. Give new services explicit dependencies.
7. Add frozen architecture rules against new global usage.

**Exit condition:** New code no longer needs global service lookup, while
legacy plugins continue to use the same API.

### Phase 6: Apply vertical slices

Recommended order:

1. Networking and transport (broadcast efficiency + dispatch topology —
   Tier 2 items, JFR-gated)
2. Persistence write-path (batching, dedicated JDBC executor, ledger
   completion)
3. Catalog purchase orchestration
4. RoomManager
5. Configuration binding (typed keys + binders with the T1.9 semantics;
   generated config reference)
6. Incoming-handler persistence
7. Wired runtime ownership
8. Remaining high-change managers

Each slice starts with passing characterization tests in an earlier
reviewable change, then adds a compatibility facade, internal extraction, and
focused plus full validation.

### Phase 7: Ratchet quality and performance

1. Add changed-file formatting.
2. Freeze architectural violations.
3. Baseline static analysis; add CodeQL.
4. Add Maven environment enforcement, wrapper, BOMs, reproducible-build
   timestamp.
5. Add a JaCoCo check floor; grow Failsafe coverage beyond the current three
   IT classes; make index-migration integration coverage executable in CI.
6. Capture representative JFR profiles.
7. Optimize only measured bottlenecks (Tier 2 backlog).

## Suggested first pull requests

### Completed prerequisites — do not reopen

- PR #393 merged the dual-baseline plugin ABI gate.
- PR #397 merged shade-based packaged-jar hardening and the runnable-jar
  Flyway provider contract.
- Keep both gates green and build residual fixtures on top of them.

### PR 1 series: Phase −1 Wave A

- The independent small fixes above, as focused conventional-commit PRs
  (`fix(core)`, `fix(net)`, `fix(db)`, `fix(rooms)`, `fix(catalog)`, …)
- Each starts with its focused reproduction/parity fixture; descriptor
  stability alone is not sufficient

### PR 2: Room lifecycle locking cluster (Wave B)

- First land timeout-guarded lifecycle characterization tests
- Then address deadlock, zombie cycle, shutdown quiesce, and dispose hardening
  without changing the established unload/event/monitor behavior

### PR 3: Release pipeline gating (Wave C)

- CI-gated, test-running releases; CI timeouts/concurrency; remove stale
  GitLab config

### PR 4: Plugin behavior fixtures

- Load representative legacy plugin jars
- Exercise events (ordering, priority, cancellation), lifecycle, resources,
  database access, bundled classpath
- Establish behavior that binary comparison cannot protect

### PR 5: Event dispatcher correctness

- Reload-safe legacy dispatch plus opt-in priority-ordered,
  cancellation-aware dispatch behind `polaris.events.honor_priority`; legacy
  remains the default
- Includes the registration-gated fast path (T2.2) since it is the same code

### PR 6: Room construction and characterization seam

- Add the smallest package-private constructor/test builder without moving
  behavior
- Add test builders/fakes
- Add behavioral characterization tests that pass on the old implementation
- In a follow-up PR, add `RoomSnapshot` behind the unchanged constructor and
  require those tests to pass unchanged
- The seam/fixture PR does not move production behavior

### PR 7: Room loader extraction and correctness

- First add current-behavior fixtures and focused failing concurrent-loading
  reproductions
- Introduce `RoomLoader` while preserving the parity fixtures
- Correct nested executor starvation and satisfy the existing reproduction
- Define completion/failure semantics and add metrics

### PR 8: Room persistence extraction

- First characterize database effects and ordering
- Introduce `RoomRepository` and `RoomPersistence`
- Preserve `Room(ResultSet)`
- Keep every existing public method and require the pre-extraction tests to
  pass unchanged

### PR 9: Runtime composition root

- First characterize the selected static getters/fields and lifecycle order
- Introduce `PolarisRuntime`
- Move bootstrap wiring
- Delegate a small group of `Emulator` getters
- Add architecture rules for new internal code

## Success criteria

The modernization is succeeding when:

- Existing plugin jars continue to load without recompilation.
- Public fields and collections retain their descriptors, object identity,
  mutability, live-view behavior, and direct-write behavior where plugins can
  observe them.
- Existing clients, CMSs, proxies, and databases require no coordinated
  changes.
- `Room`, `Emulator`, and `GameEnvironment` remain compatible facades.
- New domain/application services can be unit tested without global bootstrap.
- Room loading cannot starve its own worker pool, and no dispose path can
  deadlock (verified by timeout-guarded concurrency tests).
- Legacy plugin event delivery remains the default; the documented
  priority/cancellation contract is available and tested only in the explicit
  corrected mode.
- A failed startup exits non-zero; a slow client cannot exhaust the heap;
  headless deployments idle at ~0% CPU.
- Operator configuration knobs are authoritative (no hidden overrides), reload
  is all-or-per-key (never partial-by-accident), and the documented
  configuration ownership model determines whether shutdown uses dirty
  tracking/compare-and-set for concurrent direct SQL edits.
- Releases cannot ship without a green `verify`.
- Database ownership and transaction boundaries are visible; all first-party
  currency mutations flow through the ledger while legacy public methods keep
  their characterized behavior.
- Incoming handlers are small protocol adapters.
- New global-state and layer violations fail automated checks.
- New warnings and static-analysis findings cannot increase unnoticed.
- Performance changes are backed by profiles and production-like tests.
- Architectural changes are delivered as reviewable, independently reversible
  pull requests.
- Every refactor has tests that passed on the unchanged implementation and pass
  unchanged after extraction; intentional fixes have separate failing
  reproductions.
- Release confidence includes ABI checks, real precompiled plugin fixtures,
  assembled-jar tests, canary rollout, monitoring, and rollback—not a claim
  that repository tests can prove every unseen plugin.

## Validation status

v1–v3 were based on the older local checkout and repository-wide source
inspection. v4 refreshed current-state claims against the pinned
current-upstream tree at `4323cd84`, corrected metrics and impact statements,
and separated confirmed defects, source-confirmed risks, conditional operator
semantics, and resolved upstream work. No production code was changed as part
of this plan update.

Dynamic baseline validation was performed against a clean tree identical to
the pinned upstream baseline:

- Maven 3.9.11 / JDK 26 (`release=25`):
  `mvn -B -f Emulator/pom.xml verify` completed successfully.
- Surefire: 683 tests, 0 failures, 0 errors, 2 skipped.
- Failsafe: 9 tests across `MigrationRunnerIT`,
  `DatabaseIntegrityAuditorIT`, and `PackagedJarContractIT`; 0 failures,
  errors, or skips, against Docker MariaDB 11.4.
- The assembled runnable-jar contract discovered Flyway's MariaDB provider,
  confirming that PR #397 resolved the old T1.23 ServiceLoader defect.
- The merged japicmp checks were included in the current-upstream build.

The two skipped Surefire tests require an externally supplied
`MIGRATION_TEST_DB_HOST`. Current CI separately invokes
`MariaDbMigrationBackupIntegrationTest` with a MariaDB service, but does not
invoke `DatabaseIndexMigrationIntegrationTest` with that environment. The
index-migration test therefore remains a real CI coverage gap until an
executable job or self-contained Testcontainers `*IT` proves otherwise.

The MariaDB 10.11 matrix was not rerun locally during the v4 refresh; CI owns
that supported-version check. No JFR profiles or production-plugin corpus were
available. The deadlock, zombie-cycle, nested-executor starvation, RCON
truncation, and slow-client findings remain source-confirmed shapes rather than
dynamically reproduced failures. Their focused test-first reproductions remain
mandatory before fixes. The configuration save-back concern remains
conditional until the supported ownership model for live direct-SQL edits is
documented.

## Reference documentation

- [Java 25 virtual threads](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)
- [Java records](https://docs.oracle.com/en/java/javase/25/language/records.html)
- [Java Flight Recorder performance troubleshooting](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)
- [Netty `Channel.isWritable()` contract](https://netty.io/4.2/api/io/netty/channel/Channel.html)
- [Netty write-buffer water marks](https://netty.io/4.2/api/io/netty/channel/WriteBufferWaterMark.html)
- [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Maven Enforcer Plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/)
- [Maven Shade service-resource merging](https://maven.apache.org/plugins/maven-shade-plugin/usage.html)
- [Maven reproducible builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html)
- [Spotless](https://github.com/diffplug/spotless)
- [Error Prone criteria](https://errorprone.info/docs/criteria)
