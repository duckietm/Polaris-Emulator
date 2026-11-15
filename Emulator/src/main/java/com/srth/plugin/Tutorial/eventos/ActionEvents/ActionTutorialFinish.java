package com.srth.plugin.Tutorial.eventos.ActionEvents;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Frank.Frank.Flechas.FlechasIndicadoras;
import com.srth.plugin.habbo.Jobs.ManagerButtonsRight;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;

import gnu.trove.map.hash.THashMap;

public class ActionTutorialFinish {
    private Habbo habbo;

    public ActionTutorialFinish(Habbo habbo) throws SQLException {
        this.habbo = habbo;
        HabboClient client = HabboClientManager.getHabboClient(habbo);

        if (this.habbo != null) {
            if (client != null) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                String[] getDataTexts = RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_tutorial_finish_msg_1")
                        .replace(";", ":").split(":");

                Emulator.getThreading().run(() -> {
                    // Mostrar alerta
                    THashMap<String, String> keys3 = new THashMap<>();
                    keys3.put("display", "BUBBLE");
                    keys3.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/"
                            + getDataTexts[3].replace(" ", ""));
                    keys3.put("message", getDataTexts[1]);
                    habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/" + getDataTexts[3].replace(" ", ""),
                            getDataTexts[1]);

                    this.habbo
                            .talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("tutorial_exito_talk_habbo_cocinar"));

                }, 3000);

                Emulator.getThreading().run(() -> {
                    this.habbo.talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("tutorial_exito_talk_habbo_mapa"));
                }, 10000);

                Emulator.getThreading().run(() -> {
                    try {
                        new FlechasIndicadoras().showFlecha(habbo, 5);
                        new ManagerButtonsRight().ViewButtons(this.habbo);

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }, 12000);

            }
        }
    }
}
