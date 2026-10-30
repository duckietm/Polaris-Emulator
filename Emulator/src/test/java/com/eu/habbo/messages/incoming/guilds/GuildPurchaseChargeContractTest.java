package com.eu.habbo.messages.incoming.guilds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A group purchase charged with no check, and kept the price when the group could not be created. */
class GuildPurchaseChargeContractTest {

    private static final Path BUY = Path.of("src/main/java/com/eu/habbo/messages/incoming/guilds/RequestGuildBuyEvent.java");
    private static final Path MANAGER = Path.of("src/main/java/com/eu/habbo/habbohotel/guilds/GuildManager.java");

    @Test
    void theChargeIsCheckedAndRefundedWhenTheGroupIsNotCreated() throws Exception {
        String buy = Files.readString(BUY);

        assertFalse(buy.contains("giveCredits(-guildPrice)"));
        assertTrue(buy.contains("tryTakeCredits(guildPrice)"));
        int create = buy.indexOf(".createGuild(");
        int nullCheck = buy.indexOf("if (guild == null) {", create);
        int refund = buy.indexOf("giveCredits(charged, \"guild.purchase.refund\")", nullCheck);
        assertTrue(create > 0 && nullCheck > create && refund > nullCheck);
        assertTrue(nullCheck < buy.indexOf("r.setGuild(guild.getId());"));
    }

    @Test
    void bothGroupRowsAreWrittenInOneTransaction() throws Exception {
        String manager = Files.readString(MANAGER);

        assertTrue(manager.contains("connection.setAutoCommit(false);"));
        assertTrue(manager.contains("connection.rollback();"));
        assertTrue(manager.contains("throw new SQLException(\"No id was generated for the new group\");"));
        assertFalse(manager.contains("LOGGER.error(\"Caught SQL exception\", e);\n        }\n\n        habbo.getHabboStats().addGuild"));
    }
}
