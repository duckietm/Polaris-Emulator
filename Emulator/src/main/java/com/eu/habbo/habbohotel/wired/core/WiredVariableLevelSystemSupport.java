package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
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

    // Ceiling on level/anchor counts, mirrored from WiredExtraVariableLevelUpSystem
    // (kept private in both to stay off the frozen wired plugin ABI). Bounds the
    // per-read threshold-table loops so a huge maxLevel/anchor can't hang the room.
    private static final int MAX_LEVEL = 10_000;
    private static final int SYNTHETIC_USER_OFFSET = 700_000_000;
    private static final int SYNTHETIC_FURNI_OFFSET = 800_000_000;
    private static final int SYNTHETIC_ROOM_OFFSET = 900_000_000;
    private static final int SYNTHETIC_STRIDE = 16;
    // Time-utilities sub-variables need ids up to 26, so they get their own ranges.
    private static final int TIME_USER_OFFSET = 1_500_000_000;
    private static final int TIME_FURNI_OFFSET = 1_700_000_000;
    private static final int TIME_ROOM_OFFSET = 1_900_000_000;
    private static final int TIME_STRIDE = 32;
    private static final int TIME_MAX_BASE_ID = 200_000_000 / TIME_STRIDE - 1;

    private WiredVariableLevelSystemSupport() {}

    public static WiredExtraVariableLevelUpSystem getLevelSystem(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraVariableLevelUpSystem) {
                return (WiredExtraVariableLevelUpSystem) extra;
            }
        }

        return importedAddon(room, definition, WiredExtraVariableLevelUpSystem.class);
    }

    /** A subvariable creator a variable reference imports from its source, when it has none of its own. */
    private static <T extends InteractionWiredExtra> T importedAddon(
            Room room, InteractionWiredExtra definition, Class<T> type) {
        return WiredVariableAddonSharing.importedAddon(
                room, definition, WiredVariableAddonSharing.SUBVARIABLE_CREATORS, type);
    }

    /**
     * Find the co-located derived-variable box (the level-up box OR any {@link WiredDerivedVariableBox},
     * e.g. a quest box) for a base variable definition. At most one box per definition; first wins.
     */
    public static InteractionWiredExtra getDerivedBox(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraTimeUtilities) {
                continue;
            }

            if (extra instanceof WiredExtraVariableLevelUpSystem || extra instanceof WiredDerivedVariableBox) {
                return extra;
            }
        }

        return importedAddon(room, definition, WiredExtraVariableLevelUpSystem.class);
    }

    /** The time-utilities box on a variable definition's tile, which can sit beside a level-up or quest box. */
    public static WiredExtraTimeUtilities getTimeUtilities(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : WiredExecutionOrderUtil.sort(extras)) {
            if (extra instanceof WiredExtraTimeUtilities timeUtilities) {
                return timeUtilities;
            }
        }

        return importedAddon(room, definition, WiredExtraTimeUtilities.class);
    }

    private static List<Integer> boxSubvariables(InteractionWiredExtra box) {
        if (box instanceof WiredExtraVariableLevelUpSystem lvl) return lvl.getSelectedSubvariables();
        if (box instanceof WiredDerivedVariableBox derived) return derived.getSelectedSubvariables();
        return Collections.emptyList();
    }

    private static boolean boxHasSubvariable(InteractionWiredExtra box, int subType) {
        if (box instanceof WiredExtraVariableLevelUpSystem lvl) return lvl.hasSubvariable(subType);
        if (box instanceof WiredDerivedVariableBox derived) return derived.hasSubvariable(subType);
        return false;
    }

    private static String boxSubvariableKey(InteractionWiredExtra box, int subType) {
        if (box instanceof WiredDerivedVariableBox derived) return derived.subvariableKey(subType);
        return getSubvariableKey(subType);
    }

    public static List<WiredVariableDefinitionInfo> getDerivedDefinitions(
            Room room,
            int targetType,
            InteractionWiredExtra definitionExtra,
            WiredVariableDefinitionInfo baseDefinition) {
        if (room == null || definitionExtra == null || baseDefinition == null) {
            return Collections.emptyList();
        }

        List<WiredVariableDefinitionInfo> result = new ArrayList<>();
        InteractionWiredExtra box = baseDefinition.hasValue() ? getDerivedBox(room, definitionExtra) : null;

        if (box != null) {
            for (int subvariableType : boxSubvariables(box)) {
                result.add(createDerivedInfo(
                        createSyntheticItemId(targetType, baseDefinition.getItemId(), subvariableType),
                        baseDefinition,
                        boxSubvariableKey(box, subvariableType),
                        !isWritableLevelPart(box, subvariableType, baseDefinition),
                        !isPresencePart(box, subvariableType)));
            }
        }

        WiredExtraTimeUtilities timeUtilities = getTimeUtilities(room, definitionExtra);
        if (timeUtilities != null
                && (baseDefinition.hasValue() || timeUtilities.readsTimestamps())
                && baseDefinition.getItemId() <= TIME_MAX_BASE_ID) {
            for (int subvariableType : timeUtilities.getListedSubvariables()) {
                result.add(createDerivedInfo(
                        createTimeItemId(targetType, baseDefinition.getItemId(), subvariableType),
                        baseDefinition,
                        timeUtilities.subvariableKey(subvariableType),
                        !isWritableTimePart(timeUtilities, subvariableType, baseDefinition),
                        true));
            }
        }

        result.sort(Comparator.comparing(WiredVariableDefinitionInfo::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(WiredVariableDefinitionInfo::getItemId));
        return result;
    }

    private static WiredVariableDefinitionInfo createDerivedInfo(
            int syntheticItemId,
            WiredVariableDefinitionInfo baseDefinition,
            String key,
            boolean readOnly,
            boolean hasValue) {
        return new WiredVariableDefinitionInfo(
                syntheticItemId,
                baseDefinition.getName() + "." + key,
                hasValue,
                baseDefinition.getAvailability(),
                false,
                readOnly);
    }

    /**
     * Whether a part only exists or not, without a value: the level-up {@code is_maxed}, which Habbo shows only at
     * the last level.
     */
    public static boolean isPresencePart(InteractionWiredExtra box, int subvariableType) {
        return box instanceof WiredExtraVariableLevelUpSystem
                && subvariableType == WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX;
    }

    /** As {@link #isPresencePart(InteractionWiredExtra, int)}, for a resolved part. */
    public static boolean isPresencePart(DerivedDefinition derived) {
        return derived != null && isPresencePart(derived.levelSystem, derived.subvariableType);
    }

    /** The level-up {@code current_xp} of a writable variable can be written, as in Habbo; it writes the variable. */
    private static boolean isWritableLevelPart(
            InteractionWiredExtra box, int subvariableType, WiredVariableDefinitionInfo baseDefinition) {
        return box instanceof WiredExtraVariableLevelUpSystem
                && subvariableType == WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP
                && baseDefinition.hasValue()
                && !baseDefinition.isReadOnly();
    }

    private static boolean isWritableTimePart(
            WiredExtraTimeUtilities timeUtilities, int subvariableType, WiredVariableDefinitionInfo baseDefinition) {
        return timeUtilities.isWritable(subvariableType) && baseDefinition.hasValue() && !baseDefinition.isReadOnly();
    }

    /**
     * The base variable's new value after writing {@code partValue} into a writable derived part (a value-mode
     * Time Utilities part), or null when the part cannot be written or the result does not fit.
     */
    public static Integer writeDerivedValue(Room room, DerivedDefinition derived, Integer baseValue, int partValue) {
        if (derived == null || baseValue == null || !derived.isWritable()) {
            return null;
        }

        // current_xp writes the variable itself, at most the last level's XP (Habbo: 250 on a 0/100/200 system wrote
        // 200).
        if (derived.levelSystem instanceof WiredExtraVariableLevelUpSystem levelSystem) {
            List<LevelEntry> entries = buildThresholdEntries(levelSystem);
            int lastThreshold = entries.isEmpty() ? 0 : entries.get(entries.size() - 1).requiredXp;
            return Math.max(0, Math.min(partValue, lastThreshold));
        }

        if (!(derived.levelSystem instanceof WiredExtraTimeUtilities timeUtilities)) {
            return null;
        }

        return timeUtilities.write(derived.subvariableType, baseValue, partValue, WiredRoomTime.zoneFor(room));
    }

    public static WiredVariableDefinitionInfo getDerivedDefinitionInfo(Room room, int targetType, int syntheticItemId) {
        DerivedDefinition derived = resolveDerivedDefinition(room, targetType, syntheticItemId);

        if (derived == null) {
            return null;
        }

        return new WiredVariableDefinitionInfo(
                derived.syntheticItemId,
                derived.variableName,
                !isPresencePart(derived),
                derived.baseDefinition.getAvailability(),
                false,
                !derived.isWritable());
    }

    public static DerivedDefinition resolveDerivedDefinition(Room room, int targetType, int syntheticItemId) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        DerivedDefinition timeDefinition = resolveTimeDefinition(room, targetType, syntheticItemId);
        if (timeDefinition != null) {
            return timeDefinition;
        }

        DecodedSyntheticId decoded = decodeSyntheticId(syntheticItemId);
        if (decoded == null || decoded.targetType != targetType) {
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

        InteractionWiredExtra box = getDerivedBox(room, baseExtra);
        if (box == null || !boxHasSubvariable(box, decoded.subvariableType)) {
            return null;
        }

        return new DerivedDefinition(
                syntheticItemId,
                decoded.baseDefinitionItemId,
                decoded.subvariableType,
                baseDefinition.getName() + "." + boxSubvariableKey(box, decoded.subvariableType),
                baseDefinition,
                box);
    }

    private static DerivedDefinition resolveTimeDefinition(Room room, int targetType, int syntheticItemId) {
        int offset = timeOffset(targetType);
        int localValue = syntheticItemId - offset;
        if (syntheticItemId < offset || localValue / TIME_STRIDE > TIME_MAX_BASE_ID) {
            return null;
        }

        int baseDefinitionItemId = localValue / TIME_STRIDE;
        int subvariableType = localValue % TIME_STRIDE;
        if (baseDefinitionItemId <= 0) {
            return null;
        }

        InteractionWiredExtra baseExtra = room.getRoomSpecialTypes().getExtra(baseDefinitionItemId);
        if (!matchesTarget(baseExtra, targetType)) {
            return null;
        }

        WiredExtraTimeUtilities timeUtilities = getTimeUtilities(room, baseExtra);
        if (timeUtilities == null || !timeUtilities.listsSubvariable(subvariableType)) {
            return null;
        }

        WiredVariableDefinitionInfo baseDefinition = createBaseDefinitionInfo(room, baseExtra, targetType);
        if (baseDefinition == null || !(baseDefinition.hasValue() || timeUtilities.readsTimestamps())) {
            return null;
        }

        return new DerivedDefinition(
                syntheticItemId,
                baseDefinitionItemId,
                subvariableType,
                baseDefinition.getName() + "." + timeUtilities.subvariableKey(subvariableType),
                baseDefinition,
                timeUtilities);
    }

    /**
     * A derived value from the base variable's value and timestamps (unix seconds). Time-utilities boxes read
     * calendar parts in the room's wired timezone; other boxes only use the value.
     */
    public static Integer getDerivedValue(
            Room room,
            InteractionWiredExtra box,
            int subvariableType,
            Integer baseValue,
            int createdAt,
            int updatedAt) {
        if (box instanceof WiredExtraTimeUtilities timeUtilities) {
            return timeUtilities.derive(subvariableType, baseValue, createdAt, updatedAt, WiredRoomTime.zoneFor(room));
        }

        return getDerivedValue(box, subvariableType, baseValue);
    }

    public static Integer getDerivedValue(InteractionWiredExtra box, int subvariableType, Integer baseValue) {
        if (box == null || baseValue == null) {
            return null;
        }

        if (box instanceof WiredDerivedVariableBox derived) {
            return derived.derive(subvariableType, baseValue);
        }

        if (!(box instanceof WiredExtraVariableLevelUpSystem levelSystem)) {
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
            // A presence part: it exists (as 1) only at the last level.
            case WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX -> progress.isAtMax ? 1 : null;
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
            case TARGET_ROOM ->
                (extra instanceof WiredExtraRoomVariable)
                        || (extra instanceof WiredExtraVariableReference
                                && ((WiredExtraVariableReference) extra).isRoomReference());
            default ->
                (extra instanceof WiredExtraUserVariable)
                        || (extra instanceof WiredExtraVariableReference
                                && ((WiredExtraVariableReference) extra).isUserReference());
        };
    }

    private static WiredVariableDefinitionInfo createBaseDefinitionInfo(
            Room room, InteractionWiredExtra extra, int targetType) {
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
                    false);
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
                        false);
            }

            if (extra instanceof WiredExtraVariableReference
                    && ((WiredExtraVariableReference) extra).isUserReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;
                return new WiredVariableDefinitionInfo(
                        reference.getId(),
                        reference.getVariableName(),
                        reference.hasValue(),
                        reference.getAvailability(),
                        WiredVariableTextConnectorSupport.isTextConnected(room, reference),
                        reference.isReadOnly());
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
                        false);
            }

            if (extra instanceof WiredExtraVariableReference
                    && ((WiredExtraVariableReference) extra).isRoomReference()) {
                WiredExtraVariableReference reference = (WiredExtraVariableReference) extra;
                return new WiredVariableDefinitionInfo(
                        reference.getId(),
                        reference.getVariableName(),
                        reference.hasValue(),
                        reference.getAvailability(),
                        WiredVariableTextConnectorSupport.isTextConnected(room, reference),
                        reference.isReadOnly());
            }
        }

        return null;
    }

    private static int createSyntheticItemId(int targetType, int baseDefinitionItemId, int subvariableType) {
        int offset =
                switch (targetType) {
                    case TARGET_FURNI -> SYNTHETIC_FURNI_OFFSET;
                    case TARGET_ROOM -> SYNTHETIC_ROOM_OFFSET;
                    default -> SYNTHETIC_USER_OFFSET;
                };

        return offset + (baseDefinitionItemId * SYNTHETIC_STRIDE) + (subvariableType + 1);
    }

    private static int timeOffset(int targetType) {
        return switch (targetType) {
            case TARGET_FURNI -> TIME_FURNI_OFFSET;
            case TARGET_ROOM -> TIME_ROOM_OFFSET;
            default -> TIME_USER_OFFSET;
        };
    }

    private static int createTimeItemId(int targetType, int baseDefinitionItemId, int subvariableType) {
        return timeOffset(targetType) + (baseDefinitionItemId * TIME_STRIDE) + subvariableType;
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

        if (baseDefinitionItemId <= 0
                || subvariableType < 0
                || subvariableType >= WiredExtraVariableLevelUpSystem.SUBVARIABLE_COUNT) {
            return null;
        }

        return new DecodedSyntheticId(targetType, baseDefinitionItemId, subvariableType);
    }

    private static String getSubvariableKey(int subvariableType) {
        return switch (subvariableType) {
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL -> "current_level";
            case WiredExtraVariableLevelUpSystem.SUB_CURRENT_XP -> "current_xp";
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS -> "progress";
            case WiredExtraVariableLevelUpSystem.SUB_LEVEL_PROGRESS_PERCENT -> "progress_percentage";
            case WiredExtraVariableLevelUpSystem.SUB_TOTAL_XP_REQUIRED -> "xp_required";
            case WiredExtraVariableLevelUpSystem.SUB_XP_REMAINING -> "xp_remaining";
            case WiredExtraVariableLevelUpSystem.SUB_IS_AT_MAX -> "is_maxed";
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

        // As in Habbo, a variable at the last level reads that level's XP as current_xp, and 0 for progress,
        // progress %, XP required and XP remaining (linear 100 over three levels: 1200 XP reads 200, 0, 0, 0, 0).
        if (isAtMax) {
            return new LevelProgress(currentLevel, currentThreshold, 0, 0, 0, 0, true, maxLevel);
        }

        int progressXp = Math.max(0, currentXp - currentThreshold);
        int delta = Math.max(0, nextThreshold - currentThreshold);
        int progressPercent =
                (delta <= 0) ? 100 : Math.max(0, Math.min(100, (int) Math.floor((progressXp * 100D) / delta)));
        int totalXpRequired = nextThreshold;
        int xpRemaining = Math.max(0, totalXpRequired - currentXp);

        return new LevelProgress(
                currentLevel, currentXp, progressXp, progressPercent, totalXpRequired, xpRemaining, false, maxLevel);
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

            // Bound the anchor level: the interpolation loop below runs
            // [currentLevel+1, nextLevel), so an unbounded manual anchor level
            // (e.g. "999999999=1") pegs the room thread on every read.
            if (level == null || xp == null || level <= 0 || xp < 0 || level > MAX_LEVEL) {
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
        private final InteractionWiredExtra levelSystem;

        public DerivedDefinition(
                int syntheticItemId,
                int baseDefinitionItemId,
                int subvariableType,
                String variableName,
                WiredVariableDefinitionInfo baseDefinition,
                InteractionWiredExtra levelSystem) {
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

        public InteractionWiredExtra getLevelSystem() {
            return this.levelSystem;
        }

        /**
         * Whether the part can be written: value-mode Time Utilities parts and the level-up {@code current_xp} of a
         * writable variable.
         */
        public boolean isWritable() {
            if (isWritableLevelPart(this.levelSystem, this.subvariableType, this.baseDefinition)) {
                return true;
            }
            return this.levelSystem instanceof WiredExtraTimeUtilities timeUtilities
                    && isWritableTimePart(timeUtilities, this.subvariableType, this.baseDefinition);
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

        private LevelProgress(
                int currentLevel,
                int currentXp,
                int progressXp,
                int progressPercent,
                int totalXpRequired,
                int xpRemaining,
                boolean isAtMax,
                int maxLevel) {
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
