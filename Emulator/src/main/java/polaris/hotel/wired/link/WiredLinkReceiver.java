package polaris.hotel.wired.link;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredEvent;

/** A trigger box that fires when one of its picked WIRED Links receives something. */
public interface WiredLinkReceiver {
    /** What the trigger listens for. */
    WiredLinkPayload.Kind linkKind();

    /** Whether the trigger picked the Link. */
    boolean listensTo(UserItem link);

    /** The event this trigger's stack runs with for the payload that reached the Link. */
    WiredEvent receiveEvent(Room room, UserItem link, WiredLinkPayload payload, int hops);

    /**
     * As {@link #receiveEvent(Room, UserItem, WiredLinkPayload, int)}, knowing the room the payload was sent from
     * (AIR {@code @event.link.source_room_id}). Receivers that do not read it answer the four-argument event.
     */
    default WiredEvent receiveEvent(Room room, UserItem link, WiredLinkPayload payload, int hops, int sourceRoomId) {
        return this.receiveEvent(room, link, payload, hops);
    }
}
