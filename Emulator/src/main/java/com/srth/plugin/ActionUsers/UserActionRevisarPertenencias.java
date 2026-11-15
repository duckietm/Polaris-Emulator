package com.srth.plugin.ActionUsers;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.ItemUsuario;
import com.srth.plugin.habbo.Jobs.Job;

public class UserActionRevisarPertenencias {

    public UserActionRevisarPertenencias(GameClient client, Habbo habbo) throws SQLException {

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id")) {
            client.getHabbo()
                    .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp officerRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());
        Job job_active = officerRp.getJobActive();

        if (job_active == null) {
            return;
        }

        // Validar si el oficial NO es policía
        if (!job_active.getName().equalsIgnoreCase("police")) {
            client.getHabbo().whisper(
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_polices.need.active"),
                    RoomChatMessageBubbles.NORMAL);

            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif", "global_work_active.delito.disabled",
                    client.getHabbo().getClient());
            return;
        }

        if (habbo == null || habbo == client.getHabbo()) {
            client.getHabbo().whisper("No puedes revisarte a ti mismo.", RoomChatMessageBubbles.RED);
            return;
        }

        // Validar que estén cerca (adyacentes o en la misma casilla de interacción)
        if (!RoomLayout.tilesAdjecent(client.getHabbo().getRoomUnit().getCurrentLocation(),
                habbo.getRoomUnit().getCurrentLocation())
                && client.getHabbo().getRoomUnit().getCurrentLocation()
                        .distance(habbo.getRoomUnit().getCurrentLocation()) > 1) {
            client.getHabbo().whisper("Estás muy lejos para revisar las pertenencias de este ciudadano.",
                    RoomChatMessageBubbles.RED);
            return;
        }

        HabboRp targetRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        if (targetRp == null) {
            return;
        }

        // Animación o rol de revisión ante la sala
        client.getHabbo().shout(
                "* Comienza a cachear y revisar las pertenencias de " + habbo.getHabboInfo().getUsername() + " *",
                RoomChatMessageBubbles.NORMAL);

        // Buscar el ítem cannabis en el inventario del usuario objetivo
        ItemUsuario cannabisItem = targetRp.getNewInventoryHabbo().getItem("cannabis");

        if (cannabisItem != null && cannabisItem.getCantidad() > 0) {
            int cantidadCannabis = cannabisItem.getCantidad();

            // Aumentar los delitos al usuario (puedes ajustar el tipo de delito según tu
            // manager, aquí sumamos 1 o los puntos equivalentes)
            int delitosActuales = targetRp.getDelitosHabbo().getDelitos();
            targetRp.getDelitosHabbo().incrementDelitos(delitosActuales + 1);

            // Notis de éxito para el oficial y advertencia para el sospechoso
            client.getHabbo().shout(
                    "¡Epa! Le encontraste " + cantidadCannabis + "x de cannabis entre sus pertenencias.",
                    RoomChatMessageBubbles.GREEN);

            habbo.whisper(
                    "El oficial " + client.getHabbo().getHabboInfo().getUsername()
                            + " te ha encontrado sustancias ilícitas (Cannabis) y se te ha sumado un cargo.",
                    RoomChatMessageBubbles.RED);

            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/arresto_true.png",
                    "¡Sustancias prohibidas encontradas!", client.getHabbo().getClient());

        } else {
            // No tiene nada ilegal
            client.getHabbo().whisper(
                    "Le has revisado los bolsillos a " + habbo.getHabboInfo().getUsername()
                            + " pero no le encontraste nada ilegal.",
                    RoomChatMessageBubbles.NORMAL);

            habbo.whisper(
                    "El oficial " + client.getHabbo().getHabboInfo().getUsername()
                            + " te ha revisado y tus bolsillos están limpios.",
                    RoomChatMessageBubbles.NORMAL);
        }
    }
}