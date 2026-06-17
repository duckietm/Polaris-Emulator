package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.navigation.*;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSearchResultsComposer;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class RequestNewNavigatorRoomsEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestNewNavigatorRoomsEvent.class);
    private static final int MAX_VIEW_LENGTH = 32;
    private static final int MAX_QUERY_LENGTH = 64;

    @Override
    public int getRatelimit() {
        return 200;
    }

    @Override
    public void handle() throws Exception {
        String view = this.packet.readString();
        String query = this.packet.readString();
        if (view.length() > MAX_VIEW_LENGTH) return;
        if (query.length() > MAX_QUERY_LENGTH) query = query.substring(0, MAX_QUERY_LENGTH);
        if (view.equals("query")) view = "hotel_view";
        if (view.equals("groups")) view = "hotel_view";

        NavigatorFilter filter = Emulator.getGameEnvironment().getNavigatorManager().filters.get(view);
        RoomCategory category = Emulator.getGameEnvironment().getRoomManager().getCategoryBySafeCaption(view);

        if (filter == null) {
            List<Room> rooms = Emulator.getGameEnvironment().getNavigatorManager().getRoomsForCategory(view, this.client.getHabbo());

            if (rooms != null) {
                List<SearchResultList> resultLists = new ArrayList<>();
                resultLists.add(new SearchResultList(0, view, query, SearchAction.NONE, this.client.getHabbo().getHabboStats().navigatorWindowSettings.getListModeForCategory(view, ListMode.LIST), this.client.getHabbo().getHabboStats().navigatorWindowSettings.getDisplayModeForCategory(view, DisplayMode.VISIBLE), rooms, true, true, DisplayOrder.ACTIVITY, -1));
                this.client.sendResponse(new NewNavigatorSearchResultsComposer(view, query, resultLists));
                return;
            }
        }

        String filterField = "anything";
        String part = query;
        NavigatorFilterField field = Emulator.getGameEnvironment().getNavigatorManager().filterSettings.get(filterField);
        if (filter != null) {
            if (query.contains(":")) {
                String[] parts = query.split(":", 2);

                if (parts.length > 1) {
                    filterField = parts[0];
                    part = parts[1];
                } else {
                    filterField = parts[0].replace(":", "");
                    if (!Emulator.getGameEnvironment().getNavigatorManager().filterSettings.containsKey(filterField)) {
                        filterField = "anything";
                    }
                    part = "";
                }
            }

            if (Emulator.getGameEnvironment().getNavigatorManager().filterSettings.get(filterField) != null) {
                field = Emulator.getGameEnvironment().getNavigatorManager().filterSettings.get(filterField);
            }
        }

        if (field == null || query.isEmpty()) {
            if (filter == null)
                return;

            List<SearchResultList> resultLists = filter.getResult(this.client.getHabbo());
            Collections.sort(resultLists);

            if (!query.isEmpty()) {
                resultLists = toQueryResults(resultLists);
            }

            this.client.sendResponse(new NewNavigatorSearchResultsComposer(view, query, resultLists));
            return;
        }

        if (filter == null) {
            filter = Emulator.getGameEnvironment().getNavigatorManager().filters.get("hotel_view");
        }

        if (category == null) {
            category = Emulator.getGameEnvironment().getRoomManager().getCategoryBySafeCaption("hotel_view");
        }

        if (filter == null)
            return;

        try {
            List<SearchResultList> resultLists2 = filter.getResult(this.client.getHabbo(), field, part, category != null ? category.getId() : -1);
            List<SearchResultList> resultLists = new ArrayList<>();
            for(SearchResultList searchResultList : resultLists2) {
                List<Room> rooms = new ArrayList<>();
                rooms.addAll(searchResultList.rooms);
                resultLists.add(new SearchResultList(searchResultList.order, searchResultList.code, searchResultList.query, searchResultList.action, searchResultList.mode, searchResultList.hidden, rooms, searchResultList.filter, searchResultList.showInvisible, searchResultList.displayOrder, searchResultList.categoryOrder));
            }
            if ("group".equals(filterField)) {
                final String needle = part.toLowerCase();
                for (SearchResultList list : resultLists) {
                    list.rooms.removeIf(room -> !room.belongsToGuild()
                            || (!needle.isEmpty() && !room.getGuildName().toLowerCase().contains(needle)));
                }
            }
            filter.filter(field.field, part, resultLists);
            resultLists = toQueryResults(resultLists);
            this.client.sendResponse(new NewNavigatorSearchResultsComposer(view, query, resultLists));
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    private ArrayList<SearchResultList> toQueryResults(List<SearchResultList> resultLists) {
        ArrayList<SearchResultList> nList = new ArrayList<>();
        THashMap<Integer, Room> searchRooms = new THashMap<>();

        for (SearchResultList li : resultLists) {
            for (Room room : li.rooms) {
                searchRooms.put(room.getId(), room);
            }
        }

        SearchResultList list = new SearchResultList(0, "query", "", SearchAction.NONE, ListMode.LIST, DisplayMode.VISIBLE, new ArrayList<Room>(searchRooms.values()), true, this.client.getHabbo().hasPermission(Permission.ACC_ENTERANYROOM) || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER), DisplayOrder.ACTIVITY, -1);
        nList.add(list);
        return nList;
    }

    @SuppressWarnings("unused")
    private void filter(List<SearchResultList> resultLists, NavigatorFilter filter, String part) {
        List<SearchResultList> toRemove = new ArrayList<>();
        Map<Integer, HashMap<Integer, Room>> filteredRooms = new HashMap<>();

        for (NavigatorFilterField field : Emulator.getGameEnvironment().getNavigatorManager().filterSettings.values()) {
            for (SearchResultList result : resultLists) {
                if (result.filter) {
                    List<Room> rooms = new ArrayList<>(result.rooms.subList(0, result.rooms.size()));
                    filter.filterRooms(field.field, part, rooms);

                    if (!filteredRooms.containsKey(result.order)) {
                        filteredRooms.put(result.order, new HashMap<>());
                    }

                    for (Room room : rooms) {
                        filteredRooms.get(result.order).put(room.getId(), room);
                    }
                }
            }
        }

        for (Map.Entry<Integer, HashMap<Integer, Room>> set : filteredRooms.entrySet()) {
            for (SearchResultList resultList : resultLists) {
                if (resultList.filter) {
                    resultList.rooms.clear();
                    resultList.rooms.addAll(set.getValue().values());

                    if (resultList.rooms.isEmpty()) {
                        toRemove.add(resultList);
                    }
                }
            }
        }

        resultLists.removeAll(toRemove);
    }
}
