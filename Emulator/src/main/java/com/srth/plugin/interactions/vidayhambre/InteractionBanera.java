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
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.RemoveChatClient;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionBanera extends InteractionEffectTile implements ICycleable {

    private int default_sumatoria = RoleplayWebsocketsRoomsInfo.getConfig()
            .getInt("rp.rooms.banera_time_espera.interval");
    // private int default_sumatoria = 99999;
    private int timesumatoria = default_sumatoria;
    private int sumatoria_show = default_sumatoria + 10;
    private int startTime = 0;
    private int startTime0 = 0;
    private int roomUnitId = -1;

    public InteractionBanera(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionBanera(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/banos/icon_banera.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("tutorial.banera.warning"));
                        return;
                    }

                    if (habboRp.getVidaHambreDormir().getHigiene() >= 100) {
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/banos/icon_banera.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("banera.not.full"));
                        return;
                    }

                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/banos/banera.png?v2",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.banera.process"), 8,
                            true);

                    habboRp.setProgressPendiente(true);

                    this.roomUnitId = habbo.getRoomUnit().getId();
                    this.startTime = Emulator.getIntUnixTimestamp();
                    this.startTime0 = Emulator.getIntUnixTimestamp();

                    // Establecer estado a furni
                    this.setExtradata("2");
                    this.needsUpdate(true);
                    this.run();
                    room.updateItemState(this);
                    room.updateItem(this);
                    Emulator.getThreading().run(this);

                    habboRp.getVidaHambreDormir().setDuchaActive(true);

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

                    String[] getNombresSuenos = RoleplayWebsocketsRoomsInfo.getTexts()
                            .getValue("action_banera.nombres.suenos").split(";");
                    int cuenta_aleatorio_msg = Emulator.getRandom().nextInt(getNombresSuenos.length);
                    String msg_sueno = getNombresSuenos[cuenta_aleatorio_msg];

                    try {

                        // Establecer estado a furni
                        this.setExtradata("3");
                        this.needsUpdate(true);
                        this.run();
                        room.updateItemState(this);
                        room.updateItem(this);
                        Emulator.getThreading().run(this);

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        habboRp.getVidaHambreDormir().showTimeProcess(
                                "RoleplayWebsocketsRoomsInfo/banos/icon_banera.png?v2",
                                msg_sueno, 7,
                                true);

                        habboRp.getVidaHambreDormir().setDuchaActive(true);

                        String[] getMessagesProcess = RoleplayWebsocketsRoomsInfo.getTexts()
                                .getValue("action_banera.progress.msg").split(";");
                        int cuenta_aleatorio_msg_progress = Emulator.getRandom().nextInt(getMessagesProcess.length);
                        String msgSuccessAleatorios = getMessagesProcess[cuenta_aleatorio_msg_progress];
                        // habbo.shout(msgSuccessAleatorios, RoomChatMessageBubbles.NORMAL);

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    this.startTime0 = timestamp0;
                    this.timesumatoria = timesumatoria - 10;
                }

                int timestamp = Emulator.getIntUnixTimestamp();
                if (timestamp - this.startTime >= sumatoria_show) {

                    this.resetTime();

                    try {

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        habboRp.getVidaHambreDormir().allowMove(true);

                        habboRp.setProgressPendiente(false);

                        // Ocultar carga proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();

                        // Mostrar mensaje exito
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/banos/banera.png?v2",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.banera.success"));

                        habboRp.getVidaHambreDormir().sumarHigiene(100);

                        habboRp.checkSettings("ignore_higiene", 33);
                        habboRp.updateData("ignore_higiene", 33 + "");

                        if (room != null) {
                            room.unIdle(habbo);
                        }

                        // Establecer estado a furni
                        this.setExtradata("0");
                        this.needsUpdate(true);
                        this.run();
                        room.updateItemState(this);
                        room.updateItem(this);
                        Emulator.getThreading().run(this);

                        String[] getMessagesSuccess = RoleplayWebsocketsRoomsInfo.getTexts()
                                .getValue("action_banera.success.msg").split(";");
                        int cuenta_aleatorio_msg = Emulator.getRandom().nextInt(getMessagesSuccess.length);
                        String msgSuccessAleatorios = getMessagesSuccess[cuenta_aleatorio_msg];
                        // habbo.shout(msgSuccessAleatorios, RoomChatMessageBubbles.NORMAL);

                        habboRp.getVidaHambreDormir().setDuchaActive(false);

                        // Efecto visual (Efecto 0 - Limpio)
                        room.giveEffect(habbo.getRoomUnit(), 0, -1);
                        habboRp.setEnable(0);

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

            habboRp.getVidaHambreDormir().setDuchaActive(false);

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