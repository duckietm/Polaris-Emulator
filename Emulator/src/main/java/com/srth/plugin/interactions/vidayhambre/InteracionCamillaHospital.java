package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectTile;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Turnos.HabboTurnos;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.RemoveChatClient;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteracionCamillaHospital extends InteractionEffectTile implements ICycleable {

    private int default_sumatoria = RoleplayWebsocketsRoomsInfo.getConfig()
            .getInt("rp.rooms.camillahospital_time_espera.interval");
    // private int default_sumatoria = 99999;
    private int timesumatoria = default_sumatoria;
    private int sumatoria_show = default_sumatoria + 10;
    private int startTime = 0;
    private int startTime0 = 0;
    private int roomUnitId = -1;

    public InteracionCamillaHospital(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteracionCamillaHospital(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return this.roomUnitId == -1 && super.canWalkOn(roomUnit, room, objects)
                && (roomUnit.getRoomUnitType().equals(RoomUnitType.USER)
                        || roomUnit.getRoomUnitType().equals(RoomUnitType.BOT));
    }

    @Override
    public boolean isWalkable() {
        return this.roomUnitId == -1;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null && this.roomUnitId == -1) {

                if (habbo.getRoomUnit().getRoomUnitType() == RoomUnitType.USER) {

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                    HabboTutorial habboTutorial = habboRp.getHabboTutorial();
                    if (habboTutorial.getCompleted() == false) {
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/ws_hospital/camilla_icon.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("tutorial.camilla.warning"));
                        return;
                    }

                    if (habboRp.getVidaHambreDormir().getVida() >= 100
                            && habboRp.getVidaHambreDormir().getDormir() >= 100) {
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/ws_hospital/vida_completa.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("vida.not.full"));
                        return;
                    }

                    HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
                    habboTurnos.registerTurno("regenerarVida", default_sumatoria,
                            "Tiempo para recuperar salud o energía", 1, "job_medico/jeringa.png?v2");
                    RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/ws_hospital/camilla_icon.png?v2",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.camilla.process"), 9,
                            true);

                    habboRp.getVidaHambreDormir().camillaActive = true;
                    habboRp.setProgressPendiente(true);

                    this.roomUnitId = habbo.getRoomUnit().getId();
                    this.startTime = Emulator.getIntUnixTimestamp();
                    this.startTime0 = Emulator.getIntUnixTimestamp();

                }
            }
        }
    }

    @Override
    public void cycle(Room room) {
        if (this.roomUnitId != -1) {
            Habbo habbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (habbo != null) {
                int timestamp0 = Emulator.getIntUnixTimestamp();

                // 1. Verificación de adyacencia (Si se mueve, se cancela)
                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {
                    this.detenerProceso(habbo, room); // Método auxiliar para limpiar
                    return;
                }

                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    int vidaActual = habboRp.getVidaHambreDormir().getVida();
                    int energiaActual = habboRp.getVidaHambreDormir().getDormir();

                    if (vidaActual >= 100 && energiaActual >= 100) {
                        this.finalizarCuracionExitosa(habbo, habboRp, room);
                        return;
                    }

                    // 3. Lógica de curación por Tick (Cada 10 segundos)
                    if (timestamp0 - this.startTime0 >= 10) {

                        // Calculamos cuántos ticks de 10 segundos quedan aproximadamente
                        int tiempoTranscurrido = timestamp0 - this.startTime;
                        int tiempoRestanteTotal = Math.max(10, default_sumatoria - tiempoTranscurrido);
                        int ticksRestantes = tiempoRestanteTotal / 10;
                        if (ticksRestantes <= 0)
                            ticksRestantes = 1;

                        int vidaASumar = vidaActual < 100
                                ? (int) Math.ceil((double) (100 - vidaActual) / ticksRestantes)
                                : 0;
                        int energiaASumar = energiaActual < 100
                                ? (int) Math.ceil((double) (100 - energiaActual) / ticksRestantes)
                                : 0;

                        String progresoKey = vidaASumar > 0
                                ? "action_camilla_hospital.progress"
                                : "action_camilla_hospital.progress.energia";
                        String[] getProgresosText = RoleplayWebsocketsRoomsInfo.getTexts()
                                .getValue(progresoKey).split(";");
                        String msg = getProgresosText[Emulator.getRandom().nextInt(getProgresosText.length)];

                        if (vidaASumar > 0) {
                            habboRp.getVidaHambreDormir().sumarVidaProgresiva(vidaASumar);
                        }
                        if (energiaASumar > 0) {
                            habboRp.getVidaHambreDormir().recuperarEnergia(energiaASumar);
                        }

                        habboRp.getVidaHambreDormir().showTimeProcess(
                                "RoleplayWebsocketsRoomsInfo/ws_hospital/camilla_icon.png?v2",
                                msg + " (" + habboRp.getVidaHambreDormir().getVida() + "% vida, "
                                        + habboRp.getVidaHambreDormir().getDormir() + "% energía)",
                                9, true);

                        // Actualizar timers del tick
                        this.startTime0 = timestamp0;

                        // Comprobacion de hambre
                        if (habboRp.getVidaHambreDormir().getHambre() <= 20) {
                            habboRp.getVidaHambreDormir().comer(40);
                            habboRp.getVidaHambreDormir().ignoreHambre(6);
                        }

                    }

                    // 4. Seguridad: Si el tiempo total expiró pero no ha llegado a 100 por redondeo
                    if (timestamp0 - this.startTime >= sumatoria_show) {
                        this.finalizarCuracionExitosa(habbo, habboRp, room);
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Métodos auxiliares para mantener el código limpio:
    private void finalizarCuracionExitosa(Habbo habbo, HabboRp habboRp, Room room) {
        this.resetTime();
        try {

            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/ws_hospital/camilla_icon.png?v2",
                    "¡Tu personaje se ha recuperado!");

            HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
            habboTurnos.removeTurno("regenerarVida");
            RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

            habboRp.getVidaHambreDormir().allowMove(true);
            habboRp.getVidaHambreDormir().camillaActive = false;
            habboRp.setProgressPendiente(false);
            habboRp.getVidaHambreDormir().ocultarTimeProcess();
            if (habboRp.getVidaHambreDormir().getVida() < 100) {
                habboRp.getVidaHambreDormir().sumarVida(100);
            }
            if (habboRp.getVidaHambreDormir().getDormir() < 100) {
                habboRp.getVidaHambreDormir().recuperarEnergia(100);
            }
        } catch (Exception e) {
        }

        if (room != null) {
            room.unIdle(habbo);
            room.giveEffect(habbo.getRoomUnit(), 0, -1);
        }
        habboRp.setEnable(0);
    }

    private void detenerProceso(Habbo habbo, Room room) {
        room.giveEffect(habbo, 0, -1);
        this.reset(room);
        this.resetTime();
        room.unIdle(habbo);
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getVidaHambreDormir().camillaActive = false;
            habboRp.setProgressPendiente(false);
            habboRp.getVidaHambreDormir().ocultarTimeProcess();

            HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
            habboTurnos.removeTurno("regenerarVida");
            RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

        } catch (Exception e) {
            /* ignore */ }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null) {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            this.reset(room);
            this.resetTime();
            room.giveEffect(habbo,
                    Integer.valueOf(0),
                    -1);

            if (room != null) {
                room.unIdle(habbo);
            }

            habboRp.getVidaHambreDormir().camillaActive = false;
            habboRp.getVidaHambreDormir().allowMove(true);
            habboRp.setProgressPendiente(false);

            // Ocultar carga proceso
            habboRp.getVidaHambreDormir().ocultarTimeProcess();
            habboRp.getVidaHambreDormir().setDormirActive(false);

            if (habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(new RemoveChatClient("RemoveChatClient", false));
                habboRp.ocultMessage();
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