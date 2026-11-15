package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.TasksWorks;

import gnu.trove.map.hash.THashMap;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionBasuraUrban extends HabboItem {

    private String name_item;

    public InteractionBasuraUrban(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionBasuraUrban(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.name_item = baseItem.getName();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        this.eventClick(client, room);

    }

    public void eventClick(GameClient client, Room room) {

        try {
            if (client != null) {

                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
                    return;
                }

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

                if (RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                        client.getHabbo().getRoomUnit().getCurrentLocation())) {

                    if (habboRp.getProgressPendiente()) {
                        return;
                    }

                    if (habboRp.getJobActive() == null) {
                        this.buscarBasuraCivil(client, room);
                        return;
                    }

                    if (habboRp.getJobActive() != null
                            && !habboRp.getJobActive().getName().equalsIgnoreCase("aseo")) {
                        return;
                    }

                    if (habboRp.getJobActive() != null
                            && habboRp.getJobActive().getName().equalsIgnoreCase("aseo")) {
                        this.recogerBasuraEmpleado(client, room);
                        return;
                    }

                }
            }
        } catch (Exception e) {
        }

    }

    public void recogerBasuraEmpleado(GameClient client, Room room) {

        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

            if (this.getExtradata().equalsIgnoreCase("0")) {
                return;
            }

            this.setExtradata("0");
            this.needsUpdate(true);
            room.updateItemState(this);
            room.updateItem(this);
            Emulator.getThreading().run(this);

            String[] cantidad_basura = RoleplayWebsocketsRoomsInfo.getConfig()
                    .getValue("global_rp_urban_basura.amount").split(";");
            int amount = Integer
                    .parseInt(cantidad_basura[Emulator.getRandom().nextInt(cantidad_basura.length)]);

            // Mostrar proceso
            habboRp.getVidaHambreDormir().showTimeProcess(
                    "RoleplayWebsocketsRoomsInfo/job_aseo/" + name_item + "_icon.png",
                    RoleplayWebsocketsRoomsInfo.getTexts()
                            .getValue("action_basura_urban_contenedor_process_pendient")
                            .replace("%amount%", amount + ""),
                    5, true);
            room.giveEffect(client.getHabbo(), 4, 5);
            habboRp.setProgressPendiente(true);
            habboRp.allowMove(false);

            int count_basuras = 0;
            if (habboRp.getNewInventoryHabbo().getItem("basura") != null) {

                count_basuras = habboRp.getNewInventoryHabbo().getItem("basura").getCantidad();

            }
            int count_total = count_basuras + amount;

            Emulator.getThreading().run(() -> {

                // Mostrar alerta
                THashMap<String, String> keys2 = new THashMap<>();
                keys2.put("display", "BUBBLE");
                keys2.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/job_aseo/"
                        + name_item + "_icon.png");
                keys2.put("message",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura_empty.success")
                                .replace("%br%", "\n").replace("%cantidad%", amount + "")
                                .replace("%cantidad_total%", count_total + ""));
                client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/basura.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura_empty.success")
                                .replace("%br%", "\n").replace("%cantidad%", amount + "")
                                .replace("%cantidad_total%", count_total + ""));

                client.getHabbo().shout(RoleplayWebsocketsRoomsInfo.getTexts()
                        .getValue("action_bote_basura_calle_empty.success").replace("%cantidad%", amount + ""));

                if (habboRp.getNewInventoryHabbo().getItem("basura") == null) {
                    habboRp.getNewInventoryHabbo().createItem("basura", amount);
                } else {
                    habboRp.getNewInventoryHabbo().addCantidadItem("basura", amount);
                }
                habboRp.getNewInventoryHabbo().incrementNewItemsCounter(amount);

                // TAREA ROLEPLAY (INSERTAR)
                habboRp.getTareas().insertProgress("RecBasuraCiudad");

                // Comprobar y insertar progreso task
                new TasksWorks().CheckAndInsertTaskActive(this, client.getHabbo());

                try {
                    // Ocultar carga proceso
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // Descongelar al keko y generar efecto
                client.getHabbo().getRoomUnit().setCanWalk(true);
                room.giveEffect(client.getHabbo(),
                        Integer.valueOf(0),
                        -1);
                habboRp.setProgressPendiente(false);
                habboRp.allowMove(true);

                new EventosEmpleosTutorial(client.getHabbo()).eventRecogerBasuraBotesUrban();
                new EventosEmpleosTutorial(client.getHabbo()).eventRecogerBasuraBotesUrban20Botes();

            }, 5000);

            int time_active_interval = RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_rp_urban_basura.time.interval");
            // Vuelve a estar disponible
            Emulator.getThreading().run(() -> {
                this.setExtradata("1");
                this.needsUpdate(true);
                this.run();
                room.updateItemState(this);
                room.updateItem(this);
                Emulator.getThreading().run(this);
            }, time_active_interval);
        } catch (Exception e) {
        }
    }

    public void buscarBasuraCivil(GameClient client, Room room) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

            if (this.getExtradata().equalsIgnoreCase("0")) {
                return;
            }

            this.setExtradata("0");
            this.needsUpdate(true);
            this.run();
            room.updateItemState(this);
            room.updateItem(this);
            Emulator.getThreading().run(this);

            // Mostrar proceso de búsqueda
            habboRp.getVidaHambreDormir().showTimeProcess(
                    "RoleplayWebsocketsRoomsInfo/job_aseo/rp_urban_basura_icon.png",
                    "Buscando algo útil entre la basura...",
                    5, true);
            room.giveEffect(client.getHabbo(), 4, 5);
            habboRp.setProgressPendiente(true);
            habboRp.allowMove(false);

            Emulator.getThreading().run(() -> {
                // Probabilidad baja para cannabis (ej. 20% de probabilidad: número aleatorio
                // del 0 al 99 menor que 40)
                boolean encuentraCannabis = Emulator.getRandom().nextInt(100) < 40;

                if (encuentraCannabis) {
                    // Si hay suerte y encuentra cannabis (1 a 2 gramos para que sea cotizado)
                    int amountCannabis = Emulator.getRandom().nextInt(1, 3);

                    THashMap<String, String> keys2 = new THashMap<>();
                    keys2.put("display", "BUBBLE");
                    keys2.put("image",
                            "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/drogas/cannabis_icon.png");
                    keys2.put("message",
                            "¡Encontraste " + amountCannabis + " gramos de cannabis ocultos en la basura!");
                    client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                    if (habboRp.getNewInventoryHabbo().getItem("cannabis") == null) {
                        habboRp.getNewInventoryHabbo().createItem("cannabis", amountCannabis);
                    } else {
                        habboRp.getNewInventoryHabbo().addCantidadItem("cannabis", amountCannabis);
                    }
                    habboRp.getNewInventoryHabbo().incrementNewItemsCounter(amountCannabis);

                } else {
                    // Si no encuentra cannabis, tiramos otra probabilidad para ver si encuentra
                    // basura normal (50% de probabilidad)
                    boolean encuentraBasura = Emulator.getRandom().nextBoolean();

                    if (encuentraBasura) {
                        int amountBasura = Emulator.getRandom().nextInt(1, 3); // Entre 1 y 2 de basura

                        THashMap<String, String> keysBasura = new THashMap<>();
                        keysBasura.put("display", "BUBBLE");
                        keysBasura.put("image",
                                "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/job_aseo/"
                                        + name_item + "_icon.png");
                        keysBasura.put("message",
                                "Revisaste el contenedor y rescataste " + amountBasura + " unidades de basura.");
                        client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keysBasura));

                        if (habboRp.getNewInventoryHabbo().getItem("basura") == null) {
                            habboRp.getNewInventoryHabbo().createItem("basura", amountBasura);
                        } else {
                            habboRp.getNewInventoryHabbo().addCantidadItem("basura", amountBasura);
                        }
                        habboRp.getNewInventoryHabbo().incrementNewItemsCounter(amountBasura);

                    } else {
                        // Si no encuentra absolutamente nada
                        client.getHabbo().whisper(
                                "Revisaste bien el pote de basura, pero está más limpio que conciencia de político. No había nada útil.");
                    }
                }

                try {
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // Descongelar al keko
                client.getHabbo().getRoomUnit().setCanWalk(true);
                room.giveEffect(client.getHabbo(), Integer.valueOf(0), -1);
                habboRp.setProgressPendiente(false);
                habboRp.allowMove(true);

            }, 5000);

            int time_active_interval = RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_rp_urban_basura.time.interval");

            // Vuelve a estar disponible el basurero
            Emulator.getThreading().run(() -> {
                this.setExtradata("1");
                this.needsUpdate(true);
                this.run();
                room.updateItemState(this);
                room.updateItem(this);
                Emulator.getThreading().run(this);
            }, time_active_interval);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void droppearCannabis(GameClient client, Room room) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

            if (habboRp.getNewInventoryHabbo().getItem("cannabis") == null) {
                client.getHabbo().whisper("No tienes ni un solo gramo de cannabis en tu inventario para botar.");
                return;
            }

            int cantidadCannabis = habboRp.getNewInventoryHabbo().getItem("cannabis").getCantidad();

            if (cantidadCannabis <= 0) {
                client.getHabbo().whisper("No tienes cannabis disponible para soltar.");
                return;
            }

            // Remover el item del inventario o restar la cantidad total
            habboRp.getNewInventoryHabbo().removeCantidadItem("cannabis", cantidadCannabis);

            // Mensaje de alerta en burbuja
            THashMap<String, String> keys = new THashMap<>();
            keys.put("display", "BUBBLE");
            keys.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/drogas/cannabis_icon.png");
            keys.put("message", "Has botado y descartado " + cantidadCannabis + " gramos de cannabis al piso.");
            client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

            // El avatar grita o avisa en la sala
            client.getHabbo()
                    .shout("*Bota al suelo " + cantidadCannabis + " gramos de cannabis y se deshace de ellos*");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("1");
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
    }

}
