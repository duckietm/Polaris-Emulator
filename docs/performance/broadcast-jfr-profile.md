# Broadcast JFR profile

This profile measures the established room-broadcast encoding path with one
representative 1,024-byte `ServerMessage`, 50 recipients, and 5,000 broadcasts.
It uses the real `GameServerMessageEncoder` through Netty's
`EmbeddedChannel`, producing 250,000 recipient frames.

Run it from the repository root with JDK 25:

```bash
mvn -f Emulator/pom.xml test \
  -Dtest=BroadcastJfrProfileTest \
  -Dpolaris.profile.broadcast=true
```

The recording and text summary are written to:

- `Emulator/target/profiles/broadcast-baseline.jfr`
- `Emulator/target/profiles/broadcast-baseline.txt`

Add `-Dpolaris.profile.broadcast.shared=true` to exercise the shared-frame
broadcast path. Its artifacts use the `broadcast-shared-frame` prefix.

## Baseline

Captured on 2026-07-20 from commit `d9fe1232` with Temurin 25.0.3+9:

| Measurement | Value |
| --- | ---: |
| Broadcasts | 5,000 |
| Recipients per broadcast | 50 |
| Encoded frames | 250,000 |
| Elapsed recording window | 146.637 ms |
| Total sampled allocation | 478,218,808 bytes |
| `ServerMessage` / encoder sampled allocation | 317,071,848 bytes |
| Allocation samples | 371 |
| `ServerMessage` / encoder allocation samples | 322 |
| Garbage collections | 3 |

About 66% of sampled allocation was attributed to the message/encoder stack.
This confirms the T2.1 serialize-once candidate. The same harness must be run
after the internal broadcast path changes; public `ServerMessage.get()`
defensive-copy behavior is outside the optimization and must remain unchanged.

## Shared-frame result

Captured on 2026-07-20 with Temurin 25.0.3+9:

| Measurement | Baseline | Shared frame |
| --- | ---: | ---: |
| Broadcasts | 5,000 | 5,000 |
| Recipients per broadcast | 50 | 50 |
| Encoded frames | 250,000 | 250,000 |
| Elapsed recording window | 146.637 ms | 91.046 ms |
| Total sampled allocation | 478,218,808 bytes | 136,879,312 bytes |
| `ServerMessage` / encoder sampled allocation | 317,071,848 bytes | 13,438,248 bytes |
| Allocation samples | 371 | 82 |
| `ServerMessage` / encoder allocation samples | 322 | 19 |
| Garbage collections | 3 | 0 |

The shared-frame run reduced total sampled allocation by about 71% and
message/encoder-attributed sampled allocation by about 96%. Room broadcasts
prepare one frame and give each recipient an independently retained duplicate.
Ordinary sends and plugin-observed outgoing packets keep the established
`ServerMessage` encoder path.
