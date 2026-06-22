# Phase 2 — Security & stability: audit & hardening

**Date:** 2026-06-22 · scope: non-wired (wired subsystem frozen by request).
Gate after each change: `mvn test` green (414) on JDK 25. Behaviour-preserving.

## Audit — what is already in place (verified, not changed)

The networking / packet-boundary layer is already comprehensively hardened; the
Phase 2 design's "largely in place" note holds. Confirmed by reading the code:

| Concern | Status | Evidence |
|---|---|---|
| Oversized inner game frame (DoS) | ✅ bounded | `GameByteFrameDecoder` = `LengthFieldBasedFrameDecoder(MAX_PACKET_LENGTH=417792, …)` → `TooLongFrameException`, handled in `GameMessageHandler.exceptionCaught` |
| Oversized WS frame / HTTP body | ✅ bounded | `WebSocketChannelInitializer`: `maxFramePayloadLength`, `HttpObjectAggregator`, `WebSocketFrameAggregator` all `MAX_FRAME_SIZE` |
| Malicious string length in a packet | ✅ clamped | `ClientMessage.readString()` masks to unsigned short and clamps `length` to `readableBytes()`; every read is `try/catch` → safe default |
| One bad packet crashing dispatch | ✅ isolated | `PacketManager.handlePacket` wraps handler in `try/catch(Exception)`; unknown/unauth packets rejected; per-handler ratelimit via `ConcurrentHashMap messageTimestamps` |
| Connection-level abuse | ✅ present | `GameMessageRateLimit`, `IdleTimeoutHandler(30,60)` in pipeline |
| Auth/HTTP/RCON abuse | ✅ present | `AuthRateLimiter`, Resilience4j rate-limiters/circuit-breakers, Hibernate Validator on RCON DTOs (`messages/rcon/*`) |
| One bad room-unit crashing a tick | ✅ isolated | `RoomCycleManager.processBots/processPets` use per-unit `try/catch` |
| Background job exceptions | ◑ logged | `HabboExecutorService.afterExecute` logs `Throwable` (note: `submit()`/scheduled futures swallow into the Future; `execute()` path logged) |

## Gap found & fixed — periodic room task could be silently cancelled

`Room implements Runnable` and is scheduled with
` scheduleAtFixedRate(this, 500, 500, MS) ` (`Room.java:658`). With
`scheduleAtFixedRate`, **any `Throwable` that escapes `run()` makes the executor
cancel the periodic task** — the room then stops cycling forever, silently
(users frozen, no further ticks), with no crash to alert an operator.

`run()` previously protected only `this.cycle()` and only against `Exception`:
- `this.save()` was **outside** the `try` → a `RuntimeException` from save (e.g.
  an NPE building moodlight data, or a non-`SQLException` from the pool) would
  escape `run()` and kill the task. (`save()` catches its own `SQLException`, so
  the common DB path was safe, but not all paths.)
- The `catch (Exception)` let a `Throwable`/`Error` (e.g. `StackOverflowError`)
  from `cycle()` escape and kill the task.

**Fix (`Room.run()`):** broadened the cycle guard to `catch (Throwable)` and
wrapped `save()` in its own `try/catch(Throwable)` with a room-id-tagged log.
Now no tick can ever silently cancel a room's own cycle. Behaviour in the normal
(no-throw) path is unchanged. Wired code untouched.

Other periodic tasks audited: `EmulatorDashboard.updateMetrics` (already
`try/catch(Exception)`, non-critical — left as-is); no `java.util.Timer`/
`TimerTask` anywhere (the worst "one task kills the thread" vector is absent).

## Verification
- `mvn test` green: 414 tests, 0 failures, JDK 25.
