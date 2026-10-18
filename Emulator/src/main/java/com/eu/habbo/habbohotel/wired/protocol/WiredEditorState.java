package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;

/**
 * What the official editor shows of a box: exactly the fields its save packet sends back. It is read
 * from the box's own definition, the same way {@link WiredDefinitionWire} builds the definition packet,
 * so a box saved again with these values keeps its configuration (the editor's open-then-save round trip).
 *
 * <p>Used by the wired setup share: an exported box is this state, and an import saves it through the
 * box's normal save path.
 */
public record WiredEditorState(
        WiredDefinitionWire.Holder holder,
        int[] intParams,
        String stringParam,
        int[] stuffIds,
        int[] stuffIds2,
        int[] furniSources,
        int[] userSources,
        String[] variableIds,
        int delay,
        int quantifierCode,
        boolean selectorFilter,
        boolean selectorInvert,
        boolean fresh) {

    /** The editor state of {@code box} in {@code room}, or null when the box's definition cannot be read. */
    public static WiredEditorState of(InteractionWired box, Room room) {
        if (box == null) return null;

        WiredDefinitionWire.Holder holder = WiredHolders.of(box);
        ServerMessage legacy = new ServerMessage(Outgoing.WiredEffectDataComposer);
        box.serializeWiredData(legacy, room);
        ByteBuf body = legacy.get();
        try {
            body.skipBytes(6);
            WiredDefinitionWire.Definition definition = WiredDefinitionWire.readLegacy(body, holder);
            int[] furniSources;
            int[] userSources;
            if (box instanceof WiredOfficialSources sources) {
                furniSources = sources.officialFurniSources();
                userSources = sources.officialUserSources();
            } else {
                furniSources = WiredSourceFields.furniSources(box);
                userSources = WiredSourceFields.userSources(box);
            }
            String[] variableIds =
                    box instanceof WiredOfficialVariableIds variables ? variables.officialVariableIds() : new String[0];
            int[] stuffIds2 = box instanceof WiredOfficialSecondaryPicks picks ? picks.officialFurniIds2() : new int[0];
            boolean filter = box instanceof WiredOfficialSelector selector && selector.selectorFilter();
            boolean invert = box instanceof WiredOfficialSelector selector && selector.selectorInvert();
            return new WiredEditorState(
                    holder,
                    nonNull(definition.intParams),
                    definition.stringParam == null ? "" : definition.stringParam,
                    nonNull(definition.stuffIds),
                    nonNull(stuffIds2),
                    nonNull(furniSources),
                    nonNull(userSources),
                    variableIds == null ? new String[0] : variableIds,
                    holder == WiredDefinitionWire.Holder.ACTION ? definition.delay : 0,
                    holder == WiredDefinitionWire.Holder.CONDITION ? WiredEditorDefaults.quantifierCode(box) : 0,
                    filter,
                    invert,
                    box.isEditorFresh());
        } catch (RuntimeException malformed) {
            return null;
        } finally {
            body.release();
        }
    }

    private static int[] nonNull(int[] values) {
        return values == null ? new int[0] : values;
    }
}
