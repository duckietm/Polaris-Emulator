package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

/**
 * Legacy SnowWar prototype kept only for plugin binary compatibility
 * (PluginAbiCompatibilityTest). The live implementation lives in
 * com.eu.habbo.messages.outgoing.snowwar / habbohotel.games.snowwar.
 * @deprecated unwired prototype with placeholder data.
 */
@Deprecated
public class SnowWarsLoadingArenaComposer extends MessageComposer {
    private final int count;

    public SnowWarsLoadingArenaComposer(int count) {
        this.count = count;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3850);
        this.response.appendInt(this.count); //GameID?
        this.response.appendInt(0); //Count
        //this.response.appendInt(1); //ItemID to dispose?
        return this.response;
    }

    public int getCount() {
        return count;
    }
}
