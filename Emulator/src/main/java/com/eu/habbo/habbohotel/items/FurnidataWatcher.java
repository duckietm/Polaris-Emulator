package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

/**
 * Watches the furnidata source on a single daemon thread. On change (debounced),
 * re-indexes via the provider and broadcasts only the delta — or a compact
 * reload-hint when the delta exceeds the cap. A minimum interval throttles bursts.
 * For the split-tier directory layout, the base dir AND its immediate
 * subdirectories are registered. Never throws out of the loop.
 */
public class FurnidataWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataWatcher.class);

    private final FurnitureTextProvider provider;
    private final Path watchDir;
    private final boolean sourceIsDir;
    private final long maxBytes;
    private final long debounceMs;
    private final long minIntervalMs;
    private final int deltaCap;

    private volatile boolean running = false;
    private volatile WatchService ws;
    private long lastBroadcast = 0L;

    public FurnidataWatcher(FurnitureTextProvider provider, Path source, long maxBytes) {
        this.provider = provider;
        this.sourceIsDir = Files.isDirectory(source);
        this.watchDir = this.sourceIsDir ? source : source.getParent();
        this.maxBytes = maxBytes;
        this.debounceMs = Long.parseLong(Emulator.getConfig().getValue("items.furnidata.watch.debounce.ms", "750"));
        this.minIntervalMs = Long.parseLong(Emulator.getConfig().getValue("items.furnidata.watch.min.interval.ms", "5000"));
        this.deltaCap = Integer.parseInt(Emulator.getConfig().getValue("items.furnidata.delta.cap", "500"));
    }

    public void start() {
        if (this.running || this.watchDir == null) return;
        this.running = true;
        Thread t = new Thread(this::run, "FurnidataWatcher");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        this.running = false;
        WatchService local = this.ws;
        if (local != null) {
            try { local.close(); } catch (IOException ignored) { }
        }
    }

    private void run() {
        try {
            this.ws = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            LOGGER.warn("FurnidataWatcher: could not create WatchService", e);
            return;
        }
        try (WatchService service = this.ws) {
            registerDirs(service);
            while (this.running) {
                WatchKey key = service.take();
                key.pollEvents();
                Thread.sleep(this.debounceMs);
                key.pollEvents();
                if (!key.reset()) {
                    LOGGER.warn("FurnidataWatcher: watch key invalidated (directory removed?) — stopping");
                    break;
                }
                try {
                    onChange();
                } catch (Exception e) {
                    LOGGER.warn("FurnidataWatcher: onChange failed", e);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignored) {
            // stop() closed the service — normal shutdown
        } catch (Exception e) {
            LOGGER.warn("FurnidataWatcher stopped", e);
        }
    }

    /** Register the base dir, plus one level of subdirectories for the split-tier layout. */
    private void registerDirs(WatchService service) throws IOException {
        this.watchDir.register(service, StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
        if (this.sourceIsDir) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(this.watchDir)) {
                for (Path child : ds) {
                    if (Files.isDirectory(child)) {
                        child.register(service, StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                    }
                }
            }
        }
    }

    private void onChange() throws InterruptedException {
        // Re-index under the shared furnidata lock so the watcher and editor
        // writes never swap the index concurrently. The lock is released before
        // the throttle/broadcast below so a slow broadcast can't stall editor saves.
        List<FurnidataEntry> delta;
        FurnidataLock.LOCK.lock();
        try {
            Path source = this.provider.getSource();
            if (source == null) return;
            delta = this.provider.reindex(new FurnidataReader(source, this.maxBytes).read());
        } finally {
            FurnidataLock.LOCK.unlock();
        }
        if (delta.isEmpty()) return;

        // Min-interval throttle: the index has already been swapped, so we must
        // not drop this delta (the next reindex would diff against the updated
        // index and never re-emit it). Instead, defer the broadcast until the
        // interval elapses. Running on a dedicated daemon thread, sleeping is
        // safe; file events arriving meanwhile coalesce into the next cycle.
        long sinceLast = System.currentTimeMillis() - this.lastBroadcast;
        if (sinceLast < this.minIntervalMs) {
            Thread.sleep(this.minIntervalMs - sinceLast);
        }
        this.lastBroadcast = System.currentTimeMillis();

        FurnitureDataReloadComposer composer = (delta.size() > this.deltaCap)
            ? new FurnitureDataReloadComposer(FurnitureDataReloadComposer.MODE_RELOAD_HINT, List.of())
            : new FurnitureDataReloadComposer(FurnitureDataReloadComposer.MODE_DELTA, delta);

        broadcast(composer);
        LOGGER.info("FurnidataWatcher: broadcast {} ({} entries)",
            delta.size() > this.deltaCap ? "reload-hint" : "delta", delta.size());
    }

    private void broadcast(FurnitureDataReloadComposer composer) {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(composer);
            }
        }
    }
}
