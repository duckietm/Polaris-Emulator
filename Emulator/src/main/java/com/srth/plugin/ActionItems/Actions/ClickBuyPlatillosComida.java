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
import com.srth.plugin.habbo.Inventorys.Items.Items;

public class ClickBuyPlatillosComida {

    Item itemBase;
    HabboItem habboitem;
    Habbo habbo;

    public ClickBuyPlatillosComida(HabboItem habboitem, Habbo habbo) {

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

            Items item = RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist("rp_food_ktchn");
            if (item != null) {
                int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
                String imagen_url_item = "";
                if (item.getItemExist() == 1) {
                    imagen_url_item = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                            + "icons/";
                }
                ConfirmEventsManager confirmEventsManager = new ConfirmEventsManager();
                confirmEventsManager.setHabbo(habbo);
                confirmEventsManager.showVentSelect("select_buy_platillos_comida", 1, true,
                        habbo.getHabboInfo().getCurrencyAmount(type_moneda), imagen_url_item, true);
            }

        } catch (Exception e) {
        }
    }
}
