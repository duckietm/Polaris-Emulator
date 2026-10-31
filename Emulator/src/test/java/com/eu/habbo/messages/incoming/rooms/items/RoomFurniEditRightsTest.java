package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.users.Habbo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Who may change a furni in someone else's room. The dimmer guard was inverted (anyone switched it in a
 * room without a group and set it up in a group room), any visitor rewrote any post-it, and the mannequin
 * handlers wrote a look into whatever furni the id named.
 */
class RoomFurniEditRightsTest {
    private static final Path ITEMS = Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items");

    private static Room room(boolean rights, int guildId, RoomRightLevels guildLevel, Habbo habbo) {
        Room room = mock(Room.class);
        when(room.hasRights(habbo)).thenReturn(rights);
        when(room.getGuildId()).thenReturn(guildId);
        when(room.getGuildRightLevel(habbo)).thenReturn(guildLevel);
        return room;
    }

    @Test
    void theDimmerNeedsRightsOrGroupRights() {
        Habbo habbo = mock(Habbo.class);

        assertFalse(MoodLightTurnOnEvent.mayControl(room(false, 0, RoomRightLevels.NONE, habbo), habbo));
        assertFalse(MoodLightTurnOnEvent.mayControl(room(false, 7, RoomRightLevels.NONE, habbo), habbo));
        assertTrue(MoodLightTurnOnEvent.mayControl(room(true, 0, RoomRightLevels.NONE, habbo), habbo));
        assertTrue(MoodLightTurnOnEvent.mayControl(room(false, 7, RoomRightLevels.GUILD_RIGHTS, habbo), habbo));
        assertFalse(
                MoodLightTurnOnEvent.mayControl(room(false, 0, RoomRightLevels.GUILD_RIGHTS, habbo), habbo),
                "group rights count only in a group room");
    }

    @Test
    void bothDimmerHandlersUseTheSameGuard() throws Exception {
        assertTrue(Files.readString(ITEMS.resolve("MoodLightTurnOnEvent.java")).contains("if (!mayControl(room,"));
        assertTrue(Files.readString(ITEMS.resolve("MoodLightSaveSettingsEvent.java"))
                .contains("if (!MoodLightTurnOnEvent.mayControl(room,"));
    }

    @Test
    void aVisitorEditsOnlyTheirOwnNote() throws Exception {
        String source = Files.readString(ITEMS.resolve("PostItSaveDataEvent.java")).replaceAll("\\s+", " ");

        assertTrue(source.contains(
                "if (!room.hasRights(this.client.getHabbo()) && item.getUserId() != this.client.getHabbo().getHabboInfo().getId()) return;"));
        assertFalse(source.contains("text.startsWith(item.getExtradata()"));
    }

    @Test
    void onlyAMannequinTakesALook() throws Exception {
        for (String handler : new String[] {"MannequinSaveLookEvent.java", "MannequinSaveNameEvent.java"}) {
            assertTrue(
                    Files.readString(ITEMS.resolve(handler)).contains("if (!(item instanceof InteractionMannequin)) return;"),
                    handler);
        }
    }

    @Test
    void aSeedRarityThatIsNotANumberIsRandom() {
        assertEquals(2, ToggleFloorItemEvent.seedRarity("3"));
        assertEquals(-1, ToggleFloorItemEvent.seedRarity("0"));
        assertEquals(-1, ToggleFloorItemEvent.seedRarity(""));
        assertEquals(-1, ToggleFloorItemEvent.seedRarity("m:hr-100:name"));
        assertEquals(-1, ToggleFloorItemEvent.seedRarity(null));
    }
}
