package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowVentSelect extends OutgoingMessage {
    public ShowVentSelect(String header, String name_action, int action, Boolean costo, int cash_amount,
            String url_images, Boolean show) throws SQLException {
        super("ShowVentSelect");

        int mostrar;
        if (show == true) {
            mostrar = 1;
        } else {
            mostrar = 0;
        }

        if (show) {

            String[] getData = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_" + name_action + "_data")
                    .replace(";", ":").split(":");

            this.data.addProperty("name_action", name_action);
            this.data.addProperty("action", action);

            this.data.addProperty("title_ventana", getData[1]);
            this.data.addProperty("title_confirm", getData[3]);

            JsonArray TarrayItems = new JsonArray();
            JsonObject item;

            int dinero_inventario = cash_amount + 1;

            int items_amount = Integer.parseInt(getData[5].replace(" ", ""));
            for (int i = 1; i <= items_amount; i++) {

                String[] getDataItem = RoleplayWebsocketsRoomsInfo.getTexts()
                        .getValue("window_" + name_action + "_item_" + i).replace(";", ":").split(":");

                item = new JsonObject();
                item.addProperty("id", Integer.parseInt(getDataItem[1].replace(" ", "")));
                item.addProperty("title", getDataItem[3]);
                item.addProperty("amount", getDataItem[7]);
                if (costo == true) {
                    String clean_string = getDataItem[5].replace("cash", "");
                    int mount_total = Integer.parseInt(clean_string.replace(" ", ""));
                    if (mount_total >= dinero_inventario) {
                        item.addProperty("active", 1);
                    } else {
                        item.addProperty("active", 0);
                    }
                    item.addProperty("description", getDataItem[5]);
                    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
                    String url_images_set = RoleplayWebsocketsRoomsInfo.getConfig()
                            .getValue("global_url.hotel.notifications")
                            .replace("%url.hotel.base%", url_hotel_base) + "/RoleplayWebsocketsRoomsInfo/";

                    item.addProperty("image_cash",
                            url_images_set
                                    + "cash/0.png");
                } else {
                    item.addProperty("description", getDataItem[5]);
                    item.addProperty("active", 1);
                    item.addProperty("image_cash", "");
                }

                if (getDataItem[9].replace(" ", "").equalsIgnoreCase("rp_food_ktchn.png")) {
                    item.addProperty("image",
                            url_images.replace("%url.hotel.base%",
                                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base"))
                                    + "rp_food_ktchn_icon.png");
                } else {
                    item.addProperty("image",
                            url_images.replace("%url.hotel.base%",
                                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base"))
                                    + getDataItem[9].replace(" ", ""));
                }

                TarrayItems.add(item);
            }

            // this.data.add("items", jsonObject);
            this.data.add("items", TarrayItems);
        }

        this.data.addProperty("ver", mostrar);

        // this.data.add("items", itemsobj);
        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}
