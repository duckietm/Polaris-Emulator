package com.srth.plugin.websocket.Habbo;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.HabboRpManager;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

public class SSOClientManager {

    public static THashMap<String, Channel> SSOclients = new THashMap<>();

    public static void addClient(String sso, Channel channel) {
        SSOclients.put(sso, channel);
    }

    public static HabboClient bindToExistingSession(String sso, Channel channel) {
        return bindToExistingSession(sso, channel, RoleplayWebsocketsRoomsInfo.getHabboRpManager());
    }

    static HabboClient bindToExistingSession(String sso, Channel channel, HabboRpManager habboRpManager) {
        if (sso == null || channel == null || !channel.isActive() || habboRpManager == null) {
            return null;
        }

        for (HabboRp habboRp : habboRpManager.getHabboRps().values()) {
            Habbo habbo = habboRp.getHabbo();
            if (habbo != null && sso.equals(habbo.getHabboInfo().getSso())) {
                new HabboClientManager().addClient(channel, habbo);
                return HabboClientManager.getHabboClient(habbo);
            }
        }

        return null;
    }
}
