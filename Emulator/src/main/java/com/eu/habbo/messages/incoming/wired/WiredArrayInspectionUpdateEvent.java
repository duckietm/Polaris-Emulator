package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayNumericOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.arrays.WiredCreatorToolsArrayInspection;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredArrayInspectionDataComposer;

public final class WiredArrayInspectionUpdateEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || !room.canModifyWired(this.client.getHabbo()) || this.packet.bytesAvailable() < 30) return;

        int variableType = this.packet.readInt();
        int requestedOwnerId = this.packet.readInt();
        int definitionItemId = this.packet.readInt();
        int index = this.packet.readInt();
        int fieldId = this.packet.readInt();
        String rawValue = this.packet.readString();
        int page = this.packet.readInt();
        int pageSize = this.packet.readInt();
        long value;
        try {
            value = Long.parseLong(rawValue == null ? "" : rawValue.trim());
        } catch (NumberFormatException exception) {
            return;
        }

        WiredArrayCreatorToolsSupport.Resolved resolved =
                WiredArrayCreatorToolsSupport.resolve(room, variableType, requestedOwnerId, definitionItemId);
        if (resolved == null
                || fieldId <= 0
                || resolved.definition().getArrayDefinition().getField(fieldId) == null
                || !resolved.definition().isArrayWritable()
                || !room.getArrayVariableManager().hasValue(resolved.definition(), resolved.ownerId())) return;

        WiredArrayValue before = room.getArrayVariableManager().getValue(resolved.definition(), resolved.ownerId());
        int oldLength = before == null ? 0 : before.getLengthForCondition();
        RoomArrayVariableManager.FieldMutationOutcome outcome = room.getArrayVariableManager()
                .mutateField(
                        resolved.definition(),
                        resolved.ownerId(),
                        index,
                        fieldId,
                        WiredArrayNumericOperation.ASSIGN,
                        value);
        if (!outcome.changed()) return;

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(resolved.unit())
                .sourceItem(resolved.item())
                .variableTargetType(resolved.legacyTargetType())
                .variableDefinitionItemId(resolved.definition().getId())
                .arrayChange(WiredArrayChange.field(
                        index,
                        fieldId,
                        outcome.previousValue(),
                        outcome.currentValue(),
                        oldLength,
                        outcome.value() == null ? oldLength : outcome.value().getLengthForCondition()))
                .build();
        WiredManager.handleEvent(event);

        this.client.sendResponse(new WiredArrayInspectionDataComposer(WiredCreatorToolsArrayInspection.create(
                room,
                resolved.requestedOwnerType(),
                resolved.requestedOwnerId(),
                resolved.definition(),
                resolved.ownerId(),
                page,
                pageSize)));
    }

    @Override
    public int getRatelimit() {
        return 150;
    }
}
