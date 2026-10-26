package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.regex.Pattern;

final class WiredVariableNameValidator {
    static final int MIN_NAME_LENGTH = 1;
    static final int MAX_NAME_LENGTH = 40;

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private WiredVariableNameValidator() {}

    /**
     * The name a new save stores: each space becomes "_", capitals are lowered and leading and trailing "_" are
     * trimmed (observed on AIR: "a b" saves as "a_b", "_a" as "a"). A name too long to save is left as sent, so it
     * still fails the length check.
     */
    static String normalizeForSave(String value) {
        String spaced = withUnderscores(value);
        if (spaced.length() > MAX_NAME_LENGTH) {
            return spaced;
        }
        StringBuilder lowered = new StringBuilder(spaced.length());
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            lowered.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        int start = 0;
        int end = lowered.length();
        while (start < end && lowered.charAt(start) == '_') start++;
        while (end > start && lowered.charAt(end - 1) == '_') end--;
        return lowered.substring(start, end);
    }

    private static String withUnderscores(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\t", "").replace("\r", "").replace("\n", "").replaceAll("\\s+", "_");
    }

    /** A stored name, read as it was saved before names were cleaned. */
    static String normalizeLegacy(String value) {
        String normalized = withUnderscores(value);

        if (normalized.contains("=")) {
            normalized = normalized.substring(0, normalized.indexOf('='));
        }

        while (normalized.startsWith("@") || normalized.startsWith("~")) {
            normalized = normalized.substring(1);
        }

        if (normalized.length() > MAX_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_NAME_LENGTH);
        }

        return normalized;
    }

    static void validateDefinitionName(Room room, int currentItemId, String variableName) throws WiredSaveException {
        String normalized = normalizeForSave(variableName);

        if (normalized.length() < MIN_NAME_LENGTH || normalized.length() > MAX_NAME_LENGTH) {
            throw new WiredSaveException("wiredfurni.error.variables.name_length");
        }

        if (!VALID_NAME_PATTERN.matcher(normalized).matches()) {
            throw new WiredSaveException("wiredfurni.error.variables.name_syntax");
        }

        if (isNameInUse(room, currentItemId, normalized)) {
            throw new WiredSaveException("wiredfurni.error.variables.name_uniq");
        }
    }

    private static boolean isNameInUse(Room room, int currentItemId, String variableName) {
        if (room == null || room.getRoomSpecialTypes() == null || variableName == null || variableName.isEmpty()) {
            return false;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra == null || extra.getId() == currentItemId) {
                continue;
            }

            String existingName = getDefinitionName(extra);

            if (existingName != null && existingName.equalsIgnoreCase(variableName)) {
                return true;
            }
        }

        return false;
    }

    private static String getDefinitionName(InteractionWiredExtra extra) {
        if (extra instanceof WiredExtraUserVariable) {
            return ((WiredExtraUserVariable) extra).getVariableName();
        }

        if (extra instanceof WiredExtraFurniVariable) {
            return ((WiredExtraFurniVariable) extra).getVariableName();
        }

        if (extra instanceof WiredExtraRoomVariable) {
            return ((WiredExtraRoomVariable) extra).getVariableName();
        }

        if (extra instanceof WiredExtraContextVariable) {
            return ((WiredExtraContextVariable) extra).getVariableName();
        }

        if (extra instanceof WiredExtraVariableReference) {
            return ((WiredExtraVariableReference) extra).getVariableName();
        }

        if (extra instanceof WiredExtraVariableEcho) {
            return ((WiredExtraVariableEcho) extra).getVariableName();
        }

        if (extra instanceof WiredExtraPointerVariable pointer) {
            return pointer.getVariableName();
        }

        return null;
    }
}
