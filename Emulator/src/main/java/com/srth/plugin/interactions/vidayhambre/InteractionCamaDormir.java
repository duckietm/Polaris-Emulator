package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionEffectTile;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.outgoing.messages.RemoveChatClient;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionCamaDormir extends InteractionEffectTile implements ICycleable {

    private int default_sumatoria = RoleplayWebsocketsRoomsInfo.getConfig()
            .getInt("rp.rooms.dormir_time_espera.interval");
    // private int default_sumatoria = 99999;
    private int timesumatoria = default_sumatoria;
    private int sumatoria_show = default_sumatoria + 10;
    private int startTime = 0;
    private int startTime0 = 0;
    private int roomUnitId = -1;

    public InteractionCamaDormir(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionCamaDormir(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
                    if (habboTutorial.getParteTutorial() < 14) {
                        return;
                    }

                    if (habboRp.getVidaHambreDormir().getDormir() >= 100) {
                        habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("dormir.not.sueno"),
                                RoomChatMessageBubbles.NORMAL);
                        return;
                    }

                    habbo.getHabboInfo().getCurrentRoom().sendComposer(
                            new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.IDLE).compose());

                    // Mostrar proceso
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/dormir/suenos/dormir2.png?v2",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.dormir.process"), sumatoria_show,
                            true);

                    habboRp.getVidaHambreDormir().setDormirActive(true);
                    habboRp.setProgressPendiente(true);

                    this.roomUnitId = habbo.getRoomUnit().getId();
                    this.startTime = Emulator.getIntUnixTimestamp();
                    this.startTime0 = Emulator.getIntUnixTimestamp();

                    if (room != null) {
                        room.idle(habbo);
                    }

                    if (habboRp.getHabboClient() != null) {
                        habboRp.getHabboClient().send(new RemoveChatClient("RemoveChatClient", true));
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

                int timestamp0 = Emulator.getIntUnixTimestamp();

                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                        habbo.getRoomUnit().getCurrentLocation())) {

                    room.giveEffect(habbo,
                            Integer.valueOf(0),
                            -1);

                    this.reset(room);
                    this.resetTime();

                    if (room != null) {
                        room.unIdle(habbo);
                    }

                    return;
                }

                if (timestamp0 - this.startTime0 >= 10) {

                    int cantidad_azar = 100;
                    int cuenta_aleatorio = Emulator.getRandom().nextInt(cantidad_azar);
                    String ruta_imagen2 = "RoleplayWebsocketsRoomsInfo/dormir/suenos/";
                    String img_sueno2;

                    String[] getNombresSuenos = RoleplayWebsocketsRoomsInfo.getTexts()
                            .getValue("action_dormir.nombres.suenos").split(";");
                    String msg_sueno = getNombresSuenos[1];

                    if (cuenta_aleatorio >= 0 && cuenta_aleatorio < 10) {
                        img_sueno2 = ruta_imagen2 + "empty.png";
                        msg_sueno = getNombresSuenos[0];
                    } else if (cuenta_aleatorio >= 10 && cuenta_aleatorio < 30) {
                        img_sueno2 = ruta_imagen2 + "divertidas.png";
                        msg_sueno = getNombresSuenos[1];
                    } else if (cuenta_aleatorio >= 30 && cuenta_aleatorio < 50) {
                        img_sueno2 = ruta_imagen2 + "malas.png";
                        msg_sueno = getNombresSuenos[2];
                    } else if (cuenta_aleatorio >= 50 && cuenta_aleatorio < 70) {
                        img_sueno2 = ruta_imagen2 + "terrorificas.png";
                        msg_sueno = getNombresSuenos[3];
                    } else if (cuenta_aleatorio >= 70 && cuenta_aleatorio < 100) {
                        img_sueno2 = ruta_imagen2 + "trabajo.png";
                        msg_sueno = getNombresSuenos[4];
                    } else {
                        img_sueno2 = ruta_imagen2 + "empty.png";
                        msg_sueno = getNombresSuenos[5];
                    }

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        habboRp.showMessage(img_sueno2, msg_sueno);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    // Generar los "ZZZZzzzz"
                    int cantidad_mostrar_msg = 2;
                    int cuenta_aleatorio_msg = Emulator.getRandom().nextInt(cantidad_mostrar_msg);
                    if (timesumatoria != 0) {
                        String[] MSG1 = RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_dormir.text.talk")
                                .split(";");
                        if (cuenta_aleatorio_msg == 1) {
                            habbo.talk(MSG1[Emulator.getRandom().nextInt(MSG1.length)], RoomChatMessageBubbles.NORMAL);
                        }
                    }

                    this.startTime0 = timestamp0;
                    this.timesumatoria = timesumatoria - 10;
                }

                int timestamp = Emulator.getIntUnixTimestamp();
                if (timestamp - this.startTime >= sumatoria_show) {

                    this.resetTime();

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        habboRp.getVidaHambreDormir().dormir(100);
                        habboRp.getVidaHambreDormir().allowMove(true);
                        habboRp.setProgressPendiente(false);
                        habboRp.getVidaHambreDormir().setDormirActive(false);

                        // Ocultar carga proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();

                        // Mostrar mensaje exito al dormir
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/dormir/suenos/dormir.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("dormir.success"));

                        if (room != null) {
                            room.unIdle(habbo);
                        }

                        // TAREA ROLEPLAY (INSERTAR)
                        habboRp.getTareas().insertProgress("UseCamaRoom");

                        if (habboRp.getHabboClient() != null) {
                            habboRp.getHabboClient().send(new RemoveChatClient("RemoveChatClient", false));
                            habboRp.ocultMessage();
                        }

                        RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos()
                                .eventDormirCompleted(habbo);

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
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

            habboRp.setProgressPendiente(false);
            habboRp.getVidaHambreDormir().allowMove(true);

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