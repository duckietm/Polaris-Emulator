package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;

import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.VidaHambreDormir.VidaHambreDormir;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowButtonsTutorial;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowInfoMyHabbo;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowUserActionsPrimary;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowVentUserVersus;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowUserActions;

public class onUserExitRoom implements EventListener {

        @EventHandler
        public static void eventOnUserExitRoom(UserExitRoomEvent event) throws SQLException {

                HabboClient client = HabboClientManager.getHabboClient(event.habbo);
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);

                if (client == null) {
                        return;
                }

                if (client != null) {

                        // Reset progresos pendientes
                        habboRp.setProgressPendiente(false);

                        // Reset progresos pendientes
                        habboRp.setProgressPendiente(false);

                        // Reset progresos pendientes
                        habboRp.setProgressPendiente(false);

                        // Reset shooter active
                        habboRp.update("global_shooter.pvp.active", "0");
                        // Reset dormir active
                        habboRp.update("global_dormir_active", "0");
                        habboRp.update("global_item_active_name", "");

                        // Ocultar interval
                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false, Outcoming.ExitRoom, null));

                        habboRp.getHabboTutorial().ocult(client.getHabbo());

                        // Ocult time process
                        habboRp.getVidaHambreDormir().showTimeProcess("", "none", 00, false);

                        VidaHambreDormir vidaHambreDormir = habboRp.getVidaHambreDormir();
                        vidaHambreDormir.ocultStats();

                        // Ocultar botones
                        new ManagerClickHabbos().ocultOtherInfo(client);

                        RoleplayWebsocketsRoomsInfo.getHorarioManager()
                                        .ocultDiaTardeNoche(client.getHabbo());

                        // Ocultar botones
                        habboRp.getHabboClient().send(new OutShowUserActions("ShowUserActions", false, null));
                        habboRp.getHabboClient().send(new ShowVentUserVersus("ShowVentUserVersus", false, null, null));

                        // Ocultar tiempo de espera usar items
                        habboRp.getHabboClient().send(
                                        new MessageManager("MessageManager", false, Outcoming.TimeEsperaUse, null));

                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false,
                                                        Outcoming.InventoryButtonsOther, null));

                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false, Outcoming.InventoryButtons,
                                                        null));

                        // Ocultar botones del tutorial
                        habboRp.getHabboClient().send(new ShowButtonsTutorial("ShowButtonsTutorial", false, 0));
                        // Ocultar barra ViewButtonsExpandibles
                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false,
                                                        Outcoming.ViewButtonsExpandibles, null));

                        // Ocultar botones de usar items
                        habboRp.getHabboClient()
                                        .send(new ShowUserActionsPrimary("ShowUserActionsPrimary", false, null));
                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false, Outcoming.ButtonsClickItem,
                                                        null));

                        // Ocultar ventanas del perfil del usuario
                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false,
                                                        Outcoming.ViewLevelHabboWindow, null));
                        habboRp.getHabboClient()
                                        .send(new MessageManager("MessageManager", false,
                                                        Outcoming.ViewLevelOtherHabboWindow, null));

                        // Ocultar flecha de frank y al mismo frank
                        habboRp.getHabboClient().send(
                                        new MessageManager("MessageManager", false, Outcoming.FlechaTutorial, null));
                }
        }

}
