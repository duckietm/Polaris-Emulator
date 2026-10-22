package com.eu.habbo.habbohotel.collectibles;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * What the collectibles packets need from the running hotel, installed once at start-up: the service,
 * the live {@code hotel.collectibles.*} settings, the item manager (furni handed out with a collectible,
 * relic furni types), online users by id, and the clock (unix seconds).
 */
public record CollectiblesRuntime(
        CollectiblesService service,
        Supplier<CollectiblesSettings> settings,
        ItemManager items,
        IntFunction<Habbo> onlineHabbo,
        IntSupplier clock) {
    private static final AtomicReference<CollectiblesRuntime> INSTALLED = new AtomicReference<>();

    public CollectiblesRuntime {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(onlineHabbo, "onlineHabbo");
        Objects.requireNonNull(clock, "clock");
    }

    public static void install(CollectiblesRuntime runtime) {
        INSTALLED.set(Objects.requireNonNull(runtime, "runtime"));
    }

    /** Removes the installed runtime (tests; collectibles then behave as before start-up). */
    public static void uninstall() {
        INSTALLED.set(null);
    }

    /** The installed runtime, or null before start-up finished. */
    public static CollectiblesRuntime installed() {
        return INSTALLED.get();
    }

    /** The live settings; the migration defaults (collectibles off) before start-up finished. */
    public static CollectiblesSettings currentSettings() {
        CollectiblesRuntime runtime = INSTALLED.get();
        CollectiblesSettings settings =
                runtime == null ? null : runtime.settings().get();
        return settings == null ? CollectiblesSettings.defaults() : settings;
    }

    /** The furni type behind an {@code items_base} id, or null before start-up or for a missing furni. */
    public static Item baseItem(int baseItemId) {
        CollectiblesRuntime runtime = INSTALLED.get();
        return runtime == null || runtime.items() == null
                ? null
                : runtime.items().getItem(baseItemId);
    }

    /** Unix seconds, from the installed clock. */
    public static int now() {
        CollectiblesRuntime runtime = INSTALLED.get();
        return runtime == null
                ? (int) (System.currentTimeMillis() / 1000L)
                : runtime.clock().getAsInt();
    }
}
