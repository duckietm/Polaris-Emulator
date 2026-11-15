package com.srth.plugin.websocket.communication.incoming.messages;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.Actions.UserActionArrestar;
import com.srth.plugin.ActionUsers.Actions.UserActionComer;
import com.srth.plugin.ActionUsers.Actions.UserActionCurar;
import com.srth.plugin.ActionUsers.Actions.UserActionEmptyOtherExpediente;
import com.srth.plugin.ActionUsers.Actions.UserActionEmpujarOther;
import com.srth.plugin.ActionUsers.Actions.UserActionExitOtherPrision;
import com.srth.plugin.ActionUsers.Actions.UserActionRobar;
import com.srth.plugin.ActionUsers.Actions.UserActionViewMyExpediente;
import com.srth.plugin.ActionUsers.Actions.UserActionViewOtherExpediente;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class RequestUserActionsEvent extends IncomingMessage {

    public RequestUserActionsEvent(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient wsclient = HabboClientManager.getHabboClient(this.client);
        if (wsclient == null) {
            return;
        }

        if (((String) data.get("name")).equalsIgnoreCase("UserActionComer")) {
            int value = (int) ((double) data.get("action"));
            new UserActionComer(wsclient.getHabbo(), value);
        }

        else if (((String) data.get("name")).equalsIgnoreCase("UserActionArrestar")) {

            int value = (int) ((double) data.get("action"));
            int user_id = (int) ((double) data.get("user"));
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);

            new UserActionArrestar(wsclient.getHabbo().getClient(), habbo, value);
        }

        else if (((String) data.get("name")).equalsIgnoreCase("LlevarUsuarioEstacionPolicial")) {
            int user_id = (int) ((double) data.get("user"));
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);

            PoliceTransportManager.start(wsclient.getHabbo(), habbo);
        }

        else if (((String) data.get("name")).equalsIgnoreCase("UserActionCurar")) {

            int user_id = (int) ((double) data.get("user"));
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);

            new UserActionCurar(wsclient.getHabbo().getClient(), habbo);
        }

        else if (((String) data.get("name")).equalsIgnoreCase("UserActionViewMyExpediente")) {
            int value = (int) ((double) data.get("action"));
            new UserActionViewMyExpediente(wsclient.getHabbo(), value);
        }

        else if (((String) data.get("name")).equalsIgnoreCase("UserActionEmptyExpediente")) {

            int value = (int) ((double) data.get("action"));
            int user_id = (int) ((double) data.get("user"));
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);

            new UserActionEmptyOtherExpediente(wsclient.getHabbo().getClient(), habbo, value);

        }

        else if (((String) data.get("name")).equalsIgnoreCase("UserActionEmpujarOther")) {

            int value = (int) ((double) data.get("action"));
            int user_id = (int) ((double) data.get("user"));
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(user_id);

            new UserActionEmpujarOther(wsclient.getHabbo().getClient(), habbo, value);

        }
    }
}
