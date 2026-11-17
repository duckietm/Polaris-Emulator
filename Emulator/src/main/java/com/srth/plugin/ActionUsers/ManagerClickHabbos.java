package com.srth.plugin.ActionUsers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.ActionUsers.Actions.UserActionArrestar;
import com.srth.plugin.ActionUsers.Actions.UserActionCurar;
import com.srth.plugin.ActionUsers.Actions.UserActionExitOtherPrision;
import com.srth.plugin.ActionUsers.Actions.UserActionRobar;
import com.srth.plugin.ActionUsers.Actions.UserActionViewOtherExpediente;
import com.srth.plugin.ActionUsers.Actions.UserActionVisitHomeRoom;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.HabboRpManager;
import com.srth.plugin.habbo.Shooter.ShooterHabbo;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowInfoOtherHabbo;
import gnu.trove.map.hash.THashMap;
import java.sql.SQLException;

public class ManagerClickHabbos {

    // Helper para evitar repetir la base de la URL
    private String getIconUrl(String path) {
        String base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String folder = RoleplayWebsocketsRoomsInfo.getConfig()
                .getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", base);
        return folder + "RoleplayWebsocketsRoomsInfo/" + path;
    }

    public void eventFurniClick(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboClient client = habboRp.getHabboClient();
            ocultOtherInfo(client);
            ocultButtonsUsarOther(client);
        } catch (Exception ignored) {
        }
    }

    public void HandlerOnClickUser(Habbo targetHabbo, HabboClient client) throws SQLException {
        if (targetHabbo == null || client == null) return;

        // Si es el mismo usuario, ocultamos paneles de "otros"
        if (targetHabbo.equals(client.getHabbo())) {
            ocultOtherInfo(client);
            ocultButtonsUsarOther(client);
        } else {

            showOtherInfo(targetHabbo, client);
            showButtonsUsarOther(targetHabbo, client);
        }
    }

    public JsonArray getArrayButtonsUsedForPlayer(Habbo targetHabbo, Habbo clientHabbo) throws SQLException {
        if (targetHabbo == null) return null;

        JsonArray buttons = new JsonArray();
        HabboRpManager habboRpManager = RoleplayWebsocketsRoomsInfo.getHabboRpManager();
        if (clientHabbo == null || habboRpManager == null) return buttons;

        HabboRp rpClient = habboRpManager.getHabboRp(clientHabbo);
        HabboRp rpTarget = habboRpManager.getHabboRp(targetHabbo);

        if (rpClient == null || rpTarget == null) return buttons;

        String job = (rpClient.getJobActive() != null)
                ? rpClient.getJobActive().getName().toLowerCase()
                : "unemployed";
        switch (job) {
            case "police":
                boolean hasCrimes = rpTarget.getDelitosHabbo().getDelitos() >= 1;

                buttons.add(createButton(
                        "job_polices/expediente.png",
                        "Revisar pertenencias",
                        "UserActionRevisarPertenencias",
                        true,
                        targetHabbo));

                buttons.add(createButton(
                        "job_polices/expediente.png",
                        hasCrimes ? "Ver su expediente" : "Sin expediente",
                        "UserActionViewOtherExpediente",
                        true,
                        targetHabbo));

                buttons.add(createButton(
                        "job_polices/arresto_true.png",
                        hasCrimes ? "Arrestar al usuario" : "Expediente limpio",
                        "UserActionArrestar",
                        hasCrimes,
                        targetHabbo));

                buttons.add(createButton(
                        "job_polices/arresto_true.png",
                        "Llevar usuario a la estación policial",
                        "LlevarUsuarioEstacionPolicial",
                        PoliceTransportManager.canOffer(clientHabbo, targetHabbo),
                        targetHabbo));

                if (rpTarget.getDelitosHabbo().getEncarceled() == true) {
                    buttons.add(createButton(
                            "job_polices/expediente.png",
                            "Sacar de prisión",
                            "UserActionExitOtherPrision",
                            hasCrimes,
                            targetHabbo));
                }
                break;

            case "medico":
                boolean needsHealing = rpTarget.getVidaHambreDormir().getVida() <= 50;
                buttons.add(createButton(
                        "actions/botiquin.png",
                        needsHealing ? "Curar al usuario" : "Vida completa",
                        "UserActionCurar",
                        true,
                        targetHabbo));
                break;

            case "aseo":
                boolean hasTrash = rpTarget.checkSettings("global_basura_room", 0) >= 1;
                buttons.add(createButton(
                        "job_aseo/rp_bote_basura_icon.png",
                        hasTrash ? "Recoger su basura" : "Sin basura en casa",
                        "UserActionVisiteHome",
                        hasTrash,
                        targetHabbo));
                break;

            default: // Civil / Criminal
                if ("noche"
                        .equalsIgnoreCase(
                                RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario())) {
                    buttons.add(createButton(
                            "job_polices/arma_active.png", "Robar al usuario", "UserActionRobar", true, targetHabbo));
                }

                buttons.add(createButton(
                        "actions/llave.png?v1", "Activar combate con llave", "UserActionGolpear", true, targetHabbo));
                break;
        }
        return buttons;
    }

    private JsonObject createButton(String img, String text, String event, boolean unlocked, Habbo target) {
        JsonObject obj = new JsonObject();
        obj.addProperty("item_img", getIconUrl(img));
        obj.addProperty("unlock", unlocked ? 1 : 0);
        obj.addProperty("use_btn_name", text);
        obj.addProperty("event", event);
        obj.addProperty("user_id", target.getHabboInfo().getId());
        return obj;
    }

    public void showButtonsUsarOther(Habbo target, HabboClient client) throws SQLException {
        client.send(new MessageManager(
                "MessageManager",
                true,
                Outcoming.InventoryButtonsOther,
                getArrayButtonsUsedForPlayer(target, client.getHabbo())));
    }

    public void ocultButtonsUsarOther(HabboClient client) throws SQLException {
        client.send(new MessageManager("MessageManager", false, Outcoming.InventoryButtonsOther, null));
    }

    public void showOtherInfo(Habbo target, HabboClient client) throws SQLException {

        HabboRp rp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(target);

        client.send(new ShowInfoOtherHabbo(
                "ShowInfoOtherHabbo", true, rp.getVidaHambreDormir().getArray()));
    }

    public void ocultOtherInfo(HabboClient client) throws SQLException {
        client.send(new ShowInfoOtherHabbo("ShowInfoOtherHabbo", false, null));
    }

    public void incomingActionsReceibedOther(THashMap<String, Object> data, HabboClient wsclient) throws SQLException {
        if (wsclient == null || wsclient.getHabbo() == null) return;

        String action = (String) data.get("action");
        int targetId =
                Integer.parseInt(data.get("date").toString()); // 'date' parece ser el ID del usuario según tu código
        Habbo targetHabbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(targetId);
        Room room = wsclient.getHabbo().getHabboInfo().getCurrentRoom();

        if (targetHabbo == null || room == null || targetHabbo.getHabboInfo().getCurrentRoom() != room) return;

        HabboRp rpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
        HabboRp rpClientTarget = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(targetHabbo);

        switch (action) {
            case "UserActionRobar":
                if (wsclient.getHabbo().getRoomUnit().getHandItem() != 2002) {
                    whisper(wsclient, "interactions_robar.pistola.disabled");
                    return;
                }
                new UserActionRobar(wsclient.getHabbo().getClient(), targetHabbo, 0);
                break;

            case "UserActionArrestar":
                new UserActionArrestar(wsclient.getHabbo().getClient(), targetHabbo, 0);
                break;

            case "LlevarUsuarioEstacionPolicial":
                PoliceTransportManager.start(wsclient.getHabbo(), targetHabbo);
                break;

            case "UserActionCurar":
                if (!isJob(rpClient, "medico")) {
                    whisper(wsclient, "cmd_curar.need.active");
                    return;
                }
                new UserActionCurar(wsclient.getHabbo().getClient(), targetHabbo);
                showOtherInfo(targetHabbo, wsclient);
                break;

            case "UserActionVisiteHome":
                if (!isJob(rpClient, "aseo")) {
                    whisper(wsclient, "action_aseo_job_no_active");
                    return;
                }
                new UserActionVisitHomeRoom(wsclient.getHabbo().getClient(), targetHabbo);
                showOtherInfo(targetHabbo, wsclient);
                break;

            case "UserActionGolpear":
                if (RoleplayWebsocketsRoomsInfo.getHorarioManager()
                        .getTimeHorario()
                        .equalsIgnoreCase("dia")) {
                    wsclient.getHabbo()
                            .whisper(
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_combat_daytime_disabled"),
                                    RoomChatMessageBubbles.NORMAL);
                    return;
                }

                ShooterHabbo shooterHabbo = rpClient.getShooterHabbo();
                ShooterHabbo shooterHabboOther = rpClientTarget.getShooterHabbo();

                // Comprobaciones
                if (rpClient.getHanditem() != 1035 && rpClient.getHanditem() < 2003 && rpClient.getHanditem() > 2006) {
                    // Mostrar mensaje
                    rpClient.showMessage(
                            "RoleplayWebsocketsRoomsInfo/hack_cajeros/llave.png?v2",
                            "Necesitas un objeto con el que puedas hacer daño para poder golpear al usuario");
                    return;
                }

                shooterHabbo.setPvpManoActive(1);
                shooterHabbo.setPvpActive(1);

                shooterHabboOther.setPvpManoActive(1);
                shooterHabboOther.setPvpActive(1);
                break;

            case "UserActionViewOtherExpediente":
                new UserActionViewOtherExpediente(wsclient.getHabbo().getClient(), targetHabbo);
                break;

            case "UserActionExitOtherPrision":
                new UserActionExitOtherPrision(wsclient.getHabbo().getClient(), targetHabbo);
                break;

            case "UserActionRevisarPertenencias":
                new UserActionRevisarPertenencias(wsclient.getHabbo().getClient(), targetHabbo);
                break;
        }
    }

    private boolean isJob(HabboRp rp, String jobName) {
        return rp.getJobActive() != null && rp.getJobActive().getName().equalsIgnoreCase(jobName);
    }

    private void whisper(HabboClient client, String textKey) {
        client.getHabbo()
                .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue(textKey), RoomChatMessageBubbles.NORMAL);
    }
}
