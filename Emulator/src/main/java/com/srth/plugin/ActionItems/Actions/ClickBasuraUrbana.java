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

public class ClickBasuraUrbana {

    Item itemBase;
    HabboItem habboitem;
    Habbo habbo;

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");

    public ClickBasuraUrbana(HabboItem habboitem, Habbo habbo) {

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
            jsonObject = new JsonObject();
            jsonObject.addProperty("img_furni",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                            .replace("%url.hotel.base%", url_hotel_base) + "icons/" + habboitem.getBaseItem().getName()
                            + "_icon.png");
            jsonObject.addProperty("type", 0);
            jsonObject.addProperty("active", 1);
            jsonObject.addProperty("stack", 1);
            jsonObject.addProperty("cantidad", 1);
            jsonObject.addProperty("title", "Recoger basura");
            jsonObject.addProperty("image",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                            .replace("%url.hotel.base%", url_hotel_base)
                            + "items/basura.png");
            jsonObject.addProperty("process_name", "actionRecogerBasuraUrban");
            jsonObject.addProperty("process_data", "null");

            JsonObject jsonObject2 = new JsonObject() ;
            jsonObject2.addProperty("img_furni",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                            .replace("%url.hotel.base%", url_hotel_base) + "icons/" + habboitem.getBaseItem().getName()
                            + "_icon.png");
            jsonObject2.addProperty("type", 0);
            jsonObject2.addProperty("active", 1);
            jsonObject2.addProperty("stack", 1);
            jsonObject2.addProperty("cantidad", 1);
            jsonObject2.addProperty("title", "Buscar objetos");
            jsonObject2.addProperty("image",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                            .replace("%url.hotel.base%", url_hotel_base)
                            + "items/basura.png");
            jsonObject2.addProperty("process_name", "actionCivilRecogerBasura");
            jsonObject2.addProperty("process_data", "null");

            JsonObject jsonObject3 = new JsonObject() ;
            jsonObject2.addProperty("img_furni",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                            .replace("%url.hotel.base%", url_hotel_base) + "icons/" + habboitem.getBaseItem().getName()
                            + "_icon.png");
            jsonObject3.addProperty("type", 0);
            jsonObject3.addProperty("active", 1);
            jsonObject3.addProperty("stack", 1);
            jsonObject3.addProperty("cantidad", 1);
            jsonObject3.addProperty("title", "Soltar objetos ilegales");
            jsonObject3.addProperty("image",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                            .replace("%url.hotel.base%", url_hotel_base)
                            + "items/cannabis.png");
            jsonObject3.addProperty("process_name", "actionCivilBotarItemsIlegales");
            jsonObject3.addProperty("process_data", "null");

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp.getJobActive() != null && habboRp.getJobActive().getName().equalsIgnoreCase("aseo")) {
                Buttons.add(jsonObject);
            }

            if (habboRp.getJobActive() == null) {
                Buttons.add(jsonObject2);
                Buttons.add(jsonObject3);
            }

            RoleplayWebsocketsRoomsInfo.getItemActionManager().ShowItemAction(habbo, InfoItem, Buttons);

        } catch (Exception e) {
        }
    }

}
