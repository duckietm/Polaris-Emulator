package com.srth.plugin.websocket.communication.incoming.messages.inworks;

import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutShowVentanaTaskActive;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

import java.sql.SQLException;

public class NewUserTaskActive extends IncomingMessage {
    public NewUserTaskActive(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient client = HabboClientManager.getHabboClient(this.client);
        LevelPlayer player = WorkSystemLevelManager.getOnlinePlayers().get(client.getHabbo().getHabboInfo().getId());
        
        if(client != null)
        client.send(new MessageManagerDouble("MessageManager", false, Outcoming.OpenMenuJobs, null, null));

            int task_id = (int)((double)data.get("task_id"));
            
            player.resetTasksActive();
            player.createTaskActive(task_id);
            player.updateTasksActive();

            client.send(new OutShowVentanaTaskActive("ShowVentanaTaskActive", true, 1, player));
    }
}
