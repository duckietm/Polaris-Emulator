package com.srth.plugin.habbo.Frank.Frank.ActionEvents;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.VidaHambreDormir.VidaHambreDormir;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowButtonsTutorial;

public class CongelarUser {

    public void congelar(Habbo habbo) throws SQLException {

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        int tutorial_process = habboRp.checkSettings("tutorial_process", 1);
        Room room = habbo.getHabboInfo().getCurrentRoom();
        VidaHambreDormir vidaHambreDormir = habboRp.getVidaHambreDormir();

        if (tutorial_process < 18) {

            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.tutorial.necesited"));

            Emulator.getThreading().run(() -> {
                habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.tutorial.necesited_2"));
            }, 1000);

            // Generate efecto sim
            room.giveEffect(habbo, Integer.valueOf(13), -1);

            Emulator.getThreading().run(() -> {
                // Congelar el keko
                room.giveEffect(habbo, Integer.valueOf(44), -1);
                vidaHambreDormir.allowMove(false);

                // Llamada a acción
                Emulator.getThreading().run(() -> {
                    RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                            "/RoleplayWebsocketsRoomsInfo/tutorial/casa.png", "action.tutorial.necesited",
                            habbo.getClient());
                }, 2500);
            }, 2000);

            // Mostrar botones para el tutorial
            Emulator.getThreading().run(() -> {
                try {
                    habboRp.getHabboClient()
                            .send(new ShowButtonsTutorial("ShowButtonsTutorial", true, tutorial_process));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }, 5000);

        }
    }
}
