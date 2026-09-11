package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 13 {@code WiredClickUserResponse}: after the client reported the clicked avatar the
 * server answers with the same room index and whether the avatar menu may still open.
 */
public class WiredClickUserResponseComposer extends MessageComposer {
    private final int index;
    private final boolean openMenu;

    public WiredClickUserResponseComposer(int index, boolean openMenu) {
        this.index = index;
        this.openMenu = openMenu;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickUserResponseComposer);
        this.response.appendInt(this.index);
        this.response.appendBoolean(this.openMenu);
        return this.response;
    }
}
