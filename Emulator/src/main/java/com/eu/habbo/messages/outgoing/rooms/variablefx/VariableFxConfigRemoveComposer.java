package com.eu.habbo.messages.outgoing.rooms.variablefx;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class VariableFxConfigRemoveComposer extends MessageComposer {
    private final List<Integer> configIds;

    public VariableFxConfigRemoveComposer(List<Integer> configIds) {
        this.configIds = (configIds != null) ? configIds : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VariableFxConfigRemoveComposer);
        this.response.appendInt(this.configIds.size());

        for (int configId : this.configIds) {
            this.response.appendInt(configId);
        }

        return this.response;
    }
}
