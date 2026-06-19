package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurniByVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUser;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUsersByVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class WiredSelectionFilterSupport {
    private static final ThreadLocal<Integer> FILTER_DEPTH = ThreadLocal.withInitial(() -> 0);

    private WiredSelectionFilterSupport() {
    }

    static void applySelectorFilters(Room room, HabboItem triggerItem, WiredContext ctx) {
        if (ctx == null) {
            return;
        }

        if (ctx.targets().isItemsModifiedBySelector()) {
            ctx.targets().setItems(filterItems(room, triggerItem, ctx, ctx.targets().items()));
        }

        if (ctx.targets().isUsersModifiedBySelector()) {
            ctx.targets().setUsers(filterUsers(room, triggerItem, ctx, ctx.targets().users()));
        }
    }

    static List<HabboItem> filterItems(Room room, HabboItem triggerItem, WiredContext ctx, Iterable<HabboItem> values) {
        List<HabboItem> items = toItemList(values);

        if (items.isEmpty() || shouldBypass(room, triggerItem, ctx)) {
            return items;
        }

        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(triggerItem.getX(), triggerItem.getY());
        if (extras == null || extras.isEmpty()) {
            return items;
        }

        int furniLimit = Integer.MAX_VALUE;
        List<WiredExtraFilterFurniByVariable> variableFilters = new ArrayList<>();

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraFilterFurni) {
                furniLimit = Math.min(furniLimit, ((WiredExtraFilterFurni) extra).getAmount());
            } else if (extra instanceof WiredExtraFilterFurniByVariable) {
                variableFilters.add((WiredExtraFilterFurniByVariable) extra);
            }
        }

        if (furniLimit == Integer.MAX_VALUE && variableFilters.isEmpty()) {
            return items;
        }

        variableFilters.sort((left, right) -> Integer.compare(left.getId(), right.getId()));

        try (FilterScope ignored = enterScope()) {
            Iterable<HabboItem> filteredItems = items;

            for (WiredExtraFilterFurniByVariable extra : variableFilters) {
                filteredItems = extra.filterItems(room, ctx, filteredItems);
            }

            if (furniLimit != Integer.MAX_VALUE) {
                filteredItems = limitIterable(filteredItems, furniLimit);
            }

            return toItemList(filteredItems);
        }
    }

    static List<RoomUnit> filterUsers(Room room, HabboItem triggerItem, WiredContext ctx, Iterable<RoomUnit> values) {
        List<RoomUnit> users = toUserList(values);

        if (users.isEmpty() || shouldBypass(room, triggerItem, ctx)) {
            return users;
        }

        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(triggerItem.getX(), triggerItem.getY());
        if (extras == null || extras.isEmpty()) {
            return users;
        }

        int userLimit = Integer.MAX_VALUE;
        List<WiredExtraFilterUsersByVariable> variableFilters = new ArrayList<>();

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraFilterUser) {
                userLimit = Math.min(userLimit, ((WiredExtraFilterUser) extra).getAmount());
            } else if (extra instanceof WiredExtraFilterUsersByVariable) {
                variableFilters.add((WiredExtraFilterUsersByVariable) extra);
            }
        }

        if (userLimit == Integer.MAX_VALUE && variableFilters.isEmpty()) {
            return users;
        }

        variableFilters.sort((left, right) -> Integer.compare(left.getId(), right.getId()));

        try (FilterScope ignored = enterScope()) {
            Iterable<RoomUnit> filteredUsers = users;

            for (WiredExtraFilterUsersByVariable extra : variableFilters) {
                filteredUsers = extra.filterUsers(room, ctx, filteredUsers);
            }

            if (userLimit != Integer.MAX_VALUE) {
                filteredUsers = limitIterable(filteredUsers, userLimit);
            }

            return toUserList(filteredUsers);
        }
    }

    private static boolean shouldBypass(Room room, HabboItem triggerItem, WiredContext ctx) {
        return room == null
                || triggerItem == null
                || ctx == null
                || room.getRoomSpecialTypes() == null
                || FILTER_DEPTH.get() > 0;
    }

    private static FilterScope enterScope() {
        FILTER_DEPTH.set(FILTER_DEPTH.get() + 1);
        return new FilterScope();
    }

    private static <T> List<T> limitIterable(Iterable<T> values, int limit) {
        List<T> result = new ArrayList<>();

        if (values == null || limit <= 0) {
            return result;
        }

        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }

        if (result.size() <= limit) {
            return result;
        }

        Collections.shuffle(result, Emulator.getRandom());
        return new ArrayList<>(result.subList(0, limit));
    }

    private static List<HabboItem> toItemList(Iterable<HabboItem> values) {
        List<HabboItem> result = new ArrayList<>();

        if (values == null) {
            return result;
        }

        for (HabboItem item : values) {
            if (item != null) {
                result.add(item);
            }
        }

        return result;
    }

    private static List<RoomUnit> toUserList(Iterable<RoomUnit> values) {
        List<RoomUnit> result = new ArrayList<>();

        if (values == null) {
            return result;
        }

        for (RoomUnit unit : values) {
            if (unit != null) {
                result.add(unit);
            }
        }

        return result;
    }

    private static final class FilterScope implements AutoCloseable {
        @Override
        public void close() {
            int depth = FILTER_DEPTH.get() - 1;

            if (depth <= 0) {
                FILTER_DEPTH.remove();
            } else {
                FILTER_DEPTH.set(depth);
            }
        }
    }
}
