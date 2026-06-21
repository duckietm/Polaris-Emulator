package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurniByVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUser;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUsersByVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class WiredSourceUtil {
    public static final int SOURCE_TRIGGER = 0;
    public static final int SOURCE_CLICKED_USER = 11;
    public static final int SOURCE_SELECTED = 100;
    public static final int SOURCE_SELECTOR = 200;
    public static final int SOURCE_SIGNAL = 201;

    private WiredSourceUtil() {
    }

    public static List<HabboItem> resolveItems(WiredContext ctx, int sourceType, Collection<HabboItem> selectedItems) {
        List<HabboItem> resolvedItems = resolveItemsInternal(ctx, sourceType, selectedItems, false);

        if (ctx == null) {
            return resolvedItems;
        }

        return (sourceType == SOURCE_SELECTOR)
                ? resolvedItems
                : WiredSelectionFilterSupport.filterItems(ctx.room(), ctx.triggerItem(), ctx, resolvedItems);
    }

    public static List<HabboItem> resolveItemsRaw(WiredContext ctx, int sourceType, Collection<HabboItem> selectedItems) {
        return resolveItemsInternal(ctx, sourceType, selectedItems, true);
    }

    public static List<RoomUnit> resolveUsers(WiredContext ctx, int sourceType) {
        return resolveUsers(ctx, sourceType, null);
    }

    public static List<RoomUnit> resolveUsers(WiredContext ctx, int sourceType, Collection<RoomUnit> selectedUsers) {
        List<RoomUnit> resolvedUsers = resolveUsersInternal(ctx, sourceType, selectedUsers);

        if (ctx == null) {
            return resolvedUsers;
        }

        return (sourceType == SOURCE_SELECTOR)
                ? resolvedUsers
                : WiredSelectionFilterSupport.filterUsers(ctx.room(), ctx.triggerItem(), ctx, resolvedUsers);
    }

    public static List<RoomUnit> resolveUsersRaw(WiredContext ctx, int sourceType) {
        return resolveUsersRaw(ctx, sourceType, null);
    }

    public static List<RoomUnit> resolveUsersRaw(WiredContext ctx, int sourceType, Collection<RoomUnit> selectedUsers) {
        return resolveUsersInternal(ctx, sourceType, selectedUsers);
    }

    public static boolean isDefaultUserSource(int value) {
        switch (value) {
            case SOURCE_TRIGGER:
            case SOURCE_CLICKED_USER:
            case SOURCE_SELECTOR:
            case SOURCE_SIGNAL:
                return true;
            default:
                return false;
        }
    }

    public static boolean isSelectableUserSource(int value) {
        return value == SOURCE_SELECTED || isDefaultUserSource(value);
    }

    public static List<HabboItem> resolveSelectorItems(WiredContext ctx, boolean includeWiredItems) {
        if (ctx == null) {
            return Collections.emptyList();
        }

        if (!includeWiredItems) {
            return resolveItems(ctx, SOURCE_SELECTOR, null);
        }

        WiredContext selectorContext = executeSelectors(cloneSelectorContext(ctx, true));

        if (selectorContext == null || !selectorContext.targets().isItemsModifiedBySelector()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(selectorContext.targets().items());
    }

    private static WiredTargets getSelectorTargets(WiredContext ctx) {
        if (ctx == null) {
            return new WiredTargets();
        }

        if (ctx.targets().isItemsModifiedBySelector() || ctx.targets().isUsersModifiedBySelector()) {
            return ctx.targets();
        }

        WiredContext selectorContext = executeSelectors(ctx);

        if (selectorContext == null) {
            return ctx.targets();
        }

        if (selectorContext.targets().isItemsModifiedBySelector()) {
            ctx.targets().setItems(selectorContext.targets().items());
        }

        if (selectorContext.targets().isUsersModifiedBySelector()) {
            ctx.targets().setUsers(selectorContext.targets().users());
        }

        return ctx.targets();
    }

    private static WiredContext executeSelectors(WiredContext originalCtx) {
        if (originalCtx == null) {
            return null;
        }

        Room room = originalCtx.room();
        HabboItem triggerItem = originalCtx.triggerItem();

        if (room == null || triggerItem == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        WiredContext selectorCtx = new WiredContext(
                originalCtx.event(),
                triggerItem,
                originalCtx.stack(),
                originalCtx.services(),
                new WiredState(100),
                originalCtx.legacySettings()
        );
        selectorCtx.setIncludeWiredSelectorItems(originalCtx.includeWiredSelectorItems());

        List<InteractionWiredEffect> selectorEffects = getOrderedSelectorEffects(originalCtx, room, triggerItem);
        executeSelectorEffects(selectorCtx, selectorEffects, false);
        executeSelectorEffects(selectorCtx, selectorEffects, true);

        applySelectionFilterExtras(room, triggerItem, selectorCtx);

        return selectorCtx;
    }

    private static void executeSelectorEffects(WiredContext selectorCtx, List<InteractionWiredEffect> selectorEffects, boolean deferred) {
        for (InteractionWiredEffect effect : selectorEffects) {
            if (effect == null || effect.usesExistingSelectorTargets() != deferred) {
                continue;
            }

            if (effect.requiresActor() && !selectorCtx.hasActor()) {
                continue;
            }

            try {
                selectorCtx.state().step();
                effect.execute(selectorCtx);
            } catch (Exception ignored) {
            }
        }
    }

    private static WiredContext cloneSelectorContext(WiredContext originalCtx, boolean includeWiredItems) {
        if (originalCtx == null) {
            return null;
        }

        WiredContext selectorCtx = new WiredContext(
                originalCtx.event(),
                originalCtx.triggerItem(),
                originalCtx.stack(),
                originalCtx.services(),
                new WiredState(100),
                originalCtx.legacySettings()
        );
        selectorCtx.setIncludeWiredSelectorItems(includeWiredItems);
        return selectorCtx;
    }

    private static List<InteractionWiredEffect> getOrderedSelectorEffects(WiredContext originalCtx, Room room, HabboItem triggerItem) {
        List<InteractionWiredEffect> selectorEffects = new ArrayList<>();

        if (originalCtx != null && originalCtx.hasStack()) {
            for (IWiredEffect effect : originalCtx.stack().effects()) {
                if (effect instanceof InteractionWiredEffect && effect.isSelector()) {
                    selectorEffects.add((InteractionWiredEffect) effect);
                }
            }

            if (!selectorEffects.isEmpty()) {
                return selectorEffects;
            }
        }

        Collection<InteractionWiredEffect> roomEffects = room.getRoomSpecialTypes().getEffects(triggerItem.getX(), triggerItem.getY());
        for (InteractionWiredEffect effect : WiredExecutionOrderUtil.sort(roomEffects)) {
            if (effect != null && effect.isSelector()) {
                selectorEffects.add(effect);
            }
        }

        return selectorEffects;
    }

    private static void applySelectionFilterExtras(Room room, HabboItem triggerItem, WiredContext selectorCtx) {
        WiredSelectionFilterSupport.applySelectorFilters(room, triggerItem, selectorCtx);
    }

    private static List<HabboItem> resolveItemsInternal(WiredContext ctx, int sourceType, Collection<HabboItem> selectedItems, boolean allowTriggerItemFallback) {
        if (ctx == null) {
            return Collections.emptyList();
        }

        switch (sourceType) {
            case SOURCE_TRIGGER:
                return resolveTriggerItems(ctx, allowTriggerItemFallback);
            case SOURCE_SELECTED:
                return (selectedItems != null) ? new ArrayList<>(selectedItems) : Collections.emptyList();
            case SOURCE_SELECTOR:
                WiredTargets itemTargets = getSelectorTargets(ctx);
                return itemTargets.isItemsModifiedBySelector()
                        ? new ArrayList<>(itemTargets.items())
                        : Collections.emptyList();
            case SOURCE_SIGNAL:
                if (ctx.eventType() == WiredEvent.Type.SIGNAL_RECEIVED) {
                    return ctx.sourceItem().map(Collections::singletonList).orElse(Collections.emptyList());
                }
                return Collections.emptyList();
            default:
                return resolveTriggerItems(ctx, allowTriggerItemFallback);
        }
    }

    private static List<RoomUnit> resolveUsersInternal(WiredContext ctx, int sourceType, Collection<RoomUnit> selectedUsers) {
        if (ctx == null) {
            return Collections.emptyList();
        }

        switch (sourceType) {
            case SOURCE_TRIGGER:
                return ctx.actor().map(Collections::singletonList).orElse(Collections.emptyList());
            case SOURCE_CLICKED_USER:
                if (ctx.eventType() == WiredEvent.Type.USER_CLICKS_USER) {
                    return ctx.event().getTargetUnit().map(Collections::singletonList).orElse(Collections.emptyList());
                }
                return Collections.emptyList();
            case SOURCE_SELECTED:
                return (selectedUsers != null) ? new ArrayList<>(selectedUsers) : Collections.emptyList();
            case SOURCE_SELECTOR:
                WiredTargets userTargets = getSelectorTargets(ctx);
                return userTargets.isUsersModifiedBySelector()
                        ? new ArrayList<>(userTargets.users())
                        : Collections.emptyList();
            case SOURCE_SIGNAL:
                if (ctx.eventType() == WiredEvent.Type.SIGNAL_RECEIVED) {
                    return ctx.actor().map(Collections::singletonList).orElse(Collections.emptyList());
                }
                return Collections.emptyList();
            default:
                return ctx.actor().map(Collections::singletonList).orElse(Collections.emptyList());
        }
    }

    private static List<HabboItem> resolveTriggerItems(WiredContext ctx, boolean allowTriggerItemFallback) {
        if (ctx == null) {
            return Collections.emptyList();
        }

        if (ctx.sourceItem().isPresent()) {
            return Collections.singletonList(ctx.sourceItem().get());
        }

        if (allowTriggerItemFallback && ctx.triggerItem() != null) {
            return Collections.singletonList(ctx.triggerItem());
        }

        return Collections.emptyList();
    }
}
