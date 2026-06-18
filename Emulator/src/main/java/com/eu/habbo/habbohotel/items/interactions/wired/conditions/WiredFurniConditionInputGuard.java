package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WiredFurniConditionInputGuard {
    private WiredFurniConditionInputGuard() {
    }

    public static int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    public static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static int selectedOrNormalizedFurniSource(int value, boolean hasSelectedItems) {
        int source = normalizeFurniSource(value);
        return (hasSelectedItems && source == WiredSourceUtil.SOURCE_TRIGGER)
                ? WiredSourceUtil.SOURCE_SELECTED
                : source;
    }

    public static List<Integer> sanitizeItemIds(Collection<Integer> itemIds, int maxCount) {
        List<Integer> result = new ArrayList<>();
        if (itemIds == null || maxCount < 1) {
            return result;
        }

        for (Integer itemId : itemIds) {
            if (itemId == null || itemId < 1 || result.size() >= maxCount) {
                continue;
            }

            result.add(itemId);
        }

        return result;
    }

    public static List<Integer> parseLegacyItemIds(String value, int maxCount) {
        List<Integer> result = new ArrayList<>();
        if (value == null || value.isBlank() || maxCount < 1) {
            return result;
        }

        for (String part : value.split("[;,:\\t]")) {
            if (result.size() >= maxCount) {
                break;
            }

            try {
                int id = Integer.parseInt(part.trim());
                if (id > 0) {
                    result.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy item ids.
            }
        }

        return result;
    }
}
