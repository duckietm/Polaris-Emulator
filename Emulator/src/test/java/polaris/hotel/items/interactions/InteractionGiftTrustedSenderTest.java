package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The gift card's trusted-sender flag: staff and the hotel are trusted, players and old gifts are not. */
class InteractionGiftTrustedSenderTest {
    private static String[] gift(String tail) {
        // One item (id 55), colour 0, ribbon 0, sender shown, a message, the sender's name and look.
        return ("1\t55\t0\t0\t1\thello\tFrank\thd-180-1" + tail).split("\t");
    }

    @Test
    void theFieldAfterTheLookSaysWhetherTheSenderIsTrusted() {
        assertTrue(InteractionGift.isTrustedSenderData(gift(InteractionGift.trustedSenderField(true)), 1));
        assertFalse(InteractionGift.isTrustedSenderData(gift(InteractionGift.trustedSenderField(false)), 1));
        assertEquals("\t1", InteractionGift.trustedSenderField(true));
        assertEquals("\t0", InteractionGift.trustedSenderField(false));
    }

    @Test
    void aGiftWrappedBeforeTheFieldExistedCountsAsFromAPlayer() {
        assertFalse(InteractionGift.isTrustedSenderData(gift(""), 1));
        assertFalse(InteractionGift.isTrustedSenderData(null, 1));
    }

    @Test
    void theFieldIsFoundBehindSeveralItemsToo() {
        String[] data = ("3\t55\t56\t57\t0\t0\t0\thello\t0\t0" + InteractionGift.trustedSenderField(true)).split("\t");

        assertTrue(InteractionGift.isTrustedSenderData(data, 3));
        assertFalse(InteractionGift.isTrustedSenderData(data, 1), "counted from the wrong place it is not the flag");
    }
}
