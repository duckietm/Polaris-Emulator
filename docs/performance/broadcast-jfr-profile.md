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
