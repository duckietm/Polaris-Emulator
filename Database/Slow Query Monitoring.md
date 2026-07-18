# Slow query monitoring

Polaris logs every JDBC execution that reaches `db.slow_query.threshold_ms` as a
single `event=database_slow_query` record. The record contains the elapsed time,
operation, stable SQL fingerprint, sanitized SQL shape, thread name, SQL state,
vendor code, and a Hikari pool snapshot. Literal strings, numeric values, bound
parameters, and SQL comments are not written to the log.

The application signal explains which Polaris thread and pool state produced a
slow call. MariaDB remains the authoritative source for server execution details.
For production diagnosis, enable the MariaDB slow log temporarily and use the
Polaris fingerprint and timestamp to correlate both views:

```ini
[mariadb]
slow_query_log=ON
long_query_time=0.250
log_slow_admin_statements=ON
```

Keep the MariaDB slow log outside the web root, restrict file permissions, and
disable it again after collecting the diagnostic window. Do not enable
`general_log`: it records every statement and can expose parameter values.

Polaris defaults:

```ini
db.slow_query.enabled=true
db.slow_query.threshold_ms=250
db.slow_query.max_sql_length=1024
```

The maximum SQL length must be between 128 and 8192 characters. Invalid values
fail database-pool initialization instead of silently disabling diagnostics.
