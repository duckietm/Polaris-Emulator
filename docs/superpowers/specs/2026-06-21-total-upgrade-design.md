# Total Upgrade — Design & Roadmap

**Date:** 2026-06-21
**Status:** Phase 0 implemented & verified; Phases 1–4 planned

## Context

Arcturus Morningstar Extended is a ~199k-line, ~2,263-file Java/Maven Habbo
emulator (Netty, MariaDB/HikariCP, Caffeine, Resilience4j). The goal is a
comprehensive ("total") upgrade across four dimensions: **runtime &
dependencies, performance & scalability, architecture & code quality, and
security & stability.**

### Constraints

- **Deployment context:** dev now → production later. Free to refactor, but
  final stability matters.
- **Compatibility (default = preserve, evolve only with a clean migration
  path):** Nitro client protocol, existing MariaDB schema, plugin API. No
  gratuitous breakage; versioned SQL + plugin compat shims when evolution is
  justified.
- **Strategy:** Approach A — *safety-net first, incremental*. Make it safe →
  modern → robust → fast → clean. Each step verifiable and reversible.

## Roadmap (sequenced sub-projects)

Each phase gets its own spec → plan → implementation cycle.

| Phase | Focus | Rationale |
|---|---|---|
| **0 — Foundations** | Coherent build on **Java 25**, CI that actually validates, dependency audit, baseline safety net + observability. | Everything else needs a green build + safety net. Low risk, high leverage. |
| **1 — Runtime** | Java 25 language features where they cut risk/boilerplate (records, pattern matching, sealed packet hierarchies), **GC tuning** (Generational ZGC + Compact Object Headers → lower RAM/pauses), **virtual threads** only where safe (background/DB jobs, **not** Netty event loops). | Sits on Phase 0's green build; mostly config + targeted change with measurable RAM/lag wins. |
| **2 — Security & stability** | Packet-boundary validation (Hibernate Validator already present), rate limiting / circuit breakers on RCON/HTTP/auth (Resilience4j already present), robust error handling so one bad packet/room can't crash the server. See `docs/wired_bug_audit.md`. | Protects production-readiness using libraries already in the pom. |
| **3 — Performance & scalability** | Profile real workloads (room tick loop, pathfinding, DB query hot paths, serialization), reduce allocations, batch DB writes, improve caching, raise concurrent-user ceiling. | Needs observability + safety net from earlier phases to measure & verify. |
| **4 — Architecture & quality** | Break up god-classes, clarify module boundaries, improve testability. | Highest-risk, ongoing; benefits most from the safety net; guided by pain found in earlier phases. |

## Phase 0 — Foundations (DONE & VERIFIED)

### Goal
A verifiable, coherent base: clean build on Java 25, a CI that truly validates,
dependencies under control, a safety net on the critical paths.

### Success criteria
- `mvn clean verify` passes on **JDK 25** locally and in CI. ✅
- The existing test suite **runs and gates** PR/merge (previously skipped). ✅
- JVM runtime-tuning flags for Phase 1 are confirmed valid on JDK 25. ✅
- A baseline exists to measure Phases 1 & 3 against. ◑ (metrics inventory done;
  live numbers require a running server+DB — captured at Phase 1 start)

### Workstreams

**A. Coherent build on Java 25**
- `Emulator/pom.xml`: replaced the inconsistent `source 19 / target 19 /
  release 21` (with `properties` claiming `25`) with a single source of truth:
  `maven.compiler.release=25`, inherited by the compiler plugin.
- Verified: `mvn clean compile` and full `verify` succeed on Temurin 25.0.3.

**B. CI that validates**
- New `.github/workflows/ci.yml`: on `pull_request` and pushes to non-`main`
  branches → `mvn -B clean verify` on **JDK 25 (Temurin)**. Red build blocks
  merge.
- `.github/workflows/build-release.yml`: bumped `setup-java` 21 → 25 and removed
  `-DskipTests`, so `main` releases always pass the suite.
- Removed the stale `.gitlab-ci.yml` (JDK 11, could not compile current code).

**C. Dependency audit**
- Available updates that are **pre-release** were rejected (Netty 5.0.0-Alpha2,
  slf4j 2.1.0-alpha1, jakarta.mail 2.1.0-M1) — staying on stables.
- Applied safe stable bumps: **HikariCP 7.0.2 → 7.1.0**, **MariaDB
  3.5.8 → 3.5.9**. Build re-verified green afterwards.
- Documented (not yet replaced — invasive, own phase): **Trove4j 3.0.3** (2012)
  and **jbcrypt 0.4** are old but stable.

**D. Safety net (moderate)** — *partially done; remainder is the next Phase 0 task*
- The existing **376 tests** now run inside the CI gate (Workstream B).
- TODO: add characterization tests for the high-risk hot paths Phases 1–4 will
  touch: room tick loop, packet decode/encode, pathfinding, handshake/login.

**E. Baseline observability**
- Inventory: `EmulatorStatsService`, `EmulatorNetworkStats`, `LatencyTracker`
  already exist. Live baseline numbers (heap, mean tick time, simulated
  players) to be captured at Phase 1 start against a running instance.

### Out of scope for Phase 0
Virtual threads, GC tuning, security hardening, performance optimization,
god-class decomposition. Phase 0 is **behavior-neutral** — it only prepares the
ground.

### Verification evidence (2026-06-21, Temurin 25.0.3)
- `mvn clean compile` → BUILD SUCCESS, 0 errors.
- `mvn clean verify` → **Tests run: 376, Failures: 0, Errors: 0, Skipped: 0**,
  both `Habbo-4.2.45.jar` and `Habbo-4.2.45-jar-with-dependencies.jar` built,
  BUILD SUCCESS.
- `java -XX:+UseZGC -XX:+UseCompactObjectHeaders` accepted on JDK 25 (Phase 1
  tuning is viable).

---

## Progress log — autonomous run (2026-06-21)

### Done & verified (after the initial Phase 0 commit)

**Phase 1 (started) — GC tuning in start scripts**
- `Emulator/Example_Start/Windows/emulator.cmd` and `.../Linux/emulator`: added
  `-XX:+UseZGC -XX:+UseCompactObjectHeaders`, fixed `-Dfile.encoding=UTF-8`, and
  corrected the stale jar name (`Habbo-3.5.3` → `Habbo-4.2.45`). Config-only,
  flags verified accepted on Temurin 25.0.3. Expected effect: lower GC pauses
  and lower RAM with many in-memory furni/items/users.

**Phase 0-D (partial) — characterization tests on the codec hot path**
- `GameCodecCharacterizationTest` (4 tests, Netty `EmbeddedChannel`): locks in
  the inbound `[header:short][body]` decode, the outbound
  `[length:int][header:short][body]` encode, the header-only empty-body case,
  and a full encode→decode round-trip.
- `PacketSerializationCharacterizationTest` (7 tests): pins `ClientMessage`'s
  defensive reads — most importantly that a bogus declared string length
  **clamps to available bytes and never throws or desyncs** — plus
  `ServerMessage` length-prefix framing.
- Full suite now **387 tests, 0 failures** on JDK 25 (`mvn clean verify`).

**Phase 0-D (round 2) — config seam + more characterization**
- `EmulatorTestSupport` (test-only): installs a DB-free `ConfigurationManager`
  into the private static `Emulator.config` via reflection — the minimal first
  step of an Emulator test harness, with **no production change**. This is what
  unblocks future tests of config-dependent classes without a database.
- `EmulatorConfigBootstrapTest`: proves the seam and characterizes
  `ConfigurationManager` default handling.
- `RoomLayoutGeometryCharacterizationTest`: pins RoomLayout's pure static
  geometry helpers (`getRectangle`, `squareInSquare`, `pointInSquare`,
  `tilesAdjecent`).
- Full suite now **393 tests, 0 failures** on JDK 25.

### Findings to act on (not changed — need a human/gameplay call)
- **`ConfigurationManager.getBoolean(key, default)` ignores `default` for an
  absent key** and always returns `false` (the default is only honoured while
  loading or on a parse error). Confirmed by a passing characterization test.
  This means calls like `getBoolean("pathfinder.step.allow.falling", true)`
  resolve to **false** on any hotel that doesn't set the key. `getInt`/`getDouble`
  do NOT share this bug. Fixing it is a behaviour change (gameplay flags would
  flip) — flagged for a conscious decision, not done autonomously.

### Deferred — needs your involvement / a running instance

These were intentionally NOT done autonomously because the existing unit tests
cannot prove them safe, and getting them wrong is costly:

1. **Virtual threads on the `GamePacketHandler` executor**
   (`WebSocketChannelInitializer`, the `DefaultEventExecutorGroup`). Netty's
   `EventExecutorGroup` guarantees **per-channel ordering**; naively swapping in
   `Executors.newVirtualThreadPerTaskExecutor()` can reorder/parallelise packets
   for the same client → races and, in a Habbo economy, item dupes. This needs a
   real Nitro client + load test to validate. **Requires:** integration testing.
   (Note: pathfinding is CPU-bound A*, so virtual threads give it no benefit —
   not a candidate.)

2. **Phase 3 performance work** (room tick loop, pathfinding, DB query hot
   paths). Measuring real gains needs the emulator running against a **MariaDB**
   instance with representative data — not available in this environment.
   **Requires:** a running hotel + DB to baseline and profile.

3. **Characterization tests for the room tick loop & pathfinding.**
   `PathfinderImpl` resolves config in a **static initializer**
   (`Emulator.getConfig()` at class-load) and needs a loaded `Room`; the room
   cycle needs the full `Emulator` singleton. A proper test needs a bootstrap
   harness (or light dependency-injection refactor) first. **Requires:** a test
   harness for the `Emulator` singleton.

4. **Phase 4 god-class decomposition** (e.g. `Room`, `Emulator`) and **Phase 2**
   beyond what already exists. The codebase already has extensive packet-boundary
   guards (37+ `*InputGuard`/`*Contract`) and custom rate limiting
   (`AuthRateLimiter`, `GameMessageRateLimit`), so Phase 2 is largely in place.
   Invasive refactors are high-risk and should follow the harness in (3).

### Observation: unused declared dependencies
The pom declares **Hibernate Validator** and **Resilience4j**, but the code uses
custom guards and a custom rate limiter instead — these libraries appear unused.
Not removed (needs confirmation they aren't loaded reflectively/by plugins);
flagged for a future cleanup pass.

### Phase 3 attempt — DB clone OK, runtime boot blocked by the agent shell
- **DB clone ready:** the live `next` DB was cloned into an isolated `amx_test`
  (255 tables, `habbo` granted, GUI+WS disabled in the clone only). Run config
  staged at `Emulator/target/run-baseline/config.ini` (ports 3100/3101 so the
  real hotel is untouched). The clone connects fine — boot reaches DB connect
  (~180ms), config-from-DB, thread pool, plugins, texts.
- **Boot blocker:** constructing the Netty `GameServer` fails — `Selector.open()`
  → "Unable to establish loopback connection" → `sun.nio.ch.UnixDomainSockets.connect0`
  "Invalid argument".
- **Root cause = the agent's execution shell, NOT the code or JDK 25.** A bare
  `Selector.open()` fails identically on **JDK 21 and JDK 25**; forcing the legacy
  `WindowsSelectorProvider` doesn't help. The shell context can't create the
  AF_UNIX self-pipe the NIO selector needs. In a normal terminal it works (that's
  why the real hotel boots). **This does NOT implicate the Java 25 upgrade.**
- **Hand-off:** the user must run the baseline in a normal terminal. Command:
  `cd Emulator/target/run-baseline && "<temurin25>/bin/java" -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xms256m -Xmx2g -XX:+UseZGC -XX:+UseCompactObjectHeaders -jar ../Habbo-4.2.45-jar-with-dependencies.jar`
  Watch for `Memory: x/yMB` and `System launched in: Nms`. Drop the clone later
  with `DROP DATABASE amx_test;`.

### Done this round — false-ERROR config logging fixed
`ConfigurationManager.getValue` logged ERROR for every absent optional key (the
ones with a caller default); `getInt/getBoolean/getDouble` all route through it,
so optional settings spammed the ERROR stream on boot (`enc.e/enc.n/enc.d`, etc.,
seen live). Downgraded to DEBUG — return value unchanged, real errors stay
visible. Guarded by `ConfigurationManagerLoggingTest` (logback ListAppender).

### Next safe seam to propose — RoomLayout.fromHeightmap()
`RoomLayout.parse()` is a pure function of `heightmap` + door fields (no `Room`
needed) encoding real logic (`x`=invalid, `0-9`=height, `A-Z`=10+letter, door
front-tile adjustment). It's worth characterizing, but `RoomLayout`'s only
constructors take a JDBC `ResultSet` / `RoomLayoutData(ResultSet)`, so a test
needs Mockito (absent) or `Unsafe` reflection — both disproportionate. A small
additive seam (a `static RoomLayout fromHeightmap(name, heightmap, doorX, doorY,
doorZ, dir)` factory) would unlock clean tests AND decouple the class from the DB
schema. Left as a proposal — it changes a core class's API, so it's a human call.
