package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** IgnoreResult (AIR {@code ?_-A1M?/?_-M1q?}): the result, then the user's id. */
public class RoomUserIgnoredComposer extends MessageComposer {
    public static final int IGNORED = 1;
    public static final int MUTED = 2;
    public static final int UNIGNORED = 3;

    private final Habbo habbo;
    private final int userId;
    private final int state;

    public RoomUserIgnoredComposer(Habbo habbo, int state) {
        this.habbo = habbo;
        this.userId = habbo.getHabboInfo().getId();
        this.state = state;
    }

    /** For a user who need not be online, as an ignore is stored by id. */
    public RoomUserIgnoredComposer(int userId, int state) {
        this.habbo = null;
        this.userId = userId;
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserIgnoredComposer);
        this.response.appendInt(this.state);
        this.response.appendInt(this.userId);
        return this.response;
    }

    /** The user, when the composer was made for an online one; null otherwise. */
    public Habbo getHabbo() {
        return habbo;
    }

    public int getUserId() {
        return userId;
    }

    public int getState() {
        return state;
    }
}
