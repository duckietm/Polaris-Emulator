package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 event 2580 (`SessionDataManager.onPurchasableChatStyleChanged`): one chat bubble
 * style was added to (or removed from) this account, so the selector updates without
 * asking for the whole list again.
 */
public class ChatStyleNotificationComposer extends MessageComposer {
    private final boolean added;
    private final int styleId;

    public ChatStyleNotificationComposer(boolean added, int styleId) {
        this.added = added;
        this.styleId = styleId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChatStyleNotificationComposer);
        this.response.appendBoolean(this.added);
        this.response.appendInt(this.styleId);
        return this.response;
    }

    public boolean isAdded() {
        return this.added;
    }

    public int getStyleId() {
        return this.styleId;
    }
}
