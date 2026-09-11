package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collections;
import java.util.List;

public class WiredUserVariablesDataComposer extends MessageComposer {
    private final RoomUserVariableManager.Snapshot userSnapshot;
    private final RoomFurniVariableManager.Snapshot furniSnapshot;
    private final RoomVariableManager.Snapshot roomSnapshot;
    private final List<WiredVariableDefinitionInfo> contextDefinitions;
    private final Room room;

    public WiredUserVariablesDataComposer(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot) {
        this(userSnapshot, furniSnapshot, roomSnapshot, resolveRoom(userSnapshot, furniSnapshot, roomSnapshot));
    }

    private WiredUserVariablesDataComposer(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot,
            Room room) {
        this(
                userSnapshot,
                furniSnapshot,
                roomSnapshot,
                room != null ? WiredContextVariableSupport.createDefinitionInfos(room) : Collections.emptyList(),
                room);
    }

    public WiredUserVariablesDataComposer(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot,
            List<WiredVariableDefinitionInfo> contextDefinitions) {
        this(
                userSnapshot,
                furniSnapshot,
                roomSnapshot,
                contextDefinitions,
                resolveRoom(userSnapshot, furniSnapshot, roomSnapshot));
    }

    private WiredUserVariablesDataComposer(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot,
            List<WiredVariableDefinitionInfo> contextDefinitions,
            Room room) {
        this.userSnapshot = userSnapshot;
        this.furniSnapshot = furniSnapshot;
        this.roomSnapshot = roomSnapshot;
        this.contextDefinitions = (contextDefinitions != null) ? contextDefinitions : Collections.emptyList();
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredUserVariablesDataComposer);

        int roomId = 0;

        if (this.userSnapshot != null) {
            roomId = this.userSnapshot.getRoomId();
        } else if (this.furniSnapshot != null) {
            roomId = this.furniSnapshot.getRoomId();
        } else if (this.roomSnapshot != null) {
            roomId = this.roomSnapshot.getRoomId();
        }

        this.response.appendInt(roomId);

        this.response.appendInt(
                (this.userSnapshot != null) ? this.userSnapshot.getDefinitions().size() : 0);

        if (this.userSnapshot != null) {
            for (RoomUserVariableManager.DefinitionEntry definition : this.userSnapshot.getDefinitions()) {
                this.response.appendInt(definition.getItemId());
                this.response.appendString(definition.getName());
                this.response.appendBoolean(definition.hasValue());
                this.response.appendInt(definition.getAvailability());
                this.response.appendBoolean(definition.isTextConnected());
                this.response.appendBoolean(definition.isReadOnly());
            }
        }

        this.response.appendInt(
                (this.userSnapshot != null) ? this.userSnapshot.getUsers().size() : 0);

        if (this.userSnapshot != null) {
            for (RoomUserVariableManager.UserAssignmentsEntry user : this.userSnapshot.getUsers()) {
                this.response.appendInt(user.getUserId());
                this.response.appendInt(user.getAssignments().size());

                for (RoomUserVariableManager.AssignmentEntry assignment : user.getAssignments()) {
                    this.response.appendInt(assignment.getVariableItemId());
                    this.response.appendBoolean(assignment.hasValue());
                    this.response.appendInt((assignment.getValue() != null) ? assignment.getValue() : 0);
                    this.response.appendInt(assignment.getCreatedAt());
                    this.response.appendInt(assignment.getUpdatedAt());
                }
            }
        }

        this.response.appendInt(
                (this.furniSnapshot != null)
                        ? this.furniSnapshot.getDefinitions().size()
                        : 0);

        if (this.furniSnapshot != null) {
            for (RoomFurniVariableManager.DefinitionEntry definition : this.furniSnapshot.getDefinitions()) {
                this.response.appendInt(definition.getItemId());
                this.response.appendString(definition.getName());
                this.response.appendBoolean(definition.hasValue());
                this.response.appendInt(definition.getAvailability());
                this.response.appendBoolean(definition.isTextConnected());
                this.response.appendBoolean(definition.isReadOnly());
            }
        }

        this.response.appendInt(
                (this.furniSnapshot != null) ? this.furniSnapshot.getFurnis().size() : 0);

        if (this.furniSnapshot != null) {
            for (RoomFurniVariableManager.FurniAssignmentsEntry furni : this.furniSnapshot.getFurnis()) {
                this.response.appendInt(furni.getFurniId());
                this.response.appendInt(furni.getAssignments().size());

                for (RoomFurniVariableManager.AssignmentEntry assignment : furni.getAssignments()) {
                    this.response.appendInt(assignment.getVariableItemId());
                    this.response.appendBoolean(assignment.hasValue());
                    this.response.appendInt((assignment.getValue() != null) ? assignment.getValue() : 0);
                    this.response.appendInt(assignment.getCreatedAt());
                    this.response.appendInt(assignment.getUpdatedAt());
                }
            }
        }

        this.response.appendInt(
                (this.roomSnapshot != null) ? this.roomSnapshot.getDefinitions().size() : 0);

        if (this.roomSnapshot != null) {
            for (RoomVariableManager.DefinitionEntry definition : this.roomSnapshot.getDefinitions()) {
                this.response.appendInt(definition.getItemId());
                this.response.appendString(definition.getName());
                this.response.appendBoolean(definition.hasValue());
                this.response.appendInt(definition.getAvailability());
                this.response.appendBoolean(definition.isTextConnected());
                this.response.appendBoolean(definition.isReadOnly());
            }
        }

        this.response.appendInt(
                (this.roomSnapshot != null) ? this.roomSnapshot.getAssignments().size() : 0);

        if (this.roomSnapshot != null) {
            for (RoomVariableManager.AssignmentEntry assignment : this.roomSnapshot.getAssignments()) {
                this.response.appendInt(assignment.getVariableItemId());
                this.response.appendBoolean(assignment.hasValue());
                this.response.appendInt((assignment.getValue() != null) ? assignment.getValue() : 0);
                this.response.appendInt(assignment.getCreatedAt());
                this.response.appendInt(assignment.getUpdatedAt());
            }
        }

        this.response.appendInt(this.contextDefinitions.size());

        for (WiredVariableDefinitionInfo definition : this.contextDefinitions) {
            if (definition == null) {
                continue;
            }

            this.response.appendInt(definition.getItemId());
            this.response.appendString(definition.getName());
            this.response.appendBoolean(definition.hasValue());
            this.response.appendInt(definition.getAvailability());
            this.response.appendBoolean(definition.isTextConnected());
            this.response.appendBoolean(definition.isReadOnly());
        }

        List<WiredArrayDefinitionSupport.EditorDefinition> arrayDefinitions =
                WiredArrayDefinitionSupport.collect(this.room);
        if (!arrayDefinitions.isEmpty()) {
            this.response.appendString(WiredManager.getGson().toJson(arrayDefinitions));
        }

        return this.response;
    }

    private static Room resolveRoom(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot) {
        int roomId = 0;

        if (userSnapshot != null) {
            roomId = userSnapshot.getRoomId();
        } else if (furniSnapshot != null) {
            roomId = furniSnapshot.getRoomId();
        } else if (roomSnapshot != null) {
            roomId = roomSnapshot.getRoomId();
        }

        if (roomId <= 0) {
            return null;
        }

        return Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
    }
}
