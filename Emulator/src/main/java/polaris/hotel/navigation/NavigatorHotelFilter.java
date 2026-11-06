package polaris.hotel.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomCategory;
import polaris.hotel.users.User;

public class NavigatorHotelFilter extends NavigatorFilter {
    public static final String name = "hotel_view";

    public NavigatorHotelFilter() {
        super(name);
    }

    @Override
    public List<SearchResultList> getResult(User user) {
        boolean showInvisible =
                user.hasPermission(Permission.ACC_ENTERANYROOM) || user.hasPermission(Permission.ACC_ANYROOMOWNER);
        List<SearchResultList> resultLists = new ArrayList<>();

        int i = 0;
        resultLists.add(new SearchResultList(
                i,
                "popular",
                "",
                SearchAction.NONE,
                user.getUserStats()
                        .navigatorWindowSettings
                        .getListModeForCategory(
                                "popular",
                                ListMode.fromType(Emulator.getConfig().getInt("hotel.navigator.popular.listtype"))),
                user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("popular"),
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("popular", user),
                false,
                showInvisible,
                DisplayOrder.ORDER_NUM,
                -1));
        i++;

        for (Map.Entry<Integer, List<Room>> set : Emulator.getGameEnvironment()
                .getRoomManager()
                .getPopularRoomsByCategory(Emulator.getConfig().getInt("hotel.navigator.popular.category.maxresults"))
                .entrySet()) {
            if (!set.getValue().isEmpty()) {
                RoomCategory category =
                        Emulator.getGameEnvironment().getRoomManager().getCategory(set.getKey());
                if (category != null) {
                    resultLists.add(new SearchResultList(
                            i,
                            category.getCaption(),
                            category.getCaption(),
                            SearchAction.MORE,
                            user.getUserStats()
                                    .navigatorWindowSettings
                                    .getListModeForCategory(category.getCaptionSave()),
                            user.getUserStats()
                                    .navigatorWindowSettings
                                    .getDisplayModeForCategory(category.getCaptionSave()),
                            set.getValue(),
                            true,
                            showInvisible,
                            DisplayOrder.ORDER_NUM,
                            category.getOrder()));
                }
                i++;
            }
        }

        return resultLists;
    }

    @Override
    public List<SearchResultList> getResult(
            User user, NavigatorFilterField filterField, String value, int roomCategory) {
        boolean showInvisible =
                user.hasPermission(Permission.ACC_ENTERANYROOM) || user.hasPermission(Permission.ACC_ANYROOMOWNER);
        if (!filterField.databaseQuery.isEmpty()) {
            List<SearchResultList> resultLists = new ArrayList<>();
            int i = 0;

            for (Map.Entry<Integer, List<Room>> set : Emulator.getGameEnvironment()
                    .getRoomManager()
                    .findRooms(filterField, value, roomCategory, showInvisible)
                    .entrySet()) {
                if (!set.getValue().isEmpty()) {
                    RoomCategory category =
                            Emulator.getGameEnvironment().getRoomManager().getCategory(set.getKey());

                    if (category != null) {
                        resultLists.add(new SearchResultList(
                                i,
                                category.getCaptionSave(),
                                category.getCaption(),
                                SearchAction.MORE,
                                user.getUserStats()
                                        .navigatorWindowSettings
                                        .getListModeForCategory(category.getCaptionSave()),
                                user.getUserStats()
                                        .navigatorWindowSettings
                                        .getDisplayModeForCategory(category.getCaptionSave()),
                                set.getValue(),
                                true,
                                showInvisible,
                                DisplayOrder.ACTIVITY,
                                category.getOrder()));
                    }
                    i++;
                }
            }

            return resultLists;
        } else {
            return this.getResult(user);
        }
    }
}
