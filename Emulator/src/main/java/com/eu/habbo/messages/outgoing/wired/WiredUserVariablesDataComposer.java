package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinitionSupport;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredPointerVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WiredUserVariablesDataComposer extends MessageComposer {
    /** {@code variableType} of the trailing metadata, as the client's parser files the entries. */
    public static final int METADATA_TYPE_FURNI = 0;

    public static final int METADATA_TYPE_ROOM = 1;

    public static final int METADATA_TYPE_USER = 2;

    public static final int METADATA_TYPE_CONTEXT = 3;

    private final RoomUserVariableManager.Snapshot userSnapshot;
    private final RoomFurniVariableManager.Snapshot furniSnapshot;
    private final RoomVariableManager.Snapshot roomSnapshot;
    private final List<WiredVariableDefinitionInfo> contextDefinitions;
    private final List<TextConnectorMetadata> textConnectors;
    private final Room room;

    public WiredUserVariablesDataComposer(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot) {
        this(
                userSnapshot,
                furniSnapshot,
                roomSnapshot,
                resolveContextDefinitions(userSnapshot, furniSnapshot, roomSnapshot));
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
                resolveTextConnectors(
                        resolveRoom(userSnapshot, furniSnapshot, roomSnapshot),
                        userSnapshot,
                        furniSnapshot,
                        roomSnapshot,
                        contextDefinitions));
    }

    public WiredUserVariablesDataComposer(
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot,
            List<WiredVariableDefinitionInfo> contextDefinitions,
            List<TextConnectorMetadata> textConnectors) {
        this.userSnapshot = userSnapshot;
        this.furniSnapshot = furniSnapshot;
        this.roomSnapshot = roomSnapshot;
        this.contextDefinitions = (contextDefinitions != null) ? contextDefinitions : Collections.emptyList();
        this.textConnectors = (textConnectors != null) ? textConnectors : Collections.emptyList();
        this.room = resolveRoom(userSnapshot, furniSnapshot, roomSnapshot);
    }

    /**
     * One trailing-metadata entry: the value-to-text table of a text connected definition. Serialized
     * as JSON after the fixed part of the packet, which older clients simply do not read.
     */
    public static final class TextConnectorMetadata {
        private final int itemId;
        private final int variableType;
        private final List<TextConnectorEntry> textConnector;

        public TextConnectorMetadata(int itemId, int variableType, Map<Integer, String> mappings) {
            this.itemId = itemId;
            this.variableType = variableType;
            this.textConnector = new ArrayList<>();
            if (mappings != null) {
                for (Map.Entry<Integer, String> mapping : mappings.entrySet()) {
                    this.textConnector.add(new TextConnectorEntry(mapping.getKey(), mapping.getValue()));
                }
            }
        }

        public int getItemId() {
            return this.itemId;
        }

        public int getVariableType() {
            return this.variableType;
        }

        public List<TextConnectorEntry> getTextConnector() {
            return Collections.unmodifiableList(this.textConnector);
        }
    }

    /**
     * One trailing-metadata entry for a pointer: listed as a variable of its source type, but it only
     * exists while a stack runs ({@code requiresExecutionContext}), so editors without one leave it out.
     */
    public static final class PointerMetadata {
        private final int itemId;
        private final int variableType;
        private final boolean requiresExecutionContext;

        public PointerMetadata(int itemId, int variableType) {
            this.itemId = itemId;
            this.variableType = variableType;
            this.requiresExecutionContext = true;
        }

        public int getItemId() {
            return this.itemId;
        }

        public int getVariableType() {
            return this.variableType;
        }

        public boolean requiresExecutionContext() {
            return this.requiresExecutionContext;
        }
    }

    /** The metadata entries of the room's named pointers. */
    public static List<PointerMetadata> resolvePointers(Room room) {
        List<PointerMetadata> result = new ArrayList<>();
        if (room == null) return result;

        int[][] targets = {
            {WiredVariableTargets.OFFICIAL_FURNI, METADATA_TYPE_FURNI},
            {WiredVariableTargets.OFFICIAL_GLOBAL, METADATA_TYPE_ROOM},
            {WiredVariableTargets.OFFICIAL_USER, METADATA_TYPE_USER},
            {WiredVariableTargets.OFFICIAL_CONTEXT, METADATA_TYPE_CONTEXT}
        };
        for (int[] target : targets) {
            for (WiredExtraPointerVariable pointer : WiredPointerVariableSupport.pointers(room, target[0])) {
                result.add(new PointerMetadata(pointer.getId(), target[1]));
            }
        }
        return result;
    }

    public static final class TextConnectorEntry {
        private final int key;
        private final String value;

        public TextConnectorEntry(int key, String value) {
            this.key = key;
            this.value = value;
        }

        public int getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }
    }

    /** The text-connector tables of every text connected definition in the snapshots. */
    public static List<TextConnectorMetadata> resolveTextConnectors(
            Room room,
            RoomUserVariableManager.Snapshot userSnapshot,
            RoomFurniVariableManager.Snapshot furniSnapshot,
            RoomVariableManager.Snapshot roomSnapshot,
            List<WiredVariableDefinitionInfo> contextDefinitions) {
        List<TextConnectorMetadata> result = new ArrayList<>();

        if (room == null) {
            return result;
        }

        if (userSnapshot != null) {
            for (RoomUserVariableManager.DefinitionEntry definition : userSnapshot.getDefinitions()) {
                addTextConnector(
                        result, room, definition.getItemId(), METADATA_TYPE_USER, definition.isTextConnected());
            }
        }

        if (furniSnapshot != null) {
            for (RoomFurniVariableManager.DefinitionEntry definition : furniSnapshot.getDefinitions()) {
                addTextConnector(
                        result, room, definition.getItemId(), METADATA_TYPE_FURNI, definition.isTextConnected());
            }
        }

        if (roomSnapshot != null) {
            for (RoomVariableManager.DefinitionEntry definition : roomSnapshot.getDefinitions()) {
                addTextConnector(
                        result, room, definition.getItemId(), METADATA_TYPE_ROOM, definition.isTextConnected());
            }
        }

        if (contextDefinitions != null) {
            for (WiredVariableDefinitionInfo definition : contextDefinitions) {
                if (definition == null) {
                    continue;
                }

                addTextConnector(
                        result, room, definition.getItemId(), METADATA_TYPE_CONTEXT, definition.isTextConnected());
            }
        }

        return result;
    }

    private static void addTextConnector(
            List<TextConnectorMetadata> result, Room room, int itemId, int variableType, boolean textConnected) {
        if (!textConnected) {
            return;
        }

        Map<Integer, String> mappings = WiredVariableTextConnectorSupport.mappings(room, itemId);
        if (mappings.isEmpty()) {
            return;
        }

        result.add(new TextConnectorMetadata(itemId, variableType, mappings));
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
                this.response.appendInt(BuildersClubItemIds.toWire(furni.getFurniId()));
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

        // Optional trailing metadata: the client reads it only when bytes remain, so a packet
        // without it stays exactly what it was. Text-connector tables and array schemas share the
        // one JSON array, because the client applies every entry to the definition it names and
        // ignores the half it was not given.
        List<Object> metadata = new ArrayList<>(this.textConnectors);

        metadata.addAll(WiredArrayDefinitionSupport.collect(this.room));
        metadata.addAll(resolvePointers(this.room));

        if (!metadata.isEmpty()) {
            this.response.appendString(WiredManager.getGson().toJson(metadata));
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

        return roomById(roomId);
    }

    /** The loaded room, or null outside a running hotel or for an unknown id. */
    private static Room roomById(int roomId) {
        if (roomId <= 0) return null;

        var environment = Emulator.getGameEnvironment();
        if (environment == null || environment.getRoomManager() == null) return null;

        return environment.getRoomManager().getRoom(roomId);
    }

    private static List<WiredVariableDefinitionInfo> resolveContextDefinitions(
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

        Room room = roomById(roomId);
        return room != null ? WiredContextVariableSupport.createDefinitionInfos(room) : Collections.emptyList();
    }
}
