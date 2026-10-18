package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * WIRED Link network invalidated (AIR 999): {@code int roomId, int furniId, String deletedNetworkId}. Tells
 * an open Link window that one of its networks was deleted elsewhere.
 */
public class WiredLinkNetworkInvalidatedComposer extends MessageComposer {
    private final int roomId;
    private final int furniId;
    private final String networkId;

    public WiredLinkNetworkInvalidatedComposer(int roomId, int furniId, String networkId) {
        this.roomId = roomId;
        this.furniId = furniId;
        this.networkId = networkId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkNetworkInvalidatedComposer);
        this.response.appendInt(this.roomId);
        this.response.appendInt(this.furniId);
        this.response.appendString(this.networkId);
        return this.response;
    }
}
