package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.TasksWorks;

import gnu.trove.map.hash.THashMap;

public class EventConfirmBoteBasuraEmpty {

    private Habbo habbo;
    private Room room;

    public void updateSQLItem(int extra_data, int item_id) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE items SET `extra_data`='" + extra_data + "' WHERE `id`=" + item_id + "")) {
            statement.execute();
        } catch (SQLException e) {
        }
    }

    public EventConfirmBoteBasuraEmpty(Habbo habbo) throws SQLException {
        this.habbo = habbo;

        if (this.habbo != null) {

            room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (habboRp.getJobActive() == null || habboRp.getJobActive() != null
                        && !habboRp.getJobActive().getName().equalsIgnoreCase("aseo")) {
                    // Ocultar carga proceso
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    return;
                }

                String data[] = habboRp.checkSettings("global_confirm_data", "").replace(";", ":").split(":");
                int item_id = Integer.parseInt(data[1]);

                HabboItem habbo_item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(item_id);
                String name_item = habbo_item.getBaseItem().getName();

                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(habbo_item.getX(), habbo_item.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {
                    return;
                }

                // Congelar al keko y generar efecto
                habbo.getRoomUnit().setCanWalk(false);
                room.giveEffect(habbo,
                        Integer.valueOf(4),
                        -1);

                // Mostrar proceso
                habboRp.getVidaHambreDormir().showTimeProcess(
                        "RoleplayWebsocketsRoomsInfo/cocinero/" + name_item + "_icon.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_aseo_contenedor_process_pendient"), 5,
                        true);
                habboRp.setProgressPendiente(true);

                int bote_user_id = habbo_item.getUserId();
                HabboRp habboRpOther = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRpOffline(bote_user_id);
                int cantidad_basura_room = habboRpOther.checkSettings("global_basura_room", 0);

                int count_basuras_user = habboRp.checkSettings("work_aseo.basura.recolected", 0);
                int count_total = count_basuras_user + cantidad_basura_room;

                Emulator.getThreading().run(() -> {

                    // Mostrar alerta
                    THashMap<String, String> keys2 = new THashMap<>();
                    keys2.put("display", "BUBBLE");
                    keys2.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/"
                            + name_item + "_icon.png");
                    keys2.put("message",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura_empty.success")
                                    .replace("%br%", "\n").replace("%cantidad%", cantidad_basura_room + "")
                                    .replace("%cantidad_total%", count_total + ""));
                    habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                    habboRp.update("work_aseo.basura.recolected", count_total + "");

                    if (habboRp.getNewInventoryHabbo().getItem("basura") == null) {
                        habboRp.getNewInventoryHabbo().createItem("basura", cantidad_basura_room);
                    } else {
                        habboRp.getNewInventoryHabbo().addCantidadItem("basura", cantidad_basura_room);
                    }

                    int value = habboRp.getNewInventoryHabbo().getItem("basura") != null
                            ? habboRp.getNewInventoryHabbo().getItem("basura").getCantidad()
                            : 0;

                    Emulator.getThreading().run(() -> {

                        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/basura.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura_empty.success")
                                        .replace("%cantidad%", cantidad_basura_room + "")
                                        .replace("%cantidad_total%", value + ""));

                        habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts()
                                .getValue("action_bote_basura_calle_empty.success").replace("%cantidad%", value + ""));

                    }, 1000);

                    habboRpOther.updateData("global_basura_room", "0");

                    // TAREA ROLEPLAY (INSERTAR)
                    habboRp.getTareas().insertProgress("RecBasuraUsuarios");

                    // Cuando eres de aseo
                    // Comprobar y insertar progreso task
                    new TasksWorks().CheckAndInsertTaskActive(habbo_item, habbo);

                    try {
                        // Ocultar carga proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    // Descongelar al keko y generar efecto
                    habbo.getRoomUnit().setCanWalk(true);
                    room.giveEffect(habbo,
                            Integer.valueOf(0),
                            -1);
                    habboRp.setProgressPendiente(false);

                    habbo_item.needsUpdate(true);
                    habbo_item.setExtradata("0");
                    habbo_item.run();
                    room.updateItemState(habbo_item);
                    this.updateSQLItem(0, item_id);
                    room.updateItem(habbo_item);
                    Emulator.getThreading().run(habbo_item);

                    new EventosEmpleosTutorial(habbo).eventLimpiarBoteDeBasuraAseo();

                }, 5000);

            }

        }
    }
}
