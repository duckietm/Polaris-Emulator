package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionFailures;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which chests the two give-from-chest boxes reach, shared by both.
 *
 * <p>Official rules: a save may only pick upgraded (wired) chests of the box's kind, and is refused
 * with the box's key otherwise; at run time only an upgraded, unlocked chest is used, and when there
 * is none the triggering user is told so with transaction failure 15.
 */
final class WiredChestTargets {
    static final String REQUIRE_COIN_CHESTS = "wiredfurni.error.require_coin_chests";
    static final String REQUIRE_FURNI_CHESTS = "wiredfurni.error.require_furni_chests";

    /** The official furni slots: the chests (picked by default) and the variable holder slot. */
    static final int DEFAULT_CHEST_SOURCE = WiredSourceUtil.SOURCE_SELECTED;

    static final int DEFAULT_SECOND_FURNI_SOURCE = WiredSourceUtil.SOURCE_SELECTOR;
    static final int DEFAULT_SECOND_USER_SOURCE = WiredSourceUtil.SOURCE_SELECTOR;

    private WiredChestTargets() {}

    /**
     * Refuse a save whose picks are not all upgraded chests of {@code kind}. Runs before the box
     * changes anything, so a refused save leaves the box as it was.
     */
    static void validatePicks(Room room, int[] furniIds, Class<? extends InteractionWiredChest> kind, String refusalKey)
            throws WiredSaveException {
        if (furniIds == null || furniIds.length == 0) return;
        if (room == null) throw new WiredSaveException("Room not found");

        for (int id : furniIds) {
            HabboItem item = room.getHabboItem(id);
            // A pick the room no longer holds is dropped at run time like any stale pick.
            if (item == null) continue;
            if (!kind.isInstance(item) || !((InteractionWiredChest) item).answersWired()) {
                throw new WiredSaveException(refusalKey);
            }
        }
    }

    /** The picked chests that are still in the room. */
    static List<HabboItem> picked(Room room, Collection<Integer> chestIds) {
        List<HabboItem> items = new ArrayList<>();
        if (room == null) return items;
        for (Integer id : chestIds) {
            HabboItem item = room.getHabboItem(id);
            if (item != null) items.add(item);
        }
        return items;
    }

    /** The first chest of {@code kind} that wired may take from: upgraded and not locked. */
    static <T extends InteractionWiredChest> T firstUsable(Collection<HabboItem> candidates, Class<T> kind) {
        for (HabboItem item : candidates) {
            if (!kind.isInstance(item)) continue;
            T chest = kind.cast(item);
            if (chest.answersWired() && !chest.getContents().isLocked()) return chest;
        }
        return null;
    }

    /** Resolve the box's chest source and pick the chest it uses this time. */
    static <T extends InteractionWiredChest> T resolve(
            WiredContext ctx, Room room, int chestSource, Collection<Integer> chestIds, Class<T> kind) {
        List<HabboItem> candidates = WiredSourceUtil.resolveItems(ctx, chestSource, picked(room, chestIds));
        return firstUsable(candidates, kind);
    }

    /** No usable chest: the triggering user hears why, as the official hotel answers it. */
    static void answerNoUsableChest(WiredContext ctx, Room room) {
        if (ctx == null || room == null) return;
        ctx.actor().ifPresent(unit -> {
            Habbo habbo = room.getHabbo(unit);
            ChestTransactionFailures.send(habbo, ChestTransactionFailures.NO_OR_LOCKED_CHESTS);
        });
    }

    /** An official furni source a chest slot may hold; anything else falls back to the default. */
    static int normalizeChestSource(int source) {
        return switch (source) {
            case WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_SELECTED,
                    WiredVariableOperand.SOURCE_SECONDARY_SELECTED,
                    WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL -> source;
            default -> DEFAULT_CHEST_SOURCE;
        };
    }
}
