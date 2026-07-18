package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.layouts.RoomBundleLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

public class SendRoomBundle extends RCONMessage<SendRoomBundle.JSON> {
    public SendRoomBundle() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (json.catalog_page > 0 && json.user_id > 0) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);
            CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(json.catalog_page);

            if ((page instanceof RoomBundleLayout)) {
                if (habbo != null) {
                    ((RoomBundleLayout) page).buyRoom(habbo);
                } else {
                    HabboInfo info = HabboManager.getOfflineHabboInfo(json.user_id);

                    if (info != null) {
                        ((RoomBundleLayout) page).buyRoom(null, json.user_id, info.getUsername());
                    }
                }
            }
        }
    }

    static class JSON {
        @Positive(message = "invalid user")
        public int user_id;

        @Positive(message = "invalid catalog page")
        public int catalog_page;
    }
}
