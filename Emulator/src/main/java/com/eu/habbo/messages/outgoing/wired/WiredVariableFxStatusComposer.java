package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxStatus;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The values a player should be drawing: one status per fx, variable and holder. */
public class WiredVariableFxStatusComposer extends MessageComposer {
    private final boolean initializeAll;
    private final List<WiredVariableFxStatus> statuses;

    public WiredVariableFxStatusComposer(boolean initializeAll, List<WiredVariableFxStatus> statuses) {
        this.initializeAll = initializeAll;
        this.statuses = (statuses != null) ? statuses : Collections.emptyList();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariableFxStatusComposer);
        this.response.appendBoolean(this.initializeAll);
        this.response.appendInt(this.statuses.size());

        for (WiredVariableFxStatus status : this.statuses) {
            this.response.appendString(status.key().configAndVariable());
            this.response.appendBoolean(status.initialize());
            this.response.appendBoolean(status.key().userEntity());
            this.response.appendInt(BuildersClubItemIds.toWire(
                    status.key().entityId(), !status.key().userEntity()));
            WiredVariableHoldersPageComposer.appendLong(this.response, status.value());
            // The client reads the two bounds as a pair or not at all.
            this.response.appendBoolean(status.hasOverrides());
            if (status.hasOverrides()) {
                WiredVariableHoldersPageComposer.appendLong(this.response, status.overrideMinValue());
                WiredVariableHoldersPageComposer.appendLong(this.response, status.overrideMaxValue());
            }
            this.response.appendInt(status.extra().size());
            for (Map.Entry<String, String> entry : status.extra().entrySet()) {
                this.response.appendString(entry.getKey());
                this.response.appendString(entry.getValue());
            }
        }

        return this.response;
    }
}
