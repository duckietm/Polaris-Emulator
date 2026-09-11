package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collections;
import java.util.List;

/**
 * Official AIR 13 {@code AllVariablesDiff}: the variables the client has to drop and the ones it has
 * to add or replace, so its wired variable cache matches {@code allVariablesHash} again.
 */
public class WiredAllVariablesDiffComposer extends MessageComposer {
    private final int allVariablesHash;
    private final boolean lastChunk;
    private final List<String> removedVariables;
    private final List<RoomWiredVariableCatalog.Variable> addedOrUpdated;

    public WiredAllVariablesDiffComposer(
            int allVariablesHash,
            boolean lastChunk,
            List<String> removedVariables,
            List<RoomWiredVariableCatalog.Variable> addedOrUpdated) {
        this.allVariablesHash = allVariablesHash;
        this.lastChunk = lastChunk;
        this.removedVariables = (removedVariables != null) ? removedVariables : Collections.emptyList();
        this.addedOrUpdated = (addedOrUpdated != null) ? addedOrUpdated : Collections.emptyList();
    }

    /** Writes one {@code WiredVariable} block exactly as the official client reads it. */
    static void appendVariable(ServerMessage response, RoomWiredVariableCatalog.Variable variable) {
        response.appendString(variable.getVariableId());
        response.appendInt(variable.getVariableType());
        response.appendString(variable.getVariableName());
        response.appendInt(variable.getAvailabilityType());
        response.appendInt(variable.getVariableTarget());
        response.appendBoolean(true); // alwaysAvailable
        response.appendBoolean(!variable.isReadOnly()); // canCreateAndDelete
        response.appendBoolean(variable.hasValue());
        response.appendBoolean(!variable.isReadOnly()); // canWriteValue
        response.appendBoolean(false); // canInterceptChanges
        response.appendBoolean(false); // isInvisible
        response.appendBoolean(true); // canReadCreationTime
        response.appendBoolean(true); // canReadLastUpdateTime
        response.appendBoolean(false); // no text connector table
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredAllVariablesDiffComposer);
        this.response.appendInt(this.allVariablesHash);
        this.response.appendBoolean(this.lastChunk);

        this.response.appendInt(this.removedVariables.size());
        for (String variableId : this.removedVariables) {
            this.response.appendString(variableId);
        }

        this.response.appendInt(this.addedOrUpdated.size());
        for (RoomWiredVariableCatalog.Variable variable : this.addedOrUpdated) {
            this.response.appendInt(variable.hash());
            appendVariable(this.response, variable);
        }

        return this.response;
    }
}
