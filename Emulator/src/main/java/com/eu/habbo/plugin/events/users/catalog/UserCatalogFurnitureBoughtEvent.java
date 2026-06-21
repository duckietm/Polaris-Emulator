package com.eu.habbo.plugin.events.users.catalog;

import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Set;

public class UserCatalogFurnitureBoughtEvent extends UserCatalogEvent {

    public final Set<HabboItem> furniture;


    public UserCatalogFurnitureBoughtEvent(Habbo habbo, CatalogItem catalogItem, Set<HabboItem> furniture) {
        super(habbo, catalogItem);

        this.furniture = furniture;
    }
}
