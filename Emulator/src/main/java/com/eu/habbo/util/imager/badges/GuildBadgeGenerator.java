package com.eu.habbo.util.imager.badges;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes guild badge images ({@code <code>.png}) on one background thread with a bounded queue, so badge changes
 * never draw on a network thread and a flood of them can use at most one core and a fixed amount of memory: a
 * request that does not fit is dropped (the client then draws that badge itself). A code already queued or
 * already on disk costs nothing. Files are written under a temporary name and moved into place, so the web
 * server never serves half a file.
 */
public class GuildBadgeGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildBadgeGenerator.class);

    private final Supplier<File> outputFolder;
    private final Function<String, BufferedImage> composer;
    private final ThreadPoolExecutor executor;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean missingFolderLogged = new AtomicBoolean(false);

    public GuildBadgeGenerator(Supplier<File> outputFolder, Function<String, BufferedImage> composer, int queueSize) {
        this.outputFolder = outputFolder;
        this.composer = composer;
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Math.max(1, queueSize)), runnable -> {
                    Thread thread = new Thread(runnable, "polaris-badge-imager");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                });
    }

    /** The file of a badge code, or null for anything that is not a badge code. */
    public File outputFile(String badge) {
        File folder = this.outputFolder.get();
        if (folder == null || !GuildBadgeComposer.isBadgeCode(badge)) {
            return null;
        }
        return new File(folder, badge + ".png");
    }

    /**
     * True when the output folder ({@code imager.location.output.badges}) exists. While it does not, nothing is
     * queued and the missing folder is logged once, not once per badge.
     */
    public boolean outputReady() {
        File folder = this.outputFolder.get();
        if (folder != null && folder.isDirectory()) {
            this.missingFolderLogged.set(false);
            return true;
        }
        if (folder != null && this.missingFolderLogged.compareAndSet(false, true)) {
            LOGGER.error(
                    "[Badge Imager] Output folder {} (imager.location.output.badges) does not exist; no guild badge"
                            + " images are written until it does. Create it or fix the setting, then type"
                            + " :update_guildparts.",
                    folder.getAbsolutePath());
        }
        return false;
    }

    /** Queues a badge; false when it is not a badge code, the output folder is missing or the queue is full. */
    public boolean submit(String badge) {
        File file = this.outputFile(badge);
        if (file == null || !this.outputReady()) {
            return false;
        }
        if (file.isFile() || !this.pending.add(badge)) {
            return true;
        }
        try {
            this.executor.execute(() -> this.write(badge));
            return true;
        } catch (RejectedExecutionException e) {
            this.pending.remove(badge);
            LOGGER.warn("[Badge Imager] Queue full, not generating {} now", badge);
            return false;
        }
    }

    /** Queues a badge, waiting while the queue is full (for the backfill thread, never a network thread). */
    public boolean submitWaiting(String badge) throws InterruptedException {
        while (!this.submit(badge)) {
            if (this.outputFile(badge) == null || !this.outputReady()) {
                return false;
            }
            Thread.sleep(50);
        }
        return true;
    }

    public int pendingCount() {
        return this.pending.size();
    }

    /** Waits until everything queued so far is written (tests and shutdown). */
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!this.pending.isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    private void write(String badge) {
        try {
            File file = this.outputFile(badge);
            if (file == null || file.isFile()) {
                return;
            }
            File folder = file.getParentFile();
            if (!this.outputReady()) {
                return;
            }

            BufferedImage image = this.composer.apply(badge);
            if (image == null) {
                LOGGER.warn("[Badge Imager] Could not draw badge {} (are its badge part images installed?)", badge);
                return;
            }

            Path temp = Files.createTempFile(folder.toPath(), "." + badge, ".tmp");
            try {
                if (!ImageIO.write(image, "PNG", temp.toFile())) {
                    throw new IOException("no PNG writer");
                }
                try {
                    Files.move(
                            temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (Exception e) {
            LOGGER.error("[Badge Imager] Failed to write badge {}. Is the output folder writable?", badge, e);
        } finally {
            this.pending.remove(badge);
        }
    }
}
