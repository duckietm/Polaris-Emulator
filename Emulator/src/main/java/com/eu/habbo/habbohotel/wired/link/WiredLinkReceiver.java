package com.eu.habbo.habbohotel.wired.link;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

/** A trigger box that fires when one of its picked WIRED Links receives something. */
public interface WiredLinkReceiver {
    /** What the trigger listens for. */
    WiredLinkPayload.Kind linkKind();

    /** Whether the trigger picked the Link. */
    boolean listensTo(HabboItem link);

    /** The event this trigger's stack runs with for the payload that reached the Link. */
    WiredEvent receiveEvent(Room room, HabboItem link, WiredLinkPayload payload, int hops);

    /**
     * As {@link #receiveEvent(Room, HabboItem, WiredLinkPayload, int)}, knowing the room the payload was sent from
     * (AIR {@code @event.link.source_room_id}). Receivers that do not read it answer the four-argument event.
     */
    default WiredEvent receiveEvent(Room room, HabboItem link, WiredLinkPayload payload, int hops, int sourceRoomId) {
        return this.receiveEvent(room, link, payload, hops);
    }
}
