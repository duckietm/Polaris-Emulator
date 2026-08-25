package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.users.HabboItem;

public class ItemInteraction {
    private final String name;
    private final Class<? extends HabboItem> type;


    public ItemInteraction(String name, Class<? extends HabboItem> type) {
        this.name = name;
        this.type = type;
    }


    public Class<? extends HabboItem> getType() {
        return this.type;
    }


    public String getName() {
        return this.name;
    }
}
