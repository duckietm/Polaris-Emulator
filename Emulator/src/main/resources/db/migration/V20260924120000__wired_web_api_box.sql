-- The Variables Web API add-on (wf_xtra_var_web_api) holds the keys of a room's HTTP API, so it may
-- never change hands. The emulator enforces this in code whatever items_base says; this aligns the
-- flags so clients stop offering trade, marketplace, gift and recycle for it. Idempotent: only rows
-- that still allow something are touched. The 0/1 flags are quoted for enum('0','1') columns.
UPDATE `items_base`
SET `allow_trade` = '0',
    `allow_marketplace_sell` = '0',
    `allow_gift` = '0',
    `allow_recycle` = '0',
    `allow_inventory_stack` = '0'
WHERE (`interaction_type` = 'wf_xtra_var_web_api' OR `item_name` = 'wf_xtra_var_web_api')
  AND (`allow_trade` <> '0'
       OR `allow_marketplace_sell` <> '0'
       OR `allow_gift` <> '0'
       OR `allow_recycle` <> '0'
       OR `allow_inventory_stack` <> '0');

-- The API settings, off by default. A hotel that set any of them by hand keeps its value.
INSERT INTO `wired_emulator_settings` (`key`, `value`, `comment`) VALUES
    ('wired.api.enabled', '0', 'Serve the Variables Web API under /api/public on the websocket port (0/1).'),
    ('wired.api.max_payload_bytes', '16384', 'Largest request body the Variables Web API accepts, in bytes.'),
    ('wired.api.rate_limit.enabled', '1', 'Rate limit the Variables Web API (0/1).'),
    ('wired.api.rate_limit.per_ip', '120', 'Requests per client address per window, before authentication.'),
    ('wired.api.rate_limit.per_ip.window_ms', '10000', 'Length of the per-address window in milliseconds.'),
    ('wired.api.rate_limit.per_key', '60', 'Requests per API key per window.'),
    ('wired.api.rate_limit.per_key.window_ms', '10000', 'Length of the per-key window in milliseconds.'),
    ('wired.api.rate_limit.room_writes', '200', 'Variable writes per room per window, across all keys.'),
    ('wired.api.rate_limit.room_writes.window_ms', '10000', 'Length of the per-room write window in milliseconds.'),
    ('wired.api.auth_fail.max', '10', 'Failed authentications per client address before it is blocked.'),
    ('wired.api.auth_fail.window_ms', '60000', 'Window for counting failed authentications, in milliseconds.'),
    ('wired.api.auth_fail.block_ms', '300000', 'How long a blocked client address stays blocked, in milliseconds.'),
    ('wired.api.batch.max', '100', 'Operations per batch request and variables per profile update.'),
    ('wired.api.bulk_delete.max', '20', 'Variable names per bulk-delete request.'),
    ('wired.api.page_size.max', '100', 'Largest page of holders a list request returns.'),
    ('wired.api.cors.origins', '*', 'Origins allowed to call the Variables Web API from a browser, comma separated; * allows any.')
ON DUPLICATE KEY UPDATE `value` = `value`;
