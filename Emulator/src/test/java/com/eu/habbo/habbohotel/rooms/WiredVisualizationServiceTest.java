package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredVisualizationServiceTest {
    private static final WiredHighlight GLOW = new WiredHighlight(1, 60, 0x00FF00, 0, 2);
    private static final WiredHighlight NEON = new WiredHighlight(5, 30, 0xFF00FF, 1, 0);

    @Test
    void highlightRulesFollowTheOfficialEditor() {
        assertEquals(new WiredHighlight(5, 100, 0xFFFFFF, 1, 4), new WiredHighlight(9, 400, 0x7FFFFFFF, 7, 9));
        assertEquals(new WiredHighlight(0, 0, 0, 0, 0), new WiredHighlight(-3, -1, -5, -1, -2));
        assertEquals(0, new WiredHighlight(WiredHighlight.TYPE_DARKEN, 50, 0x123456, 0, 0).color());
        WiredHighlight brighten = new WiredHighlight(WiredHighlight.TYPE_BRIGHTEN, 38, 0x123456, 0, 0);
        assertEquals(0xFFFFFF, brighten.color());
        assertEquals(WiredHighlight.ANIMATION_PULSE, brighten.animation());
        assertEquals(38, brighten.strength());
        assertEquals(76, brighten.wireStrength());
        assertEquals(60, GLOW.wireStrength());
        assertTrue(new WiredHighlight(3, 0, 0xFF0000, 0, 0).isCleared());
    }

    @Test
    void viewerStateWinsOverTheRoomWideOneAndStaysPrivate() {
        Room room = room(61);
        HabboItem floor = item(room, 6101, FurnitureType.FLOOR);
        HabboItem wall = item(room, 6102, FurnitureType.WALL);
        WiredVisualizationService service = new WiredVisualizationService(room, 10);

        assertEquals(
                List.of(6101, 6102),
                service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(wall, floor), GLOW));
        service.highlightFurni(7, List.of(floor), NEON);

        assertEquals(
                List.of(new WiredVisualizationEntry<>(6101, NEON), new WiredVisualizationEntry<>(-6102, GLOW)),
                service.furniHighlightSnapshot(7));
        assertEquals(
                List.of(new WiredVisualizationEntry<>(6101, GLOW), new WiredVisualizationEntry<>(-6102, GLOW)),
                service.furniHighlightSnapshot(8));
        assertEquals(3, service.stateCount());
    }

    @Test
    void aZeroStrengthClearsTheEntry() {
        Room room = room(62);
        HabboItem floor = item(room, 6201, FurnitureType.FLOOR);
        RoomUnit unit = unit(room, 4);
        WiredVisualizationService service = new WiredVisualizationService(room, 10);
        WiredHighlight cleared = new WiredHighlight(1, 0, 0, 0, 0);

        service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(floor), GLOW);
        service.highlightUsers(9, List.of(unit), GLOW);
        service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(floor), cleared);
        service.highlightUsers(9, List.of(unit), cleared);

        assertEquals(0, service.stateCount());
        assertTrue(service.furniHighlightSnapshot(9).isEmpty());
        assertTrue(service.userHighlightSnapshot(9).isEmpty());
        assertEquals(
                List.of(new WiredVisualizationEntry<>(6201, WiredHighlight.NONE)),
                service.effectiveFurniHighlights(9, List.of(6201)));
    }

    @Test
    void aViewerMayStillSeeAnAvatarThatIsTransparentForEveryoneElse() {
        Room room = room(63);
        RoomUnit unit = unit(room, 5);
        WiredVisualizationService service = new WiredVisualizationService(room, 10);
        WiredUserTransparency hidden = new WiredUserTransparency(100, true, 1);

        service.setUserTransparency(WiredVisualizationService.EVERYONE, List.of(unit), hidden);
        service.setUserTransparency(11, List.of(unit), WiredUserTransparency.NONE);

        assertEquals(
                List.of(new WiredVisualizationEntry<>(5, WiredUserTransparency.NONE)),
                service.userTransparencySnapshot(11));
        assertEquals(List.of(new WiredVisualizationEntry<>(5, hidden)), service.userTransparencySnapshot(12));

        service.setUserTransparency(WiredVisualizationService.EVERYONE, List.of(unit), WiredUserTransparency.NONE);
        assertTrue(service.userTransparencySnapshot(12).isEmpty());
        assertEquals(1, service.stateCount());
    }

    @Test
    void onlyAvatarsAndFurniStillInTheRoomAreTaken() {
        Room room = room(64);
        HabboItem stale = mock(HabboItem.class);
        when(stale.getId()).thenReturn(6401);
        when(stale.getRoomId()).thenReturn(64);
        when(stale.getBaseItem()).thenReturn(mock(Item.class));
        RoomUnit stranger = mock(RoomUnit.class);
        when(stranger.getId()).thenReturn(8);
        WiredVisualizationService service = new WiredVisualizationService(room, 10);

        assertTrue(service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(stale), GLOW)
                .isEmpty());
        assertTrue(service.highlightUsers(WiredVisualizationService.EVERYONE, List.of(stranger), GLOW)
                .isEmpty());
        assertEquals(0, service.stateCount());
    }

    @Test
    void theStateBudgetIsRoomWide() {
        Room room = room(65);
        HabboItem first = item(room, 6501, FurnitureType.FLOOR);
        HabboItem second = item(room, 6502, FurnitureType.FLOOR);
        RoomUnit unit = unit(room, 2);
        WiredVisualizationService service = new WiredVisualizationService(room, 2);

        assertEquals(
                List.of(6501, 6502),
                service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(first, second), GLOW));
        assertTrue(service.highlightUsers(3, List.of(unit), GLOW).isEmpty());
        assertTrue(service.setUserTransparency(3, List.of(unit), new WiredUserTransparency(50, false, 0))
                .isEmpty());
        assertEquals(List.of(6501), service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(first), NEON));
        assertEquals(2, service.stateCount());
    }

    @Test
    void pickupLeaveAndDisposeReleaseTheirStates() {
        Room room = room(66);
        HabboItem floor = item(room, 6601, FurnitureType.FLOOR);
        RoomUnit leaving = unit(room, 3);
        RoomUnit staying = unit(room, 4);
        WiredVisualizationService service = new WiredVisualizationService(room, 20);

        service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(floor), GLOW);
        service.highlightFurni(21, List.of(floor), NEON);
        service.highlightUsers(WiredVisualizationService.EVERYONE, List.of(leaving, staying), GLOW);
        service.highlightUsers(21, List.of(staying), NEON);
        service.highlightUsers(22, List.of(leaving), NEON);
        service.setUserTransparency(22, List.of(leaving, staying), new WiredUserTransparency(40, false, 0));
        assertEquals(8, service.stateCount());

        service.forgetItem(floor);
        assertEquals(6, service.stateCount());
        assertTrue(service.furniHighlightSnapshot(21).isEmpty());

        // user 21 sits on room index 3: what they were shown and what was shown on them both go
        service.forgetUser(21, 3);
        assertEquals(List.of(new WiredVisualizationEntry<>(4, GLOW)), service.userHighlightSnapshot(21));
        assertEquals(
                List.of(new WiredVisualizationEntry<>(4, new WiredUserTransparency(40, false, 0))),
                service.userTransparencySnapshot(22));
        assertEquals(2, service.stateCount());

        service.dispose();
        assertEquals(0, service.stateCount());
        assertTrue(service.userHighlightSnapshot(22).isEmpty());
        assertTrue(service.highlightFurni(WiredVisualizationService.EVERYONE, List.of(floor), GLOW)
                .isEmpty());
    }

    static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        return room;
    }

    static HabboItem item(Room room, int itemId, FurnitureType type) {
        int roomId = room.getId();
        HabboItem item = mock(HabboItem.class);
        Item baseItem = mock(Item.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getRoomId()).thenReturn(roomId);
        when(item.getBaseItem()).thenReturn(baseItem);
        when(baseItem.getType()).thenReturn(type);
        when(room.getHabboItem(itemId)).thenReturn(item);
        return item;
    }

    static RoomUnit unit(Room room, int roomIndex) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(roomIndex);
        Habbo habbo = mock(Habbo.class);
        when(room.getHabbo(unit)).thenReturn(habbo);
        return unit;
    }
}
