package polaris.messages.outgoing.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * IgnoreResult (AIR {@code ?_-A1M?/?_-M1q?}): the result, then the user's id. It only ever reports a change to the
 * receiver's own ignore list; AIR's {@code IgnoredUsersManager.onIgnoreResult} reads 1 as "added", 2 as "added, and
 * the oldest entry dropped because the list was full" and 3 as "removed". A room or hotel mute is not an ignore: the
 * muted user alone is told through {@link polaris.messages.outgoing.users.MutedWhisperComposer}
 * (RemainingMutePeriod), and nobody else in the room is sent anything.
 */
public class RoomUserIgnoredComposer extends MessageComposer {
    public static final int IGNORED = 1;

    /** Added to a full ignore list, which drops its oldest entry (AIR result 2). */
    public static final int IGNORED_LIST_FULL = 2;

    /**
     * @deprecated result 2 is AIR's {@link #IGNORED_LIST_FULL}: a client receiving it adds the user to its ignore list
     *     and drops the oldest entry. Tell a muted user with
     *     {@link polaris.messages.outgoing.users.MutedWhisperComposer} instead.
     */
    @Deprecated
    public static final int MUTED = IGNORED_LIST_FULL;

    public static final int UNIGNORED = 3;

    private final User user;
    private final int userId;
    private final int state;

    public RoomUserIgnoredComposer(User user, int state) {
        this.user = user;
        this.userId = user.getUserInfo().getId();
        this.state = state;
    }

    /** For a user who need not be online, as an ignore is stored by id. */
    public RoomUserIgnoredComposer(int userId, int state) {
        this.user = null;
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
    public User getUser() {
        return user;
    }

    public int getUserId() {
        return userId;
    }

    public int getState() {
        return state;
    }
}
