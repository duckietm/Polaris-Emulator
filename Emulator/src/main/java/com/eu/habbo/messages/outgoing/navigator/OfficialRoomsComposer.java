package com.eu.habbo.messages.outgoing.navigator;

import com.eu.habbo.habbohotel.navigation.NavigatorPublicCategory;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;
import java.util.List;

/**
 * AIR 13 {@code OfficialRooms} (official id 438, parser {@code class_2621}).
 *
 * <p>The wire is three blocks: the folder / room list ({@code class_3508} of
 * {@code class_3685} entries), an optional ad room guarded by an int flag, and
 * the promoted-rooms block ({@code class_2358} of {@code class_4042} folders).
 *
 * <p>An entry with {@code folderId == 0} is top level; a child carries the
 * {@code index} of its folder and {@code OfficialRoomListCtrl.getVisibleEntries}
 * only shows it while that folder is open. We build one folder per navigator
 * public category and one guest-room entry per room of that category, which is
 * the official-room data the emulator actually has.
 */
public class OfficialRoomsComposer extends MessageComposer {

    private static final int ENTRY_TYPE_GUEST_ROOM = 2;
    private static final int ENTRY_TYPE_FOLDER = 4;

    /** Code of the single promoted-rooms folder we serve. */
    public static final String PROMOTED_FOLDER_CODE = "promoted";

    private final Collection<NavigatorPublicCategory> categories;
    private final List<Room> promotedRooms;

    public OfficialRoomsComposer(Collection<NavigatorPublicCategory> categories, List<Room> promotedRooms) {
        this.categories = categories;
        this.promotedRooms = promotedRooms;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.OfficialRoomsComposer);

        int entryCount = 0;
        for (NavigatorPublicCategory category : this.categories) {
            if (category.rooms.isEmpty()) {
                continue;
            }
            entryCount += 1 + category.rooms.size();
        }

        this.response.appendInt(entryCount);

        int index = 1;
        for (NavigatorPublicCategory category : this.categories) {
            if (category.rooms.isEmpty()) {
                continue;
            }

            int folderIndex = index++;
            this.appendEntryHeader(folderIndex, category.name, "", false, "", "", 0, 0, ENTRY_TYPE_FOLDER);
            this.response.appendBoolean(true);

            for (Room room : category.rooms) {
                this.appendEntryHeader(
                        index++,
                        room.getName(),
                        room.getDescription(),
                        true,
                        "",
                        "",
                        folderIndex,
                        room.getUserCount(),
                        ENTRY_TYPE_GUEST_ROOM);
                room.serialize(this.response);
            }
        }

        // No ad room: the emulator has no official-view ad slot.
        this.response.appendInt(0);

        if (this.promotedRooms == null || this.promotedRooms.isEmpty()) {
            this.response.appendInt(0);
            return this.response;
        }

        this.response.appendInt(1);
        this.response.appendString(PROMOTED_FOLDER_CODE);
        this.response.appendString("");
        this.response.appendInt(this.promotedRooms.size());
        for (Room room : this.promotedRooms) {
            room.serialize(this.response);
        }

        return this.response;
    }

    private void appendEntryHeader(
            int index,
            String popupCaption,
            String popupDesc,
            boolean showDetails,
            String picText,
            String picRef,
            int folderId,
            int userCount,
            int type) {
        this.response.appendInt(index);
        this.response.appendString(popupCaption == null ? "" : popupCaption);
        this.response.appendString(popupDesc == null ? "" : popupDesc);
        this.response.appendInt(showDetails ? 1 : 0);
        this.response.appendString(picText == null ? "" : picText);
        this.response.appendString(picRef == null ? "" : picRef);
        this.response.appendInt(folderId);
        this.response.appendInt(userCount);
        this.response.appendInt(type);
    }

    public Collection<NavigatorPublicCategory> getCategories() {
        return this.categories;
    }

    public List<Room> getPromotedRooms() {
        return this.promotedRooms;
    }
}
