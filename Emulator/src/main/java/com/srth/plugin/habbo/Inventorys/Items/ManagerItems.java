package com.srth.plugin.habbo.Inventorys.Items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import gnu.trove.map.hash.THashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class ManagerItems {

    private final THashMap<Integer, Items> items = new THashMap<>();

    public ManagerItems() {
        // Es recomendable cargar los items al instanciar si es necesario
    }

    /**
     * Actualiza la caché interna de ítems desde la base de datos.
     */
    public void updateItems() {
        items.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_items");
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                int itemExist = resultSet.getInt("item_exist");
                String itemName = resultSet.getString("item_name");

                if (this.checkingItems(itemExist, itemName, id)) {
                    items.put(id, new Items(resultSet));
                }
            }
            // try-with-resources cierra automáticamente statement y resultSet
        } catch (SQLException exception) {
            RoleplayWebsocketsRoomsInfo.utilities()
                    .LOGGER_ERROR("[WS] Error al actualizar items: " + exception.getMessage());
        }
    }

    /**
     * Retorna el mapa de items. Ya no lanza excepciones.
     */
    public THashMap<Integer, Items> getItems() {
        return items;
    }

    /**
     * Obtiene un item por su ID. Retorna null si no existe.
     */
    public Items getItem(int id) {
        return this.items.get(id);
    }

    /**
     * Valida si un item existe en el catálogo del hotel si está marcado como
     * 'item_exist'.
     */
    public boolean checkingItems(int item_exist, String item_name, int item_id) {
        if (item_exist == 1) {
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(item_name);
            if (baseItem == null) {
                this.items.remove(item_id);
                RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR(
                        "[WS] Plugin Roleplay Rooms Info | Item: '" + item_name + "' no encontrado en el hotel.");
                return false;
            }
        }
        return true;
    }

    /**
     * Retorna la cantidad de items cargados.
     */
    public int getCantidadItems() {
        return items.size();
    }

    /**
     * Busca un item por su nombre técnico (item_name).
     */
    public Items getItemExist(String name_item) {
        if (name_item == null)
            return null;

        for (Items item : items.values()) {
            if (item.getItemName().equalsIgnoreCase(name_item)) {
                return item;
            }
        }
        return null;
    }
}