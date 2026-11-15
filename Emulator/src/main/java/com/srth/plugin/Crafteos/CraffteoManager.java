package com.srth.plugin.Crafteos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.habbo.Inventorys.ItemUsuario;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import gnu.trove.map.hash.THashMap;

public class CraffteoManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CraffteoManager.class);
    private THashMap<Integer, CraftItemType> itemsCraftTypes = new THashMap<Integer, CraftItemType>();

    public CraffteoManager() {
        this.loadCrafts();
        LOGGER.info("[ROLEPLAY WEBSOCKETS ROOM INFO] | CrafteoManager ha sido iniciado correctamente.");
    }

    public CraftItem getCraftForId(int id) {

        CraftItem type = null;
        for (CraftItemType iterable_element : this.itemsCraftTypes.values()) {
            for (CraftItem craftear : iterable_element.getCrafts().values()) {
                if (craftear.getId() == id) {
                    type = craftear;
                    return type;
                }
            }
        }

        return type;
    }

    public void loadCrafts() {
        try {
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatement = conn
                        .prepareStatement("SELECT * FROM rp_craft_types ORDER BY id ASC")) {
                    ResultSet set = preparedStatement.executeQuery();
                    while (set.next()) {

                        CraftItemType newCraftItemType = new CraftItemType(set);
                        newCraftItemType.loadCrafts();

                        this.itemsCraftTypes.put(newCraftItemType.getId(), newCraftItemType);
                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        } catch (Exception e) {
        }
    }

    public void showCrafteo(Integer type, Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.CrafteoWindowType,
                                this.getData(habbo, type)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ocultCrafteo(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", false, Outcoming.CrafteoWindowType, null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JsonArray getData(Habbo habbo, Integer type) throws SQLException {

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base);
        String urlHotelFurnis = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                .replace("%url.hotel.base%", url_hotel_base);
        String urlHotelRp = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

        JsonArray TarrayTask = new JsonArray();
        JsonObject tasks = new JsonObject();

        tasks.addProperty("id", habbo.getHabboInfo().getId());

        // --- SECCIÓN: Tipos de Crafteos e Items ---
        JsonArray listTypesCrafteos = new JsonArray();

        CraftItemType craftTypes = this.itemsCraftTypes.get(type);

        JsonObject typesObjet = new JsonObject();
        typesObjet.addProperty("type_id", craftTypes.getId());
        typesObjet.addProperty("type_name", craftTypes.getType());

        // Creamos el array para los items de este tipo específico
        JsonArray listItems = new JsonArray();

        for (CraftItem craftItems : craftTypes.getCrafts().values()) {
            JsonObject itemObject = new JsonObject();

            // Agregamos las propiedades del item (ajusta los nombres según tu clase
            // CraftItem)
            itemObject.addProperty("id", craftItems.getId());
            itemObject.addProperty("name", craftItems.getName());

            // Creamos el array para los items de este tipo específico
            JsonArray listIngredientes = new JsonArray();
            for (IngredienteItem ingredienteItem : craftItems.getIngredientes().values()) {

                JsonObject ingredienteObject = new JsonObject();
                ingredienteObject.addProperty("id", ingredienteItem.getId());
                ingredienteObject.addProperty("name", ingredienteItem.getName());
                ingredienteObject.addProperty("cantidad", ingredienteItem.getCantidad());

                if (ingredienteItem.getItemExist() == 1) {
                    ingredienteObject.addProperty("img",
                            urlHotelFurnis + "icons/" + ingredienteItem.getItemName() + "_icon.png");
                } else {
                    ingredienteObject.addProperty("img",
                            urlHotelRp + "/items/" + ingredienteItem.getItemName() + ".png");
                }

                ItemUsuario itemUsuario = inventoryHabboNew.getItem(ingredienteItem.getItemName());
                if (inventoryHabboNew != null && itemUsuario != null) {
                    ingredienteObject.addProperty("inventory_cantidad", itemUsuario.getCantidad());
                } else {
                    ingredienteObject.addProperty("inventory_cantidad", 0);
                }

                // Añadimos el item al array de la lista
                listIngredientes.add(ingredienteObject);
            }
            // Añadimos la lista de ingredientes dentro del objeto del tipo
            itemObject.add("ingredientes", listIngredientes);

            // Creamos el array para los items de este tipo específico
            JsonArray listRewards = new JsonArray();
            for (RewardItem rewardItem : craftItems.getRewards().values()) {
                JsonObject rewardObject = new JsonObject();
                rewardObject.addProperty("id", rewardItem.getId());
                rewardObject.addProperty("name", rewardItem.getName());
                rewardObject.addProperty("cantidad", rewardItem.getCantidad());

                if (rewardItem.getItemExist() == 1) {
                    rewardObject.addProperty("img",
                            urlHotelFurnis + "icons/" + rewardItem.getItemName() + "_icon.png");
                } else {
                    rewardObject.addProperty("img", urlHotelRp + "/items/" + rewardItem.getItemName() + ".png");
                }

                // Añadimos el item al array de la lista
                listRewards.add(rewardObject);
            }
            // Añadimos la lista de ingredientes dentro del objeto del tipo
            itemObject.add("rewards", listRewards);

            // Añadimos el item al array de la lista
            listItems.add(itemObject);

        }

        // Añadimos la lista de items dentro del objeto del tipo
        typesObjet.add("items", listItems);

        listTypesCrafteos.add(typesObjet);

        // Añadimos la estructura completa al objeto principal
        tasks.add("types", listTypesCrafteos);

        TarrayTask.add(tasks);
        return TarrayTask;
    }

    public void incomingEvents(THashMap<String, Object> data, HabboClient wsclient) throws SQLException {
        if (wsclient == null || wsclient.getHabbo() == null)
            return;

        // Extraemos 'name' y el 'array' del mapa de datos del EventListener
        String eventName = (String) data.get("name");
        List<?> array = (List<?>) data.get("array");

        if (array == null)
            return;

        Habbo habbo = wsclient.getHabbo();
        HabboRp rpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        InventoryHabboNew inventory = rpClient.getNewInventoryHabbo();

        Room room = wsclient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        switch (eventName) {
            case "CraftearItem":

                if (array.size() < 2)
                    return;

                int craft_id = Integer.parseInt((String) array.get(0));
                int cantidad_craftear = Integer.parseInt((String) array.get(1));

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("craft_id " + craft_id);
                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("cantidad_craftear " + cantidad_craftear);

                InventoryHabboNew inventoryHabboNew = rpClient.getNewInventoryHabbo();

                CraftItem craftItem = this.getCraftForId(craft_id);
                if (craftItem == null)
                    return;

                // 1. VALIDACIÓN: Verificar si tiene todos los ingredientes antes de borrar nada
                for (IngredienteItem ing : craftItem.getIngredientes().values()) {
                    int totalNecesario = ing.getCantidad() * cantidad_craftear;
                    ItemUsuario itemEnInventario = inventoryHabboNew.getItem(ing.getItemName());

                    if (itemEnInventario == null || itemEnInventario.getCantidad() < totalNecesario) {
                        // Opcional: Enviar alerta al usuario de que no tiene suficientes materiales
                        return;
                    }
                }

                // 2. COBRO: Restar ingredientes del inventario
                for (IngredienteItem ing : craftItem.getIngredientes().values()) {
                    int totalARestar = ing.getCantidad() * cantidad_craftear;
                    inventoryHabboNew.removeCantidadItem(ing.getItemName(), totalARestar);
                }

                // 3. ENTREGA: Dar las recompensas multiplicadas
                for (RewardItem rewards : craftItem.getRewards().values()) {
                    int cantidadTotalPremio = rewards.getCantidad() * cantidad_craftear;

                    if (rewards.getItemExist() == 1) {
                        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(rewards.getItemName());
                        for (int i = 0; i < rewards.getCantidad(); i++) {
                            HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                                    habbo.getHabboInfo().getId(),
                                    baseItem,
                                    0,
                                    0,
                                    "");
                            habbo.getInventory().getItemsComponent().addItem(habboItem);
                            habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
                        }
                        habbo.getClient().sendResponse(new InventoryRefreshComposer());
                    } else {
                        // Añadir al inventario (ajusta el método según tu estructura, ej: addItem o
                        // addCantidadItem)
                        inventoryHabboNew.addCantidadItem(rewards.getItemName(), cantidadTotalPremio);
                    }

                }

                // 4. NOTIFICACIÓN: Opcional, actualizar al cliente que el crafteo fue exitoso
                this.ocultCrafteo(wsclient.getHabbo());
                this.showCrafteo(CrafteosTypes.Carpinteros, habbo);

                break;
            case "VenderMadera":
                if (array.isEmpty())
                    return;

                String comboId = (String) array.get(0); // Ej: "combo_1", "combo_2"
                int cantidadRequerida = 0;
                int pago = 0;

                if (comboId.equals("combo_1")) {
                    cantidadRequerida = 10;
                    pago = 50;
                } else if (comboId.equals("combo_2")) {
                    cantidadRequerida = 50;
                    pago = 300;
                } else if (comboId.equals("combo_3")) {
                    cantidadRequerida = 100;
                    pago = 700;
                }

                ItemUsuario maderaItem = inventory.getItem("madera"); // Nombre técnico en DB

                if (maderaItem != null && maderaItem.getCantidad() >= cantidadRequerida) {
                    inventory.removeCantidadItem("madera", cantidadRequerida);
                    habbo.giveCredits(pago);
                    habbo.shout("¡He vendido un lote de madera por $" + pago + "!");
                    // Opcional: refrescar UI
                    this.showCrafteo(CrafteosTypes.Carpinteros, habbo);
                } else {
                    habbo.whisper("No tienes suficiente madera para este trato.");
                }
                break;
        }

    }

}
