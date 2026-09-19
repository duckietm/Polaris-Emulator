package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

// The header alias is deprecated in Outgoing; the composer stays for plugin compatibility.
@SuppressWarnings("deprecation")
public class UnknownAvatarEditorComposer extends MessageComposer {
    private final int type;

    public UnknownAvatarEditorComposer(int type) {
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownAvatarEditorComposer);
        this.response.appendInt(this.type);
        return this.response;
    }

    public int getType() {
        return type;
    }
}
