package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The welcome gift is taken once. {@code nuxReward} lives only in memory and is read
 * back from {@code nux} at every login, so a client that never sends UserNux could take
 * a gift, log out and take another, for ever - and the index it sends is a position in
 * the offer, not a gift id.
 */
class NewUserGiftClaimTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/users/PickNewUserGiftEvent.java");

    @Test
    void theClaimIsPersistedBeforeTheGiftIsGiven() throws Exception {
        String source = Files.readString(SOURCE);

        int persist = source.indexOf("habbo.getHabboStats().run()");
        int give = source.indexOf("gift.give(habbo)");

        assertTrue(source.contains("nux = true"), "the claim has to reach a column that is saved");
        assertTrue(persist > 0 && give > 0, source);
        assertTrue(persist < give, "the claim is written before the gift is handed over");
    }

    @Test
    void theIndexIsBoundedByWhatTheOfferSent() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("getNewUserGifts()"), "the index is resolved against the offered list");
        assertTrue(
                source.contains("index < 0 || index >= gifts.size()"),
                "an index outside the offer must be refused:\n" + source);
        assertTrue(!source.contains("getNewUserGift(index + 1)"), "the index is a position, not a gift id");
    }
}
