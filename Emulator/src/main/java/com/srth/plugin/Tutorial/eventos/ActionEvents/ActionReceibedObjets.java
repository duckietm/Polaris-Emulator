package com.srth.plugin.Tutorial.eventos.ActionEvents;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;

import java.sql.SQLException;

public class ActionReceibedObjets {

    private Habbo habbo;

    public ActionReceibedObjets(Habbo habbo) throws SQLException {

        this.habbo = habbo;
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (this.habbo != null) {

            String[] item_name = new String[5];
            item_name[0] = "rp_cocina_byth";
            item_name[1] = "rp_nevera";
            item_name[2] = "rp_camilla_byth";
            item_name[3] = "rp_bote_basura";
            item_name[4] = "ktchn_c15_stove_rp";

            for (int i = 0; i < item_name.length; i++) {
                Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(item_name[i]);

                HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                        habbo.getHabboInfo().getId(),
                        baseItem,
                        0,
                        0,
                        "");

                habbo.getInventory().getItemsComponent().addItem(habboItem);
                habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
            }

            Emulator.getThreading().run(() -> {

                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/gift.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.tutorial.homerp.recibed_room"));
                habboRp.getNewInventoryHabbo().createItem("ingredientes_variados", 12);

            }, 1000);

            if (client != null) {
                client.getHabbo().getClient().sendResponse(new InventoryRefreshComposer());
            }
        }
    }
}
