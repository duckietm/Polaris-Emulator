package com.eu.habbo.habbohotel.items;

import com.eu.habbo.imaging.camera.assets.NitroLibraryLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The directions of each furni type, read from the hotel's own furniture bundles ({@code
 * <classname>.nitro}, or {@code <classname>.hab}) in the folder the camera renderer uses.
 *
 * <p>A bundle is read once, on a background thread, and the result (also "no data") is kept in a
 * bounded LRU cache keyed by classname. A lookup that misses waits at most {@code waitMillis} for
 * the read and otherwise answers "unknown", so callers fall back to their old rule and never stall
 * on assets; the next lookup finds the result in the cache.
 */
public final class FurnitureDirectionCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnitureDirectionCatalog.class);
    private static final int[] NONE = new int[0];
    private static final List<String> EXTENSIONS = List.of(".nitro", ".hab");
    private static final int MAX_QUEUED_READS = 8192;
    private static final int MAX_WARNINGS = 20;

    /** Settings; a null or missing folder disables the catalog. */
    public record Settings(
            boolean enabled, Path furnitureDir, int cacheEntries, long waitMillis, long maxBundleBytes) {}

    private final Path furnitureDir;
    private final boolean enabled;
    private final long waitMillis;
    private final long maxBundleBytes;
    private final NitroLibraryLoader loader;
    private final Map<String, int[]> cache;
    private final ConcurrentHashMap<String, CompletableFuture<int[]>> loading = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final AtomicInteger warnings = new AtomicInteger();

    public FurnitureDirectionCatalog(Settings settings) {
        this.furnitureDir = settings.furnitureDir() == null
                ? null
                : settings.furnitureDir().toAbsolutePath().normalize();
        this.enabled = settings.enabled() && this.furnitureDir != null && Files.isDirectory(this.furnitureDir);
        this.waitMillis = Math.max(0, settings.waitMillis());
        this.maxBundleBytes = Math.max(1024, settings.maxBundleBytes());
        this.loader = new NitroLibraryLoader(this.maxBundleBytes, 1);
        int capacity = Math.max(16, settings.cacheEntries());
        this.cache = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
                return this.size() > capacity;
            }
        };
        AtomicInteger threads = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(MAX_QUEUED_READS),
                runnable -> {
                    Thread thread = new Thread(runnable, "FurniDirections-" + threads.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public Path furnitureDir() {
        return this.furnitureDir;
    }

    /**
     * The directions of a furni type in rotation units, sorted; empty when unknown (disabled, no
     * bundle, unreadable, or still loading after the wait).
     */
    public int[] directions(String itemName) {
        String key = key(itemName);
        if (!this.enabled || key == null) {
            return NONE;
        }
        int[] cached = this.cached(key);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<int[]> read = this.schedule(key);
        if (this.waitMillis <= 0) {
            return NONE;
        }
        try {
            return read.get(this.waitMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return NONE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NONE;
        } catch (ExecutionException e) {
            return NONE;
        }
    }

    /** Starts reading a furni type's bundle in the background when it is not cached yet. */
    public void prefetch(String itemName) {
        String key = key(itemName);
        if (this.enabled && key != null && this.cached(key) == null) {
            this.schedule(key);
        }
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    private int[] cached(String key) {
        synchronized (this.cache) {
            return this.cache.get(key);
        }
    }

    private CompletableFuture<int[]> schedule(String key) {
        CompletableFuture<int[]> existing = this.loading.get(key);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<int[]> created = new CompletableFuture<>();
        existing = this.loading.putIfAbsent(key, created);
        if (existing != null) {
            return existing;
        }
        try {
            this.executor.execute(() -> {
                int[] directions = NONE;
                try {
                    directions = this.read(key);
                } finally {
                    synchronized (this.cache) {
                        this.cache.put(key, directions);
                    }
                    this.loading.remove(key, created);
                    created.complete(directions);
                }
            });
        } catch (RejectedExecutionException e) {
            // Queue full or shut down: answer "unknown" now and try again on a later lookup.
            this.loading.remove(key, created);
            created.complete(NONE);
        }
        return created;
    }

    /** Reads the directions from {@code <key>.nitro} or {@code <key>.hab}; empty when there is none. */
    int[] read(String key) {
        for (String extension : EXTENSIONS) {
            Path file = this.furnitureDir.resolve(key + extension).normalize();
            if (!file.startsWith(this.furnitureDir) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                if (Files.size(file) > this.maxBundleBytes) {
                    throw new IOException("bundle is larger than " + this.maxBundleBytes + " bytes");
                }
                byte[] bytes = Files.readAllBytes(file);
                return FurnitureDirections.parse(this.loader.readAssetLibrary(bytes));
            } catch (IOException | RuntimeException e) {
                if (this.warnings.incrementAndGet() <= MAX_WARNINGS) {
                    LOGGER.warn(
                            "Furni directions: could not read {} ({}); it keeps the default rotations",
                            file,
                            e.getMessage());
                }
                return NONE;
            }
        }
        return NONE;
    }

    /** The base classname ({@code name*3} uses {@code name}), or null when it is not a safe file name. */
    static String key(String itemName) {
        if (itemName == null) {
            return null;
        }
        int star = itemName.indexOf('*');
        String base = (star >= 0 ? itemName.substring(0, star) : itemName).trim();
        return NitroLibraryLoader.isSafeName(base) ? base : null;
    }
}
