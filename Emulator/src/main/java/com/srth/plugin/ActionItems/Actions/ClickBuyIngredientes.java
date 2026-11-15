package com.srth.plugin.ActionItems.Actions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ConfirmEvents.confirmevents.ConfirmEventsManager;

public class ClickBuyIngredientes {

    Item itemBase;
    HabboItem habboitem;
    Habbo habbo;

    public ClickBuyIngredientes(HabboItem habboitem, Habbo habbo) {

        try {

            this.habbo = habbo;
            this.itemBase = habboitem.getBaseItem();
            this.habboitem = habboitem;

            Room room = habbo.getHabboInfo().getCurrentRoom();

            RoomTile tile = HabboItem.getSquareInFront(room.getLayout(), habboitem);
            if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(habboitem.getX(), habboitem.getY()),
                    habbo.getRoomUnit().getCurrentLocation())) {
                if (tile != null && tile.isWalkable()) {
                    if (tile != habbo.getHabboInfo().getCurrentRoom().getLayout().getDoorTile()) {
                        habbo.getRoomUnit().setGoalLocation(tile);
                    }
                }
            } else {
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
            }

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (RoleplayWebsocketsRoomsInfo.getItemActionManager().comprobateCerca(habboitem, room,
                    habbo.getClient()) == false) {
                return;
            }

            String estructura = "item_id:%item_id%;";
            String total_string = estructura.replace("%item_id%", itemBase.getId() + "");

            if (habboRp.checkSettings("global_confirm_data", total_string) != "none") {
                habboRp.saveToDatabase(total_string, "global_confirm_data");
            }

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                    .replace("%url.hotel.base%", url_hotel_base);
            String url_images = global_url_images + "/items/";

            int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
            ConfirmEventsManager confirmEventsManager = new ConfirmEventsManager();
            confirmEventsManager.setHabbo(habbo);
            confirmEventsManager.showVentSelect("items_ingredientes_orden_select", 1, true,
                    habbo.getHabboInfo().getCurrencyAmount(type_moneda), url_images, true);

        } catch (Exception e) {
        }
    }
}
