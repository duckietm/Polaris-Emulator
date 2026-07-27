package com.eu.habbo.habbohotel.wired.arrays;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared, side-effect-free editor validation and normalization for array boxes. */
public final class WiredArrayEditorSupport {
    private WiredArrayEditorSupport() {}

    public static WiredArrayAddress normalizeAddress(WiredArrayAddress value) {
        WiredArrayAddress source = value == null ? new WiredArrayAddress() : value;
        WiredArrayAddress result = new WiredArrayAddress();
        result.mode =
                source.mode == WiredArrayAddress.VARIABLE ? WiredArrayAddress.VARIABLE : WiredArrayAddress.CONSTANT;
        result.value = source.value;
        result.variableType =
                WiredArrayVariableType.fromCode(source.variableType).code();
        result.variableItemId = source.variableItemId;
        result.variableSource = WiredArrayRuntimeSupport.normalizeSource(
                WiredArrayVariableType.fromCode(result.variableType), source.variableSource);
        result.capturePath = source.capturePath == null ? "" : source.capturePath.trim();
        result.fieldId = source.fieldId;
        return result;
    }

    public static WiredArrayReference normalizeReference(WiredArrayReference value) {
        WiredArrayReference source = value == null ? new WiredArrayReference() : value;
        WiredArrayReference result = new WiredArrayReference();
        result.mode = source.mode == WiredArrayReference.VARIABLE
                ? WiredArrayReference.VARIABLE
                : WiredArrayReference.CONSTANT;
        result.value = source.value == null ? "0" : source.value.trim();
        result.variableType =
                WiredArrayVariableType.fromCode(source.variableType).code();
        result.variableItemId = source.variableItemId;
        result.variableSource = WiredArrayRuntimeSupport.normalizeSource(
                WiredArrayVariableType.fromCode(result.variableType), source.variableSource);
        result.capturePath = source.capturePath == null ? "" : source.capturePath.trim();
        result.address = normalizeAddress(source.address);
        return result;
    }

    public static List<WiredArrayCriterion> normalizeCriteria(List<WiredArrayCriterion> values) {
        List<WiredArrayCriterion> result = new ArrayList<>();
        if (values == null) return result;
        for (WiredArrayCriterion source : values) {
            if (result.size() >= WiredArrayDefinition.MAX_FIELDS) break;
            if (source == null) continue;
            WiredArrayCriterion criterion = new WiredArrayCriterion();
            criterion.fieldId = source.fieldId;
            criterion.comparison = normalizeComparison(source.comparison);
            criterion.reference = normalizeReference(source.reference);
            result.add(criterion);
        }
        return result;
    }

    public static Map<Integer, WiredArrayReference> normalizeInputs(Map<Integer, WiredArrayReference> values) {
        Map<Integer, WiredArrayReference> result = new LinkedHashMap<>();
        if (values == null) return result;
        for (Map.Entry<Integer, WiredArrayReference> entry : values.entrySet()) {
            if (result.size() >= WiredArrayDefinition.MAX_FIELDS) break;
            if (entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null) continue;
            result.put(entry.getKey(), normalizeReference(entry.getValue()));
        }
        return result;
    }

    public static boolean validRawAddress(WiredArrayAddress value) {
        return value != null && (value.mode == WiredArrayAddress.CONSTANT || value.mode == WiredArrayAddress.VARIABLE);
    }

    public static boolean validRawReference(WiredArrayReference value) {
        return value != null
                && (value.mode == WiredArrayReference.CONSTANT || value.mode == WiredArrayReference.VARIABLE)
                && (value.mode != WiredArrayReference.CONSTANT || value.value != null)
                && (value.mode != WiredArrayReference.VARIABLE
                        || value.capturePath != null && !value.capturePath.isBlank()
                        || value.variableItemId > 0);
    }

    public static boolean validRawCriteria(List<WiredArrayCriterion> values) {
        if (values == null || values.isEmpty() || values.size() > WiredArrayDefinition.MAX_FIELDS) return false;
        for (WiredArrayCriterion criterion : values) {
            if (criterion == null
                    || !validComparison(criterion.comparison)
                    || !validRawReference(criterion.reference)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidAddress(
            WiredArrayAddress address, WiredArrayVariableDefinition definition, Room room) {
        if (!validRawAddress(address) || definition == null || definition.getArrayDefinition() == null) return false;
        if (address.mode == WiredArrayAddress.CONSTANT) {
            return address.value >= 0
                    && address.value < definition.getArrayDefinition().getMaxEntries();
        }
        return isValidScalarReference(address.variableType, address.variableItemId, address.capturePath, room);
    }

    public static boolean isValidReference(WiredArrayReference reference, Room room) {
        if (!validRawReference(reference)) return false;
        if (reference.mode == WiredArrayReference.CONSTANT) {
            try {
                Long.parseLong(reference.value.trim());
                return true;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        if (reference.capturePath != null && !reference.capturePath.isBlank()) {
            return WiredArrayRuntimeSupport.isValidCapturePath(reference.capturePath);
        }
        WiredArrayVariableDefinition definition =
                WiredArrayDefinitionSupport.resolve(room, reference.variableType, reference.variableItemId);
        if (definition == null) return false;
        if (!definition.isArray()) return definition.hasValue();
        return definition.getArrayDefinition() != null
                && definition.getArrayDefinition().getField(reference.address.fieldId) != null
                && isValidAddress(reference.address, definition, room);
    }

    public static boolean isValidScalarReference(int type, int itemId, String capturePath, Room room) {
        if (capturePath != null && !capturePath.isBlank()) {
            return WiredArrayRuntimeSupport.isValidCapturePath(capturePath);
        }
        WiredArrayVariableDefinition definition = WiredArrayDefinitionSupport.resolve(room, type, itemId);
        return definition != null && !definition.isArray() && definition.hasValue();
    }

    public static List<HabboItem> parseItems(int[] ids, Room room) throws WiredSaveException {
        List<HabboItem> result = new ArrayList<>();
        if (ids == null) return result;
        int limit = Math.max(0, Math.min(WiredInputGuard.MAX_ABSOLUTE_FURNI_IDS, WiredManager.MAXIMUM_FURNI_SELECTION));
        if (ids.length > limit) throw new WiredSaveException("Too many furni selected");
        for (int id : ids) {
            HabboItem item = room.getHabboItem(id);
            if (item == null) throw new WiredSaveException("Selected furni is no longer in the room");
            if (!result.contains(item)) result.add(item);
        }
        return result;
    }

    public static void loadItems(Collection<HabboItem> destination, List<Integer> ids, Room room) {
        if (destination == null || ids == null || room == null) return;
        int limit = Math.min(ids.size(), WiredManager.MAXIMUM_FURNI_SELECTION);
        for (int index = 0; index < limit; index++) {
            Integer id = ids.get(index);
            HabboItem item = id == null ? null : room.getHabboItem(id);
            if (item != null && !destination.contains(item)) destination.add(item);
        }
    }

    public static boolean requiresTrigger(WiredArrayAddress address) {
        return address != null
                && address.mode == WiredArrayAddress.VARIABLE
                && address.variableType == WiredArrayVariableType.USER.code()
                && address.variableSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static boolean requiresTrigger(WiredArrayReference reference) {
        return reference != null
                && reference.mode == WiredArrayReference.VARIABLE
                && reference.variableType == WiredArrayVariableType.USER.code()
                && reference.variableSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static List<ResolvedCriterion> resolveCriteria(
            WiredContext ctx,
            Collection<HabboItem> selectedItems,
            List<WiredArrayCriterion> criteria,
            WiredArrayRuntimeSupport.Owner owner) {
        List<ResolvedCriterion> result = new ArrayList<>();
        if (criteria == null) return result;
        for (WiredArrayCriterion criterion : criteria) {
            if (criterion == null || criterion.reference == null || !validComparison(criterion.comparison)) return null;
            Long reference = WiredArrayRuntimeSupport.resolveReference(ctx, selectedItems, criterion.reference, owner);
            if (reference == null) return null;
            result.add(new ResolvedCriterion(criterion.fieldId, criterion.comparison, reference));
        }
        return result;
    }

    public static boolean matchesEntry(WiredArrayEntry entry, List<ResolvedCriterion> criteria, boolean any) {
        for (ResolvedCriterion criterion : criteria) {
            boolean matches;
            try {
                matches = WiredArrayRuntimeSupport.compare(
                        entry.getValue(criterion.fieldId()), criterion.reference(), criterion.comparison());
            } catch (IllegalArgumentException exception) {
                return false;
            }
            if (any && matches) return true;
            if (!any && !matches) return false;
        }
        return !any;
    }

    public static boolean validComparison(int value) {
        return value >= 0 && value <= 5;
    }

    public static int normalizeComparison(int value) {
        return validComparison(value) ? value : 2;
    }

    public record ResolvedCriterion(int fieldId, int comparison, long reference) {}
}
