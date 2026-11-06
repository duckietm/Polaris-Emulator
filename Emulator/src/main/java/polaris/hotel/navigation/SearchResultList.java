package polaris.hotel.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomState;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;

public class SearchResultList implements ISerialize, Comparable<SearchResultList> {
    public final int order;
    public final String code;
    public final String query;
    public final SearchAction action;
    public final ListMode mode;
    public final DisplayMode hidden;
    public final List<Room> rooms;
    public final boolean filter;
    public final boolean showInvisible;
    public final DisplayOrder displayOrder;
    public final int categoryOrder;

    public SearchResultList(
            int order,
            String code,
            String query,
            SearchAction action,
            ListMode mode,
            DisplayMode hidden,
            List<Room> rooms,
            boolean filter,
            boolean showInvisible,
            DisplayOrder displayOrder,
            int categoryOrder) {
        this.order = order;
        this.code = code;
        this.query = query;
        this.action = action;
        this.mode = mode;
        this.rooms = rooms;
        this.hidden = hidden;
        this.filter = filter;
        this.showInvisible = showInvisible;
        this.displayOrder = displayOrder;
        this.categoryOrder = categoryOrder;
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString(this.code); // Search Code
        message.appendString(this.query); // Text
        message.appendInt(this.action.type); // Action Allowed (0 (Nothing), 1 (More Results), 2 (Go Back))
        message.appendBoolean(this.hidden.equals(DisplayMode.COLLAPSED)); // Closed
        message.appendInt(this.mode.type); // Display Mode (0 (List), 1 (Thumbnails), 2 (Thumbnail no choice))

        synchronized (this.rooms) {
            if (!this.showInvisible) {
                List<Room> toRemove = new ArrayList<>();
                for (Room room : this.rooms) {
                    if (room.getState() == RoomState.INVISIBLE) {
                        toRemove.add(room);
                    }
                }

                this.rooms.removeAll(toRemove);
            }

            message.appendInt(this.rooms.size());

            Collections.sort(this.rooms);
            for (Room room : this.rooms) {
                room.serialize(message);
            }
        }
    }

    @Override
    public int compareTo(SearchResultList o) {
        if (this.displayOrder == DisplayOrder.ACTIVITY) {
            if (this.code.equalsIgnoreCase("popular")) {
                return -1;
            }

            return this.rooms.size() - o.rooms.size();
        }
        return this.categoryOrder - o.categoryOrder;
    }
}
