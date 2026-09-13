package com.eu.habbo.messages.outgoing.rooms.variablefx;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class VariableFxStatusRemoveComposer extends MessageComposer {
    private final List<String> statusKeys;

    public VariableFxStatusRemoveComposer(List<String> statusKeys) {
        this.statusKeys = (statusKeys != null) ? statusKeys : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VariableFxStatusRemoveComposer);
        this.response.appendInt(this.statusKeys.size());

        for (String statusKey : this.statusKeys) {
            this.response.appendString(statusKey);
        }

        return this.response;
    }
}
