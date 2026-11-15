package com.srth.plugin.interactions.vidayhambre;

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
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.habbo.Jobs.ManagerButtonsRight;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionManagerJobAseo extends InteractionTeleport {
    public InteractionManagerJobAseo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionManagerJobAseo(int id, int userId, Item item, String extradata, int limitedStack,
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

        }, 1200);
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

                String job_name = "aseo";
                Job job = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(job_name);
                if (job == null) {
                    return;
                }

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                // Comprobar work global active
                if (habboRp.getJobActive() != null && !habboRp.getJobActive().getName().equalsIgnoreCase(job_name)) {
                    habbo.whisper(
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.other.work.active")
                                    .replace("%empleo%", habboRp.getJobActive().getName())
                                    .replace("%new_empleo%", job_name).replace("work_", ""),
                            RoomChatMessageBubbles.NORMAL);
                    return;
                }

                if (job.getEmpleado(habbo) != null && job.getEmpleado(habbo).getSueldoAcumulado() >= 2) {
                    int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
                    int monto = job.getSueldo();
                    if (RoleplayWebsocketsRoomsInfo.getConfig()
                            .getBoolean("seasonal.rol.primary.type.its.credits") == true) {
                        habbo.giveCredits(monto);
                    } else {
                        habbo.givePoints(type_moneda, monto);
                    }
                    habboRp.update(job_name + ".sueldo", "0");
                    Emulator.getThreading().run(() -> {
                        // Mostrar alerta
                        RoleplayWebsocketsRoomsInfo.utilities()
                                .showAlertUser("RoleplayWebsocketsRoomsInfo/cash/0.png",
                                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("works.info.sueldo.success")
                                                .replace("%monto%", monto + "").replace("%br%", "\n"),
                                        habbo.getClient());
                    }, 300);
                }

                if (job.getEmpleado(habbo) == null) {

                    job.setEmpleado(habbo);
                    habboRp.setJobActive(job);

                    new EventosEmpleosTutorial(habbo).eventActiveEmpleoAseo();

                    // TAREA ROLEPLAY (INSERTAR)
                    habboRp.getTareas().insertProgress("NewEmpleadoAseo");

                    JobEmpleado empleado = job.getEmpleado(habbo);
                    if (empleado.getTutorialActive() == true) {
                        habboRp.update("tutorial_active", "1");
                        habboRp.update("tutorial_process", "54");
                    }

                    // Activar trabajo
                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_aseo/work_active.png", "work_aseo.user.enabled",
                                habbo.getClient());
                    }, 200);

                    // Guardar ultimo look
                    habboRp.updateData("global_ultimo_look_save", habbo.getHabboInfo().getLook());
                    empleado.changeLookEmpleado(habbo.getHabboInfo().getGender().name());

                    Emulator.getThreading().run(() -> {

                        try {

                        } catch (Exception e) {
                        }

                    }, 1000);

                    // Mostrar bottones
                    new ManagerButtonsRight().ViewButtons(habbo);
                    RoleplayWebsocketsRoomsInfo.getJobsManager().showMenuJob(habbo);

                    this.habboMoveFrontTile(habbo);

                    // Mostrar turnos
                    RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

                    return;
                } else {

                    JobEmpleado empleado = job.getEmpleado(habbo);

                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_aseo/work_disabled.png", "work_aseo.user.disabled",
                                habbo.getClient());
                    }, 200);

                    // Establecer el ultimo look
                    job.resetLook(habbo);

                    // Generar efecto
                    room.giveEffect(habbo, Integer.valueOf(0), -1);

                    // Ocultar ventanas
                    new ManagerButtonsRight().ocultWindowWork(habbo);

                    // Ocultar turnos
                    RoleplayWebsocketsRoomsInfo.getTurnosManager().ocultTimeTurnos(habbo);

                    // Ocultar bottones
                    new ManagerButtonsRight().ViewButtons(habbo);
                    Emulator.getThreading().run(() -> {
                        habboRp.setJobActive(null);
                        try {
                            new ManagerButtonsRight().ViewButtons(habbo);
                        } catch (Exception e) {
                        }
                    }, 1000);

                    job.removeEmpleado(habbo);

                    Emulator.getThreading().run(() -> {
                        habboRp.setJobActive(null);
                    }, 1000);

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
