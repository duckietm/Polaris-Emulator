package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.util.regex.Pattern;

final class WiredVariableNameValidator {
    static final int MIN_NAME_LENGTH = 1;
    static final int MAX_NAME_LENGTH = 40;

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private WiredVariableNameValidator() {
    }

    static String normalizeForSave(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("\t", "")
            .replace("\r", "")
            .replace("\n", "")
            .replaceAll("\\s+", "_");
    }

    static String normalizeLegacy(String value) {
        String normalized = normalizeForSave(value);

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

        return null;
    }
}
