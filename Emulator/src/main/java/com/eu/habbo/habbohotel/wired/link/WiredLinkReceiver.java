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
}
