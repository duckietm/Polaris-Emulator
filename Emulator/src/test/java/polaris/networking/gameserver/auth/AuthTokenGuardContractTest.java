package polaris.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthTokenGuardContractTest {

    @Test
    void accessTokenRejectsOversizedTokensBeforeSplitAndDecode() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/polaris/networking/gameserver/auth/AccessTokenService.java"));

        int maxConstant = source.indexOf("MAX_TOKEN_CHARS = 2048");
        int lengthGuard = source.indexOf("token.length() > MAX_TOKEN_CHARS");
        int split = source.indexOf("token.split");
        int decode = source.indexOf("URL_DEC.decode");

        assertTrue(maxConstant > -1, "Access tokens should have a bounded serialized size");
        assertTrue(lengthGuard > -1, "Access token verification must reject oversized tokens");
        assertTrue(lengthGuard < split, "Access token length guard must run before split");
        assertTrue(lengthGuard < decode, "Access token length guard must run before Base64 decode");
    }

    @Test
    void rememberTokenRejectsOversizedTokensBeforeSplitAndDecode() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/polaris/networking/gameserver/auth/RememberJwtService.java"));

        int maxConstant = source.indexOf("MAX_TOKEN_CHARS = 2048");
        int lengthGuard = source.indexOf("jwt.length() > MAX_TOKEN_CHARS");
        int split = source.indexOf("jwt.split");
        int decode = source.indexOf("URL_DEC.decode");

        assertTrue(maxConstant > -1, "Remember tokens should have a bounded serialized size");
        assertTrue(lengthGuard > -1, "Remember token verification must reject oversized tokens");
        assertTrue(lengthGuard < split, "Remember token length guard must run before split");
        assertTrue(lengthGuard < decode, "Remember token length guard must run before Base64 decode");
    }
}
