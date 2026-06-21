package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
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

public final class WiredTriggerSourceUtil {
    private WiredTriggerSourceUtil() {
    }

    public static List<HabboItem> resolveItems(InteractionWiredTrigger trigger,
                                               WiredEvent event,
                                               int sourceType,
                                               Collection<HabboItem> selectedItems) {
        switch (sourceType) {
            case WiredSourceUtil.SOURCE_TRIGGER:
                return event.getSourceItem().map(Collections::singletonList).orElse(Collections.emptyList());
            case WiredSourceUtil.SOURCE_SELECTED:
                return (selectedItems != null) ? new ArrayList<>(selectedItems) : Collections.emptyList();
            case WiredSourceUtil.SOURCE_SELECTOR:
                return resolveSelectorItems(trigger, event);
            case WiredSourceUtil.SOURCE_SIGNAL:
                if (event.getType() == WiredEvent.Type.SIGNAL_RECEIVED) {
                    return event.getSourceItem().map(Collections::singletonList).orElse(Collections.emptyList());
                }
                return Collections.emptyList();
            default:
                return event.getSourceItem().map(Collections::singletonList).orElse(Collections.emptyList());
        }
    }

    public static List<RoomUnit> resolveUsers(InteractionWiredTrigger trigger,
                                              WiredEvent event,
                                              int sourceType,
                                              Collection<RoomUnit> selectedUsers) {
        switch (sourceType) {
            case WiredSourceUtil.SOURCE_TRIGGER:
                return event.getActor().map(Collections::singletonList).orElse(Collections.emptyList());
            case WiredSourceUtil.SOURCE_SELECTED:
                return (selectedUsers != null) ? new ArrayList<>(selectedUsers) : Collections.emptyList();
            case WiredSourceUtil.SOURCE_SELECTOR:
                return resolveSelectorUsers(trigger, event);
            case WiredSourceUtil.SOURCE_SIGNAL:
                if (event.getType() == WiredEvent.Type.SIGNAL_RECEIVED) {
                    return event.getActor().map(Collections::singletonList).orElse(Collections.emptyList());
                }
                return Collections.emptyList();
            default:
                return event.getActor().map(Collections::singletonList).orElse(Collections.emptyList());
        }
    }

    public static boolean containsItemOrTile(Room room, Collection<HabboItem> items, HabboItem sourceItem) {
        if (room == null || items == null || items.isEmpty() || sourceItem == null) {
            return false;
        }

        if (items.contains(sourceItem)) {
            return true;
        }

        for (HabboItem item : room.getItemsAt(sourceItem.getX(), sourceItem.getY())) {
            if (items.contains(item)) {
                return true;
            }
        }

        return false;
    }

    public static boolean containsUser(Collection<RoomUnit> users, RoomUnit target) {
        if (users == null || users.isEmpty() || target == null) {
            return false;
        }

        for (RoomUnit user : users) {
            if (user != null && user.getId() == target.getId()) {
                return true;
            }
        }

        return false;
    }

    private static List<HabboItem> resolveSelectorItems(InteractionWiredTrigger trigger, WiredEvent event) {
        WiredContext ctx = executeSelectors(trigger, event);

        if (ctx == null || !ctx.targets().isItemsModifiedBySelector()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(ctx.targets().items());
    }

    private static List<RoomUnit> resolveSelectorUsers(InteractionWiredTrigger trigger, WiredEvent event) {
        WiredContext ctx = executeSelectors(trigger, event);

        if (ctx == null || !ctx.targets().isUsersModifiedBySelector()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(ctx.targets().users());
    }

    private static WiredContext executeSelectors(InteractionWiredTrigger trigger, WiredEvent event) {
        if (trigger == null || event == null) {
            return null;
        }

        Room room = event.getRoom();
        if (room == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        WiredContext ctx = new WiredContext(event, trigger, DefaultWiredServices.getInstance(), new WiredState(100));

        for (InteractionWiredEffect effect : getOrderedSelectorEffects(room, trigger)) {
            if (effect.requiresActor() && !ctx.hasActor()) {
                continue;
            }

            try {
                ctx.state().step();
                effect.execute(ctx);
            } catch (Exception ignored) {
            }
        }

        applySelectionFilterExtras(room, trigger, ctx);

        return ctx;
    }

    private static List<InteractionWiredEffect> getOrderedSelectorEffects(Room room, InteractionWiredTrigger trigger) {
        if (room == null || trigger == null || room.getRoomSpecialTypes() == null) {
            return Collections.emptyList();
        }

        Collection<InteractionWiredEffect> effects = room.getRoomSpecialTypes().getEffects(trigger.getX(), trigger.getY());
        List<InteractionWiredEffect> selectorEffects = new ArrayList<>();

        for (InteractionWiredEffect effect : WiredExecutionOrderUtil.sort(effects)) {
            if (effect != null && effect.isSelector()) {
                selectorEffects.add(effect);
            }
        }

        return selectorEffects;
    }

    private static void applySelectionFilterExtras(Room room, HabboItem triggerItem, WiredContext selectorCtx) {
        WiredSelectionFilterSupport.applySelectorFilters(room, triggerItem, selectorCtx);
    }
}
