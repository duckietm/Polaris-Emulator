package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.furniture.FurnitureDataReloadComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

/**
 * Watches the furnidata source on a single daemon thread. On change (debounced),
 * re-indexes via the provider and broadcasts only the delta — or a compact
 * reload-hint when the delta exceeds the cap. A minimum interval throttles bursts.
 * Never throws out of the loop.
 */
public class FurnidataWatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataWatcher.class);

    private final FurnitureTextProvider provider;
    private final Path watchDir;
    private final long maxBytes;
    private final long debounceMs;
    private final long minIntervalMs;
    private final int deltaCap;

    private volatile boolean running = false;
    private long lastBroadcast = 0L;

    public FurnidataWatcher(FurnitureTextProvider provider, Path source, long maxBytes) {
        this.provider = provider;
        this.watchDir = java.nio.file.Files.isDirectory(source) ? source : source.getParent();
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
    }

    private void run() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            this.watchDir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);

            while (this.running) {
                WatchKey key = ws.take();
                key.pollEvents();
                Thread.sleep(this.debounceMs);
                key.pollEvents();
                key.reset();

                try {
                    onChange();
                } catch (Exception e) {
                    LOGGER.warn("FurnidataWatcher: onChange failed", e);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warn("FurnidataWatcher stopped", e);
        }
    }

    private void onChange() {
        Path source = this.provider.getSource();
        if (source == null) return;

        List<FurnidataEntry> delta = this.provider.reindex(new FurnidataReader(source, this.maxBytes).read());
        if (delta.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastBroadcast < this.minIntervalMs) {
            LOGGER.info("FurnidataWatcher: {} changes throttled (min interval)", delta.size());
            return;
        }
        this.lastBroadcast = now;

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
