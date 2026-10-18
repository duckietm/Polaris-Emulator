package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStore.Release;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStore.ReleaseStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives a wired chest's contents back to its owner when the chest item itself is deleted.
 *
 * <p>Picking a chest up keeps what it holds, but deleting it (recycler, crackable, rentable expiry,
 * Builders Club cleanup, inventory deletes, commands, ...) used to leave its {@code wired_chests} and
 * {@code wired_chest_items} rows behind, and with them the owner's furni and coins. Every path that
 * deletes an item row calls this first: the stored furni go back into the owner's inventory as
 * themselves, the coins are paid out as credits through the economy ledger, and the chest's rows are
 * removed, in one transaction ({@link ChestStore#releaseChest}).
 *
 * <p>Safe to call for any item. A furni object that is no {@link InteractionWiredChest} is left alone
 * without touching the database, so the hot deletion paths pay nothing for the furni that are not
 * chests; a caller that only holds an id pays one indexed lookup. (A chest whose interaction was
 * changed away from a chest is no chest object any more; its rows are released by {@link
 * #sweepOrphans} on the next start, under the owner recorded on them.) Idempotent: a released chest has no rows left, and the refund's ledger operation id
 * ({@code wired-chest-release:<chestId>}) pays at most once.
 *
 * <p>If nobody can be found to give a chest's contents to, its rows are kept and logged, never
 * dropped; {@link #sweepOrphans} tries again on every start.
 */
public final class ChestRelease {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestRelease.class);

    /** Prefix of the ledger operation id that refunds a released chest's coins. */
    public static final String OPERATION_PREFIX = "wired-chest-release:";

    private ChestRelease() {}

    /** The ledger operation id of a chest's refund: one per chest, so it can never pay twice. */
    public static String operationId(int chestId) {
        return OPERATION_PREFIX + chestId;
    }

    static EconomyOperation refundOperation(int chestId, int ownerId, int coins) {
        return new EconomyOperation(
                operationId(chestId),
                ownerId,
                null,
                "wired_chest_release",
                "wired.chest.release_refund",
                EconomyLedger.CREDITS,
                coins,
                chestId,
                "Wired chest deleted: its coins went back to the owner");
    }

    /**
     * Before an item's row is deleted: if it is a chest, give what it holds back to its owner.
     *
     * @return what happened; {@link ReleaseStatus#NO_CHEST} for anything that is not a chest
     */
    public static Release releaseToOwner(HabboItem item) {
        if (!isChest(item)) return Release.of(ReleaseStatus.NO_CHEST, item == null ? 0 : item.getId(), 0);

        Release release = releaseToOwner(item.getId(), item.getUserId());
        forget(item, release);
        return release;
    }

    /**
     * {@link #releaseToOwner(HabboItem)} for callers that only hold the id.
     *
     * @param ownerId the owner as the caller knows it, or 0 to use the item's row
     */
    public static Release releaseToOwner(int itemId, int ownerId) {
        if (itemId <= 0) return Release.of(ReleaseStatus.NO_CHEST, itemId, ownerId);

        ChestStore store = ChestStores.current();
        if (!store.chestIdsAmong(List.of(itemId)).contains(itemId)) {
            return Release.of(ReleaseStatus.NO_CHEST, itemId, ownerId);
        }
        return release(store, itemId, ownerId);
    }

    /**
     * {@link #releaseToOwner(HabboItem)} for a batch of items about to be deleted, with one lookup for
     * the whole batch.
     *
     * @return what happened to each chest among them (empty when there was none)
     */
    public static List<Release> releaseAll(Collection<? extends HabboItem> items) {
        List<Release> releases = new ArrayList<>();
        if (items == null || items.isEmpty()) return releases;

        Map<Integer, HabboItem> byId = new LinkedHashMap<>();
        for (HabboItem item : items) {
            if (isChest(item)) byId.putIfAbsent(item.getId(), item);
        }
        if (byId.isEmpty()) return releases;

        ChestStore store = ChestStores.current();
        Set<Integer> chests = store.chestIdsAmong(byId.keySet());
        for (Map.Entry<Integer, HabboItem> entry : byId.entrySet()) {
            if (!chests.contains(entry.getKey())) continue;
            Release release = release(store, entry.getKey(), entry.getValue().getUserId());
            forget(entry.getValue(), release);
            releases.add(release);
        }
        return releases;
    }

    /** {@link #releaseAll} for callers that only hold ids; owners come from the item rows. */
    public static List<Release> releaseAllIds(Collection<Integer> itemIds) {
        List<Release> releases = new ArrayList<>();
        if (itemIds == null || itemIds.isEmpty()) return releases;

        ChestStore store = ChestStores.current();
        for (int chestId : store.chestIdsAmong(itemIds)) {
            releases.add(release(store, chestId, 0));
        }
        return releases;
    }

    /**
     * The safety net: every chest row whose item is gone (deleted by a path that did not release it,
     * a plugin, a manual query) is released to the owner recorded on it. Rows of an owner that cannot
     * be found are kept and logged, never dropped; a row that holds nothing is removed.
     *
     * @return how many orphaned chests were released or cleared
     */
    public static int sweepOrphans() {
        ChestStore store = ChestStores.current();
        int cleared = 0;
        int kept = 0;
        for (ChestStore.OrphanRow orphan : store.orphanedChests()) {
            Release release = release(store, orphan.chestId(), orphan.ownerId());
            if (release.rowsRemoved()) {
                cleared++;
            } else {
                kept++;
            }
        }
        if (cleared > 0) LOGGER.info("Released {} wired chest(s) whose furni no longer exists", cleared);
        if (kept > 0) {
            LOGGER.warn("Kept {} wired chest(s) whose furni no longer exists: no known owner to give them to", kept);
        }
        return cleared;
    }

    private static Release release(ChestStore store, int chestId, int ownerHint) {
        Habbo online = onlineHabbo(ownerHint);
        Release release;
        if (online != null) {
            // Hold the owner's wallet while the ledger moves, so the live balance cannot be overwritten
            // by a stale one from a concurrent mutation.
            release = LedgerWalletMutation.coordinated(online, () -> {
                Release done = store.releaseChest(chestId, ownerHint);
                applyCredits(online, done);
                return done;
            });
        } else {
            release = store.releaseChest(chestId, ownerHint);
        }

        switch (release.status()) {
            case RELEASED -> {
                LOGGER.info(
                        "Wired chest {} was deleted: {} furni and {} coins went back to user {}",
                        chestId,
                        release.furni().size(),
                        release.coins(),
                        release.ownerId());
                notifyOwner(online, release);
            }
            case OWNER_UNKNOWN ->
                LOGGER.warn(
                        "Wired chest {} could not be released: no known owner (last recorded {}); its rows are kept",
                        chestId,
                        ownerHint);
            case FAILED -> LOGGER.warn("Wired chest {} could not be released; its rows are kept", chestId);
            default -> {}
        }
        return release;
    }

    /** Show an online owner what came back: the furni in their inventory and their new balance. */
    private static void notifyOwner(Habbo coordinated, Release release) {
        try {
            Habbo owner = coordinated != null && coordinated.getHabboInfo().getId() == release.ownerId()
                    ? coordinated
                    : onlineHabbo(release.ownerId());
            if (owner == null) return;

            if (owner != coordinated) {
                LedgerWalletMutation.coordinated(owner, () -> {
                    applyCredits(owner, release);
                    return null;
                });
            }
            if (release.coins() > 0 && owner.getClient() != null) {
                owner.getClient().sendResponse(new UserCreditsComposer(owner));
            }
            ChestWithdrawals.deliver(owner, release.furni().values());
        } catch (RuntimeException e) {
            // Everything is stored already; the player sees it on their next login.
            LOGGER.warn(
                    "Could not show user {} the contents of wired chest {}", release.ownerId(), release.chestId(), e);
        }
    }

    private static void applyCredits(Habbo habbo, Release release) {
        if (release.status() != ReleaseStatus.RELEASED || release.creditsAfter() < 0 || release.coins() <= 0) return;
        if (habbo.getHabboInfo().getId() != release.ownerId()) return;
        LedgerWalletMutation.applyCommitted(habbo, EconomyLedger.CREDITS, release.creditsAfter());
    }

    /** A chest furni with a row of its own (a temporary copy has none). */
    private static boolean isChest(HabboItem item) {
        return item instanceof InteractionWiredChest && !item.isTemporary() && item.getId() > 0;
    }

    private static void forget(HabboItem item, Release release) {
        if (release.rowsRemoved() && item instanceof InteractionWiredChest chest) chest.markReleased();
    }

    private static Habbo onlineHabbo(int userId) {
        if (userId <= 0) return null;
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getHabboManager() == null) return null;
        return environment.getHabboManager().getHabbo(userId);
    }
}
