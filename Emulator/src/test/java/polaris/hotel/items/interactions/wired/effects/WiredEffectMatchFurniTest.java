package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.installHotel;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredMatchFurniSetting;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * "Match furni to snapshot" takes its snapshot from the picked furni. With the dropdown left on
 * "triggering furni" the snapshot was recorded but applied to the trigger item instead.
 */
class WiredEffectMatchFurniTest {

    @Test
    void aSnapshotOfPickedFurniPromotesTheTriggerSourceToSelected() throws Exception {
        Room room = mock(Room.class);
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(301);
        when(item.allowWiredResetState()).thenReturn(true);
        when(item.getExtradata()).thenReturn("1");
        when(room.getUserItem(301)).thenReturn(item);
        WiredEffectMatchFurni box = new WiredEffectMatchFurni(1, 1, base(), "", 0, 0);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            installHotel(emulator, room);

            box.saveData(
                    new WiredSettings(new int[] {1, 0, 0, 0, WiredSourceUtil.SOURCE_TRIGGER}, "", new int[] {301}, 0),
                    null);
        }

        assertEquals(
                WiredSourceUtil.SOURCE_SELECTED, json(box).get("furniSource").getAsInt());
        assertEquals(1, json(box).getAsJsonArray("items").size());
    }

    @Test
    void directionAndAltitudeWithoutPositionRestoreBoth() throws Exception {
        Room room = mock(Room.class);
        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = mock(RoomTile.class);
        when(room.getLayout()).thenReturn(layout);
        when(layout.getTile((short) 3, (short) 4)).thenReturn(tile);
        UserItem item = mock(UserItem.class);
        when(item.getX()).thenReturn((short) 3);
        when(item.getY()).thenReturn((short) 4);
        when(item.getZ()).thenReturn(0.0);
        when(item.getRotation()).thenReturn(0);
        WiredEffectMatchFurni box = new WiredEffectMatchFurni(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"state\":false,\"direction\":true,\"position\":false,\"altitude\":true,"
                        + "\"items\":[],\"delay\":0,\"furniSource\":0}"),
                room);
        WiredContext ctx = context(room);

        try (MockedStatic<WiredMoveCarryHelper> moves = mockStatic(WiredMoveCarryHelper.class)) {
            box.applySetting(room, item, new WiredMatchFurniSetting(301, " ", 2, 3, 4, 1.5), ctx);

            // The direction branch used to win and the height never came back.
            moves.verify(() -> WiredMoveCarryHelper.moveFurni(
                    eq(room), eq(box), eq(item), eq(tile), eq(2), eq(1.5), isNull(), eq(true), eq(ctx)));
        }
    }

    @Test
    void aShortLegacyRowKeepsTheDefaults() throws Exception {
        WiredEffectMatchFurni box = new WiredEffectMatchFurni(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("5"), null);

        assertEquals(
                WiredSourceUtil.SOURCE_TRIGGER, json(box).get("furniSource").getAsInt());
        assertEquals(0, json(box).getAsJsonArray("items").size());
    }

    @Test
    void unpickedTargetsAreDealtTheSnapshotSpotsInTurnAndReuseThem() {
        java.util.List<WiredMatchFurniSetting> settings = java.util.List.of(
                new WiredMatchFurniSetting(1, "0", 0, 1, 1),
                new WiredMatchFurniSetting(2, "1", 2, 2, 2),
                new WiredMatchFurniSetting(3, "2", 4, 3, 3));

        java.util.List<WiredMatchFurniSetting> dealt =
                WiredEffectMatchFurni.dealSpots(settings, 7, new java.util.Random(42));

        assertEquals(7, dealt.size());
        // Every spot is dealt once before any is dealt again.
        assertEquals(3, new java.util.HashSet<>(dealt.subList(0, 3)).size());
        assertEquals(dealt.subList(0, 3), dealt.subList(3, 6));
        assertEquals(dealt.get(0), dealt.get(6));
    }

    @Test
    void aOneFurniSnapshotGivesItsEntryToEveryTarget() {
        WiredMatchFurniSetting only = new WiredMatchFurniSetting(9, "3", 0, 4, 4);

        java.util.List<WiredMatchFurniSetting> dealt =
                WiredEffectMatchFurni.dealSpots(java.util.List.of(only), 4, new java.util.Random(1));

        assertEquals(java.util.List.of(only, only, only, only), dealt);
    }
}
