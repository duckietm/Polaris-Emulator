package com.srth.plugin.websocket.communication.outgoing.messages.outworks;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

public class OutCancelTaskActive{

    public OutCancelTaskActive(Habbo habbo) throws  SQLException {
        
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        LevelPlayer player = WorkSystemLevelManager.getOnlinePlayers().get(client.getHabbo().getHabboInfo().getId());
        
        if(client != null && player != null)

            client.send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", false, 0, player));

            if(player != null){
                player.resetTasksActive();
                player.updateTasksActive();
            }

            client.send(new MessageManagerDouble("MessageManager", false, Outcoming.OpenMenuJobs, null, null));

    }

}
