package polaris.hotel.catalog.specialitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.catalog.specialitems.ClaimProductResultComposer;
import polaris.messages.outgoing.catalog.specialitems.HasClaimedProductComposer;

class SpecialItemsFreeClaimContractTest {
    @Test
    void usesTheOfficialHeaders() {
        assertEquals(266, Outgoing.HasClaimedProductComposer);
        assertEquals(395, Incoming.RequestHasClaimedProductEvent);
        assertEquals(3935, Incoming.ClaimFreeProductEvent);
        assertEquals(43, Outgoing.ClaimProductResultComposer);
    }

    @Test
    void claimResultIsClaimIdThenResult() {
        ByteBuf packet = new ClaimProductResultComposer(
                        "cursed_dragon_adoption", SpecialItemClaimService.Outcome.INVALID_CHOICE.code())
                .compose()
                .get();
        try {
            packet.skipBytes(4);
            assertEquals(43, packet.readShort());
            assertEquals(
                    "cursed_dragon_adoption",
                    packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                            .toString());
            assertEquals(3, packet.readInt());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void theClientReadsZeroAndOneAsClaimedAndTwoAndThreeAsUnavailable() {
        assertEquals(0, SpecialItemClaimService.Outcome.CLAIMED.code());
        assertEquals(1, SpecialItemClaimService.Outcome.ALREADY_CLAIMED.code());
        assertEquals(2, SpecialItemClaimService.Outcome.UNAVAILABLE.code());
        assertEquals(3, SpecialItemClaimService.Outcome.INVALID_CHOICE.code());
        assertEquals(4, SpecialItemClaimService.Outcome.FAILED.code());
        assertTrue(SpecialItemClaimService.Outcome.UNAVAILABLE.countsAsClaimed());
        assertFalse(SpecialItemClaimService.Outcome.INVALID_CHOICE.countsAsClaimed());
        assertFalse(SpecialItemClaimService.Outcome.FAILED.countsAsClaimed());
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
