package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.catalog.LtdRaffleEnteredComposer;
import com.eu.habbo.messages.outgoing.catalog.LtdRaffleResultComposer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR 13 limited-edition raffle (`HabboCatalog.onLtdRaffleEntered` /
 * {@code onLtdRaffleResult}). Instead of handing the rarest items to whoever clicks
 * first, the server holds the buyers of the same limited offer for a short window, then
 * draws as many winners as there are copies left. Everybody who clicked sees the
 * confirmation keep its "hold on" line until the draw, then wins or loses.
 *
 * <p>Off by default: with {@code hotel.catalog.ltd.raffle.enabled} unset a limited
 * purchase completes immediately, exactly as before.
 */
public final class LtdRaffleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LtdRaffleManager.class);

    private static final Map<Integer, Raffle> OPEN_RAFFLES = new ConcurrentHashMap<>();

    private LtdRaffleManager() {}

    public static boolean isEnabled() {
        return Emulator.getConfig().getBoolean("hotel.catalog.ltd.raffle.enabled", false);
    }

    /** How long buyers of the same offer are collected before the draw. */
    public static int windowSeconds() {
        return Math.max(1, Emulator.getConfig().getInt("hotel.catalog.ltd.raffle.seconds", 10));
    }

    /**
     * Puts this purchase into the raffle for its offer and answers true when it was taken,
     * in which case the caller must not complete the purchase itself: the draw does that
     * for the winners. Returns false for anything that is not a raffled limited offer, or
     * when the same player is already in this raffle.
     */
    public static boolean enter(CatalogManager catalogManager, CatalogPage page, CatalogItem item, Habbo habbo) {
        if (!isEnabled() || catalogManager == null || item == null || habbo == null || !item.isLimited()) {
            return false;
        }

        CatalogLimitedConfiguration configuration = catalogManager.getLimitedConfig(item);

        if (configuration == null || configuration.available() <= 0) {
            return false;
        }

        String className = className(item);
        boolean opened = false;
        Raffle raffle = OPEN_RAFFLES.computeIfAbsent(item.getId(), id -> new Raffle(page, item, className));

        synchronized (raffle) {
            if (raffle.drawn) {
                // The draw fired between the lookup and the lock; let the caller retry as
                // a normal purchase rather than joining a raffle nobody will resolve.
                return false;
            }

            if (raffle.entrants.contains(habbo)) {
                return true;
            }

            raffle.entrants.add(habbo);
            opened = raffle.entrants.size() == 1;
        }

        habbo.getClient().sendResponse(new LtdRaffleEnteredComposer(className));

        if (opened) {
            Emulator.getThreading().run(() -> draw(catalogManager, item.getId()), windowSeconds() * 1000L);
        }

        return true;
    }

    private static void draw(CatalogManager catalogManager, int catalogItemId) {
        Raffle raffle = OPEN_RAFFLES.remove(catalogItemId);

        if (raffle == null) return;

        List<Habbo> entrants;
        synchronized (raffle) {
            raffle.drawn = true;
            entrants = new ArrayList<>(raffle.entrants);
        }

        Collections.shuffle(entrants);

        CatalogLimitedConfiguration configuration = catalogManager.getLimitedConfig(raffle.item);
        int copies = configuration == null ? 0 : Math.max(0, configuration.available());

        for (int index = 0; index < entrants.size(); index++) {
            Habbo habbo = entrants.get(index);

            if (habbo == null || habbo.getClient() == null) continue;

            boolean won = index < copies;

            try {
                if (won) {
                    catalogManager.purchaseItem(raffle.page, raffle.item, habbo, 1, "", false);
                }
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to deliver the LTD raffle prize for user {}",
                        habbo.getHabboInfo().getId(),
                        e);
                habbo.getClient()
                        .sendResponse(new LtdRaffleResultComposer(raffle.className, LtdRaffleResultComposer.ERROR));
                continue;
            }

            habbo.getClient()
                    .sendResponse(new LtdRaffleResultComposer(
                            raffle.className, won ? LtdRaffleResultComposer.WON : LtdRaffleResultComposer.LOST));
        }
    }

    /** Cancels every open raffle, telling the entrants so no confirmation dialog is left spinning. */
    public static void cancelAll() {
        for (Integer catalogItemId : new ArrayList<>(OPEN_RAFFLES.keySet())) {
            Raffle raffle = OPEN_RAFFLES.remove(catalogItemId);

            if (raffle == null) continue;

            List<Habbo> entrants;
            synchronized (raffle) {
                raffle.drawn = true;
                entrants = new ArrayList<>(raffle.entrants);
            }

            for (Habbo habbo : entrants) {
                if (habbo == null || habbo.getClient() == null) continue;

                habbo.getClient()
                        .sendResponse(new LtdRaffleResultComposer(raffle.className, LtdRaffleResultComposer.CANCELLED));
            }
        }
    }

    private static String className(CatalogItem item) {
        for (Item baseItem : item.getBaseItems()) {
            if (baseItem != null && baseItem.getName() != null) {
                return baseItem.getName();
            }
        }

        return item.getName() == null ? "" : item.getName();
    }

    private static final class Raffle {
        private final CatalogPage page;
        private final CatalogItem item;
        private final String className;
        private final List<Habbo> entrants = new ArrayList<>();
        private boolean drawn;

        private Raffle(CatalogPage page, CatalogItem item, String className) {
            this.page = page;
            this.item = item;
            this.className = className;
        }
    }
}
