package com.srth.plugin.ActionItems.Actions;

import java.util.Collections;
import java.util.List;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class ClickLagoPescar {

    Item itemBase;
    HabboItem habboitem;
    Habbo habbo;

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");

    public ClickLagoPescar(HabboItem habboitem, Habbo habbo) {

        try {

            this.habbo = habbo;
            this.itemBase = habboitem.getBaseItem();
            this.habboitem = habboitem;

            Room room = habbo.getHabboInfo().getCurrentRoom();

            short x = (short) (habboitem.getX() + 2);
            short y = (short) (habboitem.getY() + 0);
            RoomTile tile = room.getLayout().getTile(x, y);

            if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
                RoomTile alternativeTile = null;
                List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

                Collections.reverse(optionalTiles);
                for (RoomTile optionalTile : optionalTiles) {
                    if (optionalTile.state != RoomTileState.INVALID
                            && optionalTile.state != RoomTileState.BLOCKED) {
                        alternativeTile = optionalTile;
                        break;
                    }
                }

                if (alternativeTile != null) {
                    tile = alternativeTile;
                }

            }

            if (tile != null && tile.isWalkable()) {
                if (tile != habbo.getHabboInfo().getCurrentRoom().getLayout().getDoorTile()) {
                    habbo.getRoomUnit().setGoalLocation(tile);
                }
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
            jsonObject.addProperty("type", 1);
            jsonObject.addProperty("active", 1);
            jsonObject.addProperty("stack", 1);
            jsonObject.addProperty("cantidad", 1);
            jsonObject.addProperty("title", "Tirar caña de pescar");

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            jsonObject.addProperty("image",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                            .replace("%url.hotel.base%", url_hotel_base)
                            + "RoleplayWebsocketsRoomsInfo/pescar/cana_basica.png");

            jsonObject.addProperty("process_name", "TirarCanaPescar");
            jsonObject.addProperty("process_data", "null");
            Buttons.add(jsonObject);

            RoleplayWebsocketsRoomsInfo.getItemActionManager().ShowItemAction(habbo, InfoItem, Buttons);

        } catch (Exception e) {
        }
    }

}
