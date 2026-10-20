package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredActionCodes;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** The official "place furni": snapshot of the picks, sources, and temporary furni with no rows. */
class WiredEffectPlaceTemporaryFurniTest {

    @Test
    void aTemporaryFurniHasNoOwnerNoRowAndAHighId() {
        Item base = mock(Item.class);
        ItemInteraction interaction = mock(ItemInteraction.class);
        when(interaction.getType()).thenAnswer(invocation -> InteractionDefault.class);
        when(base.getInteractionType()).thenReturn(interaction);

        HabboItem item = new ItemManager().createTemporaryItem(base, "1");

        assertTrue(item.isTemporary());
        assertEquals(0, item.getUserId());
        assertTrue(ItemManager.isTemporaryItemId(item.getId()), "in the official temporary band: " + item.getId());
        item.needsUpdate(true);
        item.run(); // must not reach the database
        assertFalse(item.needsUpdate());
    }

    @Test
    void itSnapshotsThePicks() throws Exception {
        Room room = mock(Room.class);
        HabboItem chair = floorItem(11, 501, 3, 4, 1.5, 2, "1");
        when(room.getHabboItem(anyInt())).thenReturn(null);
        when(room.getHabboItem(11)).thenReturn(chair);

        WiredEffectPlaceTemporaryFurni box = new WiredEffectPlaceTemporaryFurni(1, 1, mock(Item.class), "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(7)).thenReturn(room);

        WiredSettings settings =
                new WiredSettings(new int[] {1, 1, 2, 3, -2, 150, 0, 0, 0, 0}, "", new int[] {11, 12}, -1);
        settings.setFurniSourceTypes(new int[] {WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SIGNAL});
        settings.setUserSourceTypes(new int[] {WiredSourceUtil.SOURCE_CLICKED_USER});
        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            assertTrue(box.saveData(settings, null));
        }

        String saved = box.getWiredData();
        assertTrue(saved.contains("\"baseItemId\":501"), saved);
        assertTrue(saved.contains("\"offsetAltitude\":150"), saved);
        assertArrayEquals(
                new int[] {
                    WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SIGNAL, WiredSourceUtil.SOURCE_TRIGGER
                },
                box.officialFurniSources());
        assertArrayEquals(
                new int[] {WiredSourceUtil.SOURCE_CLICKED_USER, WiredSourceUtil.SOURCE_TRIGGER},
                box.officialUserSources());
    }

    @Test
    void applyingTheSnapshotPutsThePicksBackWhereAndHowTheyWereSaved() throws Exception {
        Room room = mock(Room.class);
        HabboItem chair = floorItem(11, 501, 3, 4, 1.5, 2, "1");
        HabboItem lamp = floorItem(12, 502, 5, 6, 0.0, 4, "");
        when(room.getHabboItem(11)).thenReturn(chair);
        when(room.getHabboItem(12)).thenReturn(lamp);

        WiredEffectPlaceTemporaryFurni box = new WiredEffectPlaceTemporaryFurni(1, 1, mock(Item.class), "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(7)).thenReturn(room);
        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            assertTrue(box.saveData(new WiredSettings(new int[10], "", new int[] {11, 12}, -1), null));
        }

        List<WiredMatchFurniSetting> settings = new ArrayList<>(box.getMatchFurniSettings());
        assertEquals(2, settings.size());
        WiredMatchFurniSetting first = settings.get(0);
        assertEquals(11, first.item_id);
        assertEquals("1", first.state);
        assertEquals(3, first.x);
        assertEquals(4, first.y);
        assertEquals(1.5, first.z);
        assertEquals(2, first.rotation);
        assertEquals(" ", settings.get(1).state, "an empty state is left alone");
        assertTrue(box.shouldMatchState() && box.shouldMatchPosition());
        assertTrue(box.shouldMatchRotation() && box.shouldMatchAltitude());
    }

    @Test
    void spawningWithAVariableKeepsBothVariablesAndNeedsAWritableFurniVariable() throws Exception {
        Room room = mock(Room.class);
        RoomFurniVariableManager furniVariables = mock(RoomFurniVariableManager.class);
        WiredVariableDefinitionInfo writable = mock(WiredVariableDefinitionInfo.class);
        when(writable.hasValue()).thenReturn(true);
        when(furniVariables.getDefinitionInfo(40)).thenReturn(writable);
        when(room.getFurniVariableManager()).thenReturn(furniVariables);

        WiredEffectPlaceTemporaryFurni box = new WiredEffectPlaceTemporaryFurni(1, 1, mock(Item.class), "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(7)).thenReturn(room);

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);

            assertTrue(box.saveData(
                    new WiredSettings(new int[] {0, 0, 0, 0, 0, 0, 1, 1, 0, -10}, "40\t41", new int[0], -1), null));
            String saved = box.getWiredData();
            assertTrue(saved.contains("\"spawnVariableId\":40"), saved);
            assertTrue(saved.contains("\"valueVariableId\":41"), saved);
            assertTrue(saved.contains("\"valueTarget\":-10"), saved);
            // AIR: the two variables go out as the variable ids, the string stays empty.
            assertArrayEquals(new String[] {"furni:40", "room:41"}, box.officialVariableIds());

            WiredSettings fromIds = new WiredSettings(new int[] {0, 0, 0, 0, 0, 0, 1, 1, 0, -10}, "", new int[0], -1);
            fromIds.setVariableIds(new String[] {"furni:40", "room:42"});
            assertTrue(box.saveData(fromIds, null));
            assertArrayEquals(new String[] {"furni:40", "room:42"}, box.officialVariableIds());

            // Not a furni variable of this room.
            assertThrows(
                    WiredSaveException.class,
                    () -> box.saveData(
                            new WiredSettings(new int[] {0, 0, 0, 0, 0, 0, 1, 0, 5, 0}, "99\t0", new int[0], -1),
                            null));
            // The value comes from a variable that was not picked.
            assertThrows(
                    WiredSaveException.class,
                    () -> box.saveData(
                            new WiredSettings(new int[] {0, 0, 0, 0, 0, 0, 1, 1, 0, 0}, "40\t0", new int[0], -1),
                            null));
        }
    }

    @Test
    void temporaryFurniIdsAreKnownWithoutTheItem() {
        assertTrue(ItemManager.isTemporaryItemId(2_147_401_728));
        assertTrue(ItemManager.isTemporaryItemId(2_147_418_111));
        assertFalse(ItemManager.isTemporaryItemId(2_147_418_112));
        assertFalse(ItemManager.isTemporaryItemId(2_050_000_123));
        assertFalse(ItemManager.isTemporaryItemId(123));
    }

    @Test
    void aWiredBoxAsTemplateIsRefused() {
        Room room = mock(Room.class);
        HabboItem wiredBox = mock(WiredEffectPlaceTemporaryFurni.class);
        Item boxBase = mock(Item.class);
        when(boxBase.getType()).thenReturn(FurnitureType.FLOOR);
        when(wiredBox.getBaseItem()).thenReturn(boxBase);
        when(room.getHabboItem(12)).thenReturn(wiredBox);

        WiredEffectPlaceTemporaryFurni box = new WiredEffectPlaceTemporaryFurni(1, 1, mock(Item.class), "", 0, 0);
        box.setRoomId(7);
        GameEnvironment environment = mock(GameEnvironment.class, Answers.RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoom(7)).thenReturn(room);

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::gameEnvironment).thenReturn(environment);
            WiredSaveException refused = assertThrows(
                    WiredSaveException.class,
                    () -> box.saveData(new WiredSettings(new int[10], "", new int[] {12}, -1), null));
            assertEquals("wiredfurni.error.furni_can_not_be_cloned", refused.getMessage());
        }
    }

    @Test
    void gatesAndRollersTakeNoCopies() {
        assertTrue(WiredEffectPlaceTemporaryFurni.blocksCopies(mock(InteractionGate.class)));
        assertTrue(WiredEffectPlaceTemporaryFurni.blocksCopies(mock(InteractionOneWayGate.class)));
        assertTrue(WiredEffectPlaceTemporaryFurni.blocksCopies(mock(InteractionRoller.class)));
        assertFalse(WiredEffectPlaceTemporaryFurni.blocksCopies(mock(InteractionDefault.class)));
        assertFalse(WiredEffectPlaceTemporaryFurni.blocksCopies(null));
    }

    @Test
    void theOfficialBoxIs55AndOurSpawnBoxIsCustom() {
        assertEquals(55, WiredActionCodes.official(WiredEffectType.PLACE_FURNI.code));
        assertEquals(1155, WiredActionCodes.official(WiredEffectType.SPAWN_FURNI.code));
        assertEquals(WiredEffectType.SPAWN_FURNI, WiredEffectPlaceFurni.type);
    }

    private static HabboItem floorItem(int id, int baseId, int x, int y, double z, int rotation, String extradata) {
        Item base = mock(Item.class);
        when(base.getId()).thenReturn(baseId);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(item.getZ()).thenReturn(z);
        when(item.getRotation()).thenReturn(rotation);
        when(item.getExtradata()).thenReturn(extradata);
        return item;
    }
}
