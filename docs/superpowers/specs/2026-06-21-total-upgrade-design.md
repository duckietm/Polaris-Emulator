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
