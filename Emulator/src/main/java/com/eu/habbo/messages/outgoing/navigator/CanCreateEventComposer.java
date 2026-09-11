package com.eu.habbo.messages.outgoing.navigator;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Whether this player can start a room event, and when they cannot, why. The reason is a number the
 * client turns into a sentence of its own, so the codes are the official ones and not ours to pick.
 */
public class CanCreateEventComposer extends MessageComposer {

    /** You have to be in a guest room. */
    public static final int ERROR_NOT_IN_GUEST_ROOM = 1;

    /** Only the owner of the room can start one. */
    public static final int ERROR_NOT_THE_OWNER = 2;

    /** The room has to be open. */
    public static final int ERROR_ROOM_CLOSED = 3;

    /** Events are switched off at the moment. */
    public static final int ERROR_DISABLED = 4;

    /** There is one running in this room already. */
    public static final int ERROR_ALREADY_HERE = 5;

    /** You are already running one somewhere else. */
    public static final int ERROR_ALREADY_ELSEWHERE = 6;

    private final boolean canCreate;
    private final int errorCode;

    /** Yes, go ahead. */
    public CanCreateEventComposer() {
        this(true, 0);
    }

    public CanCreateEventComposer(int errorCode) {
        this(false, errorCode);
    }

    public CanCreateEventComposer(boolean canCreate, int errorCode) {
        this.canCreate = canCreate;
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CanCreateEventComposer);
        this.response.appendBoolean(this.canCreate);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public boolean canCreate() {
        return canCreate;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
