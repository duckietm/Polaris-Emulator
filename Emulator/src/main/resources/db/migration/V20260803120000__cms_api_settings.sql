-- CMS API runtime settings live in their own operator-owned table so keys and
-- limits can be managed (and rotated) from the CMS/database without editing
-- config.ini or restarting the emulator. Rows are seeded only when missing, so
-- tuned values survive re-runs. cms.api.enabled and cms.api.allowed intentionally
-- stay in config.ini as bootstrap/network-gate settings.
CREATE TABLE IF NOT EXISTS `emulator_api` (
    `key` VARCHAR(191) NOT NULL,
    `value` TEXT NULL,
    `comment` TEXT NULL,
    PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO `emulator_api` (`key`, `value`, `comment`) VALUES
    ('cms.api.keys', '',
        'HMAC credentials. Format: keyId|secret|scopes, records separated by ;. Scopes: * / group:* / group:command / command.'),
    ('cms.api.timestamp.skew.seconds', '300',
        'Maximum accepted clock skew (seconds) between a signed request timestamp and server time.'),
    ('cms.api.nonce.ttl.seconds', '600',
        'How long request nonces are remembered for replay protection (seconds).'),
    ('cms.api.max_payload_bytes', '65536',
        'Maximum request body size in bytes.'),
    ('cms.api.require_tls', '0',
        'Require TLS for non-loopback callers (1 = yes). Terminate TLS here or at a reverse proxy.'),
    ('cms.api.rate_limit.enabled', '1',
        'Per-IP rate limiting toggle (1 = enabled).'),
    ('cms.api.rate_limit.limit_for_period', '120',
        'Allowed requests per refresh period, per IP.'),
    ('cms.api.rate_limit.refresh_period_ms', '1000',
        'Rate limiter refresh period in milliseconds.');
