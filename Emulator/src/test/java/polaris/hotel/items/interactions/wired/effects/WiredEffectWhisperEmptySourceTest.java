package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredSourceUtil;

class WiredEffectWhisperEmptySourceTest {
    @Test
    void aSelectorThatPickedNobodyShowsNothingToTheRoom() {
        assertTrue(WiredEffectWhisper.hasNobodyToShowFor(true, WiredSourceUtil.SOURCE_SELECTOR, List.of()));
        assertTrue(WiredEffectWhisper.hasNobodyToShowFor(true, WiredSourceUtil.SOURCE_SIGNAL, List.of()));
    }

    @Test
    void aSelectorThatPickedSomebodyShowsTheMessage() {
        assertFalse(WiredEffectWhisper.hasNobodyToShowFor(
                true, WiredSourceUtil.SOURCE_SELECTOR, List.of(mock(RoomUnit.class))));
    }

    @Test
    void aUserlessTriggerStillAnnouncesToTheRoom() {
        assertFalse(WiredEffectWhisper.hasNobodyToShowFor(true, WiredSourceUtil.SOURCE_TRIGGER, List.of()));
    }

    @Test
    void aMessageForTheUsersOnlyIsLeftToItsRecipients() {
        assertFalse(WiredEffectWhisper.hasNobodyToShowFor(false, WiredSourceUtil.SOURCE_SELECTOR, List.of()));
    }
}
