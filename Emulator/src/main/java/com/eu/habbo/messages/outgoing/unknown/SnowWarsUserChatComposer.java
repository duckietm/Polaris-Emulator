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
public class SnowWarsUserChatComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(2049);
        this.response.appendInt(1); //UserID
        this.response.appendString("Message");
        return this.response;
    }
}
