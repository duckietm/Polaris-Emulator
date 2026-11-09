package polaris.hotel.catalog.marketplace;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.sql.DataSource;
import polaris.hotel.items.ItemManager;
import polaris.hotel.users.User;
import polaris.plugin.PluginManager;

/** What the Collectibles marketplace needs from the running hotel, installed at start-up. */
public record NftMarketplaceRuntime(
        DataSource dataSource,
        Supplier<NftMarketplaceSettings> settings,
        ItemManager items,
        IntFunction<User> onlineUser,
        PluginManager plugins,
        Consumer<Runnable> async,
        IntSupplier clock) {
    private static final AtomicReference<NftMarketplaceRuntime> INSTALLED = new AtomicReference<>();

    public NftMarketplaceRuntime {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(onlineUser, "onlineUser");
        Objects.requireNonNull(plugins, "plugins");
        Objects.requireNonNull(async, "async");
        Objects.requireNonNull(clock, "clock");
    }

    public static void install(NftMarketplaceRuntime runtime) {
        INSTALLED.set(Objects.requireNonNull(runtime, "runtime"));
    }

    /** Removes the installed runtime (tests; the Collectibles marketplace is then off). */
    public static void uninstall() {
        INSTALLED.set(null);
    }

    /** The installed runtime, or null before start-up finished. */
    public static NftMarketplaceRuntime installed() {
        return INSTALLED.get();
    }
}
