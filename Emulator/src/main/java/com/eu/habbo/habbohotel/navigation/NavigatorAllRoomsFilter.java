package com.eu.habbo.habbohotel.navigation;

import com.eu.habbo.habbohotel.users.Habbo;
import java.util.List;

public class NavigatorAllRoomsFilter extends NavigatorHotelFilter {
    @Override
    public List<SearchResultList> getResult(Habbo habbo) {
        return this.excludeRoleplayRooms(super.getResult(habbo));
    }

    @Override
    public List<SearchResultList> getResult(
            Habbo habbo, NavigatorFilterField filterField, String value, int roomCategory) {
        return this.excludeRoleplayRooms(super.getResult(habbo, filterField, value, roomCategory));
    }

    private List<SearchResultList> excludeRoleplayRooms(List<SearchResultList> resultLists) {
        boolean roleplayPluginEnabled = NavigatorRoleplaySupport.isRoleplayPluginEnabled();
        int categoryId = roleplayPluginEnabled ? NavigatorRoleplaySupport.getRoleplayCategoryId() : -1;
        return NavigatorRoleplaySupport.filterRoomsForView(
                resultLists, NavigatorHotelFilter.name, roleplayPluginEnabled, categoryId);
    }
}
