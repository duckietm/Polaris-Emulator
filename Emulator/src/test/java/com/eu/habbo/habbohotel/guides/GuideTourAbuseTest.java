package com.eu.habbo.habbohotel.guides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.Habbo;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * An ended guide tour stays listed until its recommendation or the cleanup, and the chat and the report kept working
 * on it: a report packet every 200 ms filed a ticket each time, and unfiltered messages of any length reached the
 * former helper.
 */
class GuideTourAbuseTest {

    private static MockedStatic<Emulator> emulator() {
        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(mock(GameEnvironment.class, RETURNS_DEEP_STUBS));
        return emulator;
    }

    @SuppressWarnings("unchecked")
    private static void add(GuideManager manager, GuideTour tour) throws Exception {
        Field field = GuideManager.class.getDeclaredField("activeTours");
        field.setAccessible(true);
        ((Set<GuideTour>) field.get(manager)).add(tour);
    }

    @Test
    void anEndedTourTakesNoMoreMessagesOrReports() throws Exception {
        try (MockedStatic<Emulator> ignored = emulator()) {
            Habbo noob = mock(Habbo.class);
            Habbo helper = mock(Habbo.class);
            GuideTour tour = new GuideTour(noob, "help");
            tour.setHelper(helper);
            GuideManager manager = new GuideManager();
            add(manager, tour);

            assertSame(tour, manager.getActiveGuideTourByHabbo(noob));
            assertTrue(manager.endReportedSession(tour), "the first report closes the tour");
            assertFalse(manager.endReportedSession(tour), "a second report files nothing");
            assertNull(manager.getActiveGuideTourByHabbo(noob));
            assertNull(manager.getActiveGuideTourByHabbo(helper));
            assertSame(tour, manager.getGuideTourByNoob(noob), "still there for the recommendation");
        }
    }

    @Test
    void messagesArePacedAndTheirNumberIsBounded() {
        try (MockedStatic<Emulator> ignored = emulator()) {
            GuideTour tour = new GuideTour(mock(Habbo.class), "help");

            assertTrue(tour.allowMessage(1, 1_000));
            assertFalse(tour.allowMessage(1, 1_200));
            assertTrue(tour.allowMessage(2, 1_200), "the other side has its own pace");
            assertTrue(tour.allowMessage(1, 1_000 + GuideTour.MIN_MESSAGE_INTERVAL_MS));

            for (int i = 0; i < GuideTour.MAX_STORED_MESSAGES + 50; i++) {
                tour.addMessage(new GuideChatMessage(1, "m" + i, i));
            }
            assertEquals(GuideTour.MAX_STORED_MESSAGES, tour.messageCount());
        }
    }
}
