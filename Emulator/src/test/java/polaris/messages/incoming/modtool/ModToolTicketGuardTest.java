package polaris.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModToolTicketGuardTest {
    @Test
    void idsMustBePositive() {
        assertFalse(ModToolTicketGuard.isPositiveId(0));
        assertFalse(ModToolTicketGuard.isPositiveId(-1));
        assertTrue(ModToolTicketGuard.isPositiveId(1));
    }

    @Test
    void releaseBatchIsBounded() {
        assertFalse(ModToolTicketGuard.isValidReleaseBatch(0));
        assertTrue(ModToolTicketGuard.isValidReleaseBatch(ModToolTicketGuard.MAX_RELEASE_BATCH));
        assertFalse(ModToolTicketGuard.isValidReleaseBatch(ModToolTicketGuard.MAX_RELEASE_BATCH + 1));
    }

    @Test
    void groupedIdListMayBeEmptyButIsBounded() {
        assertTrue(ModToolTicketGuard.isValidIdListSize(0));
        assertTrue(ModToolTicketGuard.isValidIdListSize(ModToolTicketGuard.MAX_RELEASE_BATCH));
        assertFalse(ModToolTicketGuard.isValidIdListSize(-1));
        assertFalse(ModToolTicketGuard.isValidIdListSize(ModToolTicketGuard.MAX_RELEASE_BATCH + 1));
    }
}
