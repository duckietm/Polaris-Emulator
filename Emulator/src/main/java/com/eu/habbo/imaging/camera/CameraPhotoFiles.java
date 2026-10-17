package com.eu.habbo.imaging.camera;

import com.eu.habbo.imaging.camera.render.CameraUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Writes server-rendered photos and thumbnails where the upload path writes them. */
public final class CameraPhotoFiles {
    private CameraPhotoFiles() {}

    /**
     * Writes {@code <fileName>.png} and the 110x110 {@code <fileName>_small.png} into {@code
     * outputDir} and returns the photo's file name ({@code <userId>_<timestamp>.png}).
     */
    public static String writePhoto(BufferedImage image, String outputDir, String fileName)
            throws CameraRenderException {
        File directory = directory(outputDir, "imager.location.output.camera");
        File photo = new File(directory, fileName + ".png");
        File small = new File(directory, fileName + "_small.png");
        try {
            write(image, photo);
            write(
                    CameraUtils.resize(image, CameraConstants.ROOM_RENDER_WIDTH, CameraConstants.ROOM_RENDER_HEIGHT),
                    small);
        } catch (IOException e) {
            throw new CameraRenderException(
                    CameraRenderException.Reason.WRITE_FAILED, "could not write " + photo.getAbsolutePath(), e);
        }
        return photo.getName();
    }

    /** Writes the navigator thumbnail {@code <roomId>.png}. */
    public static File writeThumbnail(BufferedImage image, String outputDir, int roomId) throws CameraRenderException {
        File directory = directory(outputDir, "imager.location.output.thumbnail");
        File thumbnail = new File(directory, roomId + ".png");
        try {
            write(image, thumbnail);
        } catch (IOException e) {
            throw new CameraRenderException(
                    CameraRenderException.Reason.WRITE_FAILED, "could not write " + thumbnail.getAbsolutePath(), e);
        }
        return thumbnail;
    }

    private static File directory(String outputDir, String key) throws CameraRenderException {
        if (outputDir == null || outputDir.isBlank()) {
            throw new CameraRenderException(CameraRenderException.Reason.WRITE_FAILED, "config '" + key + "' is empty");
        }
        File directory = new File(outputDir);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new CameraRenderException(
                    CameraRenderException.Reason.WRITE_FAILED,
                    "output folder " + directory.getAbsolutePath() + " does not exist and could not be created");
        }
        return directory;
    }

    private static void write(BufferedImage image, File file) throws IOException {
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("no PNG writer available");
        }
    }
}
