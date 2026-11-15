package com.srth.plugin.interactions.hackercajeros;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.habbo.Jobs.ManagerButtonsRight;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowUserActions;

public class InteractionManagerJobPolice extends InteractionTeleport {

    private int roomUnitId = -1;

    public InteractionManagerJobPolice(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionManagerJobPolice(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    public void habboMoveFrontTile(Habbo habbo) throws Exception {

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        habboRp.allowMove(false);

        Emulator.getThreading().run(() -> {

            RoomTile tile = habbo.getHabboInfo().getCurrentRoom().getLayout().getTileInFront(
                    habbo.getRoomUnit().getCurrentLocation(), habbo.getRoomUnit().getBodyRotation().getValue());
            if (tile != null && tile.isWalkable()) {
                habbo.getRoomUnit().setGoalLocation(tile);
            }
            habboRp.allowMove(true);
        }, 1200);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

        RoleplayWebsocketsRoomsInfo.utilities().LOGGER("InteractionManagerJobPolice: " + " ha interactuado con el item " + this.getBaseItem().getName());

        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            if (habbo != null) {

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("InteractionManagerJobPolice: Habbo " + habbo.getHabboInfo().getUsername()
                        + " ha interactuado con el item " + this.getBaseItem().getName());

                if (this.forceRotation()) {
                    roomUnit.setRotation(RoomUserRotation.fromValue(this.getRotation()));
                    roomUnit.canRotate = false;
                }
                this.roomUnitId = roomUnit.getId();

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                habboRp.checkSettings("job_polices.reporte.pendiente", 0);

                String job_name = "police";
                Job job = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(job_name);
                if (job == null) {
                    return;
                }

                // Comprobar work global active
                if (habboRp.getJobActive() != null && !habboRp.getJobActive().getName().equalsIgnoreCase(job_name)) {
                    habbo.whisper(
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.other.work.active")
                                    .replace("%empleo%", habboRp.getJobActive().getName())
                                    .replace("%new_empleo%", job_name).replace("work_", ""),
                            RoomChatMessageBubbles.NORMAL);
                    return;
                }

                if (job.getEmpleado(habbo) == null) {

                    // Insertar tarea al empleado
                    habboRp.getTareas().insertProgress("NewEmpleadoPolicia");

                    job.setEmpleado(habbo);
                    habboRp.setJobActive(job);
                    JobEmpleado empleado = job.getEmpleado(habbo);

                    new EventosEmpleosTutorial(habbo).eventActivarEmpleoPolicia();

                    InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

                    inventoryHabboNew.desequipAllItemsExcPlatillos();
                    inventoryHabboNew.addItemUsed("arma_policial");
                    inventoryHabboNew.showButtonsUsar(habbo);

                    // Mostrar bottones
                    new ManagerButtonsRight().ViewButtons(habbo);
                    RoleplayWebsocketsRoomsInfo.getJobsManager().showMenuJob(habbo);

                    habboRp.updateData("global.delitos.disabled", "1");
                    habboRp.updateData("work_polices.its.active", "1");

                    Emulator.getThreading().run(() -> {
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/police_active.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_police.user.active"));
                    }, 500);

                    // Guardar ultimo look
                    habboRp.updateData("global_ultimo_look_save", habbo.getHabboInfo().getLook());
                    empleado.changeLookEmpleado(habbo.getHabboInfo().getGender().name());

                    // Insertar ITEM en inventarioRP
                    habboRp.getNewInventoryHabbo().createItem("arma_policial", 1);

                    if (habboRp.checkSettings("job_polices.reporte.pendiente", 0) >= 1) {
                        Emulator.getThreading().run(() -> {
                            // Mostrar alerta
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                    "RoleplayWebsocketsRoomsInfo/job_polices/reporte_police_pendiente.png",
                                    "work_police.report.pendiente", habbo.getClient());
                        }, 1500);
                    }

                    // Te mueve hacia al frente
                    this.habboMoveFrontTile(habbo);
                    return;
                } else {

                    JobEmpleado empleado = job.getEmpleado(habbo);

                    if (empleado.getSueldoAcumulado() >= 1) {
                        habboRp.givePoints(empleado.getSueldoAcumulado());
                        empleado.setSueldoAcumulado(0);
                    }

                    if (habboRp.getHanditem() == 2001) {
                        habboRp.setHanditem(0);
                        room.giveHandItem(habbo.getRoomUnit(), 0);
                        habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_desequip_objet")
                                .replace("%OBJET_NAME%", "arma policial"));
                    }

                    job.removeEmpleado(habbo);
                    habboRp.setJobActive(null);

                    InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

                    inventoryHabboNew.desequipAllItemsExcPlatillos();
                    inventoryHabboNew.showButtonsUsar(habbo);

                    Emulator.getThreading().run(() -> {

                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/police_disabled.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_police.user.disabled"));

                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_polices/police_disabled.png",
                                "work_police.user.disabled", habbo.getClient());
                    }, 200);

                    // Establecer el ultimo look
                    habboRp.changeLookUltimo(habbo);

                    // Ocultar ventanas
                    habboRp.getHabboClient().send(new OutShowUserActions("ShowUserActions", false, null));
                    Emulator.getThreading().run(() -> {
                        try {
                            // Mostrar bottones
                            new ManagerButtonsRight().ViewButtons(habbo);
                        } catch (Exception e) {
                        }
                    }, 1000);

                    this.habboMoveFrontTile(habbo);
                }
            }
        }
    }

    public boolean forceRotation() {
        return true;
    }

    @Override
    public void setRotation(int rotation) {
        super.setRotation(rotation);

        if (this.forceRotation() && this.roomUnitId != -1) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room != null) {
                RoomUnit roomUnit = this.getCurrentRoomUnit(room);

                if (roomUnit != null) {
                    roomUnit.setRotation(RoomUserRotation.fromValue(rotation));
                    room.updateRoomUnit(roomUnit);
                }
            }
        }
    }

    private RoomUnit getCurrentRoomUnit(Room room) {
        Habbo habbo = room.getHabboByRoomUnitId(this.roomUnitId);
        if (habbo != null) {
            return habbo.getRoomUnit();
        } else {
            Bot bot = room.getBotByRoomUnitId(this.roomUnitId);
            if (bot != null) {
                return bot.getRoomUnit();
            }
        }

        return null;
    }
}
