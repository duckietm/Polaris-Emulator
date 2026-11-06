package polaris.hotel.wired.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import polaris.hotel.users.UserItem;

public final class WiredExecutionOrderUtil {
    private static final Comparator<UserItem> WIRED_STACK_ORDER =
            Comparator.comparingDouble(UserItem::getZ).thenComparingInt(UserItem::getId);

    private WiredExecutionOrderUtil() {}

    public static <T extends UserItem> List<T> sort(Collection<T> items) {
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
