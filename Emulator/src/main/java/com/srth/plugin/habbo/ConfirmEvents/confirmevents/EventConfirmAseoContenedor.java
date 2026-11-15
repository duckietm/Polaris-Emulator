package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.TasksWorks;

public class EventConfirmAseoContenedor {

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

    public EventConfirmAseoContenedor(Habbo habbo) throws SQLException {
        this.habbo = habbo;

        if (this.habbo != null) {

            room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                // Cantidad succes actual
                int cantidad_basuras_success = habboRp.getNewInventoryHabbo().getItem("basura").getCantidad();
                int operacion = cantidad_basuras_success / 3;

                String data[] = habboRp.checkSettings("global_confirm_data", "").replace(";", ":").split(":");
                int item_id = Integer.parseInt(data[1]);

                HabboItem habbo_item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(item_id);
                String name_item = habbo_item.getBaseItem().getName();

                habbo_item.setExtradata("1");
                habbo_item.needsUpdate(true);
                habbo_item.run();
                this.updateSQLItem(1, item_id);
                room.updateItem(habbo_item);
                room.updateItemState(habbo_item);
                Emulator.getThreading().run(habbo_item);

                // Congelar al keko y generar efecto
                habbo.getRoomUnit().setCanWalk(false);
                room.giveEffect(habbo,
                        Integer.valueOf(4),
                        -1);

                // Mostrar proceso
                habboRp.getVidaHambreDormir()
                        .showTimeProcess("RoleplayWebsocketsRoomsInfo/job_aseo/" + name_item + "_icon.png",
                                RoleplayWebsocketsRoomsInfo.getTexts()
                                        .getValue("action_aseo_contenedor.action_bote_basura")
                                        .replace("%cantidad%", cantidad_basuras_success + ""),
                                2, true);
                habboRp.setProgressPendiente(true);

                Emulator.getThreading().run(() -> {

                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cash/0.png",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_aseo_contenedor_cash.success")
                                    .replace("%br%", "\n").replace("%cantidad%", operacion + ""));
                    habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_bote_basura_contenedor.success")
                            .replace("%cantidad%", cantidad_basuras_success + ""));
                    int type = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
                    habboRp.getHabbo().givePoints(type, operacion);
                    // Progreso de tareas
                    habboRp.getTareas().insertProgress("AseoBotarBasura");

                    try {

                        // Comprobar y insertar progreso task
                        new TasksWorks().CheckAndInsertTaskActive(habbo_item, habbo);
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

                    habbo_item.setExtradata("0");
                    habbo_item.needsUpdate(true);
                    this.updateSQLItem(0, item_id);
                    room.updateItem(habbo_item);
                    room.updateItemState(habbo_item);
                    room.updateTile(room.getLayout().getTile(habbo_item.getX(), habbo_item.getY()));
                    Emulator.getThreading().run(habbo_item);

                    new EventosEmpleosTutorial(habbo).eventTirarBotesDeBasuraContenedor();

                    // Remover items del inventario
                    habboRp.getNewInventoryHabbo().removeCantidadItem("basura", cantidad_basuras_success);

                }, 2500);
            }
        }
    }
}
