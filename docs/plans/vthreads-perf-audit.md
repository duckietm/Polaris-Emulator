# Virtual threads + performance — audit & incremental plan (Java 25)

**Context:** Java 25. **JEP 491** (finalized JDK 24) means plain `synchronized` **no longer pins** virtual threads — the historical vthread blocker is gone. Audit confirms **three whole hazard categories are empty**: no JNI/`System.loadLibrary`, no `Object.wait()/notify()`, no `parallelStream`.

This is recon + a risk-ranked plan. Nothing applied yet — pick what to do.

---
## Part A — Virtual threads

### Current threading model
- `Emulator.getThreading()` = **`HabboExecutorService`** (a `ScheduledThreadPoolExecutor`) sized by `runtime.threads`. ~100+ files schedule fire-and-forget + delayed work through it. (`ThreadPooling.java:22`, `Emulator.java:142`)
- **DB pool (HikariCP)** = `runtime.threads * 2` — coupled to the same knob. (`Emulator.java:143`)
- **Packet handler:** plain-TCP pipeline runs handlers **inline on Netty IO worker threads** (`GameServer.java:55`). The **WebSocket** pipeline puts the handler on a `DefaultEventExecutorGroup`, optionally vthread-backed via `io.packet.handler.virtual` (default off). (`WebSocketChannelInitializer.java:46,70,131`)
- **WiredTickService:** fixed `max(2,min(8,cores))` **single-threaded platform** shards for room→shard ordering. (`WiredTickService.java:30`)
- **YoutubeManager** already uses `Executors.newVirtualThreadPerTaskExecutor()` correctly — **the model to copy.** (`YoutubeManager.java:95`)

### Hazards (ranked)
| # | Sev | Issue | Fix |
|---|---|---|---|
| **H1** | MED-HIGH | `io.packet.handler.virtual` backs a `DefaultEventExecutorGroup` with vthreads → **N permanent vthreads in event loops** (anti-pattern: a vthread pinned to a forever-loop gives no unmount benefit, and per-channel parallelism is still capped at N). | **Keep the flag OFF.** If true per-channel isolation under blocking JDBC is wanted, replace with per-channel ordered dispatch onto `newVirtualThreadPerTaskExecutor()` (one short vthread per packet, serialized per channel) — a real change, not just flipping the flag. |
| **H2** | MED | Central `getThreading()` pool is CPU-sized and is the real fan-out target; naive vthread swap doesn't fit a `ScheduledThreadPoolExecutor`. And **HikariCP at `runtime.threads*2` is a hard ceiling** — more vthreads just queue on DB connections. | Keep `getThreading()` as a **small platform scheduled pool** (timing) + add a **separate `newVirtualThreadPerTaskExecutor()`** for the `delay==0` blocking work. **Resize HikariCP independently first.** |
| **H3** | LOW-MED | WiredTickService shards are CPU-sized — but **correctly** (ordering, CPU-bound). | **Exclude** from any vthread migration; document as intentionally platform. |
| **H4** | LOW | Wired-engine `ThreadLocal`s (`EVENT_HANDLING_DEPTH`, `DEFERRED_EFFECT_EVENTS`, + 4 helpers) — bounded, `remove()`d at depth 0 in finally/AutoCloseable. Safe. | None needed. If ever rewritten vthread-native, prefer **`ScopedValue`** (JEP 487/506). |
| **H5** | LOW | YoutubeManager — correct vthread usage. | Reference shape for migrating other blocking-I/O fan-outs (auth, translation, image fetch). |
| **H6** | LOW | Busy-wait spin loops on bind futures (`Server.connect` `while(!isDone()){}`, `GameServer.initializeWebSocketServer`). Harmless on the platform startup thread, but a hard CPU-pin if ever on a vthread. | Replace with `channelFuture.sync()`/`.await()` — **cheap correctness win regardless of vthreads.** |
| **H7** | LOW | Thread-identity/name assumptions. | None — essentially absent. |

### Suggested vthread order (low → high risk)
1. **H6** — swap the two bind spin-loops to `.sync()`. Trivial, safe, do anytime.
2. **HikariCP decoupling** — size the DB pool independently of `runtime.threads`. Prereq for any concurrency increase.
3. **H2** — introduce a dedicated `newVirtualThreadPerTaskExecutor()` for fire-and-forget blocking tasks; migrate a *few* clearly-blocking call sites (auth HTTP, translation, image fetch) to it, à la YoutubeManager. Measure.
4. **H1** — only if WebSocket scaling is actually needed: redesign the per-channel dispatch. Biggest change; load-test first.
> **Do NOT** blanket-convert every executor to vthreads (would break WiredTickService ordering). Needs a thread dump under load to validate H1.

---
## Part B — Performance hotspots (independent of vthreads)
| # | Impact | Hotspot | Fix |
|---|---|---|---|
| **HS1** | **LARGE** | `RoomUnitManager.getHabboByRoomUnit` is an **O(n) linear scan**, called ~5×/unit/walk-step → **O(n²)/tick** in crowded rooms (`RoomUnit.java:165,197,223,285,345`). | Add a constant-time reverse index `roomUnitId → Habbo/Bot/Pet`, **or** pass the already-known `Habbo` into `cycle()`. |
| **HS2** | MED-HIGH | Room broadcast **flushes the channel per recipient** + fires an `OutgoingPacketEvent` plugin event **per packet per user** with no subscriber (`GameClient.java:122`, `PluginManager.java:358`). | `voidPromise` write + single flush at the end; guard the event behind `isRegistered(...)`. |
| **HS3** | MED | `ServerMessage.get()` **deep-copies the whole `ByteBuf` per recipient** → N copies of identical bytes per broadcast (`ServerMessage.java:180`). | `retainedDuplicate()` of one shared buffer (mind Netty refcounting + voidPromise). |
| **HS4** | LOW-MED | `getStackHeight` allocates **3 throwaway `THashSet`s per call** (`RoomItemManager.java:344,360`); runs on every place/move/state-change + pathfinding height checks. | Single `instanceof` pass over the cached tile set. |
| **HS5** | LOW-MED | `furnitureFitsAt` does **synchronized `Properties` lookups per occupied tile in a loop** (`RoomItemManager.java:1410-1411` → `ConfigurationManager.java:170`). | Hoist the config read **before** the loop (pattern already used at `RoomCycleManager.java:92`). |

### Suggested perf order
**HS1 first** (clear biggest win, crowded-room scaling), then **HS2/HS3** together (broadcast path — touch carefully, Netty refcounting), then **HS4/HS5** (cheap allocation/lock trims). Each is independent and `mvn test`-verifiable; HS2/HS3 want an in-room smoke test because they touch the live broadcast/encoder path.

---
## Bottom line
- **Safest immediate wins (low risk, do anytime):** H6 (spin→sync), HS1 (reverse index), HS4, HS5.
- **Needs measurement/load-test:** H1, H2, HS2, HS3, HikariCP resize.
- **Leave alone:** WiredTickService shards (H3), the wired ThreadLocals (H4 — correct as-is).

## Status — applied (build 4.2.62 → 4.2.64, branch java25-migration)
- ✅ **HS1** — O(1) `getHabboByRoomUnit(Id)` reverse index (4.2.62, `96950f4b`).
- ✅ **H6** — bind spin-loops → `awaitUninterruptibly()` (4.2.63, `5f8d1a27`).
- ✅ **HS4** — `getStackHeight` single pass over cached tile items (4.2.63).
- ✅ **HS5** — `furnitureFitsAt` hoisted the synchronized config read out of the per-tile loop (4.2.63).
- ✅ **HS2 (the safe half)** — `OutgoingPacketEvent` now only built+fired when a plugin is registered (4.2.64). NOTE: the recon's "single flush per broadcast" does NOT apply — each recipient is a *separate channel*, so the per-channel flush is inherent; `sendResponse` already used `voidPromise`.
- ⏸️ **HS3** (`ServerMessage.get()` `copy()` → `retainedDuplicate()`) — **NOT done**: changes the ByteBuf refcount lifecycle (who releases the shared buffer after N broadcasts), not covered by the test suite. Needs a deliberate refcount analysis + in-room/load smoke test before applying.
- ⏸️ **Virtual threads (H1/H2 + HikariCP)** — structural, needs a thread dump + load test; not started.
