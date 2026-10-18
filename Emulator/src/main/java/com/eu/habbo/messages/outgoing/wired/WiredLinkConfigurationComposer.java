package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.link.WiredLinkService;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * WIRED Link configuration (AIR 3405): {@code int roomId, int furniId, String selectedNetworkId, bool
 * receiveLocal, int count, count x network}, a network being {@code String id, String name, int
 * connectionCount, bool linkedInCurrentRoom}.
 */
public class WiredLinkConfigurationComposer extends MessageComposer {
    private final WiredLinkService.Configuration configuration;

    public WiredLinkConfigurationComposer(WiredLinkService.Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkConfigurationComposer);
        this.response.appendInt(this.configuration.roomId());
        this.response.appendInt(this.configuration.furniId());
        this.response.appendString(this.configuration.selectedNetworkId());
        this.response.appendBoolean(this.configuration.receiveLocal());
        this.response.appendInt(this.configuration.networks().size());
        for (WiredLinkService.NetworkView network : this.configuration.networks()) {
            WiredLinkOperationResultComposer.appendNetwork(this.response, network);
        }
        return this.response;
    }
}
