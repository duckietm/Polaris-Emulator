package polaris.hotel.navigation;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

public class NavigatorPublicFilter extends NavigatorFilter {
    public static final String name = "official_view";

    public NavigatorPublicFilter() {
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
                "official-root",
                "",
                SearchAction.NONE,
                user.getUserStats()
                        .navigatorWindowSettings
                        .getListModeForCategory("official-root", ListMode.THUMBNAILS),
                user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory("official-root"),
                Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory("official-root", user),
                false,
                showInvisible,
                DisplayOrder.ORDER_NUM,
                -1));
        i++;

        for (NavigatorPublicCategory category : Emulator.getGameEnvironment()
                .getNavigatorManager()
                .publicCategories
                .values()) {
            if (!category.rooms.isEmpty()) {
                resultLists.add(new SearchResultList(
                        i,
                        "",
                        category.name,
                        SearchAction.NONE,
                        user.getUserStats()
                                .navigatorWindowSettings
                                .getListModeForCategory(category.name, category.image),
                        user.getUserStats().navigatorWindowSettings.getDisplayModeForCategory(category.name),
                        category.rooms,
                        true,
                        showInvisible,
                        DisplayOrder.ORDER_NUM,
                        category.order));
                i++;
            }
        }

        return resultLists;
    }
}
