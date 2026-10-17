package com.eu.habbo.imaging.camera;

import com.eu.habbo.imaging.camera.render.CameraRenderOptions;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/** Builds tiny synthetic bundles, folders and scenes for the camera renderer tests. */
public final class CameraTestAssets {
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;

    private CameraTestAssets() {}

    public static BufferedImage image(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    public static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    public static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    public static byte[] deflate(String text) {
        return deflate(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A .nitro bundle: short file count, then per file short name length, name, int length, deflated bytes. */
    public static byte[] nitro(String name, String json, BufferedImage sheet) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(name + ".json", json.getBytes(StandardCharsets.UTF_8));
        if (sheet != null) {
            files.put(name + ".png", png(sheet));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeShort(files.size());
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                byte[] fileName = file.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] data = deflate(file.getValue());
                out.writeShort(fileName.length);
                out.write(fileName);
                out.writeInt(data.length);
                out.write(data);
            }
        }
        return bytes.toByteArray();
    }

    public static Path writeNitro(Path dir, String name, String json, BufferedImage sheet) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".nitro");
        Files.write(file, nitro(name, json, sheet));
        return file;
    }

    public static void writePng(Path file, BufferedImage image) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, png(image));
    }

    /**
     * The scene JSON exactly as the official client composes it (fixed keys and spacing), sealed with
     * status, timestamp and checksum.
     */
    public static String airScene(
            String planes, String sprites, String filters, int roomId, int zoom, long clientMillis) {
        String body = "{ \"planes\" : " + planes + ",\"sprites\" : " + sprites + ",\"modifiers\" : {}"
                + ",\"filters\" : " + filters + ",\"roomid\" : " + roomId;
        if (zoom != 1) {
            body += ",\"zoom\" : " + zoom;
        }
        return AirRenderChecksum.seal(body, roomId, clientMillis, 0);
    }

    public static CameraRenderSettings settings(
            int timeoutMs, boolean verifyChecksum, int maxCompressed, int maxSprites) {
        return new CameraRenderSettings(
                CameraRenderMode.AUTO,
                timeoutMs,
                maxCompressed,
                1_048_576,
                64,
                maxSprites,
                16,
                verifyChecksum,
                0,
                new CameraRenderOptions(1_048_576, false, 0, 0, 100, 100, 1024, 1024, "", 0L),
                "",
                "",
                "http://localhost/camera/",
                "{\"t\":%timestamp%,\"s\":%room_id%,\"w\":\"%url%\"}");
    }
}
