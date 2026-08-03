package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CmsApiKeyStoreTest {

    @Test
    void parsesIdSecretAndScopes() {
        CmsApiKeyStore store = CmsApiKeyStore.parse("cms-main|topsecret|economy:*,users:setrank");

        CmsApiKey key = store.get("cms-main");
        assertEquals(1, store.size());
        assertEquals("cms-main", key.keyId());
        assertEquals("topsecret", new String(key.secret(), StandardCharsets.UTF_8));
        assertTrue(key.scopes().contains("economy:*"));
        assertTrue(key.scopes().contains("users:setrank"));
    }

    @Test
    void secretMayContainColons() {
        CmsApiKeyStore store = CmsApiKeyStore.parse("k|se:cr:et|*");
        assertEquals("se:cr:et", new String(store.get("k").secret(), StandardCharsets.UTF_8));
    }

    @Test
    void parsesMultipleRecords() {
        CmsApiKeyStore store = CmsApiKeyStore.parse("a|s1|*;b|s2|alerts:*");
        assertEquals(2, store.size());
        assertTrue(store.get("a").scopes().contains("*"));
        assertTrue(store.get("b").scopes().contains("alerts:*"));
    }

    @Test
    void skipsMalformedBlankAndDuplicateEntries() {
        CmsApiKeyStore store = CmsApiKeyStore.parse("onlytwo|secret; |s|*;good|secret|*;good|other|alerts:*");
        assertEquals(1, store.size());
        // First "good" wins; the duplicate is ignored.
        assertEquals("secret", new String(store.get("good").secret(), StandardCharsets.UTF_8));
        assertNull(store.get("onlytwo"));
    }

    @Test
    void blankConfigYieldsEmptyStore() {
        assertTrue(CmsApiKeyStore.parse("").isEmpty());
        assertTrue(CmsApiKeyStore.parse(null).isEmpty());
    }
}
