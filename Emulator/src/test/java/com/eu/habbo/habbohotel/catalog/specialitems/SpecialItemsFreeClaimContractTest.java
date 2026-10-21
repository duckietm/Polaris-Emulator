package com.eu.habbo.habbohotel.catalog.specialitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SpecialItemsFreeClaimContractTest {
    @Test
    void usesTheOfficialHeaders() {
        assertEquals(266, Outgoing.HasClaimedProductComposer);
        assertEquals(395, Incoming.RequestHasClaimedProductEvent);
        assertEquals(3935, Incoming.ClaimFreeProductEvent);
    }

    @Test
    void hasClaimedResponseIsClaimIdThenFlag() {
        ByteBuf packet =
                new HasClaimedProductComposer("chests_starter", true).compose().get();
        try {
            packet.skipBytes(4);
            assertEquals(266, packet.readShort());
            assertEquals(
                    "chests_starter",
                    packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                            .toString());
            assertTrue(packet.readBoolean());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void claimIdsAreShortConfigurationKeys() {
        assertTrue(SpecialItemClaimService.isValidClaimId("chests_starter"));
        assertTrue(SpecialItemClaimService.isValidClaimId("wf15.free-1"));
        assertTrue(SpecialItemClaimService.isValidClaimId("a".repeat(SpecialItemClaimService.MAX_CLAIM_ID_LENGTH)));
        assertFalse(SpecialItemClaimService.isValidClaimId(null));
        assertFalse(SpecialItemClaimService.isValidClaimId(""));
        assertFalse(
                SpecialItemClaimService.isValidClaimId("a".repeat(SpecialItemClaimService.MAX_CLAIM_ID_LENGTH + 1)));
        assertFalse(SpecialItemClaimService.isValidClaimId("chests starter"));
        assertFalse(SpecialItemClaimService.isValidClaimId("x';--"));
    }
}
