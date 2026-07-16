-- Signed access JWTs are otherwise valid until their exp claim even after
-- logout or a password change. This per-user version is embedded in new tokens
-- and incremented whenever all existing access tokens must be revoked.
ALTER TABLE `users`
    ADD COLUMN IF NOT EXISTS `access_token_version` BIGINT NOT NULL DEFAULT 0
    AFTER `remember_token_expires_at`;
