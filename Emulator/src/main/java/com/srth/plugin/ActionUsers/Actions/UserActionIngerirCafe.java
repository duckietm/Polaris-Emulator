package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.ActionUsers.comprobateTaskForAction;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

public class UserActionIngerirCafe {

    public UserActionIngerirCafe(Habbo habbo) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getParteTutorial() == 10) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/comida.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_comer.hambre.no.permited.tuto"));
            return;
        }

        if (habboRp.getVidaHambreDormir().getDormir() >= 100) {
            habboRp.setCuracionPendiente(false);
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/dormir.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("energia.full.cafe.action"));
            return;
        }

        if (habboRp.getProgressPendiente()) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "/RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif",
                    "global_alert.no.accion.progress.pendiente", habbo.getClient());
            return;
        }

        int amount = 50;

        // --- Textos aleatorios ---
        String[] inicioFrases = {
                "Toma su taza de café y lo remueve",
                "Sopla un poco su café mientras sale vapor",
                "Sujeta la taza con ambas manos disfrutando el aroma",
                "Prepara su café listo para darle un buen sorbo"
        };

        String[] finalFrases = {
                "Ingiere todo el café recuperando %cantidad% de energía.",
                "Termina su café de un trago, sintiendo un subidón de %cantidad% energía.",
                "Disfruta del último sorbo de café, recuperando %cantidad% de energía.",
                "Bebe el café caliente y siente cómo recupera %cantidad% de energía."
        };

        String fraseInicio = inicioFrases[Emulator.getRandom().nextInt(inicioFrases.length)];
        String fraseFinal = finalFrases[Emulator.getRandom().nextInt(finalFrases.length)].replace("%cantidad%",
                amount + "");

        habboRp.setProgressPendiente(true);
        habboRp.allowMove(false);

        Emulator.getThreading().run(() -> {
            habbo.talk(fraseInicio);
            room.giveHandItem(habbo, 1);
        }, 500);

        Emulator.getThreading().run(() -> {
            try {
                habboRp.getVidaHambreDormir().dormir(amount);

                habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/dormir/cafe.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("energia.succes_action").replace("%br%", "\n")
                                .replace("%cantidad%", amount + ""));

                InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();
                inventoryHabboNew.removeCantidadItem("cafe", 1);
                inventoryHabboNew.updateItemsUsed();
                inventoryHabboNew.showButtonsUsar(habbo);

                habbo.talk(fraseFinal);

                room.giveHandItem(habbo, 0);

                habboRp.setProgressPendiente(false);
                habboRp.allowMove(true);

                Emulator.getThreading().run(() -> {
                    HabboClient client = HabboClientManager.getHabboClient(habbo);
                    try {
                        client.send(new PlaySound("PlaySound", true, "comer.mp3", habbo));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }, 500);

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 4500);

        new comprobateTaskForAction(this.getClass().getSimpleName(), habbo);
    }
}