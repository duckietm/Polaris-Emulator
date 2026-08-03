package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class CmsApiAuthenticatorTest {

    private static final String SECRET = "topsecret";
    private static final String BODY = "{\"key\":\"givecredits\",\"data\":{\"user_id\":1,\"credits\":5}}";

    private CmsApiAuthenticator authenticatorAt(long now) {
        CmsApiKeyStore store = CmsApiKeyStore.parse("cms-main|" + SECRET + "|*");
        return new CmsApiAuthenticator(store, 300, 600, (LongSupplier) () -> now);
    }

    private static String sign(String keyId, String ts, String nonce, String body) {
        String signingString = keyId + "\n" + ts + "\n" + nonce + "\n" + body;
        return CmsApiAuthenticator.hmacSha256Hex(SECRET.getBytes(StandardCharsets.UTF_8), signingString);
    }

    @Test
    void acceptsAValidSignature() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        String sig = sign("cms-main", "1000", "n1", BODY);

        CmsApiAuthenticator.Result result = auth.authenticate("cms-main", "1000", "n1", sig, BODY);

        assertEquals(CmsApiAuthenticator.Status.OK, result.status());
        assertEquals("cms-main", result.key().keyId());
    }

    @Test
    void signatureIsCaseInsensitiveHex() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        String sig = sign("cms-main", "1000", "n1", BODY).toUpperCase();

        assertEquals(
                CmsApiAuthenticator.Status.OK,
                auth.authenticate("cms-main", "1000", "n1", sig, BODY).status());
    }

    @Test
    void rejectsAReplayedNonce() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        String sig = sign("cms-main", "1000", "n1", BODY);

        assertEquals(
                CmsApiAuthenticator.Status.OK,
                auth.authenticate("cms-main", "1000", "n1", sig, BODY).status());
        assertEquals(
                CmsApiAuthenticator.Status.REPLAY,
                auth.authenticate("cms-main", "1000", "n1", sig, BODY).status());
    }

    @Test
    void rejectsATamperedBody() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        String sig = sign("cms-main", "1000", "n1", BODY);

        assertEquals(
                CmsApiAuthenticator.Status.BAD_SIGNATURE,
                auth.authenticate("cms-main", "1000", "n1", sig, BODY + " ").status());
    }

    @Test
    void rejectsAnUnknownKey() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        String sig = sign("nope", "1000", "n1", BODY);

        assertEquals(
                CmsApiAuthenticator.Status.UNKNOWN_KEY,
                auth.authenticate("nope", "1000", "n1", sig, BODY).status());
    }

    @Test
    void rejectsAStaleTimestamp() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        String sig = sign("cms-main", "600", "n1", BODY); // 400s < now, skew is 300

        assertEquals(
                CmsApiAuthenticator.Status.BAD_TIMESTAMP,
                auth.authenticate("cms-main", "600", "n1", sig, BODY).status());
    }

    @Test
    void rejectsAnOverflowCraftedTimestamp() {
        long now = 1000;
        CmsApiAuthenticator auth = authenticatorAt(now);
        // Chosen so the old Math.abs(now - ts) would wrap to Long.MIN_VALUE and slip
        // past the window. A VALID signature is supplied to prove the time gate — not
        // just the signature check — rejects it.
        String ts = Long.toString(now + Long.MIN_VALUE);
        String sig = sign("cms-main", ts, "n1", BODY);

        assertEquals(
                CmsApiAuthenticator.Status.BAD_TIMESTAMP,
                auth.authenticate("cms-main", ts, "n1", sig, BODY).status());
    }

    @Test
    void rejectsANonNumericTimestamp() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        assertEquals(
                CmsApiAuthenticator.Status.BAD_TIMESTAMP,
                auth.authenticate("cms-main", "notanumber", "n1", "deadbeef", BODY)
                        .status());
    }

    @Test
    void rejectsMissingHeaders() {
        CmsApiAuthenticator auth = authenticatorAt(1000);
        assertEquals(
                CmsApiAuthenticator.Status.MISSING_HEADERS,
                auth.authenticate("cms-main", "1000", "", "sig", BODY).status());
        assertEquals(
                CmsApiAuthenticator.Status.MISSING_HEADERS,
                auth.authenticate("", "1000", "n1", "sig", BODY).status());
    }
}
