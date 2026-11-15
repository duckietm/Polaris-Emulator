package com.srth.plugin.habbo.Inventorys;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.Items.Items;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

import gnu.trove.map.hash.THashMap;

public class CofreInventoryManager {

    public THashMap<Habbo, HabboItem> habbosCofresOpen = new THashMap<>();

    /**
     * Abre la interfaz de un cofre específico basado en un HabboItem de la sala.
     */
    public void openCofreInventory(Habbo habbo, HabboItem habboItem) {

        if (habbosCofresOpen.get(habbo) != null) {
            habbosCofresOpen.remove(habbo);
            this.habbosCofresOpen.put(habbo, habboItem);
        } else {
            this.habbosCofresOpen.put(habbo, habboItem);
        }

        THashMap<Integer, ItemCofre> items_cofre = new THashMap<>();

        int cofreInternalId = -1;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // 1. Buscamos el ID del registro en rp_cofres vinculado al item físico
            String sqlCofre = "SELECT id FROM rp_cofres WHERE item_cofre_id = ? LIMIT 1";
            try (PreparedStatement st = connection.prepareStatement(sqlCofre)) {
                st.setInt(1, habboItem.getId());
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        cofreInternalId = rs.getInt("id");
                    }
                }
            }

            // 2. Si existe, cargamos sus items desde la tabla relacional
            if (cofreInternalId != -1) {
                String sqlItems = "SELECT * FROM rp_cofres_items WHERE cofre_id = ?";
                try (PreparedStatement stItems = connection.prepareStatement(sqlItems)) {
                    stItems.setInt(1, cofreInternalId);
                    try (ResultSet rsItems = stItems.executeQuery()) {
                        while (rsItems.next()) {
                            int id = rsItems.getInt("id");
                            items_cofre.put(id, new ItemCofre(rsItems));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error openCofreInventory: " + e.getMessage());
        }

        // 3. Enviamos el paquete al cliente con los datos del cofre
        this.sendInventoryToClientCofre(habbo, items_cofre);
    }

    /**
     * Helper para enviar los datos por WebSocket.
     */
    private void sendInventoryToClientCofre(Habbo habbo, THashMap<Integer, ItemCofre> source) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp != null && habboRp.getHabboClient() != null) {

                habboRp.getHabboClient().send(
                        new MessageManagerDouble(
                                "MessageManager",
                                true,
                                Outcoming.ShowCofreHabbos,
                                this.getArrayTypeCofre(source), habboRp.getNewInventoryHabbo().getArrayType(0)));

                // habboRp.getNewInventoryHabbo().showWindowInventory(0);
            }
        } catch (Exception e) {
        }
    }

    public void ocultInventoryToClientCofre(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp != null && habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager(
                                "MessageManager",
                                false,
                                Outcoming.ShowCofreHabbos,
                                null));
            }
        } catch (Exception e) {
        }
    }

    /**
     * Genera el JsonArray basado en una fuente de datos específica (Inventario o
     * Cofre).
     */
    public JsonArray getArrayTypeCofre(THashMap<Integer, ItemCofre> fuente) {
        JsonArray array = new JsonArray();
        int number = 0;

        for (ItemCofre itemCofre : fuente.values()) {

            Items info = RoleplayWebsocketsRoomsInfo.getItemsManager().getItems().get(itemCofre.getItemId());

            if (info == null)
                continue;
            if (itemCofre.getCantidad() <= 0)
                continue;

            JsonObject itemObj = buildBaseItemJson(info);
            itemObj.addProperty("id_item_cofre", itemCofre.getId());
            itemObj.addProperty("number", number++);
            itemObj.addProperty("unlock", "1");
            itemObj.addProperty("cantidad_stock", itemCofre.getCantidad());

            array.add(itemObj);
        }

        if (array.size() == 0) {
            JsonObject empty = new JsonObject();
            empty.addProperty("count_items", 0);
            array.add(empty);
        }

        return array;
    }

    private JsonObject buildBaseItemJson(Items item) {
        JsonObject obj = new JsonObject();

        obj.addProperty("id_item_cofre", item.getId());
        obj.addProperty("item_id", item.getId());
        obj.addProperty("count_items", RoleplayWebsocketsRoomsInfo.getItemsManager().getCantidadItems());
        obj.addProperty("item_name", item.getItemName());
        obj.addProperty("type", item.getType());
        obj.addProperty("public_name", item.getPublicName());
        obj.addProperty("public_desc", item.getPublicDesc());
        obj.addProperty("public_info", item.getPublicInfo());
        obj.addProperty("collect", item.getCollect());
        obj.addProperty("equipable", item.getType());
        obj.addProperty("inventory_stock", item.getInventoryStock());

        String urlBase = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlKey = (item.getItemExist() == 0) ? "global_url_images" : "global_url.hotel.furnis";

        String url = RoleplayWebsocketsRoomsInfo.getConfig().getValue(urlKey).replace("%url.hotel.base%", urlBase);
        String suffix = (item.getItemExist() == 0)
                ? "/items/" + item.getItemName() + ".png"
                : "icons/" + item.getItemName() + "_icon.png";

        obj.addProperty("item_img", url + suffix);

        return obj;
    }

    public Boolean getItemCofreExist(int id_item_cofre) {
        Boolean exist = false;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            String sqlCofre = "SELECT * FROM rp_cofres_items WHERE id = ? LIMIT 1";
            try (PreparedStatement st = connection.prepareStatement(sqlCofre)) {
                st.setInt(1, id_item_cofre);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        exist = true;
                    }
                }
            }
            return exist;
        } catch (SQLException e) {
        }
        return exist;
    }

    public int getInfoItemCofre(String data_obtener, int id_item_cofre) {
        int dataInt = -1;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            String sqlCofre = "SELECT * FROM rp_cofres_items WHERE id = ? LIMIT 1";
            try (PreparedStatement st = connection.prepareStatement(sqlCofre)) {
                st.setInt(1, id_item_cofre);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        dataInt = Integer.parseInt(rs.getString(data_obtener));
                    }
                }
            }

            return dataInt;

        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error getInfoItemCofre: " + e.getMessage());
        }

        return dataInt;
    }

    public int getCofreIdFromItemId(int id_item_cofre) {
        int dataInt = -1;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            String sqlCofre = "SELECT * FROM rp_cofres WHERE item_id = ? LIMIT 1";
            try (PreparedStatement st = connection.prepareStatement(sqlCofre)) {
                st.setInt(1, id_item_cofre);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        dataInt = Integer.parseInt(rs.getString("id"));
                    }
                }
            }

            return dataInt;

        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error getInfoItemCofre: " + e.getMessage());
        }

        return dataInt;
    }

    public void deleteItemFromCofre(int itemId) {
        String sql = "DELETE FROM rp_cofres_items WHERE id = ?";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement st = connection.prepareStatement(sql)) {
            st.setInt(1, itemId);
            st.executeUpdate();
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error deleteItemFromCofre: " + e.getMessage());
        }
    }

    public Boolean cofreConItems(HabboItem habboItem) {

        RoleplayWebsocketsRoomsInfo.utilities().LOGGER("ME LLEGO");

        // 2. Comprobar si existen ítems dentro del cofre
        boolean tieneItems = false;

        if (habboItem == null) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("El objeto habboItem es NULL");
            return false;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // 1. Obtener el ID interno del cofre
            int cofreInternalId = -1;
            String sqlSelect = "SELECT id FROM rp_cofres WHERE item_cofre_id = ? LIMIT 1";

            try (PreparedStatement stSelect = connection.prepareStatement(sqlSelect)) {
                stSelect.setInt(1, habboItem.getId());

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("HABBOITEM: " + habboItem.getId());

                try (ResultSet rs = stSelect.executeQuery()) {
                    if (rs.next()) {
                        cofreInternalId = rs.getInt("id");

                        RoleplayWebsocketsRoomsInfo.utilities().LOGGER("INTERNAL ID: " + cofreInternalId);
                    }
                }
            }

            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("ME LLEGO x2: " + cofreInternalId);

            if (cofreInternalId != -1) {

                String sqlCheckItems = "SELECT COUNT(*) FROM rp_cofres_items WHERE cofre_id = ?";

                try (PreparedStatement stCheck = connection.prepareStatement(sqlCheckItems)) {
                    stCheck.setInt(1, cofreInternalId);
                    try (ResultSet rsCount = stCheck.executeQuery()) {
                        if (rsCount.next()) {
                            if (rsCount.getInt(1) > 0) {
                                tieneItems = true;

                                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("ME LLEGO x3: SI TENIA");
                            }
                        }
                    }
                }

                return tieneItems;
            }

            return tieneItems;

        } catch (Exception e) { // Cambia SQLException por Exception temporalmente
            e.printStackTrace(); // Esto imprimirá el error directamente en la consola/terminal
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error crítico: " + e.toString());
        }

        return tieneItems;

    }

    public void destroyCofreCompletely(Habbo habbo, HabboItem habboItem) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // 1. Obtener el ID interno del cofre
            int cofreInternalId = -1;
            String sqlSelect = "SELECT id FROM rp_cofres WHERE item_cofre_id = ? LIMIT 1";

            try (PreparedStatement stSelect = connection.prepareStatement(sqlSelect)) {
                stSelect.setInt(1, habboItem.getId());
                try (ResultSet rs = stSelect.executeQuery()) {
                    if (rs.next()) {
                        cofreInternalId = rs.getInt("id");
                    }
                }
            }

            if (cofreInternalId != -1) {

                if (cofreConItems(habboItem) == true) {
                    return;
                }

                // 3. Eliminar el registro del cofre principal (Ya sabemos que rp_cofres_items
                // está vacío)
                String sqlDeleteCofre = "DELETE FROM rp_cofres WHERE id = ?";
                try (PreparedStatement stDelCofre = connection.prepareStatement(sqlDeleteCofre)) {
                    stDelCofre.setInt(1, cofreInternalId);
                    stDelCofre.executeUpdate();
                }

                // 4. Eliminar el item físico del emulador
                habbo.getHabboInfo().getCurrentRoom().removeHabboItem(habboItem);
                habbo.getHabboInfo().getCurrentRoom().sendComposer(new RemoveFloorItemComposer(habboItem).compose());
                habbo.getHabboInfo().getCurrentRoom().updateTile(
                        habbo.getHabboInfo().getCurrentRoom().getLayout().getTile(habboItem.getX(), habboItem.getY()));
                new QueryDeleteHabboItem(habboItem.getId()).run();

            }

        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error destroyCofreCompletely: " + e.getMessage());
        }
    }

    public void addItemToCofreDB(int cofreId, int itemId, int cantidad) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Verificar si el item ya existe en ese cofre
            String checkSql = "SELECT id, cantidad FROM rp_cofres_items WHERE cofre_id = ? AND item_id = ? LIMIT 1";
            try (PreparedStatement stCheck = connection.prepareStatement(checkSql)) {
                stCheck.setInt(1, cofreId);
                stCheck.setInt(2, itemId);
                try (ResultSet rs = stCheck.executeQuery()) {
                    if (rs.next()) {
                        // Update
                        String updateSql = "UPDATE rp_cofres_items SET cantidad = cantidad + ? WHERE id = ?";
                        try (PreparedStatement stUp = connection.prepareStatement(updateSql)) {
                            stUp.setInt(1, cantidad);
                            stUp.setInt(2, rs.getInt("id"));
                            stUp.executeUpdate();
                        }
                    } else {
                        // Insert
                        String insertSql = "INSERT INTO rp_cofres_items (cofre_id, item_id, cantidad) VALUES (?, ?, ?)";
                        try (PreparedStatement stIns = connection.prepareStatement(insertSql)) {
                            stIns.setInt(1, cofreId);
                            stIns.setInt(2, itemId);
                            stIns.setInt(3, cantidad);
                            stIns.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("Error addItemToCofreDB: " + e.getMessage());
        }
    }

    public int getInternalCofreId(int habboItemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            String sql = "SELECT id FROM rp_cofres WHERE item_cofre_id = ? LIMIT 1";
            try (PreparedStatement st = connection.prepareStatement(sql)) {
                st.setInt(1, habboItemId);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next())
                        return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
        }
        return -1;
    }

    public void incomingActionsReceibedOther(THashMap<String, Object> data, HabboClient wsclient) throws SQLException {
        if (wsclient == null || wsclient.getHabbo() == null)
            return;

        String action = (String) data.get("action");
        int targetId = Integer.parseInt(data.get("date").toString());

        Room room = wsclient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        HabboRp rpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());

        int id_item_cofre = habbosCofresOpen.get(wsclient.getHabbo()).getId();
        int item_id = targetId;

        int cantidad = this.getInfoItemCofre("cantidad", targetId);

        InventoryHabboNew inventoryHabboNew = rpClient.getNewInventoryHabbo();

        switch (action) {

            case "MoveToInventory":

                Items info = RoleplayWebsocketsRoomsInfo.getItemsManager().getItems()
                        .get(this.getInfoItemCofre("item_id", targetId));
                if (info == null) {
                    return;
                }

                if (this.getItemCofreExist(item_id) == false) {
                    wsclient.getHabbo().whisper("El item ya fue tomado");
                    return;
                }

                if (info.getItemExist() == 1 && info.getItemName().startsWith("CASH_1_money_cash_by_th") == true) {

                    // Cuando SI sean cash

                    // Remover el item del cofre
                    this.deleteItemFromCofre(item_id);

                    // Agregar cash al monedero
                    wsclient.getHabbo().givePoints(
                            RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"), cantidad);

                } else if (info.getItemExist() == 1
                        && info.getItemName().startsWith("CASH_1_money_cash_by_th") == false) {

                    // Cuando no sean cash

                    // Remover el item del cofre
                    this.deleteItemFromCofre(item_id);

                    // Agregar uno a uno al inventario principal
                    for (int i = 0; i < cantidad; i++) {
                        Item rewardItem = Emulator.getGameEnvironment().getItemManager().getItem(info.getItemName());
                        HabboItem newItem = Emulator.getGameEnvironment().getItemManager().createItem(
                                wsclient.getHabbo().getHabboInfo().getId(),
                                rewardItem, 0, 0, "");
                        if (newItem != null) {
                            wsclient.getHabbo().getInventory().getItemsComponent().addItem(newItem);
                            ;
                            wsclient.getHabbo().getClient().sendResponse(new InventoryRefreshComposer());
                        }
                    }
                }

                // Cuando tenga el objeto
                if (inventoryHabboNew.getItem(info.getItemName()) != null) {
                    inventoryHabboNew.addCantidadItem(info.getItemName(), cantidad);
                    this.deleteItemFromCofre(item_id);
                } else {
                    // Cuando no tenga el objeto en existencia
                    inventoryHabboNew.createItem(info.getItemName(), cantidad);
                    this.deleteItemFromCofre(item_id);
                }

                // Actualizar vista del cofre
                HabboItem habboItem = habbosCofresOpen.get(wsclient.getHabbo());

                if (cofreConItems(habboItem) == false) {
                    // Cuando este vacio
                    destroyCofreCompletely(wsclient.getHabbo(), habboItem);
                    this.ocultInventoryToClientCofre(wsclient.getHabbo());
                } else {
                    this.openCofreInventory(wsclient.getHabbo(), habboItem);
                }

                break;

            case "MoveToCofre":

                if (this.habbosCofresOpen.get(wsclient.getHabbo()) == null)
                    return;
                int habboItemCofreId = this.habbosCofresOpen.get(wsclient.getHabbo()).getId();
                int internalCofreId = getInternalCofreId(habboItemCofreId);

                if (internalCofreId == -1)
                    return;

                // Obtenemos el item del inventario del usuario usando el targetId
                ItemUsuario itemUser = inventoryHabboNew.items_usuario.get(targetId);
                if (itemUser == null)
                    return;

                Items itemInfo = RoleplayWebsocketsRoomsInfo.getItemsManager().getItems().get(itemUser.getItemId());
                if (itemInfo == null)
                    return;

                // Lógica de movimiento
                int cantidadAMover = itemUser.getCantidad(); // O una cantidad específica si tu UI lo permite

                if (itemInfo.getItemExist() == 1
                        && itemInfo.getItemName().startsWith("CASH_1_money_cash_by_th") == true) {

                    int monedaType = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
                    int cantidad_mover = inventoryHabboNew.getCantidadExistInventory(itemInfo)
                            + wsclient.getHabbo().getHabboInfo().getCurrencyAmount(monedaType);
                    // Remover cash al monedero
                    wsclient.getHabbo().givePoints(monedaType, -cantidad_mover);
                    inventoryHabboNew.deleteItemInventario(itemInfo.getItemName(),
                            inventoryHabboNew.getCantidadExistInventory(itemInfo));

                    // 1. Agregar a la DB del cofre
                    this.addItemToCofreDB(internalCofreId, itemUser.getItemId(), cantidad_mover);

                } else if (itemInfo.getItemExist() == 1
                        && itemInfo.getItemName().startsWith("CASH_1_money_cash_by_th") == false) {

                    int cantidad_mover = inventoryHabboNew.getCantidadExistInventory(itemInfo);

                    inventoryHabboNew.deleteItemInventario(itemInfo.getItemName(),
                            cantidad_mover);

                    // 1. Agregar a la DB del cofre
                    this.addItemToCofreDB(internalCofreId, itemUser.getItemId(), cantidad_mover);

                } else {

                    // 1. Agregar a la DB del cofre
                    this.addItemToCofreDB(internalCofreId, itemUser.getItemId(), cantidadAMover);
                }

                // 2. Eliminar del inventario del usuario
                inventoryHabboNew.remove(itemInfo.getId());

                // 3. Actualizar vista
                HabboItem hItemCofre = room.getHabboItem(habboItemCofreId);
                this.openCofreInventory(wsclient.getHabbo(), hItemCofre);

                break;

        }
    }
}