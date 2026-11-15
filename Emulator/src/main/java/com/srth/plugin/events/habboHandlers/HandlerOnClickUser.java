package com.srth.plugin.events.habboHandlers;

import java.sql.SQLException;
import java.util.Map;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.eu.habbo.messages.outgoing.users.ProfileFriendsComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowVentUserVersus;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

public class HandlerOnClickUser implements ICallable {

    @Override
    public void call(MessageHandler event) {

        // La copia se hace AQUÍ para esta ejecución específica.
        // Asegúrate de que el método `.clone()` exista en tu clase de paquete.
        ClientMessage packetCopy;
        int user_id = 0;
        try {
            packetCopy = event.packet.clone();
            // Usar la función de búsqueda que propuse:
            user_id = packetCopy.readInt();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        GameClient client = event.client;
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);
        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            try {
                HabboClient hclient = HabboClientManager.getHabboClient(client.getHabbo());
                new ManagerClickHabbos().HandlerOnClickUser(habbo, hclient);

                new ShooterManager().HandlerOnClickUser(habbo, hclient);

            } catch (SQLException e) {
                e.printStackTrace();
            }

            if (client.getHabbo().getRoomUnit().getEffectId() == 101) {
                if (habbo != client.getHabbo()) {

                    HabboClient client1 = HabboClientManager.getHabboClient(client.getHabbo());
                    HabboClient client2 = HabboClientManager.getHabboClient(habbo);

                    try {

                        HabboRp habboRp1 = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                .getHabboRp(client.getHabbo());
                        HabboRp habboRp2 = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        if (habboRp1.checkSettings("global_shooter.pvp.active", 0) == 0) {
                            return;
                        }

                        if (habboRp2.checkSettings("asesined", 0) == 1) {
                            return;
                        }

                        if (habboRp1.checkSettings("global.municion", 0) == 0) {

                            client1.send(new ShowVentUserVersus("ShowVentUserVersus", true, client.getHabbo(), habbo));
                            client2.send(new ShowVentUserVersus("ShowVentUserVersus", true, habbo, client.getHabbo()));

                            return;
                        }

                        if (habboRp2.checkSettings("global_pvp.mood_pasive.active", 0) == 1) {
                            return;
                        }

                        RoomTile tile = habbo.getHabboInfo().getCurrentRoom().getLayout().getTileInFront(
                                habbo.getRoomUnit().getCurrentLocation(),
                                habbo.getRoomUnit().getBodyRotation().getValue());
                        if (tile != null && tile.isWalkable()) {
                            if (tile != habbo.getHabboInfo().getCurrentRoom().getLayout().getDoorTile()) {
                                client.getHabbo().getRoomUnit().setGoalLocation(tile);
                            }
                        }

                        if (habbo.getRoomUnit().getCurrentLocation()
                                .distance(client.getHabbo().getRoomUnit().getCurrentLocation()) >= 6) {
                            return;
                        }

                        if (habboRp2.getVidaHambreDormir().getVida() <= 1) {
                            if (habboRp2.checkSettings("asesined", 0) == 0) {

                                // Updatear
                                habboRp2.update("asesined", "1");
                                habboRp2.update("asesined_by", client.getHabbo().getHabboInfo().getUsername());

                                client.getHabbo().shout("*Asesine a %username%*".replace("%username%",
                                        habbo.getHabboInfo().getUsername()));

                                // Sumar
                                int kills = habboRp1.checkSettings("global_shooter.pvp.kills", 0);
                                habboRp1.update("global_shooter.pvp.kills", kills + 1 + "");

                                habboRp2.getVidaHambreDormir().gotoHospital(
                                        "/RoleplayWebsocketsRoomsInfo/ws_hospital/vida_vacia.png",
                                        "shooter_alert.hospital.true.vida.empty");

                                client1.send(
                                        new ShowVentUserVersus("ShowVentUserVersus", true, client.getHabbo(), null));

                                event.isCancelled = true;
                                return;
                            }
                        }

                        int municion_actual = habboRp1.checkSettings("global.municion", 0);
                        int restar_municion = 1;
                        int cuenta = municion_actual - restar_municion;
                        if (cuenta >= 1) {
                            habboRp1.update("global.municion", cuenta + "");
                        } else {
                            habboRp1.update("global.municion", "0");
                        }

                        int cantidad_vida = 5;
                        int restar_vida_aleatorio = Emulator.getRandom().nextInt(cantidad_vida);

                        int user_escudo = habboRp2.checkSettings("global.escudo", 0);
                        if (user_escudo >= 1) {

                            int cuenta_escudo = user_escudo - restar_vida_aleatorio;

                            if (cuenta_escudo <= 0) {
                                cuenta_escudo = 0;
                            }

                            habboRp2.update("global.escudo", cuenta_escudo + "");
                        } else {
                            habboRp2.getVidaHambreDormir().restarVida(restar_vida_aleatorio);
                        }

                        client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(
                                new RoomUserActionComposer(client.getHabbo().getRoomUnit(), RoomUserAction.WAVE)
                                        .compose());

                        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager()
                                .getOnlineHabbos().entrySet()) {

                            Habbo habbomap = map.getValue();

                            if (habbomap != null && habbomap.getHabboInfo().getCurrentRoom() == room) {

                                HabboClient clientmap = HabboClientManager.getHabboClient(habbomap);

                                if (clientmap != null) {
                                    clientmap.send(new PlaySound("PlaySound", true, "disparo.mp3", habbo));
                                }

                            }
                        }

                        client1.send(new ShowVentUserVersus("ShowVentUserVersus", true, client.getHabbo(), habbo));
                        client2.send(new ShowVentUserVersus("ShowVentUserVersus", true, habbo, client.getHabbo()));

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                } else {

                    HabboClient clients = HabboClientManager.getHabboClient(client.getHabbo());
                    try {

                        HabboRp habboRp1 = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                .getHabboRp(client.getHabbo());

                        if (habboRp1.checkSettings("global_shooter.pvp.active", 0) == 0) {
                            return;
                        }

                        clients.send(new ShowVentUserVersus("ShowVentUserVersus", true, client.getHabbo(), null));

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                }
            }

        }

        if (habbo != null) {
            client.sendResponse(new ProfileFriendsComposer(habbo));
        } else {
            client.sendResponse(new ProfileFriendsComposer(Messenger.getFriends(user_id), user_id));
        }

    }
}