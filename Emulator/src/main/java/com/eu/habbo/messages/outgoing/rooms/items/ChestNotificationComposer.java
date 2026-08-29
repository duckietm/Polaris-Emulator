package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Something happened to a chest its owner asked to hear about:
 * {@code int chestId, int reason, string chestName, string actorName, int amount}.
 *
 * <p>The window's notification switches were saved, sent and drawn from the start, and nothing on the
 * server ever read them — a whole panel of promises that did nothing. This is the message that makes
 * them mean something.
 *
 * <p>The chest's name travels with it because the notification is read somewhere else: the owner may
 * be in another room entirely, where "your chest" needs to say which one.
 */
public class ChestNotificationComposer extends MessageComposer {
    /** The chest just filled up. */
    public static final int REASON_FULL = 0;

    /** Somebody else put something in. */
    public static final int REASON_DONATION = 1;

    /** Somebody took something out. */
    public static final int REASON_WITHDRAW = 2;

    /** The last of it just left. */
    public static final int REASON_EMPTY = 3;

    /** A wired effect moved something. */
    public static final int REASON_WIRED = 4;

    private final int chestId;
    private final int reason;
    private final String chestName;
    private final String actorName;
    private final int amount;

    public ChestNotificationComposer(int chestId, int reason, String chestName, String actorName, int amount) {
        this.chestId = chestId;
        this.reason = reason;
        this.chestName = chestName;
        this.actorName = actorName;
        this.amount = amount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestNotificationComposer);
        this.response.appendInt(this.chestId);
        this.response.appendInt(this.reason);
        this.response.appendString(this.chestName == null ? "" : this.chestName);
        this.response.appendString(this.actorName == null ? "" : this.actorName);
        this.response.appendInt(this.amount);
        return this.response;
    }
}
