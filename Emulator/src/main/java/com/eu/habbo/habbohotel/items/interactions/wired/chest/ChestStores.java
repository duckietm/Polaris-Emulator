package com.eu.habbo.habbohotel.items.interactions.wired.chest;

/**
 * The {@link ChestStore} the hotel's chests use. The database by default; tests swap in their own so
 * the chest rules can be exercised without a database.
 */
public final class ChestStores {
    private static final java.util.concurrent.atomic.AtomicReference<ChestStore> CURRENT =
            new java.util.concurrent.atomic.AtomicReference<>(new DatabaseChestStore());

    private ChestStores() {}

    public static ChestStore current() {
        return CURRENT.get();
    }

    /** Replace the store, returning the previous one so a test can put it back. */
    public static ChestStore use(ChestStore store) {
        return CURRENT.getAndSet(store == null ? new DatabaseChestStore() : store);
    }
}
