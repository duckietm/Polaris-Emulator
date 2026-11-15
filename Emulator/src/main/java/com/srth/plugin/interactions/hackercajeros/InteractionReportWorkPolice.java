package com.srth.plugin.interactions.hackercajeros;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.habbo.Jobs.TasksWorks;

import gnu.trove.map.hash.THashMap;

public class InteractionReportWorkPolice extends InteractionEffectTile implements ICycleable {

    private int default_sumatoria = 10;
    private int timesumatoria = default_sumatoria;

    private int startTime = 0;
    private int startTime0 = 0;
    private int roomUnitId = -1;
    private int count = 1;

    public InteractionReportWorkPolice(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionReportWorkPolice(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {

        super.onClick(client, room, objects);

        if (client != null) {

            if (client.getHabbo().getRoomUnit().getRoomUnitType() == RoomUnitType.USER) {

                Habbo habbo = client.getHabbo();

                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
                    return;
                }

                if (habbo != null) {

                    if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                            habbo.getRoomUnit().getCurrentLocation())) {
                        return;
                    }

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                    String job_name = "police";
                    Job job = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(job_name);
                    if (job == null) {
                        return;
                    }

                    // Comprobar work global active
                    if (habboRp.getJobActive() != null
                            && !habboRp.getJobActive().getName().equalsIgnoreCase(job_name)) {
                        habbo.whisper(
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.other.work.active")
                                        .replace("%empleo%", habboRp.getJobActive().getName())
                                        .replace("%new_empleo%", job_name).replace("work_", ""),
                                RoomChatMessageBubbles.NORMAL);
                        return;
                    }

                    if (habboRp.getInt("job_polices.reporte.pendiente") == 0) {

                        room.giveEffect(habbo, Integer.valueOf(0), -1);

                        if (habboRp.getHanditem() != 2001) {
                            habboRp.setHanditem(2001);
                            room.giveHandItem(habbo.getRoomUnit(), 2001);
                        }

                        // Mostrar alerta
                        // RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/job_polices/arma_active.png","work_police.success.arma",
                        // client);

                        // Ver estadisticas
                        RoleplayWebsocketsRoomsInfo.utilities().policeViewReportes(habbo);

                        return;
                    }

                    // Si esta disponible para hacer reporte
                    if (this.getExtradata().equalsIgnoreCase("0")) {

                        // Colocar en uso
                        this.setExtradata("1");
                        this.needsUpdate(true);
                        room.updateItemState(this);
                        room.updateItem(this);
                        Emulator.getThreading().run(this);

                        this.roomUnitId = client.getHabbo().getRoomUnit().getId();

                        this.startTime = Emulator.getIntUnixTimestamp();
                        this.startTime0 = Emulator.getIntUnixTimestamp();

                        // Mostrar alerta
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_polices/reporte_police.png",
                                "work_police.notif.report.inicie", habbo.getClient());

                        // Efecto de estrellitas
                        room.giveEffect(habbo, Integer.valueOf(4), 9);

                        // Mostrar alerta proceso
                        habboRp.getVidaHambreDormir().showTimeProcess(
                                "RoleplayWebsocketsRoomsInfo/job_polices/reporte_police_pendiente.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.reporte.process"),
                                default_sumatoria + 10, true);
                        habboRp.setProgressPendiente(true);

                    } else {
                        client.getHabbo().whisper(
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_police.report.furni.disabled"),
                                RoomChatMessageBubbles.NORMAL);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void cycle(Room room) {
        if (this.roomUnitId != -1) {

            Habbo habbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (habbo != null) {

                if (habbo.getRoomUnit().getEffectId() == 101) {
                    return;
                }

                int timestamp0 = Emulator.getIntUnixTimestamp();

                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {
                    this.resetTime();

                    room.giveEffect(habbo,
                            Integer.valueOf(0),
                            -1);

                    this.setExtradata("0");
                    this.needsUpdate(true);
                    room.updateItemState(this);
                    room.updateItem(this);
                    Emulator.getThreading().run(this);

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        // Ocultar alerta proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();
                        habboRp.setProgressPendiente(false);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    return;
                }

                if (timestamp0 - this.startTime0 >= 10) {

                    this.setExtradata(count + 1 + "");
                    this.needsUpdate(true);
                    room.updateItemState(this);
                    room.updateItem(this);
                    Emulator.getThreading().run(this);

                    // Mostrar alerta al usuario
                    THashMap<String, String> keys2 = new THashMap<>();
                    keys2.put("display", "BUBBLE");
                    keys2.put("image",
                            "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/job_polices/.gif");
                    keys2.put("message",
                            RoleplayWebsocketsRoomsInfo.getTexts()
                                    .getValue("work_police.notif.report.increment.time.espera")
                                    .replace("%total_time%", timesumatoria + ""));

                    if (timesumatoria != 0) {
                        habbo.getClient()
                                .sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));
                        room.giveEffect(habbo, Integer.valueOf(4), 8);
                    }

                    this.startTime0 = timestamp0;
                    this.timesumatoria = timesumatoria - 10;

                }

                int timestamp = Emulator.getIntUnixTimestamp();
                if (timestamp - this.startTime >= 20) {

                    // Dar arma policial
                    room.giveHandItem(habbo.getRoomUnit(), 2001);

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        habboRp.setHanditem(2001);
                    } catch (Exception e) {
                    }

                    this.resetTime();

                    this.setExtradata("0");
                    this.needsUpdate(true);
                    room.updateItemState(this);
                    room.updateItem(this);
                    Emulator.getThreading().run(this);

                    try {
                        String job_name = "police";
                        Job job = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(job_name);
                        if (job == null) {
                            return;
                        }

                        JobEmpleado jobEmpleado = job.getEmpleado(habbo);

                        jobEmpleado.incrementSueldoAcumulado(job.getSueldo());

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        // Agregar un reporte pendiente al policia
                        int ultimo_reporte_pendiente = habboRp.checkSettings("job_polices.reporte.pendiente", 0);
                        int operacion = ultimo_reporte_pendiente - 1;
                        habboRp.update("job_polices.reporte.pendiente", operacion + "");

                    } catch (Exception e) {
                    }

                    // Comprobar y insertar progreso task
                    new TasksWorks().CheckAndInsertTaskActive(this, habbo);

                    try {

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        // Ocultar alerta proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();
                        habboRp.setProgressPendiente(false);

                        // Insertar tarea al empleado
                        habboRp.getTareas().insertProgress("RealizarExpediente");

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_polices/reporte_police.png",
                                "work_police.notif.report.success", habbo.getClient());
                    }, 500);

                }
            }
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        this.reset(room);
    }

    private void reset(Room room) {
        this.roomUnitId = -1;
        this.startTime = 0;
        this.setExtradata("0");
        room.updateItem(this);
    }

    private void resetTime() {
        int timestamp = Emulator.getIntUnixTimestamp();
        this.roomUnitId = -1;
        this.startTime = timestamp;
        this.startTime0 = timestamp;
        this.timesumatoria = default_sumatoria;
    }

}