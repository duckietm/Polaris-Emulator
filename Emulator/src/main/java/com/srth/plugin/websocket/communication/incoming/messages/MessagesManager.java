package com.srth.plugin.websocket.communication.incoming.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.TutorialHabboRpManager;
import com.srth.plugin.Tutorial.partes_delitos.PartesDelitosTutorial;
import com.srth.plugin.Tutorial.partes_empleos.PartesEmpleosTutorial;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;
import com.srth.plugin.habbo.ButtonsExpandiblesRoom.ManagerButtonsExpandiblesRoom;
import com.srth.plugin.habbo.CraftItems.CraftManager;
import com.srth.plugin.habbo.Inventorys.CofreInventoryManager;
import com.srth.plugin.habbo.Inventorys.IncomingInventory;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public class MessagesManager extends IncomingMessage {
    public MessagesManager(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient wsclient = HabboClientManager.getHabboClient(this.client);
        if (wsclient == null) {
            return;
        }

        PartesEmpleosTutorial.incomingListParteEmpleos(data, wsclient);
        PartesDelitosTutorial.incomingListPartesDelitos(data, wsclient);

        new ManagerButtonsExpandiblesRoom().incoming(data, wsclient);
        RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().incoming(data, wsclient);

        new CraftManager().incomingList(data, wsclient);

        // Obtener los datos recibidos y comprobar si existen acá
        new IncomingInventory(data, wsclient);

        new ManagerClickHabbos().incomingActionsReceibedOther(data, wsclient);
        RoleplayWebsocketsRoomsInfo.getCofreInventoryManager().incomingActionsReceibedOther(data, wsclient);

    }
}
