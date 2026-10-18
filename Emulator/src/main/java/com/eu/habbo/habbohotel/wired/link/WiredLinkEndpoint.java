package com.eu.habbo.habbohotel.wired.link;

/**
 * The saved settings of one WIRED Link furni: the network it is on (0 none), the room it stood in when
 * they were saved, its owner, and whether it also receives what its own room sends.
 */
public record WiredLinkEndpoint(int itemId, int networkId, int roomId, int ownerId, boolean receiveLocal) {
    public boolean connected() {
        return this.networkId > 0;
    }
}
