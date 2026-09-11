package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;
import java.util.List;

/**
 * AIR 13 event 946 (`SessionDataManager.onPurchasableChatStyles`): every chat bubble
 * style this account owns. The chat-style selector offers these on top of the styles
 * `chat.styles` already grants by rank, club or ambassador flag.
 */
public class PurchasableChatStylesComposer extends MessageComposer {
    private final List<Integer> styleIds;

    public PurchasableChatStylesComposer(Collection<Integer> styleIds) {
        this.styleIds = styleIds == null ? List.of() : List.copyOf(styleIds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PurchasableChatStylesComposer);
        this.response.appendInt(this.styleIds.size());

        for (Integer styleId : this.styleIds) {
            this.response.appendInt(styleId);
        }

        return this.response;
    }

    public List<Integer> getStyleIds() {
        return this.styleIds;
    }
}
