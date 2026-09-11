package com.eu.habbo.messages.outgoing.gamecenter;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR Game2GameNotFoundMessageEvent (444): the requested game does not exist. */
public class Game2GameNotFoundComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.Game2GameNotFoundComposer);
        return this.response;
    }
}
