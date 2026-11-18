package com.eu.habbo.habbohotel.navigation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Collections;
import java.util.List;

public class NavigatorRoleplayFilter extends NavigatorFilter {
    private final NavigatorHotelFilter hotelFilter;

    public NavigatorRoleplayFilter(NavigatorHotelFilter hotelFilter) {
        super(NavigatorRoleplaySupport.ROLEPLAY_VIEW);
        this.hotelFilter = hotelFilter;
    }

    @Override
    public List<SearchResultList> getResult(Habbo habbo) {
        if (!NavigatorRoleplaySupport.isRoleplayPluginEnabled()) {
            return Collections.emptyList();
        }

        int categoryId = NavigatorRoleplaySupport.getRoleplayCategoryId();
        NavigatorFilterField anything = Emulator.getGameEnvironment()
                .getNavigatorManager()
                .filterSettings
                .get("anything");
        if (anything != null) {
            return NavigatorRoleplaySupport.filterRoomsForView(
                    this.hotelFilter.getResult(habbo, anything, "", categoryId), this.viewName, true, categoryId);
        }

        return NavigatorRoleplaySupport.filterRoomsForView(
                this.hotelFilter.getResult(habbo), this.viewName, true, categoryId);
    }

    @Override
    public List<SearchResultList> getResult(
            Habbo habbo, NavigatorFilterField filterField, String value, int roomCategory) {
        if (!NavigatorRoleplaySupport.isRoleplayPluginEnabled()) {
            return Collections.emptyList();
        }

        int categoryId = NavigatorRoleplaySupport.getRoleplayCategoryId();
        return NavigatorRoleplaySupport.filterRoomsForView(
                this.hotelFilter.getResult(habbo, filterField, value, categoryId), this.viewName, true, categoryId);
    }
}
