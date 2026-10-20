package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.earnings.WiredEarnings;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paying a contract's reward out of its chests (AIR model): a reward only ever comes out of chests
 * that answer wired and are not locked, and nothing is created when they run short.
 */
public final class WiredChestPayouts {
    private WiredChestPayouts() {}

    /** The chests among {@code items} that wired may use, in id order (the order they are locked in). */
    public static List<InteractionWiredChest> usable(Collection<? extends HabboItem> items) {
        List<InteractionWiredChest> chests = new ArrayList<>();
        if (items == null) return chests;
        for (HabboItem item : items) {
            if (item instanceof InteractionWiredChest chest
                    && chest.answersWired()
                    && !chest.getContents().isLocked()
                    && !chests.contains(chest)) {
                chests.add(chest);
            }
        }
        chests.sort(Comparator.comparingInt(HabboItem::getId));
        return chests;
    }

    /**
     * Run {@code body} while holding every chest, always in id order, so a settlement sees and moves
     * a consistent set of contents.
     */
    public static <T> T holding(List<InteractionWiredChest> chests, Supplier<T> body) {
        List<InteractionWiredChest> ordered = new ArrayList<>(chests);
        ordered.sort(Comparator.comparingInt(HabboItem::getId));
        return hold(ordered, 0, body);
    }

    private static <T> T hold(List<InteractionWiredChest> chests, int index, Supplier<T> body) {
        if (index >= chests.size()) return body.get();
        synchronized (chests.get(index).getContents()) {
            return hold(chests, index + 1, body);
        }
    }

    /** Coins required by the currency terms of a rule (every currency term is coins in AIR). */
    public static int coins(List<Term> rule) {
        long sum = 0;
        if (rule != null) {
            for (Term term : rule) {
                if (term != null && term.isCurrency() && term.amount > 0) sum += term.amount;
            }
        }
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    /**
     * Why the chests cannot pay {@code reward} right now, or {@link ChestTransactionFailures#NONE}.
     * Call it while {@link #holding} the chests.
     */
    public static int coverFailure(List<InteractionWiredChest> chests, List<Term> reward) {
        if (reward == null || reward.isEmpty()) return ChestTransactionFailures.NONE;
        if (chests.isEmpty()) return ChestTransactionFailures.NO_OR_LOCKED_CHESTS;

        long coinsHeld = 0;
        for (InteractionWiredChest chest : chests) {
            if (chest.chestKind() == ChestStorage.KIND_CURRENCY)
                coinsHeld += chest.getContents().getCoins();
        }
        if (coinsHeld < coins(reward)) return ChestTransactionFailures.FUNDS_GONE;

        for (Term term : reward) {
            if (term == null || !term.isFurni() || term.amount <= 0) continue;
            int held = 0;
            for (InteractionWiredChest chest : chests) {
                if (chest.chestKind() == ChestStorage.KIND_FURNI)
                    held += matching(chest.getContents(), term).size();
            }
            if (held < term.amount) return ChestTransactionFailures.FUNDS_GONE;
        }
        return ChestTransactionFailures.NONE;
    }

    /** The stored rows a furni term would take, oldest first, without taking them. */
    public static List<ChestFurniStoredItem> matching(ChestStorage storage, Term term) {
        List<ChestFurniStoredItem> rows = new ArrayList<>();
        String poster = term.posterId() == null ? "" : term.posterId();
        for (ChestFurniStoredItem row : storage.furniItems()) {
            if (row.wallItem != term.wallItem || row.baseItemId != term.baseItemId) continue;
            String rowPoster = row.legacyPosterId == null ? "" : row.legacyPosterId;
            if (term.wallItem && !poster.equals(rowPoster)) continue;
            rows.add(row);
        }
        return rows;
    }

    /** What the chests would hand out for {@code reward}, for the trade window's preview. */
    public static List<ChestFurniStoredItem> preview(List<InteractionWiredChest> chests, List<Term> reward) {
        List<ChestFurniStoredItem> rows = new ArrayList<>();
        if (reward == null) return rows;
        for (Term term : reward) {
            if (term == null || !term.isFurni()) continue;
            int remaining = term.amount;
            for (InteractionWiredChest chest : chests) {
                if (remaining <= 0 || chest.chestKind() != ChestStorage.KIND_FURNI) continue;
                for (ChestFurniStoredItem row : matching(chest.getContents(), term)) {
                    if (remaining <= 0) break;
                    if (rows.contains(row)) continue;
                    rows.add(row);
                    remaining--;
                }
            }
        }
        return rows;
    }

    /**
     * Hand {@code reward} out of the chests to a player.
     *
     * @return what was actually given, as a rule for the success notification
     */
    public static List<Term> give(Habbo habbo, Room room, List<InteractionWiredChest> chests, List<Term> reward) {
        return give(habbo, room, chests, reward, WiredEarnings.CATEGORY_GAMES);
    }

    /**
     * Hand {@code reward} out of the chests to a player, the coins into the earnings window row of
     * {@code earningsCategory} (AIR 11 games, 13 agency).
     *
     * @return what was actually given, as a rule for the success notification
     */
    public static List<Term> give(
            Habbo habbo, Room room, List<InteractionWiredChest> chests, List<Term> reward, int earningsCategory) {
        int coinsGiven = 0;
        List<ChestFurniStoredItem> furniGiven = new ArrayList<>();
        if (habbo == null || reward == null) return ChestAirWire.givenRule(0, furniGiven);

        int coinsDue = coins(reward);
        for (InteractionWiredChest chest : chests) {
            if (coinsDue - coinsGiven <= 0) break;
            if (chest.chestKind() != ChestStorage.KIND_CURRENCY) continue;
            coinsGiven += ChestWithdrawals.coins(
                            habbo,
                            chest,
                            room,
                            coinsDue - coinsGiven,
                            ChestTransactionLog.SOURCE_WIRED,
                            earningsCategory)
                    .coins();
        }

        for (Term term : reward) {
            if (term == null || !term.isFurni() || term.amount <= 0) continue;
            int remaining = term.amount;
            for (InteractionWiredChest chest : chests) {
                if (remaining <= 0) break;
                if (chest.chestKind() != ChestStorage.KIND_FURNI) continue;
                int want = remaining;
                List<ChestFurniStoredItem> taken = ChestWithdrawals.furni(
                                habbo,
                                chest,
                                room,
                                storage -> storage.removeFurniByType(
                                        term.wallItem, term.baseItemId, term.posterId(), want),
                                ChestTransactionLog.SOURCE_WIRED)
                        .furni();
                furniGiven.addAll(taken);
                remaining -= taken.size();
            }
        }
        return ChestAirWire.givenRule(coinsGiven, furniGiven);
    }
}
