package com.srth.plugin.bots.shop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import java.sql.Connection;

import gnu.trove.map.hash.THashMap;

public class ShopBotsManager {

    private THashMap<String, TypeShop> types = new THashMap<String, TypeShop>();

    public ShopBotsManager() {
    }

    public void loadShops() {

        this.types.clear();

        // Load shops from configuration or database
        try {
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatement = conn
                        .prepareStatement("SELECT * FROM rp_shop_bots ORDER BY id ASC")) {
                    ResultSet set = preparedStatement.executeQuery();
                    while (set.next()) {

                        TypeShop type = new TypeShop(set);
                        type.loadItems();

                        addType(set.getString("type"), type);
                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        }
    }

    public void addType(String name, TypeShop type) {
        types.put(name, type);
    }

    public TypeShop getType(String name) {
        return types.get(name);
    }

    // Add methods to manage bots here

    public void show(String type, Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.ShopBotsWindows,
                                this.getData(habbo, type)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ocult(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", false, Outcoming.ShopBotsWindows, null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JsonArray getData(Habbo habbo, String type) throws SQLException {

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";
        String urlHotelFurnis = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                .replace("%url.hotel.base%", url_hotel_base);
        String urlHotelRp = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

        JsonArray TarrayTask = new JsonArray();
        JsonObject tasks = new JsonObject();

        tasks.addProperty("id", habbo.getHabboInfo().getId());

        // --- SECCIÓN: Tipos ---
        JsonArray listTypes = new JsonArray();

        TypeShop shopType = this.getType(type);

        JsonObject typesObjet = new JsonObject();
        typesObjet.addProperty("type_id", shopType.getId());
        typesObjet.addProperty("type_name", shopType.getType());

        // Creamos el array para los items de este tipo específico
        JsonArray list = new JsonArray();

        JsonObject itemObject = new JsonObject();
        itemObject.addProperty("title", shopType.getTitle());
        itemObject.addProperty("desc", shopType.getDesc());

        // Creamos el array para los items de este tipo específico
        JsonArray listItems = new JsonArray();
        for (ItemShop items : shopType.getItems().values()) {
            JsonObject itemObjet = new JsonObject();
            itemObjet.addProperty("id", items.getId());
            itemObjet.addProperty("title", items.getTitle());
            itemObjet.addProperty("desc", items.getDesc());
            itemObjet.addProperty("cantidad_item_required", items.getCantidad_item_required());
            itemObjet.addProperty("name_item_required", items.getName_item_required());
            itemObjet.addProperty("item_required_exist", items.getItem_required_exist());

            if (items.getItem_required_exist() == 1) {
                itemObjet.addProperty("img_item_required",
                        urlHotelFurnis + "icons/" + items.getName_item_required() + "_icon.png");
            } else {
                itemObjet.addProperty("img_item_required",
                        urlHotelRp + "/items/" + items.getName_item_required() + ".png");
            }

            itemObjet.addProperty("img_promo", items.getImgPromo());

            itemObjet.addProperty("img_points", urlNotifications + "cash/0.png");
            itemObjet.addProperty("points_reward", items.getPoints_reward());

            // Añadimos el item al array de la lista
            listItems.add(itemObjet);
        }
        // Añadimos la lista de ingredientes dentro del objeto del tipo
        itemObject.add("items", listItems);

        // Añadimos el item al array de la lista
        list.add(itemObject);

        // Añadimos la lista de items dentro del objeto del tipo
        typesObjet.add("list", list);
        listTypes.add(typesObjet);

        // Añadimos la estructura completa al objeto principal
        tasks.add("types", listTypes);

        TarrayTask.add(tasks);
        return TarrayTask;
    }

}