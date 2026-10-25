package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.imaging.camera.CameraPhotoFiles;
import com.eu.habbo.imaging.camera.CameraRenderException;
import com.eu.habbo.imaging.camera.CameraRenderMode;
import com.eu.habbo.imaging.camera.CameraRenderRuntime;
import com.eu.habbo.imaging.camera.CameraRenderService;
import com.eu.habbo.imaging.camera.CameraRenderSettings;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraURLComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RenderRoom (3226). The body is a byte array: either the PNG the Octane client rendered itself,
 * or, from the official client, the zlib-deflated scene JSON the server renders. {@code
 * camera.render.mode} chooses which are accepted; see {@link CameraRenderMode}.
 */
public class CameraRoomPictureEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRoomPictureEvent.class);

    public static int CAMERA_RENDER_DELAY = 15;
    public static int MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2 MB max upload
    // Room photos are always 320x320, as the client renders them: an upload must be exactly this size.
    public static int MAX_IMAGE_WIDTH = 320;
    public static int MAX_IMAGE_HEIGHT = 320;
    public static int MAX_DAILY_RENDERS = 50;

    private ByteBuf image = null;

    @Override
    public void handle() {
        try {
            this.make();
        } finally {
            if (this.image != null) {
                this.image.release();
            }
        }
    }

    private void make() {
        Habbo habbo = this.client.getHabbo();
        if (!habbo.hasPermission("acc_camera")) {
            habbo.alert(text("camera.permission", null));
            return;
        }

        HabboInfo habboInfo = habbo.getHabboInfo();
        int timestamp = Emulator.getIntUnixTimestamp();

        int userId = habboInfo.getId();
        Integer lastAttempt = CameraQuota.lastAttempt(userId);

        if (lastAttempt != null) {
            int cameraTimestamp = lastAttempt;
            if (timestamp - cameraTimestamp < CAMERA_RENDER_DELAY) {
                String alertMessage = text("camera.wait", null)
                        .replace("%seconds%", Integer.toString(CAMERA_RENDER_DELAY - (timestamp - cameraTimestamp)));
                habbo.alert(alertMessage);
                if (habboInfo.getPhotoURL() != null) {
                    String[] splittedPhotoURL = habboInfo.getPhotoURL().split("/");
                    if (splittedPhotoURL.length > 0) {
                        this.client.sendResponse(new CameraURLComposer(splittedPhotoURL[splittedPhotoURL.length - 1]));
                    }
                }
                return;
            }
        }

        int dailyRenderCount = CameraQuota.dailyCount(userId, timestamp);
        if (dailyRenderCount >= MAX_DAILY_RENDERS) {
            habbo.alert(text("camera.daily.limit", "You have reached the daily photo limit. Try again tomorrow."));
            return;
        }
        Room room = habboInfo.getCurrentRoom();
        if (room == null) return;

        CameraQuota.markAttempt(userId, timestamp);

        int count = this.packet.readInt();
        if (count <= 0
                || count > MAX_IMAGE_BYTES
                || count > this.packet.getBuffer().readableBytes()) {
            LOGGER.warn("User {} attempted camera upload with invalid size: {} bytes", habboInfo.getUsername(), count);
            // The cooldown stays: giving it back let an invalid 400 KB payload be retried at once, without end.
            handleImageProcessingError(habbo);
            return;
        }
        this.image = this.packet.getBuffer().readBytes(count);

        CameraRenderService service = CameraRenderRuntime.service();
        ConfigurationManager config = Emulator.getConfig();
        CameraRenderMode mode = service != null
                ? service.settings().mode()
                : CameraRenderMode.parse(config == null ? null : config.getValue(CameraRenderSettings.MODE, "auto"));
        CameraRenderMode.Payload payload =
                CameraRenderMode.Payload.detect(ByteBufUtil.getBytes(this.image, 0, Math.min(4, count), true));
        CameraRenderMode.Decision decision =
                mode.decide(payload, service != null && service.isServerRenderingAvailable());

        switch (decision) {
            case STORE_UPLOAD -> {
                String url = this.storeUpload(habbo, habboInfo, room, timestamp, config);
                if (url == null) return;
                CameraQuota.recordPhoto(userId, timestamp);
                this.client.sendResponse(new CameraURLComposer(url));
            }
            case RENDER -> {
                if (!this.submitServerRender(service, habbo, habboInfo, room, timestamp)) {
                    CameraQuota.refundAttempt(userId);
                    return;
                }
                CameraQuota.recordPhoto(userId, timestamp);
            }
            default -> {
                logRefusal(decision, habboInfo.getUsername(), mode, "photo");
                CameraQuota.refundAttempt(userId);
                handleImageProcessingError(habbo);
            }
        }
    }

    static void logRefusal(CameraRenderMode.Decision decision, String username, CameraRenderMode mode, String what) {
        switch (decision) {
            case REFUSE_UPLOAD_DISABLED ->
                LOGGER.warn(
                        "Camera {} from {} refused: a PNG upload, but camera.render.mode is 'server'.", what, username);
            case REFUSE_RENDER_DISABLED ->
                LOGGER.warn(
                        "Camera {} from {} refused: scene JSON (official client), but camera.render.mode is 'upload'.",
                        what,
                        username);
            case REFUSE_RENDER_UNAVAILABLE ->
                LOGGER.warn(
                        "Camera {} from {} refused: scene JSON (official client) needs server rendering, but no "
                                + "camera.render.assets.* source is configured (mode '{}'). See docs/camera-server-render.md.",
                        what,
                        username,
                        mode.name().toLowerCase(java.util.Locale.ROOT));
            default ->
                LOGGER.warn("Camera {} from {} refused: the payload is neither a PNG nor zlib data.", what, username);
        }
    }

    /**
     * Queues the official client's scene for rendering. The room's state is captured here, on the
     * packet thread; the worker only draws, writes the files and replies.
     */
    private boolean submitServerRender(
            CameraRenderService service, Habbo habbo, HabboInfo habboInfo, Room room, int timestamp) {
        CameraRenderSettings settings = service.settings();
        if (this.image.readableBytes() > settings.maxCompressedBytes()) {
            LOGGER.warn(
                    "Camera photo from {} refused: scene is {} bytes compressed, limit {} ({}).",
                    habboInfo.getUsername(),
                    this.image.readableBytes(),
                    settings.maxCompressedBytes(),
                    CameraRenderSettings.MAX_COMPRESSED);
            handleImageProcessingError(habbo);
            return false;
        }
        byte[] scene = ByteBufUtil.getBytes(this.image);
        int roomId = room.getId();
        String failure = text("camera.error.creation", null);
        GameClient gameClient = this.client;

        CameraRenderService.Job job = new CameraRenderService.Job(
                CameraRenderService.Kind.PHOTO,
                scene,
                roomId,
                room.getBackgroundTonerColor().getRGB(),
                room.getWallPaint(),
                rendered -> {
                    try {
                        commitServerPhoto(gameClient, habboInfo, service.settings(), roomId, timestamp, rendered);
                    } catch (CameraRenderException e) {
                        LOGGER.error("Camera photo for {} not saved: {}", habboInfo.getUsername(), e.getMessage());
                        habbo.alert(failure);
                    }
                },
                error -> {
                    LOGGER.warn(
                            "Camera photo for {} (room {}) not rendered: {} ({})",
                            habboInfo.getUsername(),
                            roomId,
                            error.getMessage(),
                            error.reason());
                    habbo.alert(failure);
                });
        if (!service.submit(job)) {
            LOGGER.warn("Camera photo for {} refused: the render queue is full.", habboInfo.getUsername());
            habbo.alert(text("camera.render.busy", "The camera is busy right now. Please try again in a moment."));
            return false;
        }
        return true;
    }

    /**
     * Saves a server-rendered photo exactly as an uploaded one is saved ({@code
     * <userId>_<timestamp>.png} plus {@code _small}), sets the pending photo and replies with its URL.
     * Runs on a render worker.
     */
    static void commitServerPhoto(
            GameClient client,
            HabboInfo habboInfo,
            CameraRenderSettings settings,
            int roomId,
            int timestamp,
            BufferedImage rendered)
            throws CameraRenderException {
        String url =
                CameraPhotoFiles.writePhoto(rendered, settings.photoOutputDir(), habboInfo.getId() + "_" + timestamp);
        if (habboInfo.getPhotoTimestamp() > timestamp) {
            return; // a newer photo was committed meanwhile; keep it pending
        }
        String base = settings.photoUrlBase();
        String json = settings.photoExtradata()
                .replace("%timestamp%", Integer.toString(timestamp))
                .replace("%room_id%", Integer.toString(roomId))
                .replace("%url%", base + url);
        habboInfo.setPhotoURL(base + url);
        habboInfo.setPhotoRoomId(roomId);
        habboInfo.setPhotoJSON(json);
        habboInfo.setPhotoTimestamp(timestamp);
        client.sendResponse(new CameraURLComposer(url));
    }

    private String storeUpload(
            Habbo habbo, HabboInfo habboInfo, Room room, int timestamp, ConfigurationManager config) {
        int[] dimensions;
        try {
            dimensions = readPNGDimensions(this.image);
        } catch (IOException e) {
            LOGGER.warn("User {} uploaded image with unreadable dimensions", habboInfo.getUsername());
            handleImageProcessingError(habbo);
            return null;
        }

        if (!hasPhotoDimensions(dimensions)) {
            LOGGER.warn(
                    "User {} attempted camera upload with invalid dimensions: {}x{}",
                    habboInfo.getUsername(),
                    dimensions != null ? dimensions[0] : "null",
                    dimensions != null ? dimensions[1] : "null");
            handleImageProcessingError(habbo);
            return null;
        }

        BufferedImage theImage;
        try (ByteBufInputStream in = new ByteBufInputStream(this.image)) {
            theImage = ImageIO.read(in);
        } catch (IOException e) {
            handleImageProcessingError(habbo);
            return null;
        }

        if (theImage == null) {
            LOGGER.warn("User {} uploaded image that could not be decoded", habboInfo.getUsername());
            handleImageProcessingError(habbo);
            return null;
        }

        if (theImage.getWidth() != MAX_IMAGE_WIDTH || theImage.getHeight() != MAX_IMAGE_HEIGHT) {
            LOGGER.warn(
                    "User {} decoded image is not {}x{}: {}x{}",
                    habboInfo.getUsername(),
                    MAX_IMAGE_WIDTH,
                    MAX_IMAGE_HEIGHT,
                    theImage.getWidth(),
                    theImage.getHeight());
            handleImageProcessingError(habbo);
            return null;
        }

        String outputDir = config.getValue("imager.location.output.camera");
        if (outputDir == null || outputDir.isBlank()) {
            LOGGER.error("Camera photo not saved: config 'imager.location.output.camera' is empty.");
            handleImageProcessingError(habbo);
            return null;
        }

        File directory = new File(outputDir);
        if (!directory.exists() && !directory.mkdirs()) {
            LOGGER.error(
                    "Camera photo not saved: output folder {} does not exist and could not be created.",
                    directory.getAbsolutePath());
            handleImageProcessingError(habbo);
            return null;
        }
        if (!directory.isDirectory()) {
            LOGGER.error(
                    "Camera photo not saved: configured output path {} is not a directory.",
                    directory.getAbsolutePath());
            handleImageProcessingError(habbo);
            return null;
        }

        String fileName = habboInfo.getId() + "_" + timestamp;
        String url = fileName + ".png";
        String urlSmall = fileName + "_small.png";

        File imageFile = new File(directory, url);
        File smallImageFile = new File(directory, urlSmall);

        try {
            if (!ImageIO.write(theImage, "png", imageFile)) {
                LOGGER.error(
                        "Camera photo not saved: no PNG ImageWriter available (target {}).",
                        imageFile.getAbsolutePath());
                handleImageProcessingError(habbo);
                return null;
            }

            int smallWidth = Math.max(1, theImage.getWidth(null) / 2);
            int smallHeight = Math.max(1, theImage.getHeight(null) / 2);
            BufferedImage bi = new BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics2D = bi.createGraphics();
            graphics2D.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics2D.drawImage(theImage, 0, 0, smallWidth, smallHeight, null);
            graphics2D.dispose();
            ImageIO.write(bi, "png", smallImageFile);
        } catch (IOException e) {
            LOGGER.error("Camera photo could not be written to {}", imageFile.getAbsolutePath(), e);
            handleImageProcessingError(habbo);
            return null;
        }

        String base = config.getValue("camera.url");
        String json = config.getValue("camera.extradata")
                .replace("%timestamp%", Integer.toString(timestamp))
                .replace("%room_id%", Integer.toString(room.getId()))
                .replace("%url%", base + url);
        habboInfo.setPhotoURL(base + url);
        habboInfo.setPhotoTimestamp(timestamp);
        habboInfo.setPhotoRoomId(room.getId());
        habboInfo.setPhotoJSON(json);

        LOGGER.debug("Camera photo saved to {}", imageFile.getAbsolutePath());
        return url;
    }

    static boolean hasPhotoDimensions(int[] dimensions) {
        return dimensions != null
                && dimensions.length == 2
                && dimensions[0] == MAX_IMAGE_WIDTH
                && dimensions[1] == MAX_IMAGE_HEIGHT;
    }

    private int[] readPNGDimensions(ByteBuf buf) throws IOException {
        try (ByteBufInputStream in = new ByteBufInputStream(buf.duplicate())) {
            try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) return null;

                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    return new int[] {width, height};
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    private void handleImageProcessingError(Habbo habbo) {
        habbo.alert(text("camera.error.creation", null));
    }

    static String text(String key, String fallback) {
        return fallback == null
                ? Emulator.getTexts().getValue(key)
                : Emulator.getTexts().getValue(key, fallback);
    }
}
