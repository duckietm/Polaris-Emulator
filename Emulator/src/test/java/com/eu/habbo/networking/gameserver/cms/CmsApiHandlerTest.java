package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.command.CommandResult;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CmsApiHandlerTest {

    @Test
    void mapsCommandResultsToHttpStatuses() {
        assertEquals(HttpResponseStatus.OK, CmsApiHandler.httpStatusFor(new CommandResult(true, 0, "")));
        assertEquals(
                HttpResponseStatus.BAD_REQUEST,
                CmsApiHandler.httpStatusFor(new CommandResult(true, 1, "invalid credits")));
        assertEquals(
                HttpResponseStatus.NOT_FOUND,
                CmsApiHandler.httpStatusFor(new CommandResult(true, 2, "user not found")));
        assertEquals(
                HttpResponseStatus.NOT_FOUND,
                CmsApiHandler.httpStatusFor(new CommandResult(true, 3, "room not found")));
        assertEquals(
                HttpResponseStatus.INTERNAL_SERVER_ERROR, CmsApiHandler.httpStatusFor(new CommandResult(true, 4, "")));
        assertEquals(
                HttpResponseStatus.NOT_FOUND,
                CmsApiHandler.httpStatusFor(new CommandResult(false, 1, "unknown command")));
    }

    @Test
    void defaultAllowListAdmitsOnlyLoopback() {
        // With no Emulator config loaded, isAddressAllowed falls back to the
        // documented default of loopback-only.
        assertTrue(CmsApiHandler.isAddressAllowed("127.0.0.1"));
        assertTrue(CmsApiHandler.isAddressAllowed("::1"));
        assertFalse(CmsApiHandler.isAddressAllowed("8.8.8.8"));
        assertFalse(CmsApiHandler.isAddressAllowed(""));
        assertFalse(CmsApiHandler.isAddressAllowed(null));
    }

    private static HttpHeaders headers(String... pairs) {
        HttpHeaders headers = new DefaultHttpHeaders();
        for (int i = 0; i < pairs.length; i += 2) {
            headers.add(pairs[i], pairs[i + 1]);
        }
        return headers;
    }

    @Test
    void aLocalProxyForwardingPlainHttpIsNotExemptFromTls() {
        assertTrue(CmsApiHandler.isTlsExempt("127.0.0.1", headers()), "the CMS on the same host");
        assertFalse(CmsApiHandler.isTlsExempt("127.0.0.1", headers("X-Forwarded-For", "198.51.100.4")));
        assertFalse(CmsApiHandler.isTlsExempt("::1", headers("X-Real-IP", "198.51.100.4")));
        assertTrue(CmsApiHandler.isTlsExempt(
                "127.0.0.1", headers("X-Forwarded-For", "198.51.100.4", "X-Forwarded-Proto", "https")));
        assertFalse(CmsApiHandler.isTlsExempt("10.0.0.5", headers()), "only loopback is ever exempt");
    }

    @Test
    void clientsBehindALocalProxyHaveTheirOwnBucket() {
        assertEquals("127.0.0.1", CmsApiHandler.rateLimitKey("127.0.0.1", headers()));
        assertEquals(
                "fwd:198.51.100.4",
                CmsApiHandler.rateLimitKey("127.0.0.1", headers("X-Forwarded-For", "198.51.100.4")));
        assertEquals("fwd:198.51.100.9", CmsApiHandler.rateLimitKey("::1", headers("X-Real-IP", "198.51.100.9")));
        assertEquals(
                "10.0.0.5",
                CmsApiHandler.rateLimitKey("10.0.0.5", headers("X-Forwarded-For", "198.51.100.4")),
                "a header from a peer that is not local names nobody");
    }

    @Test
    void anAuthenticatedRequestAlsoSpendsItsKeyBudget() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/cms/CmsApiHandler.java"));
        int auth = source.indexOf("CmsApiKey key = auth.key();");

        assertTrue(auth > 0 && source.indexOf("acquirePermit(\"key:\" + key.keyId())", auth) > auth);
    }
}
