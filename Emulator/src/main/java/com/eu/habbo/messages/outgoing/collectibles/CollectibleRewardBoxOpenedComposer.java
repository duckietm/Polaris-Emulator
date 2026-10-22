package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 15 {@code class_3652} (3374), sent to the room: a reward box was opened ({@code state} 1; 0 is the
 * start) by {@code openerId}, with the product it gave.
 */
public class CollectibleRewardBoxOpenedComposer extends MessageComposer {
    public static final int STATE_START = 0;
    public static final int STATE_FINISH = 1;

    private final int state;
    private final int openerId;
    private final Product reward;

    public CollectibleRewardBoxOpenedComposer(int state, int openerId, Product reward) {
        this.state = state;
        this.openerId = openerId;
        this.reward = reward;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleRewardBoxOpenedComposer);
        this.response.appendShort(this.state);
        this.response.appendInt(this.openerId);
        CollectibleWire.appendProduct(this.response, this.reward);
        return this.response;
    }
}
