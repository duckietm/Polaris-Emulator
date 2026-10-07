package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionGate;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

class BuildersClubRecolorServiceTest {

    @Test
    void onlyNumberedColourVariantsHaveAFamily() {
        assertEquals("bc_cone", BuildersClubRecolorService.familyOf("bc_cone*13"));
        assertEquals("bc_alpha1_a", BuildersClubRecolorService.familyOf("bc_alpha1_a*1"));
        assertNull(BuildersClubRecolorService.familyOf("bc_cone"));
        assertNull(BuildersClubRecolorService.familyOf("bc_cone*"));
        assertNull(BuildersClubRecolorService.familyOf("*3"));
        assertNull(BuildersClubRecolorService.familyOf("bc_cone*1a"));
        assertNull(BuildersClubRecolorService.familyOf(null));
    }

    @Test
    void onlyTheColourMayDiffer() {
        Item red = block(1, 1, 1, 1.0, InteractionDefault.class);
        Item blue = block(2, 1, 1, 1.0, InteractionDefault.class);

        assertTrue(BuildersClubRecolorService.interchangeable(red, blue));
        assertFalse(BuildersClubRecolorService.interchangeable(red, red));
        assertFalse(BuildersClubRecolorService.interchangeable(red, null));
        assertFalse(BuildersClubRecolorService.interchangeable(red, block(3, 2, 1, 1.0, InteractionDefault.class)));
        assertFalse(BuildersClubRecolorService.interchangeable(red, block(4, 1, 1, 0.5, InteractionDefault.class)));
        assertFalse(BuildersClubRecolorService.interchangeable(red, block(5, 1, 1, 1.0, InteractionGate.class)));

        Item wall = block(6, 1, 1, 1.0, InteractionDefault.class);
        when(wall.getType()).thenReturn(FurnitureType.WALL);
        assertFalse(BuildersClubRecolorService.interchangeable(red, wall));
    }

    @Test
    void requestsFasterThanTheCooldownAreDropped() {
        int userId = 980_001;
        int roomId = 980_101;
        assertTrue(BuildersClubRecolorService.admit(userId, roomId, 10_000L));
        assertFalse(
                BuildersClubRecolorService.admit(userId, roomId, 10_000L + BuildersClubRecolorService.COOLDOWN_MS - 1));
        assertTrue(
                BuildersClubRecolorService.admit(userId, roomId, 10_000L + 2 * BuildersClubRecolorService.COOLDOWN_MS));
    }

    @Test
    void bigRecolorsMakeTheUserAndTheRoomWaitLonger() {
        int userId = 980_002;
        int otherUser = 980_003;
        int roomId = 980_102;
        long now = 50_000L;
        long wait = 500 * BuildersClubRecolorService.COOLDOWN_PER_ITEM_MS;

        assertTrue(BuildersClubRecolorService.admit(userId, roomId, now));
        BuildersClubRecolorService.charge(userId, roomId, now, 500);

        assertFalse(BuildersClubRecolorService.admit(userId, roomId, now + wait - 1));
        assertFalse(BuildersClubRecolorService.admit(otherUser, roomId, now + wait - 1));
        assertTrue(BuildersClubRecolorService.admit(otherUser, roomId, now + wait));
        assertTrue(
                BuildersClubRecolorService.admit(userId, roomId, now + wait + BuildersClubRecolorService.COOLDOWN_MS));
    }

    private static Item block(int id, int width, int length, double height, Class<? extends HabboItem> type) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getType()).thenReturn(FurnitureType.FLOOR);
        when(item.getWidth()).thenReturn(width);
        when(item.getLength()).thenReturn(length);
        when(item.getHeight()).thenReturn(height);
        when(item.allowStack()).thenReturn(true);
        when(item.getStateCount()).thenReturn(1);
        when(item.getInteractionType()).thenReturn(new ItemInteraction("default", type));
        return item;
    }
}
