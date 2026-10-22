package com.eu.habbo.util.imager.badges;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Reads badge part images ({@code badgepart_<name>.png}) safely: the name may only hold letters, digits,
 * {@code _} and {@code -} (so it can never leave the parts folder), the file must be a small PNG or GIF, and
 * its size is read from the header before any pixel is decoded, so an oversized or crafted image is refused
 * without using memory or CPU.
 */
public final class BadgePartFiles {
    /** A part is drawn inside the 39x39 badge, so it can never be larger. */
    public static final int MAX_DIMENSION = GuildBadgeComposer.SIZE;

    /** The bundled parts are under 2 KB; anything far larger is not a badge part. */
    public static final long MAX_FILE_BYTES = 64 * 1024;

    private static final Pattern PART_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private BadgePartFiles() {}

    /** The part name of a {@code guilds_elements} value: {@code symbol_star.gif} gives {@code symbol_star}. */
    public static String partName(String value) {
        if (value == null) {
            return null;
        }
        String name = value.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".gif")) {
            name = name.substring(0, name.length() - 4);
        }
        return PART_NAME.matcher(name).matches() ? name : null;
    }

    /** True when the value names a part that may be read from the parts folder. */
    public static boolean isSafePartValue(String value) {
        return partName(value) != null;
    }

    /** The file of a part value in the parts folder, or null for a value that is not a safe part name. */
    public static File partFile(File folder, String value) {
        String name = partName(value);
        return (folder == null || name == null) ? null : new File(folder, "badgepart_" + name + ".png");
    }

    /**
     * The decoded part image, or null when the file is missing, too large, not a PNG/GIF, or larger than the
     * badge. The reason is thrown as an {@link IOException} for the caller to log.
     */
    public static BufferedImage read(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("missing");
        }
        if (file.length() > MAX_FILE_BYTES) {
            throw new IOException("file is " + file.length() + " bytes, more than " + MAX_FILE_BYTES);
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new IOException("unreadable");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("not an image");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("gif")) {
                    throw new IOException("format " + format + " is not PNG or GIF");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IOException("image is " + width + "x" + height + ", parts are at most " + MAX_DIMENSION
                            + "x" + MAX_DIMENSION);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
