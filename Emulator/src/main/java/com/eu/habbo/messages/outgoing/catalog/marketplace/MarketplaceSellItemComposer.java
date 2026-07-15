package com.eu.habbo.messages.outgoing.catalog.marketplace;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class MarketplaceSellItemComposer extends MessageComposer {
    public static final int NOT_ALLOWED = 2;
    public static final int NO_TRADE_PASS = 3;
    public static final int NO_ADS_LEFT = 4;

    private final int errorCode;
    private final int tokenCount;

    public MarketplaceSellItemComposer(int errorCode, int tokenCount) {
        this.errorCode = errorCode;
        this.tokenCount = tokenCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceSellItemComposer);
        this.response.appendInt(this.errorCode);
        this.response.appendInt(this.tokenCount);
        return this.response;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getTokenCount() {
        return tokenCount;
    }
}
