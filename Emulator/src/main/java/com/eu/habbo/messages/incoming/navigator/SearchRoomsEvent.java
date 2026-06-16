package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.PrivateRoomsComposer;
import com.eu.habbo.plugin.events.navigator.NavigatorSearchResultEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SearchRoomsEvent extends MessageHandler {
    private static final int MAX_CACHE_SIZE = 200;
    public final static Map<Rank, Map<String, ServerMessage>> cachedResults = new ConcurrentHashMap<>(4);

    private static Map<String, ServerMessage> createLRUCache() {
        return Collections.synchronizedMap(new LinkedHashMap<String, ServerMessage>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ServerMessage> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });
    }

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String name = NavigatorInputGuard.normalizeSearch(this.packet.readString());

        String prefix = "";
        String query = name;
        ArrayList<Room> rooms;

        if (name.startsWith("owner:")) {
            query = NavigatorInputGuard.normalizeSearch(name.substring("owner:".length()));
            prefix = "owner:";
        } else if (name.startsWith("tag:")) {
            query = NavigatorInputGuard.normalizeSearch(name.substring("tag:".length()));
            prefix = "tag:";
        } else if (name.startsWith("group:")) {
            query = NavigatorInputGuard.normalizeSearch(name.substring("group:".length()));
            prefix = "group:";
        }

        String cacheKey = buildCacheKey(prefix, query);

        ServerMessage message = null;
        Map<String, ServerMessage> rankCache = cachedResults.get(this.client.getHabbo().getHabboInfo().getRank());
        if (rankCache != null) {
            message = rankCache.get(cacheKey);
        } else {
            rankCache = createLRUCache();
            cachedResults.put(this.client.getHabbo().getHabboInfo().getRank(), rankCache);
        }

        if (message == null) {
            if (prefix.equals("owner:")) {
                rooms = (ArrayList<Room>) Emulator.getGameEnvironment().getRoomManager().getRoomsForHabbo(query);
            } else if (prefix.equals("tag:")) {
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsWithTag(query);
            } else if (prefix.equals("group:")) {
                rooms = Emulator.getGameEnvironment().getRoomManager().getGroupRoomsWithName(query);
            } else {
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsWithName(query);
            }

            message = new PrivateRoomsComposer(rooms).compose();
            Map<String, ServerMessage> map = cachedResults.get(this.client.getHabbo().getHabboInfo().getRank());

            if (map == null) {
                map = createLRUCache();
            }

            map.put(cacheKey, message);
            cachedResults.put(this.client.getHabbo().getHabboInfo().getRank(), map);

            NavigatorSearchResultEvent event = new NavigatorSearchResultEvent(this.client.getHabbo(), prefix, query, rooms);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return;
            }
        }

        this.client.sendResponse(message);
    }

    private static String buildCacheKey(String prefix, String query) {
        return (prefix + "\t" + query).toLowerCase();
    }
}
