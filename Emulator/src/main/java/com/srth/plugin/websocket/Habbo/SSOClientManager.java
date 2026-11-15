package com.srth.plugin.websocket.Habbo;

import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;

public class SSOClientManager {

public static THashMap<String,Channel> SSOclients = new THashMap<>(); 

//Get channel after user is logged in
    
    public static void addClient(String sso, Channel channel){
        SSOclients.put(sso,channel);
    }

}
