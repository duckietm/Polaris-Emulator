package com.srth.plugin.interactions.vidayhambre;

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
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.habbo.Jobs.ManagerButtonsRight;

public class InteractionManagerJobMedico extends InteractionTeleport {
    public InteractionManagerJobMedico(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionManagerJobMedico(int id, int userId, Item item, String extradata, int limitedStack,
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

    private int roomUnitId = -1;

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

        }, 1500);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            if (this.forceRotation()) {
                roomUnit.setRotation(RoomUserRotation.fromValue(this.getRotation()));
                roomUnit.canRotate = false;
            }
            this.roomUnitId = roomUnit.getId();

            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) {

                String job_name = "medico";
                Job job = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(job_name);
                if (job == null) {
                    return;
                }

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                // Comprobacion permiso para activar empleo
                habboRp.checkSettings("job_medico.permise_active", 0);
                if(habboRp.getBoolean("job_medico.permise_active") == false){
                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_medico/work_active.png", "job_permise_staff.need.active",
                                habbo.getClient());
                    }, 100);
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

                    new EventosEmpleosTutorial(habbo).eventActiveEmpleoMedico();

                    // TAREA ROLEPLAY (INSERTAR)
                    habboRp.getTareas().insertProgress("NewEmpleadoMedico");

                    job.setEmpleado(habbo);
                    habboRp.setJobActive(job);
                    JobEmpleado empleado = job.getEmpleado(habbo);

                    InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

                    inventoryHabboNew.desequipAllItemsExcPlatillos();
                    inventoryHabboNew.addItemUsed("jeringa");
                    inventoryHabboNew.showButtonsUsar(habbo);

                    // Mostrar bottones
                    new ManagerButtonsRight().ViewButtons(habbo);
                    RoleplayWebsocketsRoomsInfo.getJobsManager().showMenuJob(habbo);

                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_medico/work_active.png", "work_medico.user.enabled",
                                habbo.getClient());
                    }, 200);

                    // Guardar ultimo look
                    habboRp.updateData("global_ultimo_look_save", habbo.getHabboInfo().getLook());
                    empleado.changeLookEmpleado(habbo.getHabboInfo().getGender().name());
                    this.habboMoveFrontTile(habbo);

                    return;
                } else {

                    if (habboRp.getHanditem() == 1014) {
                        habboRp.setHanditem(0);
                        room.giveHandItem(roomUnit, 0);
                    }

                    JobEmpleado empleado = job.getEmpleado(habbo);

                    job.removeEmpleado(habbo);
                    habboRp.setJobActive(null);

                    InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

                    inventoryHabboNew.desequipAllItemsExcPlatillos();
                    inventoryHabboNew.showButtonsUsar(habbo);

                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_medico/work_disabled.png", "work_medico.user.disabled",
                                habbo.getClient());
                    }, 200);

                    // Establecer el ultimo look
                    job.resetLook(habbo);

                    // Ocultar bottones
                    new ManagerButtonsRight().ViewButtons(habbo);
                    Emulator.getThreading().run(() -> {
                        habboRp.setJobActive(null);
                        try {
                            new ManagerButtonsRight().ViewButtons(habbo);
                        } catch (Exception e) {
                        }
                    }, 1000);

                    // Ocultar ventanas
                    new ManagerButtonsRight().ocultWindowWork(habbo);

                    this.habboMoveFrontTile(habbo);

                    return;
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
