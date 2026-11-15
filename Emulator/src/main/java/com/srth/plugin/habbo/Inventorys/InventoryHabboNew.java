package com.srth.plugin.habbo.Inventorys;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.Items.Items;
import com.srth.plugin.habbo.Inventorys.Items.ManagerItems;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDoubleObjet;

import gnu.trove.map.hash.THashMap;

public class InventoryHabboNew {

    private final ManagerItems managerItems = RoleplayWebsocketsRoomsInfo.getItemsManager();
    private final Habbo habbo;
    private HabboRp habboRp;
    public final THashMap<Integer, ItemUsuario> items_usuario = new THashMap<>();
    public final THashMap<Integer, ItemUsuario> items_used = new THashMap<>();
    private int cantidad_new_items = 0;
    private final int userId;

    public InventoryHabboNew(Habbo habbo) {
        this.habbo = habbo;
        this.userId = habbo.getHabboInfo().getId();
        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        } catch (Exception ignored) {
        }

        this.updateHabboItems();
        this.loadItemsFromInventory();
    }

    // --- MÉTODOS DE ACTUALIZACIÓN (DB -> MEMORIA) ---

    public void updateHabboItems() {

        this.items_usuario.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT * FROM rp_items_users WHERE user_id = ?")) {

            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ItemUsuario item = new ItemUsuario(resultSet);
                    this.items_usuario.put(item.getItemId(), item);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        this.updateItemsUsed();

    }

    public void loadItemsFromInventory() {

        for (Items itemFromManager : RoleplayWebsocketsRoomsInfo.getItemsManager().getItems().values()) {

            if (itemFromManager.getItemExist() == 0) {
                continue;
            }

            if (itemFromManager.getItemExist() == 1
                    && itemFromManager.getItemName().startsWith("CASH_1_money_cash_by_th") == false
                    && this.getCantidadExistInventory(itemFromManager) == 0) {
                continue;
            }

            if (itemFromManager.getItemExist() == 1
                    && itemFromManager.getItemName().startsWith("CASH_1_money_cash_by_th") == true
                    && this.getCantidadExistInventory(itemFromManager) <= 0
                    && this.habbo.getHabboInfo().getCurrencyAmount(
                            RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type")) <= 0) {
                continue;
            }

            if (this.getItem(itemFromManager.getItemName()) == null) {
                this.createItem(itemFromManager.getItemName(), this.getCantidadExistInventory(itemFromManager));
            } else {

                int cantidad_existente = this.getItem(itemFromManager.getItemName()).getCantidad();
                int cantidad_total = cantidad_existente + this.getCantidadExistInventory(itemFromManager);

                this.items_usuario.remove(itemFromManager.getId());

                this.createItem(itemFromManager.getItemName(), cantidad_total);

            }

        }
    }

    public void updateItemsUsed() {
        this.items_used.clear();
        for (ItemUsuario item : this.items_usuario.values()) {
            if (item.getUsed() == 1) {
                this.items_used.put(item.getItemId(), item);
            }
        }
    }

    // --- GESTIÓN DE ITEMS ---

    /**
     * Al morir, crea un cofre y transfiere todos los ítems desbloqueados.
     * 
     * @param itemCofreBaseId El ID del nuevo HabboItem que servirá como cofre.
     */
    public void dropInventoryToCofre(int itemCofreBaseId) {
        if (this.items_usuario.isEmpty())
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false); // Transacción para asegurar la integridad de los datos

            int generatedCofreId = -1;

            // 1. Crear el nuevo cofre en la base de datos
            try (PreparedStatement stCofre = connection.prepareStatement(
                    "INSERT INTO `rp_cofres` (user_id, item_cofre_id, used) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                stCofre.setInt(1, userId);
                stCofre.setInt(2, itemCofreBaseId);
                stCofre.setInt(3, 0);
                stCofre.executeUpdate();

                try (ResultSet rs = stCofre.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedCofreId = rs.getInt(1);
                    }
                }
            }

            if (generatedCofreId != -1) {
                // 2. Insertar todos los ítems actuales en la tabla del cofre
                try (PreparedStatement stItemsCofre = connection.prepareStatement(
                        "INSERT INTO `rp_cofres_items` (cofre_id, item_id, cantidad) VALUES (?, ?, ?)")) {

                    for (ItemUsuario item : this.items_usuario.values()) {

                        Items info = RoleplayWebsocketsRoomsInfo.getItemsManager().getItems().get(item.getItemId());

                        stItemsCofre.setInt(1, generatedCofreId);
                        stItemsCofre.setInt(2, item.getItemId());

                        if (info.getItemExist() == 1
                                && info.getItemName().startsWith("CASH_1_money_cash_by_th") == true
                                && this.getCantidadExistInventory(info) <= 0
                                && this.habbo.getHabboInfo().getCurrencyAmount(RoleplayWebsocketsRoomsInfo.getConfig()
                                        .getInt("seasonal.rol.primary.type")) <= 0) {
                            continue;
                        }

                        if (info.getItemExist() == 1
                                && info.getItemName().startsWith("CASH_1_money_cash_by_th") == false
                                && this.getCantidadExistInventory(info) == 0) {
                            continue;
                        }

                        if (info.getItemExist() == 1
                                && info.getItemName().startsWith("CASH_1_money_cash_by_th") == true) {

                            int money_actual = this.habbo.getHabboInfo().getCurrencyAmount(
                                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"));

                            int cantidad = this.getCantidadExistInventory(info) + money_actual;
                            stItemsCofre.setInt(3, cantidad);

                            this.habbo.givePoints(
                                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"),
                                    -money_actual);

                            // Si existe alguna cantidad en inventario se elimina
                            this.removeCantidadItem(info.getItemName(), this.getCantidadExistInventory(info));

                        } else if (info.getItemExist() == 1
                                && info.getItemName().startsWith("CASH_1_money_cash_by_th") == false) {

                            if (this.getCantidadExistInventory(info) == 0) {
                                continue;
                            }

                            stItemsCofre.setInt(3, this.getCantidadExistInventory(info));
                            this.deleteItemInventario(info.getItemName(), this.getCantidadExistInventory(info));

                            // Si existe alguna cantidad en inventario se elimina
                            this.removeCantidadItem(info.getItemName(), this.getCantidadExistInventory(info));

                        } else {

                            stItemsCofre.setInt(3, item.getCantidad());
                        }

                        stItemsCofre.addBatch();
                    }
                    stItemsCofre.executeBatch();
                }

                // 3. Eliminar los ítems del inventario del usuario
                try (PreparedStatement stDelete = connection.prepareStatement(
                        "DELETE FROM `rp_items_users` WHERE user_id = ?")) {
                    stDelete.setInt(1, userId);
                    stDelete.executeUpdate();
                }

                connection.commit();

                // 4. Limpiar datos en memoria y actualizar interfaz
                this.items_usuario.clear();
                this.items_used.clear();
                this.cantidad_new_items = 0;

                this.ocultButtonsUsar(habbo);
                this.showButtonsUsar(habbo);

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("[Inventory] Muerte de "
                        + habbo.getHabboInfo().getUsername() + ": Items movidos al cofre " + generatedCofreId);
            }
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities()
                    .LOGGER_ERROR("Error al procesar cofre por muerte: " + e.getMessage());
        }
    }

    public ItemUsuario getItem(String name) {
        Items itemRP = managerItems.getItemExist(name);
        return (itemRP != null) ? this.items_usuario.get(itemRP.getId()) : null;
    }

    public void createItem(String itemName, int cantidad) {
        Items itemRP = managerItems.getItemExist(itemName);
        if (itemRP == null || this.items_usuario.containsKey(itemRP.getId()))
            return;

        executeUpdate("INSERT INTO rp_items_users (user_id, item_id, cantidad) VALUES (?, ?, ?)",
                userId, itemRP.getId(), cantidad);

        this.updateHabboItems();
        incrementNewItemsCounter(1);
    }

    public void addCantidadItem(String itemName, int value) {
        Items itemRP = managerItems.getItemExist(itemName);
        if (itemRP == null)
            return;

        ItemUsuario itemUsuario = this.getItem(itemName);
        if (itemUsuario != null) {
            int nuevaCantidad = itemUsuario.getCantidad() + value;
            executeUpdate("UPDATE rp_items_users SET cantidad = ? WHERE user_id = ? AND item_id = ?",
                    nuevaCantidad, userId, itemRP.getId());
            this.updateHabboItems();
            incrementNewItemsCounter(value);
        } else {
            this.createItem(itemName, value);
        }
    }

    public int removeCantidadItem(String itemName, int value) {
        Items itemRP = managerItems.getItemExist(itemName);
        if (itemRP == null)
            return 0;

        ItemUsuario itemUsuario = this.getItem(itemName);
        if (itemUsuario != null) {
            int operacion = Math.max(0, itemUsuario.getCantidad() - value);
            executeUpdate("UPDATE rp_items_users SET cantidad = ? WHERE user_id = ? AND item_id = ?",
                    operacion, userId, itemRP.getId());
            this.updateHabboItems();
            return operacion;
        }
        return 0;
    }

    public void remove(int item_id) {
        executeUpdate("DELETE FROM rp_items_users WHERE user_id = ? AND item_id = ?", userId, item_id);
        this.updateHabboItems();
    }

    // --- GESTIÓN DE EQUIPAMIENTO (USED) ---

    public ItemUsuario getItemUsed(String itemName) {
        Items itemRP = managerItems.getItemExist(itemName);
        return (itemRP != null) ? this.items_used.get(itemRP.getId()) : null;
    }

    public void addItemUsed(String itemName) {
        Items itemRP = managerItems.getItemExist(itemName);
        if (itemRP != null) {
            this.addItemUsed(itemRP.getId());
        } else {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR(
                    "[Inventory] No se pudo equipar '" + itemName + "' porque no existe en la configuración.");
        }
    }

    public void addItemUsed(int itemID) {
        if (this.items_used.containsKey(itemID))
            return;

        if (this.items_used.size() >= 3) {
            if (this.habboRp != null) {
                this.habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/inventory.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("inventory_used_item_max"));
            }
            return;
        }

        executeUpdate("UPDATE rp_items_users SET used = 1 WHERE user_id = ? AND item_id = ?", userId, itemID);
        this.updateHabboItems();
        this.showButtonsUsar(habbo);
    }

    public void desequipAllItemsUsed() {
        for (ItemUsuario item : this.items_used.values()) {
            executeUpdate("UPDATE rp_items_users SET used = 0 WHERE user_id = ? AND item_id = ?", userId,
                    item.getItemId());
        }
        this.updateHabboItems();
    }

    public void desequipAllItemsExcPlatillos() {
        for (ItemUsuario item : this.items_used.values()) {
            if (item.getItemId() == 1)
                continue;
            executeUpdate("UPDATE rp_items_users SET used = 0 WHERE user_id = ? AND item_id = ?", userId,
                    item.getItemId());
        }
        this.updateHabboItems();
    }

    // --- UTILIDADES DE INTERFAZ Y JSON ---

    public int getCantidadExistInventory(Items item) {
        String baseName = item.getItemName();
        return (int) habbo.getInventory().getItemsComponent().getItemsAsValueCollection().stream()
                .filter(i -> i.getBaseItem().getName().equalsIgnoreCase(baseName) && i.getUserId() == userId)
                .count();
    }

    public void showWindowInventory(int type) {
        if (habboRp != null && habboRp.getHabboClient() != null) {
            try {
                habboRp.getHabboClient().send(
                        new MessageManagerDoubleObjet(
                                "MessageManager",
                                true,
                                Outcoming.OpenMyInventary,
                                this.getArrayType(type),
                                this.getInventoryCatalog()));
            } catch (Exception e) {
            }
        }
    }

    public void showButtonsUsar(Habbo habbo) {
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        if (client != null) {
            try {
                client.send(new MessageManager("MessageManager", true, Outcoming.InventoryButtons,
                        this.getArrayItemsUsedForPlayer(habbo)));
            } catch (Exception e) {
            }
        }
    }

    public void ocultButtonsUsar(Habbo habbo) {
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        if (client != null) {
            try {
                client.send(new MessageManager("MessageManager", false, Outcoming.InventoryButtons, null));
            } catch (Exception e) {
            }
        }
    }

    public JsonArray getArrayType(int type) {
        JsonArray array = new JsonArray();
        int number = 0;

        for (ItemUsuario itemUsuario : this.items_usuario.values()) {
            Items info = managerItems.getItems().get(itemUsuario.getItemId());

            if (info == null)
                continue;

            if (itemUsuario.getCantidad() == 0 && info.getCollect() == 1 && info.getItemExist() == 0) {
                this.remove(info.getId());
                continue;
            }

            if (type == 2 && !info.getType().equalsIgnoreCase("no-equipable"))
                continue;
            if (type == 1 && !info.getType().equalsIgnoreCase("equipable"))
                continue;

            JsonObject itemObj = buildBaseItemJson(info);
            itemObj.addProperty("number", number++);
            itemObj.addProperty("unlock", "1");
            itemObj.addProperty("id_item_cofre", itemUsuario.getItemId());

            int stock = calculateStock(info, itemUsuario);
            itemObj.addProperty("cantidad_stock", stock);
            itemObj.addProperty("used", (this.getItemUsed(info.getItemName()) != null) ? "1" : "0");

            array.add(itemObj);
        }

        return array;
    }

    public JsonObject getInventoryCatalog() {
        JsonObject catalog = new JsonObject();
        JsonArray itemsArray = new JsonArray();

        for (Items item : managerItems.getItems().values()) {
            JsonObject itemObj = buildBaseItemJson(item);
            ItemUsuario itemUsuario = this.getItem(item.getItemName());
            itemObj.addProperty("is_owned", (itemUsuario != null) ? "1" : "0");
            itemsArray.add(itemObj);
        }

        catalog.add("all_items", itemsArray);
        catalog.addProperty("total_catalog_count", itemsArray.size());

        return catalog;
    }

    public JsonArray getArrayItemsUsedForPlayer(Habbo habbo) {
        JsonArray array = new JsonArray();
        if (this.items_used.isEmpty()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("count_items", 0);
            obj.addProperty("news_items_count", this.getCantidadNewItems());
            array.add(obj);
            return array;
        }

        TreeMap<Integer, ItemUsuario> sortedItems = new TreeMap<>(this.items_used);
        for (ItemUsuario used : sortedItems.values()) {
            Items info = managerItems.getItems().get(used.getItemId());
            if (info == null)
                continue;

            JsonObject obj = buildBaseItemJson(info);
            obj.addProperty("id", used.getId());
            obj.addProperty("ranura", used.getRanura());
            obj.addProperty("item_id", used.getItemId());
            obj.addProperty("use_btn_name", info.getUseBtnName());
            obj.addProperty("event", info.getEvent());

            int stock = calculateStock(info, used);
            obj.addProperty("cantidad_stock", stock);
            obj.addProperty("unlock", (info.getInventoryStock() == 0 || stock >= 1) ? "1" : "0");

            obj.addProperty("count_items", this.items_used.size());
            obj.addProperty("news_items_count", this.getCantidadNewItems());
            array.add(obj);
        }
        return array;
    }

    // --- HELPERS PRIVADOS ---

    private JsonObject buildBaseItemJson(Items item) {
        JsonObject obj = new JsonObject();
        obj.addProperty("count_items", managerItems.getCantidadItems());
        obj.addProperty("id", item.getId());
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
        String suffix = (item.getItemExist() == 0) ? "/items/" + item.getItemName() + ".png"
                : "icons/" + item.getItemName() + "_icon.png";

        String url = RoleplayWebsocketsRoomsInfo.getConfig().getValue(urlKey).replace("%url.hotel.base%", urlBase);
        obj.addProperty("item_img", url + suffix);

        return obj;
    }

    private int calculateStock(Items item, ItemUsuario userItem) {
        if (item.getInventoryStock() == 0) {
            return (userItem != null) ? userItem.getCantidad() : 0;
        }
        if (item.getItemName().equalsIgnoreCase("CASH_1_money_cash_by_th")) {
            int currencyType = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
            return getCantidadExistInventory(item) + habbo.getHabboInfo().getCurrencyAmount(currencyType);
        }
        return getCantidadExistInventory(item);
    }

    private void executeUpdate(String query, Object... params) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.execute();
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("Inventory Error: " + e.getMessage());
        }
    }

    public void incrementNewItemsCounter(int value) {
        this.cantidad_new_items += value;
        if (this.cantidad_new_items >= 1) {

            if (this.habbo.getHabboInfo().getCurrentRoom() == null) {
                return;
            }

            showButtonsUsar(habbo);
        }
    }

    // --- GETTERS & SETTERS ---

    public THashMap<Integer, ItemUsuario> getItemsUsed() {
        return items_used;
    }

    public int getCantidadNewItems() {
        return cantidad_new_items;
    }

    public void setCantidadNewItems(int value) {
        this.cantidad_new_items = value;
    }

    public void removeItemUsed(String itemName) {
        Items itemRP = managerItems.getItemExist(itemName);
        if (itemRP != null) {
            this.removeItemUsed(itemRP.getId());
        }
    }

    public void removeItemUsed(int itemID) {
        executeUpdate("UPDATE rp_items_users SET used = 0 WHERE user_id = ? AND item_id = ?", userId, itemID);
        ItemUsuario item = this.items_usuario.get(itemID);
        if (item != null) {
            this.updateHabboItems();
        }
    }

    public void deleteItemInventario(String item_name_exist, int cantidad) {
        int eliminados = 0;
        Map<Integer, HabboItem> itemsToDelete = new HashMap<>();

        // 1. Identificamos los ítems sin modificar la colección original todavía
        for (HabboItem item : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (eliminados >= cantidad)
                break;

            if (item.getBaseItem().getName().startsWith(item_name_exist) &&
                    item.getUserId() == habbo.getHabboInfo().getId()) {

                itemsToDelete.put(item.getId(), item);
                eliminados++;
            }
        }

        // 2. Si encontramos ítems, procedemos a eliminarlos de forma masiva
        if (!itemsToDelete.isEmpty()) {
            for (HabboItem item : itemsToDelete.values()) {
                habbo.getInventory().getItemsComponent().removeHabboItem(item);
            }

            // 3. Pasamos la colección de valores para que encaje con el constructor
            Emulator.getThreading().run(new QueryDeleteHabboItems(itemsToDelete.values()));

            // 4. Un solo paquete de refresco para el cliente
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }
    }
}