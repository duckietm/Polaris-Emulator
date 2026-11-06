package polaris.hotel.navigation;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

public class NavigatorRoomAdsFilter extends NavigatorFilter {
    public static final String name = "roomads_view";

    public NavigatorRoomAdsFilter() {
        super(name);
    }

    @Override
    public List<SearchResultList> getResult(User user) {
        boolean showInvisible =
                user.hasPermission(Permission.ACC_ENTERANYROOM) || user.hasPermission(Permission.ACC_ANYROOMOWNER);
        List<SearchResultList> resultList = new ArrayList<>();
        resultList.add(new SearchResultList(
                0,
                "categories",
                "",
                SearchAction.NONE,
                user.getUserStats().navigatorWindowSettings.getListModeForCategory("categories", ListMode.LIST),
                user.getUserStats()
                        .navigatorWindowSettings
                        .getDisplayModeForCategory("official-root", DisplayMode.VISIBLE),
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("categories", user),
                false,
                showInvisible,
                DisplayOrder.ACTIVITY,
                0));
        return resultList;
    }
}
