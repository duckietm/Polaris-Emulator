package polaris.hotel.navigation;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class NavigatorFavoriteFilter extends NavigatorFilter {
    public static final String name = "favorites";

    public NavigatorFavoriteFilter() {
        super(name);
    }

    @Override
    public List<SearchResultList> getResult(User user) {
        List<SearchResultList> resultLists = new ArrayList<>();
        List<Room> rooms = Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("favorites", user);
        resultLists.add(new SearchResultList(
                0,
                "favorites",
                "",
                SearchAction.NONE,
                user.getUserStats().navigatorWindowSettings.getListModeForCategory("favorites", ListMode.LIST),
                user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("popular", DisplayMode.VISIBLE),
                rooms,
                true,
                true,
                DisplayOrder.ACTIVITY,
                -1));
        return resultLists;
    }
}
