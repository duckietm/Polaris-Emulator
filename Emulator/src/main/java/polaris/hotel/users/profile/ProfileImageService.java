package polaris.hotel.users.profile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.core.ConfigurationManager;
import polaris.database.SqlQueries;

/**
 * The pictures a player uploads for the banner and the background of their profile window. The client
 * crops a picture to the exact size of its place and sends it as JPEG or PNG; it is decoded here and
 * written again as a fresh JPEG, so nothing but the pixels of the original reaches the disk.
 */
public class ProfileImageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileImageService.class);

    public static final String ENABLED_SETTING = "profile.images.enabled";
    public static final String LOCATION_SETTING = "profile.images.location";
    public static final String COOLDOWN_SETTING = "profile.images.cooldown_seconds";
    public static final String DEFAULT_LOCATION = "profile-images";
    public static final int DEFAULT_COOLDOWN_SECONDS = 60;

    /** Under the 500 000 bytes the HTTP aggregator accepts for a whole request. */
    public static final int MAX_BYTES = 460_800;

    private static final Pattern FILE_NAME = Pattern.compile("[0-9]+_(banner|background)_[0-9a-f]{16}\\.jpg");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Where a picture goes, the column that names its file, and the exact size the client crops it to. */
    public enum Kind {
        BANNER("banner", "profile_banner_image", 1280, 220),
        BACKGROUND("background", "profile_background_image", 1280, 1380);

        public final String key;
        public final String column;
        public final int width;
        public final int height;

        Kind(String key, String column, int width, int height) {
            this.key = key;
            this.column = column;
            this.width = width;
            this.height = height;
        }

        public static Kind fromKey(String key) {
            for (Kind kind : values()) {
                if (kind.key.equalsIgnoreCase(key)) return kind;
            }
            return null;
        }
    }

    /** Why an upload was refused; the client shows its own text for each code. */
    public static final class ProfileImageException extends Exception {
        private final String code;

        public ProfileImageException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return this.code;
        }
    }

    private static final ProfileImageService INSTANCE = new ProfileImageService();

    /** The hotel's settings for uploads, read from its configuration by whoever owns it. */
    public record Settings(boolean enabled, String location, int cooldownSeconds) {
        public static final Settings DEFAULTS = new Settings(true, DEFAULT_LOCATION, DEFAULT_COOLDOWN_SECONDS);

        public static Settings from(ConfigurationManager configuration) {
            if (configuration == null) return DEFAULTS;
            String location = configuration.getValue(LOCATION_SETTING, DEFAULT_LOCATION);
            return new Settings(
                    configuration.getBoolean(ENABLED_SETTING, true),
                    location == null || location.isBlank() ? DEFAULT_LOCATION : location,
                    Math.max(0, configuration.getInt(COOLDOWN_SETTING, DEFAULT_COOLDOWN_SECONDS)));
        }
    }

    private final Map<Integer, Long> lastUpload = new ConcurrentHashMap<>();
    private volatile Settings settings = Settings.DEFAULTS;

    public static ProfileImageService get() {
        return INSTANCE;
    }

    /** Takes the hotel's current settings; the HTTP handler passes them as it is set up for a connection. */
    public void configure(Settings settings) {
        this.settings = settings == null ? Settings.DEFAULTS : settings;
    }

    /** The stored file names of a user's banner and background pictures, "" for each one they do not have. */
    public static String[] storedNames(int userId) {
        return SqlQueries.queryOne(
                        "SELECT profile_banner_image, profile_background_image FROM users WHERE id = ? LIMIT 1",
                        set -> new String[] {nullToEmpty(set.getString(1)), nullToEmpty(set.getString(2))},
                        userId)
                .orElse(new String[] {"", ""});
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Checks and stores a picture, replaces the previous one of the same kind, and returns the new file name. */
    public String store(int userId, Kind kind, byte[] bytes) throws ProfileImageException {
        Settings current = this.settings;
        if (!current.enabled()) {
            throw new ProfileImageException("disabled", "Profile pictures are turned off.");
        }
        if (isBlocked(userId)) {
            throw new ProfileImageException("blocked", "This user may not upload profile pictures.");
        }

        long now = System.currentTimeMillis();
        long cooldown = current.cooldownSeconds() * 1000L;
        Long last = this.lastUpload.get(userId);
        if (last != null && now - last < cooldown) {
            throw new ProfileImageException("rate_limited", "Wait before uploading again.");
        }

        BufferedImage image = decode(kind, bytes);
        String fileName = userId + "_" + kind.key + "_" + randomHex() + ".jpg";
        Path directory = directory();
        Path target = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);
            Files.write(target, encodeJpeg(image));
        } catch (IOException e) {
            LOGGER.error("Could not write the profile picture {}", target, e);
            throw new ProfileImageException("failed", "The picture could not be written.");
        }

        this.lastUpload.put(userId, now);
        String previous = replace(userId, kind, fileName);
        deleteFile(previous);
        return fileName;
    }

    /** Takes the picture of one kind off a profile. */
    public void remove(int userId, Kind kind) {
        deleteFile(replace(userId, kind, null));
    }

    /** Removes both pictures; with {@code blocked} set, also sets or clears the flag that stops the user uploading. */
    public void removeAll(int userId, Boolean blocked) {
        for (Kind kind : Kind.values()) {
            remove(userId, kind);
        }
        if (blocked != null) setBlocked(userId, blocked);
    }

    public void setBlocked(int userId, boolean blocked) {
        SqlQueries.update("UPDATE users SET profile_images_blocked = ? WHERE id = ?", blocked ? 1 : 0, userId);
    }

    /** The stored file for a name the client asked for, or null when the name is not one this service writes. */
    public Path resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) return null;

        Path root = directory().toAbsolutePath().normalize();
        Path file = root.resolve(fileName).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        return file;
    }

    static boolean isValidFileName(String fileName) {
        return fileName != null && FILE_NAME.matcher(fileName).matches();
    }

    /** Decodes a JPEG or PNG of exactly the kind's size, reading the size before the pixels. */
    static BufferedImage decode(Kind kind, byte[] bytes) throws ProfileImageException {
        if (bytes == null || bytes.length == 0) {
            throw new ProfileImageException("invalid", "The request holds no picture.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ProfileImageException("too_large", "The picture is larger than " + MAX_BYTES + " bytes.");
        }
        if (!isJpeg(bytes) && !isPng(bytes)) {
            throw new ProfileImageException("invalid", "Only JPEG and PNG pictures are accepted.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new ProfileImageException("invalid", "The picture cannot be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ProfileImageException("invalid", "The picture cannot be read.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != kind.width || reader.getHeight(0) != kind.height) {
                    throw new ProfileImageException(
                            "invalid", "A " + kind.key + " is " + kind.width + "x" + kind.height + " pixels.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new ProfileImageException("invalid", "The picture cannot be read.");
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ProfileImageException("invalid", "The picture cannot be read.");
        }
    }

    static byte[] encodeJpeg(BufferedImage source) throws IOException {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] bytes) {
        return bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    private static boolean isBlocked(int userId) {
        return SqlQueries.queryOne(
                                "SELECT profile_images_blocked FROM users WHERE id = ? LIMIT 1",
                                set -> set.getInt(1),
                                userId)
                        .orElse(0)
                == 1;
    }

    /** Writes the new file name (or none) and returns the file it replaced. */
    private static String replace(int userId, Kind kind, String fileName) {
        String previous = SqlQueries.queryOne(
                        "SELECT " + kind.column + " FROM users WHERE id = ? LIMIT 1", set -> set.getString(1), userId)
                .orElse(null);
        SqlQueries.update("UPDATE users SET " + kind.column + " = ? WHERE id = ?", fileName, userId);
        return previous;
    }

    private void deleteFile(String fileName) {
        if (!isValidFileName(fileName)) return;
        try {
            Files.deleteIfExists(directory().resolve(fileName));
        } catch (IOException e) {
            LOGGER.warn("Could not delete the old profile picture {}", fileName, e);
        }
    }

    private Path directory() {
        return Path.of(this.settings.location());
    }

    private static String randomHex() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
