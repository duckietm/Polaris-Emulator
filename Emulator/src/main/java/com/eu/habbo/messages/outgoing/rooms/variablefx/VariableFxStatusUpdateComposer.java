package com.eu.habbo.messages.outgoing.rooms.variablefx;

import com.eu.habbo.habbohotel.wired.variablefx.VariableFxStatus;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Map;

public class VariableFxStatusUpdateComposer extends MessageComposer {
    private final boolean initializeAll;
    private final List<VariableFxStatus> statuses;

    public VariableFxStatusUpdateComposer(boolean initializeAll, List<VariableFxStatus> statuses) {
        this.initializeAll = initializeAll;
        this.statuses = (statuses != null) ? statuses : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VariableFxStatusUpdateComposer);
        this.response.appendBoolean(this.initializeAll);
        this.response.appendInt(this.statuses.size());

        for (VariableFxStatus status : this.statuses) {
            this.response.appendString(status.statusKey());
            this.response.appendBoolean(status.isInitialize());
            this.response.appendBoolean(status.isUserEntity());
            this.response.appendInt(status.entityId());
            appendLong(status.value());
            this.response.appendBoolean(status.hasOverrides());

            if (status.hasOverrides()) {
                appendLong(status.overrideMinValue());
                appendLong(status.overrideMaxValue());
            }

            this.response.appendInt(status.extras().size());
            for (Map.Entry<String, String> extra : status.extras().entrySet()) {
                this.response.appendString(extra.getKey());
                this.response.appendString(extra.getValue());
            }
        }

        return this.response;
    }

    /** No appendLong on ServerMessage: a 64-bit value is two ints, high word first. */
    private void appendLong(long value) {
        this.response.appendInt((int) (value >> 32));
        this.response.appendInt((int) value);
    }
}
