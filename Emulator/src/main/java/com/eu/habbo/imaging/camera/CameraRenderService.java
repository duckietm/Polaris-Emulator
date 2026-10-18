package com.eu.habbo.imaging.camera;

import com.eu.habbo.imaging.camera.assets.CameraAssetSource;
import com.eu.habbo.imaging.camera.render.CameraRender;
import com.eu.habbo.imaging.camera.render.CameraRenderOptions;
import com.eu.habbo.imaging.camera.render.CameraRenderTimeoutException;
import com.eu.habbo.imaging.camera.render.CameraUtils;
import com.eu.habbo.imaging.camera.render.JSONCamera;
import com.eu.habbo.imaging.camera.render.WallColorResolver;
import com.eu.habbo.util.crypto.ZIP;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the official client's camera scene JSON on a small pool of its own, never on a game,
 * room or network thread. The queue is bounded: when it is full a request is refused at once
 * instead of piling up. Each render has a deadline and the renderer gives up when it passes.
 */
public final class CameraRenderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRenderService.class);

    /** What is rendered: a 320x320 photo or a 110x110 navigator thumbnail. */
    public enum Kind {
        PHOTO(CameraConstants.RENDER_WIDTH, CameraConstants.RENDER_HEIGHT),
        THUMBNAIL(CameraConstants.ROOM_RENDER_WIDTH, CameraConstants.ROOM_RENDER_HEIGHT);

        public final int width;
        public final int height;

        Kind(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Everything a render needs, captured on the packet thread so the worker never touches the
     * room: the compressed payload, the room the user is in, and the room's colours.
     */
    public record Job(
            Kind kind,
            byte[] compressedScene,
            int expectedRoomId,
            int backgroundColor,
            String wallPaint,
            Consumer<BufferedImage> onSuccess,
            Consumer<CameraRenderException> onFailure) {}

    private final Supplier<CameraRenderSettings> settings;
    private final CameraAssetSource sprites;
    private final CameraAssetSource effects;
    private final WallColorResolver wallColors;
    private final ThreadPoolExecutor executor;

    public CameraRenderService(
            Supplier<CameraRenderSettings> settings,
            CameraAssetSource sprites,
            CameraAssetSource effects,
            WallColorResolver wallColors,
            int workers,
            int queueCapacity) {
        this.settings = settings;
        this.sprites = sprites;
        this.effects = effects;
        this.wallColors = wallColors;
        int threads = Math.max(1, Math.min(16, workers));
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, Math.min(1024, queueCapacity))),
                runnable -> {
                    Thread thread = new Thread(runnable, "CameraRender-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public CameraRenderSettings settings() {
        CameraRenderSettings current = this.settings.get();
        return current == null ? CameraRenderSettings.defaults() : current;
    }

    /** True when an asset source is configured, so scene JSON can be rendered at all. */
    public boolean isServerRenderingAvailable() {
        return this.sprites != null && this.sprites.isConfigured();
    }

    public String describeAssets() {
        return this.sprites == null ? "none" : this.sprites.describe();
    }

    /**
     * Queues a render. Returns false, without running anything, when the queue is full or the
     * service has stopped; the caller tells the user to try again.
     */
    public boolean submit(Job job) {
        try {
            this.executor.execute(() -> this.run(job));
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    private void run(Job job) {
        BufferedImage image;
        try {
            image = this.render(
                    job.kind(), job.compressedScene(), job.expectedRoomId(), job.backgroundColor(), job.wallPaint());
        } catch (CameraRenderException e) {
            job.onFailure().accept(e);
            return;
        } catch (RuntimeException | OutOfMemoryError e) {
            LOGGER.error("Camera render crashed", e);
            job.onFailure()
                    .accept(new CameraRenderException(CameraRenderException.Reason.RENDER_FAILED, "render crashed", e));
            return;
        }
        try {
            job.onSuccess().accept(image);
        } catch (RuntimeException e) {
            LOGGER.error("Camera render result could not be delivered", e);
        }
    }

    /**
     * Validates and renders one compressed scene synchronously (the worker body; tests call it
     * directly). The payload is inflated within the configured limit, its client checksum and room
     * id are checked, and the scene is drawn within the configured deadline.
     */
    public BufferedImage render(Kind kind, byte[] compressed, int expectedRoomId, int backgroundColor, String wallPaint)
            throws CameraRenderException {
        CameraRenderSettings current = this.settings();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(current.timeoutMs());

        if (compressed == null || compressed.length == 0 || compressed.length > current.maxCompressedBytes()) {
            throw new CameraRenderException(
                    CameraRenderException.Reason.TOO_LARGE,
                    "compressed scene is " + (compressed == null ? 0 : compressed.length) + " bytes, limit "
                            + current.maxCompressedBytes());
        }

        byte[] inflated;
        try {
            inflated = ZIP.inflate(compressed, current.maxInflatedBytes());
        } catch (IOException e) {
            throw new CameraRenderException(CameraRenderException.Reason.INFLATE_FAILED, e.getMessage(), e);
        }
        String json = new String(inflated, StandardCharsets.UTF_8);

        JSONCamera scene = CameraParser.parse(json, current.maxPlanes(), current.maxSprites(), current.maxFilters());
        if (scene.getRoomid() != expectedRoomId) {
            throw new CameraRenderException(
                    CameraRenderException.Reason.ROOM_MISMATCH,
                    "scene is for room " + scene.getRoomid() + " but the user is in room " + expectedRoomId);
        }
        if (current.verifyChecksum()) {
            AirRenderChecksum.Result result = AirRenderChecksum.verify(json, scene.getRoomid());
            if (result != AirRenderChecksum.Result.OK) {
                throw new CameraRenderException(
                        CameraRenderException.Reason.CHECKSUM_FAILED, "client checksum: " + result);
            }
        }
        if (current.maxClockSkewSeconds() > 0) {
            long clientMillis = AirRenderChecksum.clientTimeMillis(scene.getTimestamp());
            long skew = Math.abs(System.currentTimeMillis() - clientMillis) / 1000L;
            if (skew > current.maxClockSkewSeconds()) {
                throw new CameraRenderException(
                        CameraRenderException.Reason.CLOCK_SKEW, "client clock is " + skew + " s away from ours");
            }
        }

        CameraRenderOptions options = current.renderOptions().withDeadline(deadline);
        try {
            BufferedImage image = new CameraRender(
                            scene,
                            kind.width,
                            kind.height,
                            backgroundColor,
                            this.sprites,
                            this.effects,
                            wallPaint,
                            this.wallColors,
                            options)
                    .render();
            return applyZoom(image, scene.getZoom());
        } catch (CameraRenderTimeoutException e) {
            throw new CameraRenderException(CameraRenderException.Reason.TIMEOUT, e.getMessage(), e);
        }
    }

    /** Zoom 2 (the editor's zoom button) is a 2x enlargement of the centre, as the client previews it. */
    static BufferedImage applyZoom(BufferedImage image, int zoom) {
        if (zoom <= 1) {
            return image;
        }
        int factor = Math.min(zoom, 4);
        int cropW = image.getWidth() / factor;
        int cropH = image.getHeight() / factor;
        int cropX = (image.getWidth() - cropW) / 2;
        int cropY = (image.getHeight() - cropH) / 2;
        BufferedImage cropped = image.getSubimage(cropX, cropY, cropW, cropH);
        return CameraUtils.resize(cropped, image.getWidth(), image.getHeight());
    }

    public int queuedRenders() {
        return this.executor.getQueue().size();
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }
}
