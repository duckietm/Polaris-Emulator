package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collection;

public class RoomInviteErrorComposer extends MessageComposer {
    private final int errorCode;
    private final Collection<MessengerBuddy> buddies;

    public RoomInviteErrorComposer(int errorCode, Collection<MessengerBuddy> buddies) {
        this.errorCode = errorCode;
        this.buddies = buddies;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomInviteErrorComposer);
        this.response.appendInt(this.errorCode);
        this.response.appendInt(this.buddies.size());
        for (MessengerBuddy buddy : this.buddies) {
            this.response.appendInt(buddy.getId());
        }
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public Collection<MessengerBuddy> getBuddies() {
        return buddies;
    }
}
