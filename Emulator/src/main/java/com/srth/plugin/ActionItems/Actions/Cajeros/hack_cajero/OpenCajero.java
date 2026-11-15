package com.srth.plugin.ActionItems.Actions.Cajeros.hack_cajero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

import gnu.trove.map.hash.THashMap;

public class OpenCajero extends Scheduler {

    private int roomUnitId = -1;
    private Habbo habbo;
    private HabboItem item;

    public OpenCajero(HabboItem habboItem, Habbo habbo) {
        super(1);
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp == null || habboItem == null) {
                return;
            }

            this.habbo = habbo;
            this.item = habboItem;

            // Poner a funcionar el timer cada 1 segundo exacto
            this.roomUnitId = habbo.getRoomUnit().getId();
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);

        } catch (Exception e) {
        }
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    public void showVentana(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp == null) {
                return;
            }

            JsonArray HabboInfo = new JsonArray();
            JsonObject jsonObjectInfo;
            jsonObjectInfo = new JsonObject();
            jsonObjectInfo.addProperty("img_furni",
                    RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis") + "icons/"
                            + item.getBaseItem().getName() + "_icon.png");
            jsonObjectInfo.addProperty("item_id", item.getId());
            jsonObjectInfo.addProperty("dinero_deposited", habboRp.getDineroDeposited());
            jsonObjectInfo.addProperty("dinero_monedero", habboRp.getDineroMonederoAndInventory());

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                    .replace("%url.hotel.base%", url_hotel_base);
            String url_images = global_url_images + "/";
            jsonObjectInfo.addProperty("url_img_content", url_images + "media/cajero_view.png");

            // Nombres de los eventos que se recibiran
            jsonObjectInfo.addProperty("event_retirar_dinero", "RetirarMyDinero");
            jsonObjectInfo.addProperty("event_depositar_dinero", "DepositarMyDinero");
            jsonObjectInfo.addProperty("event_transferir_otros", "TransferDineroToHabbo");

            HabboInfo.add(jsonObjectInfo);

            JsonArray HabbosOnlines = new JsonArray();
            JsonObject jsonObject;
            for (Habbo habbos : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {

                Room roomHabbo = habbos.getHabboInfo().getCurrentRoom();
                if (roomHabbo.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id") || habbos == habboRp.getHabbo()) {
                    continue;
                }

                jsonObject = new JsonObject();
                String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                        + "&headonly=1&size=a&gesture=sml&head_direction=2";
                jsonObject.addProperty("user_look", look.replace("%LOOK%", habbo.getHabboInfo().getLook()));
                jsonObject.addProperty("username", habbos.getHabboInfo().getUsername());
                jsonObject.addProperty("user_id", habbos.getHabboInfo().getId());
                HabbosOnlines.add(jsonObject);
            }

            habboRp.getHabboClient().send(
                    new MessageManagerDouble("MessageManager", true, Outcoming.OpenCajero, HabboInfo, HabbosOnlines));
        } catch (Exception e) {
        }
    }

    public void ocultVentana(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp == null) {
                return;
            }
            habboRp.getHabboClient()
                    .send(new MessageManagerDouble("MessageManager", false, Outcoming.OpenCajero, null, null));
        } catch (Exception e) {
        }
    }

    @Override
    public void run() {
        super.run();

        if (this.disposed || roomUnitId == -1 || habbo == null) {
            return;
        }

        try {
            final Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room == null) {
                this.setDisposed(true);
                return;
            }

            Habbo currentHabbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (currentHabbo != null && currentHabbo.isOnline()) {
                // Cuando te alejas del cajero
                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                        currentHabbo.getRoomUnit().getCurrentLocation())) {
                    this.roomUnitId = -1;
                    this.setDisposed(true);

                    if (ItemActionManager.getCajeroAbierto(currentHabbo) != null) {
                        ItemActionManager.deleteCajeroAbierto(currentHabbo);
                    }

                    this.ocultVentana(currentHabbo);
                    return;
                }

                // Reprogramar el siguiente tick de 1 segundo si sigue abierto y al lado
                if (!this.disposed) {
                    Emulator.getThreading().run(this, 1000);
                }
            } else {
                this.setDisposed(true);
            }
        } catch (Exception e) {
            this.setDisposed(true);
        }
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }

    public void getIncomingActions(GameClient client, THashMap<String, Object> data) {

        if (((String) data.get("name")).equalsIgnoreCase("RetirarMyDinero")) {

            int dinero_retirar = 0;
            try {
                dinero_retirar = Integer.parseInt((String) data.get("action"));
            } catch (Exception e) {
                dinero_retirar = (int) ((double) data.get("action"));
            }

            try {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

                int dineroDepositado = habboRp.getDineroDeposited();

                if (dineroDepositado >= dinero_retirar) {

                    int operacion = habboRp.getDineroDeposited() - dinero_retirar;
                    habboRp.setDineroDeposited(operacion);

                    client.getHabbo().givePoints(
                            RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"),
                            dinero_retirar);

                    client.getHabbo()
                            .alert("Retiraste: " + dinero_retirar + " "
                                    + RoleplayWebsocketsRoomsInfo.getConfig()
                                            .getValue("seasonal.rol.primary.item.starts.name")
                                    + " de tu cuenta bancaria exitosamente.");

                    this.showVentana(client.getHabbo());
                } else {
                    client.getHabbo().alert("Estas intentando retirar una cantidad mayor a la que tienes depositada.");
                }

            } catch (Exception e) {
            }
        }

        if (((String) data.get("name")).equalsIgnoreCase("DepositarMyDinero")) {
            try {

                int dinero_depositar = 0;
                try {
                    dinero_depositar = Integer.parseInt((String) data.get("action"));
                } catch (Exception e) {
                    dinero_depositar = (int) ((double) data.get("action"));
                }

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

                int dinero_inventario = habboRp.getCashInventario();
                int dinero_monedero = habboRp.getDineroMonedero();
                int dineroAll = habboRp.getDineroMonederoAndInventory();

                if (dineroAll >= dinero_depositar) {

                    int dinero_total_inventario = 0;

                    if (dinero_inventario >= 1) {
                        ArrayList<HabboItem> items = new ArrayList<>();
                        Map<Integer, Integer> points = new HashMap<>();

                        for (HabboItem item : client.getHabbo().getInventory().getItemsComponent()
                                .getItemsAsValueCollection()) {
                            if (item.getBaseItem().getName().startsWith("CASH_")) {

                                int pointsType = RoleplayWebsocketsRoomsInfo.getConfig()
                                        .getInt("seasonal.rol.primary.type");
                                int pointsAmount = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                                points.put(pointsType, points.getOrDefault(pointsType, 0) + pointsAmount);

                                dinero_total_inventario += pointsAmount;

                                items.add(item);
                            }
                        }

                        Map<Integer, HabboItem> deleted = new HashMap<>();
                        for (HabboItem item : items) {
                            client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
                            deleted.put(item.getId(), item);
                        }

                        Emulator.getThreading().run(new QueryDeleteHabboItems(deleted.values()));
                        client.sendResponse(new InventoryRefreshComposer());
                    }

                    if (dinero_monedero >= 1) {

                        if (dinero_total_inventario >= 1) {

                            int cantidad = dinero_depositar;

                            if (dinero_total_inventario >= dinero_depositar) {
                                return;
                            } else {
                                cantidad -= dinero_total_inventario;
                            }

                            client.getHabbo().givePoints(
                                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"),
                                    -cantidad);
                        } else {
                            int cantidad = dinero_depositar;
                            client.getHabbo().givePoints(
                                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"),
                                    -cantidad);
                        }
                    }

                    int operacion = habboRp.getDineroDeposited() + dinero_depositar;
                    habboRp.setDineroDeposited(operacion);

                    client.getHabbo().alert("Depositaste en tu cuenta: " + dinero_depositar + " "
                            + RoleplayWebsocketsRoomsInfo.getConfig().getValue("seasonal.rol.primary.item.starts.name")
                            + ", exitosamente");
                    this.showVentana(client.getHabbo());

                    // EVENTO DEL TUTORIAL
                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos()
                            .eventDepositarDineroCajero(habbo);

                } else {
                    client.getHabbo().alert("Estas intentando depositar un dinero mayor al que tienes.");
                }

            } catch (Exception e) {
            }
        }

        if (((String) data.get("name")).equalsIgnoreCase("TransferDineroToHabbo")) {

            String viewReceibed = (String) data.get("item");
            String[] data_receibed = viewReceibed.replace(" ", "").split(":");

            Habbo habboCuestion = Emulator.getGameEnvironment().getHabboManager().getHabbo(data_receibed[0]);

            if (habboCuestion == null) {
                client.getHabbo().alert("El usuario no se encuentra o no esta conectado.");
                return;
            } else {

                if (habboCuestion == client.getHabbo()) {
                    client.getHabbo().alert("No puedes transferirte dinero a ti mismo.");
                    return;
                }

                try {

                    HabboRp habboRpMy = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());
                    HabboRp habboRpOther = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habboCuestion);

                    int dinero_transferencia = Integer.parseInt(data_receibed[1]);
                    int dinero_depositado_en_cuenta = habboRpMy.getDineroDeposited();

                    if (dinero_transferencia > dinero_depositado_en_cuenta) {
                        client.getHabbo().alert("La cantidad a transferir supera tu monto actual.");
                        return;
                    } else {
                        int operacion = habboRpMy.getDineroDeposited() - dinero_transferencia;
                        habboRpMy.setDineroDeposited(operacion);

                        int sumatoriaOtro = habboRpOther.getDineroDeposited() + dinero_transferencia;
                        habboRpOther.setDineroDeposited(sumatoriaOtro);

                        client.getHabbo().alert("Dinero transferido a: " + habboCuestion.getHabboInfo().getUsername()
                                + " exitosamente");
                        this.showVentana(client.getHabbo());

                        if (ItemActionManager.getCajeroAbierto(habboCuestion) != null) {
                            habboCuestion.alert("Recibiste: " + dinero_transferencia + " exitosamente, por parte de: "
                                    + client.getHabbo().getHabboInfo().getUsername());
                            ItemActionManager.getCajeroAbierto(habboCuestion).showVentana(habboCuestion);
                        }

                        return;
                    }
                } catch (Exception e) {

                }
            }
        }
    }
}