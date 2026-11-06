package polaris.messages.incoming.navigator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomState;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.PrivateRoomsComposer;
import polaris.plugin.events.navigator.NavigatorSearchResultEvent;

public class SearchRoomsEvent extends MessageHandler {
    private static final int MAX_CACHE_SIZE = 200;
    public static final Map<Rank, Map<String, ServerMessage>> cachedResults = new ConcurrentHashMap<>(4);

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

        // As in the new navigator: an invisible room is listed only to who may enter or own any room.
        boolean showInvisible = this.client.getUser().hasPermission(Permission.ACC_ENTERANYROOM)
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER);
        String cacheKey = buildCacheKey(prefix, query) + (showInvisible ? "\tall" : "");

        ServerMessage message = null;
        Map<String, ServerMessage> rankCache =
                cachedResults.get(this.client.getUser().getUserInfo().getRank());
        if (rankCache != null) {
            message = rankCache.get(cacheKey);
        } else {
            rankCache = createLRUCache();
            cachedResults.put(this.client.getUser().getUserInfo().getRank(), rankCache);
        }

        if (message == null) {
            if (prefix.equals("owner:")) {
                rooms = (ArrayList<Room>)
                        Emulator.getGameEnvironment().getRoomManager().getRoomsForUser(query);
            } else if (prefix.equals("tag:")) {
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsWithTag(query);
            } else if (prefix.equals("group:")) {
                rooms = Emulator.getGameEnvironment().getRoomManager().getGroupRoomsWithName(query);
            } else {
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsWithName(query);
            }

            if (!showInvisible) rooms = visibleRooms(rooms);

            message = new PrivateRoomsComposer(rooms).compose();
            Map<String, ServerMessage> map =
                    cachedResults.get(this.client.getUser().getUserInfo().getRank());

            if (map == null) {
                map = createLRUCache();
            }

            map.put(cacheKey, message);
            cachedResults.put(this.client.getUser().getUserInfo().getRank(), map);

            NavigatorSearchResultEvent event =
                    new NavigatorSearchResultEvent(this.client.getUser(), prefix, query, rooms);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) {
                return;
            }
        }

        this.client.sendResponse(message);
    }

    static ArrayList<Room> visibleRooms(List<Room> rooms) {
        ArrayList<Room> visible = new ArrayList<>();
        for (Room room : rooms) {
            if (room.getState() != RoomState.INVISIBLE) visible.add(room);
        }
        return visible;
    }

    private static String buildCacheKey(String prefix, String query) {
        return (prefix + "\t" + query).toLowerCase();
    }
}
