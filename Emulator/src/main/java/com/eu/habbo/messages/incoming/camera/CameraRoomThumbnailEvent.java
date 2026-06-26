package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraRoomThumbnailSavedComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

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
            habbo.alert(Emulator.getTexts().getValue("camera.permission"));
            return;
        }

        HabboStats habboStats = habbo.getHabboStats();
        int timestamp = Emulator.getIntUnixTimestamp();

        if (habboStats.cache.containsKey("camera_render_cooldown")) {
            int cameraTimestamp = (Integer) habboStats.cache.get("camera_render_cooldown");
            if (timestamp - cameraTimestamp < CAMERA_RENDER_DELAY) {
                String alertMessage = Emulator.getTexts().getValue("camera.wait").replace("%seconds%", Integer.toString(CAMERA_RENDER_DELAY - (timestamp - cameraTimestamp)));
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
        if (count <= 0 || count > MAX_THUMBNAIL_BYTES) {
            LOGGER.warn("User {} attempted thumbnail upload with invalid size: {} bytes", habboInfo.getUsername(), count);
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        this.image = this.packet.getBuffer().readBytes(count);
        if (this.image == null || !isValidImage(this.image)) {
            LOGGER.warn("User {} attempted thumbnail upload with non-PNG data", habboInfo.getUsername());
            return;
        }

        // Validate dimensions before fully decoding (prevents decompression bombs)
        int[] dimensions;
        try {
            dimensions = readPNGDimensions(this.image);
        } catch (IOException e) {
            LOGGER.warn("User {} uploaded thumbnail with unreadable dimensions", habboInfo.getUsername());
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        if (dimensions == null || dimensions[0] <= 0 || dimensions[1] <= 0
                || dimensions[0] > MAX_THUMBNAIL_WIDTH || dimensions[1] > MAX_THUMBNAIL_HEIGHT) {
            LOGGER.warn("User {} attempted thumbnail upload with invalid dimensions: {}x{}",
                    habboInfo.getUsername(),
                    dimensions != null ? dimensions[0] : "null",
                    dimensions != null ? dimensions[1] : "null");
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        BufferedImage theImage;
        try (var in = new ByteBufInputStream(this.image)) {
            theImage = ImageIO.read(in);
        } catch (IOException e) {
            LOGGER.error("Failed to decode thumbnail from user {}", habboInfo.getUsername(), e);
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        if (theImage == null) {
            LOGGER.warn("User {} uploaded thumbnail that could not be decoded", habboInfo.getUsername());
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        // Double-check decoded dimensions
        if (theImage.getWidth() > MAX_THUMBNAIL_WIDTH || theImage.getHeight() > MAX_THUMBNAIL_HEIGHT) {
            LOGGER.warn("User {} decoded thumbnail exceeds dimension limits: {}x{}", habboInfo.getUsername(), theImage.getWidth(), theImage.getHeight());
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        var imageFile = new File(Emulator.getConfig().getValue("imager.location.output.thumbnail") + room.getId() + ".png");
        try {
            ImageIO.write(theImage, "png", imageFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write thumbnail for room {}", room.getId(), e);
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        this.client.sendResponse(new CameraRoomThumbnailSavedComposer());
    }

    private boolean isValidImage(ByteBuf imageBuffer) {
        byte[] imageBytes = ByteBufUtil.getBytes(imageBuffer, 0, 4, true);
        return imageBytes != null && imageBytes.length >= 4
                && imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50
                && imageBytes[2] == 0x4E && imageBytes[3] == 0x47;
    }

    /**
     * Read PNG dimensions from the IHDR chunk without fully decoding the image.
     * This prevents decompression bomb attacks by checking dimensions before allocation.
     */
    private int[] readPNGDimensions(ByteBuf buf) throws IOException {
        try (var in = new ByteBufInputStream(buf.duplicate())) {
            try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) return null;

                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    return new int[]{width, height};
                } finally {
                    reader.dispose();
                }
            }
        }
    }
}
