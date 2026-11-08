package polaris.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Account changes checked the current password with the IP counter only; SMTP over TLS took any certificate; a
 * user-to-furni box sent users to a furni no longer in the room; and an HC gift claim was spent with nothing given.
 */
class AccountChangeAndMiscGuardsTest {
    private static final Path MAIN = Path.of("src/main/java/polaris");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", " ");
    }

    @Test
    void everyAccountChangeUsesTheAccountCounter() throws Exception {
        String changes = source("networking/gameserver/auth/AccountChangeEndpoints.java");

        assertEquals(3, changes.split("AuthRateLimiter\\.isAccountLocked\\(", -1).length - 1);
        assertEquals(3, changes.split("AuthRateLimiter\\.recordAccountFailure\\(", -1).length - 1);
    }

    @Test
    void smtpOverTlsChecksTheServerIdentity() throws Exception {
        assertTrue(source("networking/gameserver/auth/SmtpMailService.java")
                .contains("props.put(\"mail.smtp.ssl.checkserveridentity\", \"true\");"));
    }

    @Test
    void userToFurniTargetsOnlyFurniInTheRoom() throws Exception {
        assertTrue(source("hotel/items/interactions/wired/effects/WiredEffectUserFurniBase.java")
                .contains("ctx.room().getUserItem(item.getId()) == item"));
    }

    @Test
    void anHcGiftClaimIsSpentOnlyWhenSomethingWasGiven() throws Exception {
        String gift = source("messages/incoming/catalog/CatalogSelectClubGiftEvent.java");

        int claim = gift.indexOf("tryClaimClubGift()");
        int create = gift.indexOf(".createGift(");
        int refund = gift.indexOf("refundClubGift()");

        assertTrue(claim > 0 && claim < create, "the claim is taken before any gift is created");
        assertTrue(gift.lastIndexOf("if (itemsGiven.isEmpty()) {", refund) > create, "and handed back when none was");
    }

    @Test
    void anHcGiftNeedsARunningClub() throws Exception {
        String gift = source("messages/incoming/catalog/CatalogSelectClubGiftEvent.java");

        int active = gift.indexOf("hasActiveClub()");
        assertTrue(active > 0, "an expired membership opens no gift");
        assertTrue(active < gift.indexOf("tryClaimClubGift()"), "checked before the claim is taken");
    }
}
