package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.imaging.camera.CameraPhotoFiles;
import com.eu.habbo.imaging.camera.CameraRenderException;
import com.eu.habbo.imaging.camera.CameraRenderMode;
import com.eu.habbo.imaging.camera.CameraRenderRuntime;
import com.eu.habbo.imaging.camera.CameraRenderService;
import com.eu.habbo.imaging.camera.CameraRenderSettings;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraRoomThumbnailSavedComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
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
 * RenderRoomThumbnail (1982): the room owner's navigator thumbnail, either a PNG the client
 * rendered or the official client's scene JSON, rendered here at 110x110. {@code
 * camera.render.mode} applies as for photos.
 */
public class CameraRoomThumbnailEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRoomThumbnailEvent.class);

    public static int CAMERA_RENDER_DELAY = 15;
    public static int MAX_THUMBNAIL_BYTES = 1024 * 1024; // 1 MB max for thumbnails
    public static int MAX_THUMBNAIL_WIDTH = 640;
    public static int MAX_THUMBNAIL_HEIGHT = 640;

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
            habbo.alert(text("camera.permission"));
            return;
        }

        HabboStats habboStats = habbo.getHabboStats();
        int timestamp = Emulator.getIntUnixTimestamp();

        if (habboStats.cache.containsKey("camera_render_cooldown")) {
            int cameraTimestamp = (Integer) habboStats.cache.get("camera_render_cooldown");
            if (timestamp - cameraTimestamp < CAMERA_RENDER_DELAY) {
                String alertMessage = text("camera.wait")
                        .replace("%seconds%", Integer.toString(CAMERA_RENDER_DELAY - (timestamp - cameraTimestamp)));
                habbo.alert(alertMessage);
                return;
            }
        }

        habboStats.cache.put("camera_render_cooldown", timestamp);
        HabboInfo habboInfo = habbo.getHabboInfo();
        Room room = habboInfo.getCurrentRoom();
        if (room == null || !room.isOwner(habbo)) return;

        int count = this.packet.readInt();

        // Reject oversized payloads before reading
        if (count <= 0
                || count > MAX_THUMBNAIL_BYTES
                || count > this.packet.getBuffer().readableBytes()) {
            LOGGER.warn(
                    "User {} attempted thumbnail upload with invalid size: {} bytes", habboInfo.getUsername(), count);
            habbo.alert(text("camera.error.creation"));
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
            case STORE_UPLOAD -> this.storeUpload(habbo, habboInfo, room, config);
            case RENDER -> this.submitServerRender(service, habbo, habboInfo, room);
            default -> {
                CameraRoomPictureEvent.logRefusal(decision, habboInfo.getUsername(), mode, "thumbnail");
                habbo.alert(text("camera.error.creation"));
            }
        }
    }

    private void submitServerRender(CameraRenderService service, Habbo habbo, HabboInfo habboInfo, Room room) {
        CameraRenderSettings settings = service.settings();
        if (this.image.readableBytes() > settings.maxCompressedBytes()) {
            LOGGER.warn(
                    "Camera thumbnail from {} refused: scene is {} bytes compressed, limit {}.",
                    habboInfo.getUsername(),
                    this.image.readableBytes(),
                    settings.maxCompressedBytes());
            habbo.alert(text("camera.error.creation"));
            return;
        }
        int roomId = room.getId();
        String failure = text("camera.error.creation");
        GameClient gameClient = this.client;
        CameraRenderService.Job job = new CameraRenderService.Job(
                CameraRenderService.Kind.THUMBNAIL,
                ByteBufUtil.getBytes(this.image),
                roomId,
                room.getBackgroundTonerColor().getRGB(),
                room.getWallPaint(),
                rendered -> {
                    try {
                        CameraPhotoFiles.writeThumbnail(
                                rendered, service.settings().thumbnailOutputDir(), roomId);
                        gameClient.sendResponse(new CameraRoomThumbnailSavedComposer());
                    } catch (CameraRenderException e) {
                        LOGGER.error("Thumbnail for room {} not saved: {}", roomId, e.getMessage());
                        habbo.alert(failure);
                    }
                },
                error -> {
                    LOGGER.warn(
                            "Thumbnail for room {} by {} not rendered: {} ({})",
                            roomId,
                            habboInfo.getUsername(),
                            error.getMessage(),
                            error.reason());
                    habbo.alert(failure);
                });
        if (!service.submit(job)) {
            LOGGER.warn("Camera thumbnail for {} refused: the render queue is full.", habboInfo.getUsername());
            habbo.alert(CameraRoomPictureEvent.text(
                    "camera.render.busy", "The camera is busy right now. Please try again in a moment."));
        }
    }

    private void storeUpload(Habbo habbo, HabboInfo habboInfo, Room room, ConfigurationManager config) {
        // Validate dimensions before fully decoding (prevents decompression bombs)
        int[] dimensions;
        try {
            dimensions = readPNGDimensions(this.image);
        } catch (IOException e) {
            LOGGER.warn("User {} uploaded thumbnail with unreadable dimensions", habboInfo.getUsername());
            habbo.alert(text("camera.error.creation"));
            return;
        }

        if (dimensions == null
                || dimensions[0] <= 0
                || dimensions[1] <= 0
                || dimensions[0] > MAX_THUMBNAIL_WIDTH
                || dimensions[1] > MAX_THUMBNAIL_HEIGHT) {
            LOGGER.warn(
                    "User {} attempted thumbnail upload with invalid dimensions: {}x{}",
                    habboInfo.getUsername(),
                    dimensions != null ? dimensions[0] : "null",
                    dimensions != null ? dimensions[1] : "null");
            habbo.alert(text("camera.error.creation"));
            return;
        }

        BufferedImage theImage;
        try (ByteBufInputStream in = new ByteBufInputStream(this.image)) {
            theImage = ImageIO.read(in);
        } catch (IOException e) {
            LOGGER.error("Failed to decode thumbnail from user {}", habboInfo.getUsername(), e);
            habbo.alert(text("camera.error.creation"));
            return;
        }

        if (theImage == null) {
            LOGGER.warn("User {} uploaded thumbnail that could not be decoded", habboInfo.getUsername());
            habbo.alert(text("camera.error.creation"));
            return;
        }

        // Double-check decoded dimensions
        if (theImage.getWidth() > MAX_THUMBNAIL_WIDTH || theImage.getHeight() > MAX_THUMBNAIL_HEIGHT) {
            LOGGER.warn(
                    "User {} decoded thumbnail exceeds dimension limits: {}x{}",
                    habboInfo.getUsername(),
                    theImage.getWidth(),
                    theImage.getHeight());
            habbo.alert(text("camera.error.creation"));
            return;
        }

        File imageFile = new File(config.getValue("imager.location.output.thumbnail") + room.getId() + ".png");
        try {
            ImageIO.write(theImage, "png", imageFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write thumbnail for room {}", room.getId(), e);
            habbo.alert(text("camera.error.creation"));
            return;
        }

        this.client.sendResponse(new CameraRoomThumbnailSavedComposer());
    }

    private static String text(String key) {
        return CameraRoomPictureEvent.text(key, null);
    }

    /**
     * Read PNG dimensions from the IHDR chunk without fully decoding the image.
     * This prevents decompression bomb attacks by checking dimensions before allocation.
     */
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
}
