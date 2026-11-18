package com.eu.habbo.habbohotel.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.plugin.HabboPlugin;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavigatorRoleplaySupportTest {

    @Test
    void includesRoleplayNavigatorViewOnlyWhenPluginIsLoaded() {
        List<HabboPlugin> plugins = new ArrayList<>();
        assertFalse(NavigatorRoleplaySupport.hasRoleplayPlugin(plugins));
        assertEquals(
                List.of("official_view", "hotel_view", "roomads_view", "myworld_view"),
                NavigatorRoleplaySupport.getNavigatorViewCodes(false));

        plugins.add(new RoleplayWebsocketsRoomsInfo());

        assertTrue(NavigatorRoleplaySupport.hasRoleplayPlugin(plugins));
        assertEquals(
                List.of("official_view", "hotel_view", "roleplay_view", "roomads_view", "myworld_view"),
                NavigatorRoleplaySupport.getNavigatorViewCodes(true));
    }

    @Test
    void categoryFilteringKeepsOnlyMatchingRoomsAndDoesNotMutateSourceResults() {
        Room roleplayRoom = mock(Room.class);
        Room regularRoom = mock(Room.class);
        when(roleplayRoom.getCategory()).thenReturn(100);
        when(regularRoom.getCategory()).thenReturn(10);

        List<Room> rooms = new ArrayList<>(List.of(roleplayRoom, regularRoom));
        SearchResultList source = new SearchResultList(
                0,
                "rooms",
                "",
                SearchAction.MORE,
                ListMode.LIST,
                DisplayMode.VISIBLE,
                rooms,
                true,
                false,
                DisplayOrder.ORDER_NUM,
                0);

        List<SearchResultList> roleplayResults =
                NavigatorRoleplaySupport.filterRoomsByCategory(List.of(source), 100, true);
        List<SearchResultList> allOtherResults =
                NavigatorRoleplaySupport.filterRoomsByCategory(List.of(source), 100, false);

        assertEquals(List.of(roleplayRoom), roleplayResults.get(0).rooms);
        assertEquals(List.of(regularRoom), allOtherResults.get(0).rooms);
        assertNotSame(source, roleplayResults.get(0));
        assertEquals(List.of(roleplayRoom, regularRoom), source.rooms);
    }

    @Test
    void removesResultGroupsThatHaveNoRoomsAfterCategoryFiltering() {
        Room regularRoom = mock(Room.class);
        when(regularRoom.getCategory()).thenReturn(10);

        SearchResultList source = new SearchResultList(
                0,
                "rooms",
                "",
                SearchAction.MORE,
                ListMode.LIST,
                DisplayMode.VISIBLE,
                new ArrayList<>(List.of(regularRoom)),
                true,
                false,
                DisplayOrder.ORDER_NUM,
                0);

        assertTrue(NavigatorRoleplaySupport.filterRoomsByCategory(List.of(source), 100, true)
                .isEmpty());
    }

    @Test
    void filtersBothNavigatorViewsOnlyWhenRoleplayPluginIsEnabled() {
        Room roleplayRoom = mock(Room.class);
        Room regularRoom = mock(Room.class);
        when(roleplayRoom.getCategory()).thenReturn(100);
        when(regularRoom.getCategory()).thenReturn(10);

        SearchResultList source = new SearchResultList(
                0,
                "rooms",
                "",
                SearchAction.MORE,
                ListMode.LIST,
                DisplayMode.VISIBLE,
                new ArrayList<>(List.of(roleplayRoom, regularRoom)),
                true,
                false,
                DisplayOrder.ORDER_NUM,
                0);
        List<SearchResultList> sourceLists = List.of(source);

        assertEquals(
                List.of(roleplayRoom),
                NavigatorRoleplaySupport.filterRoomsForView(
                                sourceLists, NavigatorRoleplaySupport.ROLEPLAY_VIEW, true, 100)
                        .get(0)
                        .rooms);
        assertEquals(
                List.of(regularRoom),
                NavigatorRoleplaySupport.filterRoomsForView(
                                sourceLists, NavigatorRoleplaySupport.ALL_ROOMS_VIEW, true, 100)
                        .get(0)
                        .rooms);
        List<SearchResultList> unchanged = NavigatorRoleplaySupport.filterRoomsForView(
                sourceLists, NavigatorRoleplaySupport.ROLEPLAY_VIEW, false, 100);
        assertSame(sourceLists, unchanged);
    }
}
