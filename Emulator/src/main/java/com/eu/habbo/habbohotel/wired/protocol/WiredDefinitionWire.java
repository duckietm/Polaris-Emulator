package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/**
 * Writes a wired box's editor definition in the official AIR layout (build WIN63-202610131318), from the
 * legacy Arcturus layout every box still serializes itself in.
 *
 * <p>Legacy: {@code bool selectionEnabled, int furniLimit, int[] stuffIds, int spriteId, int id, String
 * stringParam, int[] intParams, int selectionCode, int code}, then per holder: trigger {@code int[]
 * conflicts}, action {@code int delay, int[] conflicts, [contexts]}, condition two unused ints.
 *
 * <p>Official ({@code §_-lk§}): {@code int furniLimit, int[] stuffIds, int[] stuffIds2, int spriteId, int
 * id, String stringParam, int[] intParams, String[] variableIds, int[] furniSources, int[] userSources, int
 * code}, the holder's definition specifics (action delay, condition quantifier code), {@code bool
 * advancedMode}, the input sources conf, {@code bool allowWallFurni}, the holder's type specifics
 * (condition quantifier type byte and invert), the wired context and {@code int[] defaultIntParams}.
 *
 * <p>The furni and user sources come from the box's source fields and the input sources conf from
 * {@link WiredInputSourcesConf}; the other new fields go out empty until each box writes them itself. An action's context block (the variable leaderboard) is passed
 * on as it is.
 */
public final class WiredDefinitionWire {
    /** Which wired definition packet this is. */
    public enum Holder {
        TRIGGER,
        ACTION,
        CONDITION,
        /** Official addon definition (2168); legacy-wise an extra box, written like an action. */
        ADDON,
        /** Official selector definition (2509); legacy-wise an effect box. */
        SELECTOR,
        /** Official variable definition (2955); legacy-wise an extra box. */
        VARIABLE;

        /** Addons, selectors and variables still write the legacy action layout. */
        boolean writesLegacyAction() {
            return this == ACTION || this == ADDON || this == SELECTOR || this == VARIABLE;
        }
    }

    /** Legacy definitions longer than this are not trusted. */
    static final int MAX_ARRAY = 10_000;

    private WiredDefinitionWire() {}

    /** Framing of a {@link ServerMessage}: int length, short header. */
    private static final int FRAME_BYTES = 6;

    /** Rewrites {@code legacy} (a message holding one legacy definition) into {@code target}. */
    public static void writeOfficial(ServerMessage legacy, Holder holder, ServerMessage target) {
        writeOfficial(legacy, holder, target, null);
    }

    /**
     * Rewrites {@code legacy} into {@code target}, with the furni and user sources of {@code box} in the
     * official source fields.
     */
    public static void writeOfficial(ServerMessage legacy, Holder holder, ServerMessage target, Object box) {
        ByteBuf body = legacy.get();
        try {
            body.skipBytes(FRAME_BYTES);
            Definition definition = readLegacy(body, holder);
            if (box instanceof WiredOfficialSources sources) {
                definition.furniSources = sources.officialFurniSources();
                definition.userSources = sources.officialUserSources();
            } else {
                definition.furniSources = WiredSourceFields.furniSources(box);
                definition.userSources = WiredSourceFields.userSources(box);
            }
            if (box instanceof WiredOfficialVariableIds variables) {
                definition.variableIds = variables.officialVariableIds();
            }
            if (box instanceof WiredOfficialSecondaryPicks picks) {
                definition.stuffIds2 = picks.officialFurniIds2();
            }
            if (box instanceof WiredOfficialSelector selector) {
                definition.selectorFilter = selector.selectorFilter();
                definition.selectorInvert = selector.selectorInvert();
            }
            definition.inputSources = WiredInputSourcesConf.of(box);
            definition.textPlaceholders = WiredTextPlaceholderContext.namesFor(box);
            if (box instanceof InteractionWired wired && wired.isEditorFresh()) {
                // A box that was never saved opens on the official default sources.
                definition.furniSources = WiredSourceSaveRules.fill(
                        new int[0],
                        definition.inputSources.allowedFurni().length,
                        definition.inputSources.defaultFurni(),
                        definition.furniSources);
                definition.userSources = WiredSourceSaveRules.fill(
                        new int[0],
                        definition.inputSources.allowedUsers().length,
                        definition.inputSources.defaultUsers(),
                        definition.userSources);
            }
            writeOfficial(definition, holder, target);
        } finally {
            body.release();
        }
    }

    /** The fields of one definition, as both layouts know them. */
    static final class Definition {
        int furniLimit;
        int[] stuffIds = new int[0];
        int[] stuffIds2 = new int[0];
        int spriteId;
        int id;
        String stringParam = "";
        int[] intParams = new int[0];
        String[] variableIds = new String[0];
        int[] furniSources = new int[0];
        int[] userSources = new int[0];
        boolean selectorFilter;
        boolean selectorInvert;
        int code;
        int delay;
        WiredInputSourcesConf inputSources = WiredInputSourcesConf.NONE;
        /** The text-placeholder context of a message box; null for the others. */
        WiredTextPlaceholderContext.Names textPlaceholders;
        /** An action's legacy context block (count and entries), passed on unchanged. */
        byte[] contexts = new byte[0];
        /** Bytes a legacy box wrote after the fields this layout knows. */
        int leftover;
    }

    static Definition readLegacy(ByteBuf in, Holder holder) {
        Definition definition = new Definition();
        in.readBoolean();
        definition.furniLimit = in.readInt();
        definition.stuffIds = readInts(in);
        definition.spriteId = in.readInt();
        definition.id = in.readInt();
        definition.stringParam = readString(in);
        definition.intParams = readInts(in);
        in.readInt(); // selection code
        definition.code = in.readInt();

        if (holder == Holder.TRIGGER) {
            readInts(in); // conflicting actions
        } else if (holder.writesLegacyAction()) {
            definition.delay = in.readInt();
            readInts(in); // conflicting triggers
            if (in.isReadable()) {
                definition.contexts = new byte[in.readableBytes()];
                in.readBytes(definition.contexts);
            }
        }
        // Whatever a legacy box wrote after its known fields (two unused ints on conditions) is dropped.
        definition.leftover = in.readableBytes();
        in.skipBytes(in.readableBytes());
        return definition;
    }

    static void writeOfficial(Definition definition, Holder holder, ServerMessage out) {
        out.appendInt(definition.furniLimit);
        writeInts(out, definition.stuffIds);
        writeInts(out, definition.stuffIds2);
        out.appendInt(definition.spriteId);
        out.appendInt(definition.id);
        out.appendString(definition.stringParam);
        writeInts(out, definition.intParams);
        out.appendInt(definition.variableIds.length);
        for (String variableId : definition.variableIds) out.appendString(variableId);
        writeInts(out, definition.furniSources);
        writeInts(out, definition.userSources);
        out.appendInt(officialCode(holder, definition.code));

        switch (holder) {
            case ACTION -> out.appendInt(definition.delay);
            case CONDITION -> out.appendInt(0); // quantifier code
            case SELECTOR -> {
                out.appendBoolean(definition.selectorFilter);
                out.appendBoolean(definition.selectorInvert);
            }
            default -> {}
        }

        out.appendBoolean(false); // advanced mode
        definition.inputSources.write(out); // input sources conf and allow wall furni

        if (holder == Holder.CONDITION) {
            out.appendByte(0); // quantifier type: none
            out.appendBoolean(false); // invert
        }

        if (definition.contexts.length > 0) {
            out.appendRawBytes(definition.contexts);
        } else if (definition.textPlaceholders != null) {
            WiredTextPlaceholderContext.write(out, definition.textPlaceholders);
        } else {
            out.appendInt(0); // wired context
        }
        writeInts(out, new int[0]); // default int params
    }

    /** Actions, selectors, addons and variables go out with their official codes. */
    static int officialCode(Holder holder, int legacyCode) {
        return switch (holder) {
            case ACTION -> WiredActionCodes.official(legacyCode);
            case SELECTOR -> WiredSelectorCodes.official(legacyCode);
            case ADDON -> WiredAddonCodes.officialAddon(legacyCode);
            case VARIABLE -> WiredAddonCodes.officialVariable(legacyCode);
            default -> legacyCode;
        };
    }

    private static int[] readInts(ByteBuf in) {
        int count = in.readInt();
        if (count < 0 || count > MAX_ARRAY || count * 4L > in.readableBytes()) {
            throw new IllegalStateException("Malformed legacy wired definition: array of " + count);
        }
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = in.readInt();
        return values;
    }

    private static String readString(ByteBuf in) {
        int length = in.readUnsignedShort();
        if (length > in.readableBytes()) {
            throw new IllegalStateException("Malformed legacy wired definition: string of " + length);
        }
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeInts(ServerMessage out, int[] values) {
        out.appendInt(values.length);
        for (int value : values) out.appendInt(value);
    }
}
