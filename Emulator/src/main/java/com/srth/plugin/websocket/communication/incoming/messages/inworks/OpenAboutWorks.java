package com.srth.plugin.websocket.communication.incoming.messages.inworks;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

public class OpenAboutWorks extends IncomingMessage {
    public OpenAboutWorks(String header, THashMap<String, Object> data, Channel client) throws SQLException {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() throws SQLException {

        HabboClient client = HabboClientManager.getHabboClient(this.client);
        LevelPlayer player = WorkSystemLevelManager.getOnlinePlayers().get(client.getHabbo().getHabboInfo().getId());

        if (client != null) {

            Habbo habbo = Emulator.getGameEnvironment().getHabboManager()
                    .getHabbo(client.getHabbo().getHabboInfo().getId());

            RoleplayWebsocketsRoomsInfo.getJobsManager().showMenuJob(habbo);

        }

    }
}
