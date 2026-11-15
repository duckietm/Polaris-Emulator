package com.srth.plugin.ActionItems.Actions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class ClickSacoDeCafe {

    Item itemBase;
    HabboItem habboitem;
    Habbo habbo;

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");

    public ClickSacoDeCafe(HabboItem habboitem, Habbo habbo) {

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

            JsonArray InfoItem = new JsonArray();
            JsonObject jsonObjectInfo;
            jsonObjectInfo = new JsonObject();
            jsonObjectInfo.addProperty("img_furni",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                            .replace("%url.hotel.base%", url_hotel_base) + "icons/" + habboitem.getBaseItem().getName()
                            + "_icon.png");
            jsonObjectInfo.addProperty("item_id", habboitem.getId());
            InfoItem.add(jsonObjectInfo);

            JsonArray Buttons = new JsonArray();
            JsonObject jsonObject;

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            int progress_granos = habboRp.getCacheInt("granos_cafe_progreso");

            if (habboRp.getJobActive() != null
                    && habboRp.getJobActive().getName().equalsIgnoreCase("cafetero")) {

                jsonObject = new JsonObject();
                jsonObject.addProperty("type", 0);
                jsonObject.addProperty("active", 1);
                jsonObject.addProperty("title", "Tomar granos de cafe");
                jsonObject.addProperty("image",
                        RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                                .replace("%url.hotel.base%", url_hotel_base) + "icons/"
                                + habboitem.getBaseItem().getName()
                                + "_icon.png");
                jsonObject.addProperty("process_name", "actionTomarGranosCafe");
                if (progress_granos == 0) {
                    Buttons.add(jsonObject);
                }

            }

            RoleplayWebsocketsRoomsInfo.getItemActionManager().ShowItemAction(habbo, InfoItem, Buttons);

        } catch (Exception e) {
        }
    }

}
