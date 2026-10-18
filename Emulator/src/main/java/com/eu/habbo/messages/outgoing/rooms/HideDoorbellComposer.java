package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** FlatAccessible, the doorbell was answered (AIR {@code ?_-bt?/?_-L1W?}): the room id, then the user name. */
public class HideDoorbellComposer extends MessageComposer {
    private final int roomId;
    private final String username;

    /** Kept for plugins; sends room id 0. */
    public HideDoorbellComposer(String username) {
        this(0, username);
    }

    /** The doorbell of {@code habbo} at room {@code roomId} is gone (they left the queue or were let in). */
    public HideDoorbellComposer(int roomId, Habbo habbo) {
        this(roomId, habbo.getHabboInfo().getUsername());
    }

    public HideDoorbellComposer(int roomId, String username) {
        this.roomId = roomId;
        this.username = username;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HideDoorbellComposer);
        this.response.appendInt(this.roomId);
        this.response.appendString(this.username);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getUsername() {
        return username;
    }
}
