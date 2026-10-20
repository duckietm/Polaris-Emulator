package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.rooms.RoomWiredInternalVariables;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import java.util.Arrays;

/**
 * The variable ids of the official definition and save packets ({@code variableIds}), against the tokens our boxes
 * store ({@code custom:<box id>}, {@code internal:<key>}).
 *
 * <p>An id is what the variable list names the variable by, as AIR sends back the id it picked from that list: a
 * box's variable is {@code user:<box id>}, {@code furni:<box id>}, {@code room:<box id>} or {@code ctx:<box id>}
 * ({@link RoomWiredVariableCatalog#variableId}); a built-in one has Habbo's fixed id ({@code -125}, {@code ~133},
 * {@link RoomWiredInternalVariables}); a built-in Habbo does not have goes out as {@code <target>:<key>}
 * ({@code furni:@opacity}). An empty slot before a filled one is AIR's {@code n} ({@code WiredVariable} "none").
 *
 * <p>Reading takes every form an older client, share code or box wrote too: the tokens themselves, a bare box id
 * and {@code <target>:<key>}.
 */
public final class WiredVariableIds {
    /** AIR's id of "no variable" in a list of variable ids. */
    public static final String NONE = "n";

    public static final String CUSTOM_TOKEN_PREFIX = "custom:";
    public static final String INTERNAL_TOKEN_PREFIX = "internal:";

    private static final String[] TARGET_PREFIXES = {"user:", "furni:", "room:", "ctx:"};
    private static final String[] EMPTY = new String[0];

    private WiredVariableIds() {}

    /**
     * The id of a token's variable on an official target (furni 0, user 1, global -10, context -20); empty for no
     * variable.
     */
    public static String toWire(int officialTarget, String token) {
        String normalized = normalizeToken(token);
        if (normalized.startsWith(CUSTOM_TOKEN_PREFIX)) {
            return RoomWiredVariableCatalog.variableId(
                    officialTarget, Integer.parseInt(normalized.substring(CUSTOM_TOKEN_PREFIX.length())));
        }
        if (normalized.startsWith(INTERNAL_TOKEN_PREFIX)) {
            String key = normalized.substring(INTERNAL_TOKEN_PREFIX.length());
            String id = RoomWiredInternalVariables.idOf(officialTarget, key);
            return id != null ? id : prefix(officialTarget) + key;
        }
        return "";
    }

    /** {@link #toWire} for one of our holder kinds (user 0, furni 1, context 2, room 3). */
    public static String toWireForHolder(int holderKind, String token) {
        return toWire(WiredVariableTargets.toOfficial(holderKind), token);
    }

    /** {@link #toWire} for a box's variable by its id (0 or less: no variable). */
    public static String toWire(int officialTarget, int boxId) {
        return boxId > 0 ? RoomWiredVariableCatalog.variableId(officialTarget, boxId) : "";
    }

    /** The token of a variable id in any of the forms we read; empty when it names no variable. */
    public static String toToken(String id) {
        if (id == null) {
            return "";
        }
        String text = id.trim();
        if (text.isEmpty() || NONE.equals(text)) {
            return "";
        }
        if (text.startsWith(CUSTOM_TOKEN_PREFIX) || text.startsWith(INTERNAL_TOKEN_PREFIX)) {
            return normalizeToken(text);
        }

        RoomWiredInternalVariables.Row row = RoomWiredInternalVariables.byId(text);
        if (row != null) {
            return INTERNAL_TOKEN_PREFIX + row.key();
        }

        for (String prefix : TARGET_PREFIXES) {
            if (text.startsWith(prefix)) {
                return fromRest(text.substring(prefix.length()));
            }
        }
        int boxId = parsePositive(text);
        return boxId > 0 ? CUSTOM_TOKEN_PREFIX + boxId : "";
    }

    /** The box id of a variable id ({@code user:12}, {@code custom:12}, {@code 12}), or 0 for any other id. */
    public static int boxIdOf(String id) {
        String token = toToken(id);
        return token.startsWith(CUSTOM_TOKEN_PREFIX) ? parsePositive(token.substring(CUSTOM_TOKEN_PREFIX.length())) : 0;
    }

    /**
     * The official target a variable id names ({@code user:}, {@code furni:}, {@code room:}, {@code ctx:} or a
     * built-in's own target), or {@link Integer#MIN_VALUE} when the id does not say.
     */
    public static int officialTargetOf(String id) {
        if (id == null) {
            return Integer.MIN_VALUE;
        }
        String text = id.trim();
        RoomWiredInternalVariables.Row row = RoomWiredInternalVariables.byId(text);
        if (row != null) {
            return row.target();
        }
        if (text.startsWith("user:")) return RoomWiredVariableCatalog.TARGET_USER;
        if (text.startsWith("furni:")) return RoomWiredVariableCatalog.TARGET_FURNI;
        if (text.startsWith("room:")) return RoomWiredVariableCatalog.TARGET_GLOBAL;
        if (text.startsWith("ctx:")) return RoomWiredVariableCatalog.TARGET_CONTEXT;
        return Integer.MIN_VALUE;
    }

    /** The token in slot {@code index} of a save's variable ids; empty when the slot is missing or "none". */
    public static String tokenAt(String[] ids, int index) {
        if (ids == null || index < 0 || index >= ids.length) {
            return "";
        }
        return toToken(ids[index]);
    }

    /** Whether a save carries any variable id at all (an editor that sends them), "none" slots included. */
    public static boolean present(String[] ids) {
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The definition's variable ids, slot by slot: an empty slot before a filled one is {@link #NONE}, and empty
     * slots at the end are left out.
     */
    public static String[] list(String... ids) {
        if (ids == null) {
            return EMPTY;
        }
        int length = ids.length;
        while (length > 0 && (ids[length - 1] == null || ids[length - 1].isEmpty() || NONE.equals(ids[length - 1]))) {
            length--;
        }
        String[] out = Arrays.copyOf(ids, length);
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null || out[i].isEmpty()) {
                out[i] = NONE;
            }
        }
        return out;
    }

    /** {@code custom:<positive id>} or {@code internal:<registry key>}; a bare positive id is a box's variable. */
    public static String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        String text = token.trim();
        if (text.startsWith(CUSTOM_TOKEN_PREFIX)) {
            int boxId = parsePositive(text.substring(CUSTOM_TOKEN_PREFIX.length()));
            return boxId > 0 ? CUSTOM_TOKEN_PREFIX + boxId : "";
        }
        if (text.startsWith(INTERNAL_TOKEN_PREFIX)) {
            String key = WiredInternalVariableSupport.normalizeKey(text.substring(INTERNAL_TOKEN_PREFIX.length()));
            return key == null || key.isEmpty() ? "" : INTERNAL_TOKEN_PREFIX + key;
        }
        int boxId = parsePositive(text);
        return boxId > 0 ? CUSTOM_TOKEN_PREFIX + boxId : "";
    }

    private static String fromRest(String rest) {
        String text = rest.trim();
        if (text.startsWith("@") || text.startsWith("~")) {
            String key = WiredInternalVariableSupport.normalizeKey(text);
            return key == null || key.isEmpty() ? "" : INTERNAL_TOKEN_PREFIX + key;
        }
        int boxId = parsePositive(text);
        return boxId > 0 ? CUSTOM_TOKEN_PREFIX + boxId : "";
    }

    private static String prefix(int officialTarget) {
        return switch (officialTarget) {
            case RoomWiredVariableCatalog.TARGET_USER -> "user:";
            case RoomWiredVariableCatalog.TARGET_FURNI -> "furni:";
            case RoomWiredVariableCatalog.TARGET_CONTEXT -> "ctx:";
            default -> "room:";
        };
    }

    private static int parsePositive(String text) {
        if (text == null || text.isEmpty() || text.length() > 10) {
            return 0;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return 0;
            }
        }
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
