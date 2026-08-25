package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class WiredExecutionOrderUtil {
    private static final Comparator<HabboItem> WIRED_STACK_ORDER = Comparator
            .comparingDouble(HabboItem::getZ)
            .thenComparingInt(HabboItem::getId);

    private WiredExecutionOrderUtil() {
    }

    public static <T extends HabboItem> List<T> sort(Collection<T> items) {
        List<T> sorted = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            return sorted;
        }

        for (T item : items) {
            if (item != null) {
                sorted.add(item);
            }
        }

        sorted.sort(WIRED_STACK_ORDER);
        return sorted;
    }
}
