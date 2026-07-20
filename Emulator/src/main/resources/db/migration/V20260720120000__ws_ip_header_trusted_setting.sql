-- ws.ip.header is only honoured when the direct peer is a trusted reverse
-- proxy (loopback, or an entry in ws.ip.header.trusted). Seed the trusted
-- list empty so operators can discover the key in emulator_settings instead
-- of finding out via silently ignored forwarded-IP headers.
INSERT INTO emulator_settings (`key`, `value`)
SELECT 'ws.ip.header.trusted', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM emulator_settings current_setting
    WHERE current_setting.`key` = 'ws.ip.header.trusted'
);
