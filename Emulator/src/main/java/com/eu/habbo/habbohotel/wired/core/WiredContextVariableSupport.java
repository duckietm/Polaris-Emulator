package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.messages.outgoing.wired.WiredUserVariablesDataComposer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class WiredContextVariableSupport {
    private WiredContextVariableSupport() {
    }

    public static List<WiredExtraContextVariable> getDefinitions(Room room) {
        List<WiredExtraContextVariable> definitions = new ArrayList<>();

        if (room == null || room.getRoomSpecialTypes() == null) {
            return definitions;
        }

        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras();
        if (extras == null || extras.isEmpty()) {
            return definitions;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraContextVariable) {
                definitions.add((WiredExtraContextVariable) extra);
            }
        }

        definitions.sort(Comparator
                .comparing(WiredExtraContextVariable::getVariableName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(WiredExtraContextVariable::getId));

        return definitions;
    }

    public static List<WiredVariableDefinitionInfo> createDefinitionInfos(Room room) {
        List<WiredVariableDefinitionInfo> definitions = new ArrayList<>();

        for (WiredExtraContextVariable definition : getDefinitions(room)) {
            if (definition == null || definition.getVariableName() == null || definition.getVariableName().isEmpty()) {
                continue;
            }

            definitions.add(new WiredVariableDefinitionInfo(
                    definition.getId(),
                    definition.getVariableName(),
                    definition.hasValue(),
                    0,
                    WiredVariableTextConnectorSupport.isTextConnected(room, definition.getId()),
                    false));
        }

        return definitions;
    }

    public static WiredExtraContextVariable getDefinition(Room room, int definitionItemId) {
        if (room == null || room.getRoomSpecialTypes() == null || definitionItemId <= 0) {
            return null;
        }

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(definitionItemId);
        return (extra instanceof WiredExtraContextVariable) ? (WiredExtraContextVariable) extra : null;
    }

    public static WiredVariableDefinitionInfo getDefinitionInfo(Room room, int definitionItemId) {
        WiredExtraContextVariable definition = getDefinition(room, definitionItemId);
        if (definition == null || definition.getVariableName() == null || definition.getVariableName().trim().isEmpty()) {
            return null;
        }

        return new WiredVariableDefinitionInfo(
                definition.getId(),
                definition.getVariableName(),
                definition.hasValue(),
                0,
                WiredVariableTextConnectorSupport.isTextConnected(room, definition.getId()),
                false);
    }

    public static boolean hasDefinition(Room room, int definitionItemId) {
        return getDefinitionInfo(room, definitionItemId) != null;
    }

    public static boolean assignVariable(WiredContext ctx, Room room, int definitionItemId, Integer value, boolean overrideExisting) {
        WiredExtraContextVariable definition = getDefinition(room, definitionItemId);
        if (ctx == null || definition == null) {
            return false;
        }

        if (overrideExisting && ctx.contextVariables().hasVariable(definitionItemId)) {
            ctx.forkContextVariables();
        }

        return ctx.contextVariables().assignValue(definitionItemId, definition.hasValue() ? value : null, overrideExisting);
    }

    public static boolean updateVariableValue(WiredContext ctx, Room room, int definitionItemId, Integer value) {
        WiredExtraContextVariable definition = getDefinition(room, definitionItemId);
        if (ctx == null || definition == null || !definition.hasValue()) {
            return false;
        }

        return ctx.contextVariables().updateValue(definitionItemId, value);
    }

    public static boolean removeVariable(WiredContext ctx, Room room, int definitionItemId) {
        return ctx != null && getDefinition(room, definitionItemId) != null && ctx.contextVariables().removeValue(definitionItemId);
    }

    public static boolean hasVariable(WiredContext ctx, int definitionItemId) {
        return ctx != null && ctx.contextVariables().hasVariable(definitionItemId);
    }

    public static Integer getCurrentValue(WiredContext ctx, int definitionItemId) {
        return ctx != null ? ctx.contextVariables().getValue(definitionItemId) : null;
    }

    public static int getCreatedAt(WiredContext ctx, int definitionItemId) {
        return ctx != null ? ctx.contextVariables().getCreatedAt(definitionItemId) : 0;
    }

    public static int getUpdatedAt(WiredContext ctx, int definitionItemId) {
        return ctx != null ? ctx.contextVariables().getUpdatedAt(definitionItemId) : 0;
    }

    public static void broadcastDefinitions(Room room) {
        if (room == null) {
            return;
        }

        WiredUserVariablesDataComposer composer = new WiredUserVariablesDataComposer(
                room.getUserVariableManager().createSnapshot(),
                room.getFurniVariableManager().createSnapshot(),
                room.getRoomVariableManager().createSnapshot());

        room.getHabbos().forEach(habbo ->
        {
            if (habbo == null || habbo.getClient() == null) {
                return;
            }

            habbo.getClient().sendResponse(composer);
        });
    }
}
