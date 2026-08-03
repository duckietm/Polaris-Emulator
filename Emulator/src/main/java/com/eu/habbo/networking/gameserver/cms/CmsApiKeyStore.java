package com.eu.habbo.networking.gameserver.cms;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the {@code cms.api.keys} configuration value into a lookup of
 * {@link CmsApiKey} by key id.
 *
 * <p>Format — records separated by {@code ;}, fields within a record separated by
 * {@code |}:
 *
 * <pre>{@code keyId|secret|scopeA,scopeB ; keyId2|secret2|*}</pre>
 *
 * The {@code |}/{@code ;} separators are used instead of {@code :} because scope
 * tokens contain colons (e.g. {@code economy:*}). A record missing fields, with a
 * blank id/secret, or with a duplicate id is skipped with a warning rather than
 * failing startup, so one malformed entry cannot lock the operator out of the
 * remaining keys.
 */
public final class CmsApiKeyStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmsApiKeyStore.class);

    private final Map<String, CmsApiKey> keysById;

    private CmsApiKeyStore(Map<String, CmsApiKey> keysById) {
        this.keysById = keysById;
    }

    public static CmsApiKeyStore parse(String raw) {
        Map<String, CmsApiKey> keys = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return new CmsApiKeyStore(keys);
        }

        for (String record : raw.split(";")) {
            if (record.isBlank()) {
                continue;
            }
            String[] fields = record.split("\\|", -1);
            if (fields.length != 3) {
                LOGGER.warn("Ignoring malformed cms.api.keys entry (expected keyId|secret|scopes): {}", record.trim());
                continue;
            }

            String keyId = fields[0].trim();
            String secret = fields[1];
            String scopeList = fields[2].trim();

            if (keyId.isEmpty() || secret.isEmpty()) {
                LOGGER.warn("Ignoring cms.api.keys entry with blank id or secret: {}", keyId);
                continue;
            }
            if (keys.containsKey(keyId)) {
                LOGGER.warn("Ignoring duplicate cms.api.keys id: {}", keyId);
                continue;
            }

            Set<String> scopes = new LinkedHashSet<>();
            for (String scope : scopeList.split(",")) {
                String s = scope.trim();
                if (!s.isEmpty()) {
                    scopes.add(s);
                }
            }
            if (scopes.isEmpty()) {
                LOGGER.warn("cms.api.keys id {} has no scopes; it will be unable to run any command", keyId);
            }

            keys.put(
                    keyId,
                    new CmsApiKey(keyId, secret.getBytes(StandardCharsets.UTF_8), Collections.unmodifiableSet(scopes)));
        }

        return new CmsApiKeyStore(keys);
    }

    public CmsApiKey get(String keyId) {
        return keyId == null ? null : this.keysById.get(keyId);
    }

    public int size() {
        return this.keysById.size();
    }

    public boolean isEmpty() {
        return this.keysById.isEmpty();
    }
}
