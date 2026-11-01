package com.eu.habbo.networking.gameserver.auth;

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
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

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
        assertTrue(source("habbohotel/items/interactions/wired/effects/WiredEffectUserFurniBase.java")
                .contains("ctx.room().getHabboItem(item.getId()) == item"));
    }

    @Test
    void anHcGiftClaimIsSpentOnlyWhenSomethingWasGiven() throws Exception {
        String gift = source("messages/incoming/catalog/CatalogSelectClubGiftEvent.java");

        assertTrue(gift.indexOf("if (itemsGiven.isEmpty()) {") < gift.indexOf("hcGiftsClaimed++"));
    }
}
