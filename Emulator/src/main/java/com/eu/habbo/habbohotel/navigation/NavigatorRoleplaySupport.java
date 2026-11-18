package com.eu.habbo.habbohotel.navigation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.plugin.HabboPlugin;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NavigatorRoleplaySupport {
    public static final String ROLEPLAY_VIEW = "roleplay_view";
    public static final String ALL_ROOMS_VIEW = "hotel_view";

    private static final String ROLEPLAY_PLUGIN_CLASS = RoleplayWebsocketsRoomsInfo.class.getName();
    private static final String ROLEPLAY_CATEGORY_CONFIG = "global_room.category.roleplay.id";

    private NavigatorRoleplaySupport() {}

    public static boolean isRoleplayPluginEnabled() {
        return hasRoleplayPlugin(Emulator.getPluginManager().getPlugins());
    }

    static boolean hasRoleplayPlugin(Collection<? extends HabboPlugin> plugins) {
        return plugins.stream()
                .anyMatch(
                        plugin -> ROLEPLAY_PLUGIN_CLASS.equals(plugin.getClass().getName()));
    }

    public static int getRoleplayCategoryId() {
        return RoleplayWebsocketsRoomsInfo.getConfig().getInt(ROLEPLAY_CATEGORY_CONFIG, 100);
    }

    public static List<String> getNavigatorViewCodes(boolean roleplayPluginEnabled) {
        if (roleplayPluginEnabled) {
            return List.of("official_view", ALL_ROOMS_VIEW, ROLEPLAY_VIEW, "roomads_view", "myworld_view");
        }

        return List.of("official_view", ALL_ROOMS_VIEW, "roomads_view", "myworld_view");
    }

    public static List<SearchResultList> filterRoomsByCategory(
            List<SearchResultList> resultLists, int categoryId, boolean includeCategory) {
        List<SearchResultList> filteredLists = new ArrayList<>();

        for (SearchResultList resultList : resultLists) {
            List<Room> filteredRooms = new ArrayList<>();
            for (Room room : resultList.rooms) {
                if ((room.getCategory() == categoryId) == includeCategory) {
                    filteredRooms.add(room);
                }
            }

            if (!filteredRooms.isEmpty()) {
                filteredLists.add(new SearchResultList(
                        resultList.order,
                        resultList.code,
                        resultList.query,
                        resultList.action,
                        resultList.mode,
                        resultList.hidden,
                        filteredRooms,
                        resultList.filter,
                        resultList.showInvisible,
                        resultList.displayOrder,
                        resultList.categoryOrder));
            }
        }

        return filteredLists;
    }

    public static List<SearchResultList> filterRoomsForView(
            List<SearchResultList> resultLists, String viewCode, boolean roleplayPluginEnabled, int categoryId) {
        if (!roleplayPluginEnabled) {
            return resultLists;
        }

        if (ROLEPLAY_VIEW.equals(viewCode)) {
            return filterRoomsByCategory(resultLists, categoryId, true);
        }

        if (ALL_ROOMS_VIEW.equals(viewCode)) {
            return filterRoomsByCategory(resultLists, categoryId, false);
        }

        return resultLists;
    }
}
