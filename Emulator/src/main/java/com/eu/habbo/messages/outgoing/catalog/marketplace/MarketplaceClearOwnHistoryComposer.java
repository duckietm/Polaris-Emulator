package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Answer to the AIR 13 "clear history" button
 * (`MarketPlaceLogic.onClearOwnHistoryResult`, event 175): a single flag. On success the
 * client drops the sold or expired rows of the tab it asked to clear.
 */
public class MarketplaceClearOwnHistoryComposer extends MessageComposer {
    private final boolean success;

    public MarketplaceClearOwnHistoryComposer(boolean success) {
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceClearOwnHistoryComposer);
        this.response.appendBoolean(this.success);
        return this.response;
    }

    public boolean isSuccess() {
        return this.success;
    }
}
