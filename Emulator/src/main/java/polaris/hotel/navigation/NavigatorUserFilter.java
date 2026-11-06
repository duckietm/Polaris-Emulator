package polaris.hotel.navigation;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class NavigatorUserFilter extends NavigatorFilter {
    public static final String name = "myworld_view";

    public NavigatorUserFilter() {
        super(name);
    }

    @Override
    public List<SearchResultList> getResult(User user) {
        int i = 0;
        List<SearchResultList> resultLists = new ArrayList<>();

        List<Room> rooms = Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("my", user);
        resultLists.add(new SearchResultList(
                i,
                "my",
                "",
                SearchAction.NONE,
                user.getUserStats().navigatorWindowSettings.getListModeForCategory("my"),
                user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("my"),
                rooms,
                true,
                true,
                DisplayOrder.ORDER_NUM,
                i));
        i++;

        List<Room> favoriteRooms =
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("favorites", user);
        if (!favoriteRooms.isEmpty()) {
            resultLists.add(new SearchResultList(
                    i,
                    "favorites",
                    "",
                    SearchAction.NONE,
                    user.getUserStats().navigatorWindowSettings.getListModeForCategory("favorites"),
                    user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("favorites"),
                    favoriteRooms,
                    true,
                    true,
                    DisplayOrder.ORDER_NUM,
                    i));
            i++;
        }

        List<Room> frequentlyVisited =
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("history_freq", user);
        if (!frequentlyVisited.isEmpty()) {
            resultLists.add(new SearchResultList(
                    i,
                    "history_freq",
                    "",
                    SearchAction.NONE,
                    user.getUserStats().navigatorWindowSettings.getListModeForCategory("history_freq"),
                    user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("history_freq"),
                    frequentlyVisited,
                    true,
                    true,
                    DisplayOrder.ORDER_NUM,
                    i));
            i++;
        }

        List<Room> groupRooms =
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("my_groups", user);
        if (!groupRooms.isEmpty()) {
            resultLists.add(new SearchResultList(
                    i,
                    "my_groups",
                    "",
                    SearchAction.NONE,
                    user.getUserStats().navigatorWindowSettings.getListModeForCategory("my_groups"),
                    user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("my_groups"),
                    groupRooms,
                    true,
                    true,
                    DisplayOrder.ORDER_NUM,
                    i));
            i++;
        }

        List<Room> friendRooms =
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("with_friends", user);
        if (!friendRooms.isEmpty()) {
            resultLists.add(new SearchResultList(
                    i,
                    "with_friends",
                    "",
                    SearchAction.NONE,
                    user.getUserStats().navigatorWindowSettings.getListModeForCategory("with_friends"),
                    user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("with_friends"),
                    friendRooms,
                    true,
                    true,
                    DisplayOrder.ORDER_NUM,
                    i));
            i++;
        }

        List<Room> rightRooms =
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("with_rights", user);
        if (!rightRooms.isEmpty()) {
            resultLists.add(new SearchResultList(
                    i,
                    "with_rights",
                    "",
                    SearchAction.NONE,
                    user.getUserStats().navigatorWindowSettings.getListModeForCategory("with_rights"),
                    user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("with_rights"),
                    rightRooms,
                    true,
                    true,
                    DisplayOrder.ORDER_NUM,
                    i));
        }

        return resultLists;
    }
}
