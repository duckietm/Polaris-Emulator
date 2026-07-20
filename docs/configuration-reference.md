# Polaris startup configuration reference

Unknown keys remain allowed for plugins. Database-backed hotel settings are documented separately from this startup-file registry.

| Key | Type | Default | Environment | Restart | Live reload | Description |
| --- | --- | --- | --- | --- | --- | --- |
| `client.release.allowed` | string | `` | — | yes | no | Polaris startup setting. |
| `crypto.ws.enabled` | boolean | `false` | — | yes | no | WebSocket listener setting. |
| `db.database` | string | `` | `DB_DATABASE` | yes | no | Database startup setting. |
| `db.hostname` | string | `` | `DB_HOSTNAME` | yes | no | Database startup setting. |
| `db.integrity.audit.max_duration_seconds` | integer | `0` | — | yes | no | Startup integrity-audit setting. |
| `db.integrity.audit.mode` | string | `warn` | — | yes | no | Startup integrity-audit setting. |
| `db.integrity.audit.query_timeout_seconds` | integer | `0` | — | yes | no | Startup integrity-audit setting. |
| `db.integrity.audit.sample_limit` | integer | `0` | — | yes | no | Startup integrity-audit setting. |
| `db.migrate.on_startup` | boolean | `false` | `DB_MIGRATE_ON_STARTUP` | yes | no | Database startup setting. |
| `db.migrations.backup.directory` | string | `` | — | yes | no | Migration backup setting. |
| `db.migrations.backup.enabled` | boolean | `false` | — | yes | no | Migration backup setting. |
| `db.migrations.backup.executable` | string | `` | — | yes | no | Migration backup setting. |
| `db.migrations.backup.keep` | integer | `0` | — | yes | no | Migration backup setting. |
| `db.migrations.backup.timeout_seconds` | integer | `0` | — | yes | no | Migration backup setting. |
| `db.params` | string | `` | `DB_PARAMS` | yes | no | Database startup setting. |
| `db.password` | string | `` | `DB_PASSWORD` | yes | no | Database startup setting. |
| `db.pool.connection_timeout_ms` | long | `0` | — | yes | no | Database connection-pool setting. |
| `db.pool.idle_timeout_ms` | long | `0` | — | yes | no | Database connection-pool setting. |
| `db.pool.leak_detection_ms` | long | `0` | — | yes | no | Database connection-pool setting. |
| `db.pool.max_lifetime_ms` | long | `0` | — | yes | no | Database connection-pool setting. |
| `db.pool.maxsize` | integer | `0` | — | yes | no | Database connection-pool setting. |
| `db.pool.minsize` | integer | `0` | — | yes | no | Database connection-pool setting. |
| `db.pool.validation_timeout_ms` | long | `0` | — | yes | no | Database connection-pool setting. |
| `db.port` | integer | `0` | `DB_PORT` | yes | no | Database startup setting. |
| `db.slow_query.enabled` | boolean | `false` | — | yes | no | Sanitized slow-query diagnostic setting. |
| `db.slow_query.max_sql_length` | integer | `0` | — | yes | no | Sanitized slow-query diagnostic setting. |
| `db.slow_query.threshold_ms` | integer | `0` | — | yes | no | Sanitized slow-query diagnostic setting. |
| `db.username` | string | `` | `DB_USERNAME` | yes | no | Database startup setting. |
| `e2e.enabled` | boolean | `false` | — | yes | no | Polaris startup setting. |
| `enc.d` | string | `` | — | yes | no | Legacy transport encryption setting. |
| `enc.e` | string | `` | — | yes | no | Legacy transport encryption setting. |
| `enc.enabled` | boolean | `false` | — | yes | no | Legacy transport encryption setting. |
| `enc.n` | string | `` | — | yes | no | Legacy transport encryption setting. |
| `game.host` | string | `` | `EMU_HOST` | yes | no | Game listener setting. |
| `game.port` | integer | `0` | `EMU_PORT` | yes | no | Game listener setting. |
| `habbo.console.style` | string | `` | — | yes | no | Polaris startup setting. |
| `http.blocking.pool.size` | integer | `8` | — | yes | no | Blocking HTTP worker setting. |
| `io.netty.unwritable.timeout.seconds` | integer | `10` | — | yes | no | Netty channel flow-control setting. |
| `io.netty.write_buffer.high_water_mark` | integer | `65536` | — | yes | no | Netty channel flow-control setting. |
| `io.netty.write_buffer.low_water_mark` | integer | `32768` | — | yes | no | Netty channel flow-control setting. |
| `login.news.limit` | integer | `0` | — | yes | no | Built-in login endpoint setting. |
| `login.remember.duration.days` | integer | `0` | — | yes | no | Built-in login endpoint setting. |
| `login.remember.enabled` | boolean | `false` | — | yes | no | Built-in login endpoint setting. |
| `login.remember.jwt.secret` | string | `` | — | yes | no | Built-in login endpoint setting. |
| `login.sso.ticket.ttl.seconds` | integer | `0` | — | yes | no | Built-in login endpoint setting. |
| `nitro.secure.api.enabled` | boolean | `false` | — | yes | no | Nitro secure-asset runtime setting. |
| `nitro.secure.assets.enabled` | boolean | `false` | — | yes | no | Nitro secure-asset runtime setting. |
| `nitro.secure.config.root` | string | `` | — | yes | no | Nitro secure-asset runtime setting. |
| `nitro.secure.gamedata.root` | string | `` | — | yes | no | Nitro secure-asset runtime setting. |
| `nitro.secure.master_key` | string | `` | — | yes | no | Nitro secure-asset runtime setting. |
| `nitro.secure.session_ttl_sec` | integer | `0` | — | yes | no | Nitro secure-asset runtime setting. |
| `polaris.events.honor_priority` | boolean | `false` | — | no | yes | Enables priority-ordered, cancellation-aware plugin event dispatch. |
| `rcon.allowed` | string | `` | `RCON_ALLOWED` | yes | no | RCON listener setting. |
| `rcon.host` | string | `` | `RCON_HOST` | yes | no | RCON listener setting. |
| `rcon.port` | integer | `0` | `RCON_PORT` | yes | no | RCON listener setting. |
| `runtime.threads` | integer | `8` | — | yes | no | Polaris startup setting. |
| `session.reconnect.grace.seconds` | integer | `0` | — | yes | no | Polaris startup setting. |
| `stress.enabled` | boolean | `false` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_bots` | integer | `5000` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_chat_per_second` | integer | `10000` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_duration_seconds` | integer | `3600` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_items` | integer | `100000` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_rollers` | integer | `50000` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_total_entities` | integer | `200000` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_wired_events_per_second` | integer | `100` | — | yes | no | Opt-in transient room stress-lab setting. |
| `stress.max_wired_stacks` | integer | `50000` | — | yes | no | Opt-in transient room stress-lab setting. |
| `ws.enabled` | boolean | `false` | — | yes | no | WebSocket listener setting. |
| `ws.host` | string | `` | — | yes | no | WebSocket listener setting. |
| `ws.port` | integer | `0` | — | yes | no | WebSocket listener setting. |
| `ws.whitelist` | string | `` | — | yes | no | WebSocket listener setting. |
