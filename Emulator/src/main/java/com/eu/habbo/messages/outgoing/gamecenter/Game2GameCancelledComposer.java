package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR Game2GameCancelledMessageEvent (3493): the lobby was dropped before the
 * match started; the official handler calls gameCancelled(false).
 */
public class Game2GameCancelledComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.Game2GameCancelledComposer);
        return this.response;
    }
}
