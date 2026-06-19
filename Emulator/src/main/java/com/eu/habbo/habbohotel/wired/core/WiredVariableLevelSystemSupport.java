package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WiredVariableLevelSystemSupport {
    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_ROOM = 3;

    private static final int SYNTHETIC_USER_OFFSET = 700_000_000;
    private static final int SYNTHETIC_FURNI_OFFSET = 800_000_000;
    private static final int SYNTHETIC_ROOM_OFFSET = 900_000_000;
    private static final int SYNTHETIC_STRIDE = 16;

    private WiredVariableLevelSystemSupport() {
    }

    public static WiredExtraVariableLevelUpSystem getLevelSystem(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraVariableLevelUpSystem) {
                return (WiredExtraVariableLevelUpSystem) extra;
            }
        }

        return null;
    }

    public static List<WiredVariableDefinitionInfo> getDerivedDefinitions(Room room, int targetType, InteractionWiredExtra definitionExtra, WiredVariableDefinitionInfo baseDefinition) {
        if (room == null || definitionExtra == null || baseDefinition == null || !baseDefinition.hasValue()) {
            return Collections.emptyList();
        }

        WiredExtraVariableLevelUpSystem levelSystem = getLevelSystem(room, definitionExtra);
        if (levelSystem == null) {
            return Collections.emptyList();
        }

        List<WiredVariableDefinitionInfo> result = new ArrayList<>();

        for (int subvariableType : levelSystem.getSelectedSubvariables()) {
            result.add(new WiredVariableDefinitionInfo(
                createSyntheticItemId(targetType, baseDefinition.getItemId(), subvariableType),
                baseDefinition.getName() + "." + getSubvariableKey(subvariableType),
                true,
                baseDefinition.getAvailability(),
                false,
                true
            ));
        }

        result.sort(Comparator.comparing(WiredVariableDefinitionInfo::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(WiredVariableDefinitionInfo::getItemId));
        return result;
    }

    public static WiredVariableDefinitionInfo getDerivedDefinitionInfo(Room room, int targetType, int syntheticItemId) {
        DerivedDefinition derived = resolveDerivedDefinition(room, targetType, syntheticItemId);

        if (derived == null) {
            return null;
        }

        return new WiredVariableDefinitionInfo(
            derived.syntheticItemId,
            derived.variableName,
            true,
            derived.baseDefinition.getAvailability(),
            false,
            true
        );
    }

    public static DerivedDefinition resolveDerivedDefinition(Room room, int targetType, int syntheticItemId) {
        DecodedSyntheticId decoded = decodeSyntheticId(syntheticItemId);
        if (decoded == null || decoded.targetType != targetType || room == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        InteractionWiredExtra baseExtra = room.getRoomSpecialTypes().getExtra(decoded.baseDefinitionItemId);
        if (!matchesTarget(baseExtra, targetType)) {
            return null;
        }

        WiredVariableDefinitionInfo baseDefinition = createBaseDefinitionInfo(room, baseExtra, targetType);
        if (baseDefinition == null || !baseDefinition.hasValue()) {
            return null;
        }

        WiredExtraVariableLevelUpSystem levelSystem = getLevelSystem(room, baseExtra);
        if (levelSystem == null || !levelSystem.hasSubvariable(decoded.subvariableType)) {
            return null;
        }

        return new DerivedDefinition(
            syntheticItemId,
            decoded.baseDefinitionItemId,
            decoded.subvariableType,
            baseDefinition.getName() + "." + getSubvariableKey(decoded.subvariableType),
            baseDefinition,
            levelSystem
        );
    }

    public static Integer getDerivedValue(WiredExtraVariableLevelUpSystem levelSystem, int subvariableType, Integer baseValue) {
        if (levelSystem == null || baseValue == null) {
            return null;
        }

        LevelProgress progress = calculateProgress(levelSystem, baseValue);
        return switch (subvariableType) {
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL -> progress.currentLevel;
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP -> progress.currentXp;
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS -> progress.progressXp;
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT -> progress.progressPercent;
            case WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED -> progress.totalXpRequired;
            case WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING -> progress.xpRemaining;
            case WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX -> progress.isAtMax ? 1 : 0;
            case WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL -> progress.maxLevel;
            default -> null;
        };
    }

    public static List<LevelEntry> buildPreviewEntries(WiredExtraVariableLevelUpSystem levelSystem) {
        if (levelSystem == null) {
            return Collections.emptyList();
        }

        return buildThresholdEntries(levelSystem);
    }

    private static boolean matchesTarget(InteractionWiredExtra extra, int targetType) {
        if (extra == null) {
            return false;
        }

        return switch (targetType) {
            case TARGET_FURNI -> extra instanceof WiredExtraFurniVariable;
            case TARGET_ROOM -> (extra instanceof WiredExtraRoomVariable)
                || (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isRoomReference());
            default -> (extra instanceof WiredExtraUserVariable)
                || (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isUserReference());
        };
    }

    private static WiredVariableDefinitionInfo createBaseDefinitionInfo(Room room, InteractionWiredExtra extra, int targetType) {
        if (room == null || extra == null) {
            return null;
        }

        if (targetType == TARGET_FURNI && extra instanceof WiredExtraFurniVariable) {
            WiredExtraFurniVariable definition = (WiredExtraFurniVariable) extra;
            return new WiredVariableDefinitionInfo(
                definition.getId(),
                definition.getVariableName(),
                definition.hasValue(),
                definition.getAvailability(),
                WiredVariableTextConnectorSupport.isTextConnected(room, definition),
                false
            );
        }

        if (targetType == TARGET_USER) {
            if (extra instanceof WiredExtraUserVariable) {
                WiredExtraUserVariable definition = (WiredExtraUserVariable) extra;
                return new WiredVariableDefinitionInfo(
                    definition.getId(),
                    definition.getVariableName(),
                    definition.hasValue(),
                    definition.getAvailability(),
                    WiredVariableTextConnectorSupport.isTextConnected(room, definition),
                    false
                );
            }

            if (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isUserReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;
                return new WiredVariableDefinitionInfo(reference.getId(), reference.getVariableName(), reference.hasValue(), reference.getAvailability(), false, reference.isReadOnly());
            }
        }

        if (targetType == TARGET_ROOM) {
            if (extra instanceof WiredExtraRoomVariable) {
                WiredExtraRoomVariable definition = (WiredExtraRoomVariable) extra;
                return new WiredVariableDefinitionInfo(
                    definition.getId(),
                    definition.getVariableName(),
                    definition.hasValue(),
                    definition.getAvailability(),
                    WiredVariableTextConnectorSupport.isTextConnected(room, definition),
                    false
                );
            }

            if (extra instanceof WiredExtraVariableReference && ((WiredExtraVariableReference) extra).isRoomReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;
                return new WiredVariableDefinitionInfo(reference.getId(), reference.getVariableName(), reference.hasValue(), reference.getAvailability(), false, reference.isReadOnly());
            }
        }

        return null;
    }

    private static int createSyntheticItemId(int targetType, int baseDefinitionItemId, int subvariableType) {
        int offset = switch (targetType) {
            case TARGET_FURNI -> SYNTHETIC_FURNI_OFFSET;
            case TARGET_ROOM -> SYNTHETIC_ROOM_OFFSET;
            default -> SYNTHETIC_USER_OFFSET;
        };

        return offset + (baseDefinitionItemId * SYNTHETIC_STRIDE) + (subvariableType + 1);
    }

    private static DecodedSyntheticId decodeSyntheticId(int syntheticItemId) {
        if (syntheticItemId >= SYNTHETIC_ROOM_OFFSET) {
            return decodeSyntheticId(syntheticItemId, TARGET_ROOM, SYNTHETIC_ROOM_OFFSET);
        }

        if (syntheticItemId >= SYNTHETIC_FURNI_OFFSET) {
            return decodeSyntheticId(syntheticItemId, TARGET_FURNI, SYNTHETIC_FURNI_OFFSET);
        }

        if (syntheticItemId >= SYNTHETIC_USER_OFFSET) {
            return decodeSyntheticId(syntheticItemId, TARGET_USER, SYNTHETIC_USER_OFFSET);
        }

        return null;
    }

    private static DecodedSyntheticId decodeSyntheticId(int syntheticItemId, int targetType, int offset) {
        int localValue = syntheticItemId - offset;
        if (localValue < 0) {
            return null;
        }

        int encodedSubvariable = localValue % SYNTHETIC_STRIDE;
        int baseDefinitionItemId = localValue / SYNTHETIC_STRIDE;
        int subvariableType = encodedSubvariable - 1;

        if (baseDefinitionItemId <= 0 || subvariableType < 0 || subvariableType >= WiredExtraVariableLevelUpSystem.SUBVARIABLE_COUNT) {
            return null;
        }

        return new DecodedSyntheticId(targetType, baseDefinitionItemId, subvariableType);
    }

    private static String getSubvariableKey(int subvariableType) {
        return switch (subvariableType) {
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL -> "current_level";
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP -> "current_xp";
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS -> "level_progress";
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT -> "level_progress_percent";
            case WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED -> "total_xp_required";
            case WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING -> "xp_remaining";
            case WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX -> "is_at_max";
            case WiredExtraVariableLevelUpSystem.SUB_MAX_LEVEL -> "max_level";
            default -> "value";
        };
    }

    private static LevelProgress calculateProgress(WiredExtraVariableLevelUpSystem levelSystem, int rawBaseValue) {
        int currentXp = Math.max(0, rawBaseValue);
        List<LevelEntry> entries = buildThresholdEntries(levelSystem);

        if (entries.isEmpty()) {
            entries = new ArrayList<>();
            entries.add(new LevelEntry(1, 0));
        }

        int maxLevel = entries.get(entries.size() - 1).level;
        int currentLevel = 1;
        int currentThreshold = 0;
        int nextThreshold = 0;

        for (int index = 0; index < entries.size(); index++) {
            LevelEntry entry = entries.get(index);

            if (currentXp >= entry.requiredXp) {
                currentLevel = entry.level;
                currentThreshold = entry.requiredXp;
                nextThreshold = (index + 1 < entries.size()) ? entries.get(index + 1).requiredXp : entry.requiredXp;
                continue;
            }

            nextThreshold = entry.requiredXp;
            break;
        }

        boolean isAtMax = currentLevel >= maxLevel;

        if (isAtMax) {
            nextThreshold = currentThreshold;
        }

        int progressXp = Math.max(0, currentXp - currentThreshold);
        int progressPercent;

        if (isAtMax) {
            progressPercent = 100;
        } else {
            int delta = Math.max(0, nextThreshold - currentThreshold);
            progressPercent = (delta <= 0) ? 100 : Math.max(0, Math.min(100, (int) Math.floor((progressXp * 100D) / delta)));
        }

        int totalXpRequired = isAtMax ? currentThreshold : nextThreshold;
        int xpRemaining = Math.max(0, totalXpRequired - currentXp);

        return new LevelProgress(currentLevel, currentXp, progressXp, progressPercent, totalXpRequired, xpRemaining, isAtMax, maxLevel);
    }

    private static List<LevelEntry> buildThresholdEntries(WiredExtraVariableLevelUpSystem levelSystem) {
        return switch (levelSystem.getMode()) {
            case WiredExtraVariableLevelUpSystem.MODE_EXPONENTIAL -> buildExponentialEntries(levelSystem);
            case WiredExtraVariableLevelUpSystem.MODE_MANUAL -> buildManualEntries(levelSystem);
            default -> buildLinearEntries(levelSystem);
        };
    }

    private static List<LevelEntry> buildLinearEntries(WiredExtraVariableLevelUpSystem levelSystem) {
        List<LevelEntry> entries = new ArrayList<>();
        int maxLevel = Math.max(1, levelSystem.getMaxLevel());
        int stepSize = Math.max(0, levelSystem.getStepSize());

        for (int level = 1; level <= maxLevel; level++) {
            entries.add(new LevelEntry(level, clamp((long) (level - 1) * stepSize)));
        }

        return entries;
    }

    private static List<LevelEntry> buildExponentialEntries(WiredExtraVariableLevelUpSystem levelSystem) {
        List<LevelEntry> entries = new ArrayList<>();
        int maxLevel = Math.max(1, levelSystem.getMaxLevel());
        int currentIncrement = Math.max(0, levelSystem.getFirstLevelXp());
        int factor = Math.max(0, levelSystem.getIncreaseFactor());
        long threshold = 0L;

        entries.add(new LevelEntry(1, 0));

        for (int level = 2; level <= maxLevel; level++) {
            threshold += currentIncrement;
            entries.add(new LevelEntry(level, clamp(threshold)));
            currentIncrement = clamp(Math.round(currentIncrement * (100D + factor) / 100D));
        }

        return entries;
    }

    private static List<LevelEntry> buildManualEntries(WiredExtraVariableLevelUpSystem levelSystem) {
        LinkedHashMap<Integer, Integer> anchors = parseAnchors(levelSystem.getInterpolationText());
        if (!anchors.containsKey(1)) {
            anchors.put(1, 0);
        }

        List<Map.Entry<Integer, Integer>> sortedAnchors = new ArrayList<>(anchors.entrySet());
        sortedAnchors.sort(Map.Entry.comparingByKey());

        if (sortedAnchors.isEmpty()) {
            return Collections.singletonList(new LevelEntry(1, 0));
        }

        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();

        for (int index = 0; index < sortedAnchors.size(); index++) {
            Map.Entry<Integer, Integer> current = sortedAnchors.get(index);
            int currentLevel = Math.max(1, current.getKey());
            int currentXp = Math.max(0, current.getValue());

            result.put(currentLevel, currentXp);

            if (index + 1 >= sortedAnchors.size()) {
                continue;
            }

            Map.Entry<Integer, Integer> next = sortedAnchors.get(index + 1);
            int nextLevel = Math.max(currentLevel, next.getKey());
            int nextXp = Math.max(0, next.getValue());

            if (nextLevel <= currentLevel) {
                continue;
            }

            for (int level = currentLevel + 1; level < nextLevel; level++) {
                double ratio = (double) (level - currentLevel) / (double) (nextLevel - currentLevel);
                int interpolatedXp = clamp(Math.round(currentXp + ((nextXp - currentXp) * ratio)));
                result.put(level, interpolatedXp);
            }
        }

        List<LevelEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
            entries.add(new LevelEntry(entry.getKey(), entry.getValue()));
        }

        entries.sort(Comparator.comparingInt(levelEntry -> levelEntry.level));
        return entries;
    }

    private static LinkedHashMap<Integer, Integer> parseAnchors(String interpolationText) {
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        if (interpolationText == null || interpolationText.trim().isEmpty()) {
            return result;
        }

        for (String rawLine : interpolationText.split("\n")) {
            if (rawLine == null) {
                continue;
            }

            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex < 0) {
                separatorIndex = line.indexOf(',');
            }

            if (separatorIndex <= 0) {
                continue;
            }

            Integer level = parseInteger(line.substring(0, separatorIndex));
            Integer xp = parseInteger(line.substring(separatorIndex + 1));

            if (level == null || xp == null || level <= 0 || xp < 0) {
                continue;
            }

            result.put(level, xp);
        }

        return result;
    }

    private static Integer parseInteger(String value) {
        try {
            return (value == null || value.trim().isEmpty()) ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int clamp(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }

        return (int) value;
    }

    public static class DerivedDefinition {
        private final int syntheticItemId;
        private final int baseDefinitionItemId;
        private final int subvariableType;
        private final String variableName;
        private final WiredVariableDefinitionInfo baseDefinition;
        private final WiredExtraVariableLevelUpSystem levelSystem;

        public DerivedDefinition(int syntheticItemId, int baseDefinitionItemId, int subvariableType, String variableName, WiredVariableDefinitionInfo baseDefinition, WiredExtraVariableLevelUpSystem levelSystem) {
            this.syntheticItemId = syntheticItemId;
            this.baseDefinitionItemId = baseDefinitionItemId;
            this.subvariableType = subvariableType;
            this.variableName = variableName;
            this.baseDefinition = baseDefinition;
            this.levelSystem = levelSystem;
        }

        public int getBaseDefinitionItemId() {
            return this.baseDefinitionItemId;
        }

        public int getSubvariableType() {
            return this.subvariableType;
        }

        public WiredVariableDefinitionInfo getBaseDefinition() {
            return this.baseDefinition;
        }

        public WiredExtraVariableLevelUpSystem getLevelSystem() {
            return this.levelSystem;
        }
    }

    public static class LevelEntry {
        private final int level;
        private final int requiredXp;

        public LevelEntry(int level, int requiredXp) {
            this.level = level;
            this.requiredXp = requiredXp;
        }

        public int getLevel() {
            return this.level;
        }

        public int getRequiredXp() {
            return this.requiredXp;
        }
    }

    private static class LevelProgress {
        private final int currentLevel;
        private final int currentXp;
        private final int progressXp;
        private final int progressPercent;
        private final int totalXpRequired;
        private final int xpRemaining;
        private final boolean isAtMax;
        private final int maxLevel;

        private LevelProgress(int currentLevel, int currentXp, int progressXp, int progressPercent, int totalXpRequired, int xpRemaining, boolean isAtMax, int maxLevel) {
            this.currentLevel = currentLevel;
            this.currentXp = currentXp;
            this.progressXp = progressXp;
            this.progressPercent = progressPercent;
            this.totalXpRequired = totalXpRequired;
            this.xpRemaining = xpRemaining;
            this.isAtMax = isAtMax;
            this.maxLevel = maxLevel;
        }
    }

    private static class DecodedSyntheticId {
        private final int targetType;
        private final int baseDefinitionItemId;
        private final int subvariableType;

        private DecodedSyntheticId(int targetType, int baseDefinitionItemId, int subvariableType) {
            this.targetType = targetType;
            this.baseDefinitionItemId = baseDefinitionItemId;
            this.subvariableType = subvariableType;
        }
    }
}
