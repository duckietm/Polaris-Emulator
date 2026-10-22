package com.eu.habbo.util.imager.badges;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.guilds.GuildPart;
import com.eu.habbo.habbohotel.guilds.GuildPartType;
import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgeImager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeImager.class);

    /** Badges waiting to be written; more are dropped (the client draws those itself). */
    private static final int DEFAULT_QUEUE_SIZE = 500;

    final Map<String, BufferedImage> cachedImages = new HashMap<>();
    private String partsFolder;
    private final GuildBadgeGenerator generator;
    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);
    private volatile Supplier<List<String>> guildBadges;

    public BadgeImager() {
        this.generator = new GuildBadgeGenerator(
                BadgeImager::outputFolder,
                this::compose,
                config().getInt("imager.badges.queue.size", DEFAULT_QUEUE_SIZE));

        if (config().getBoolean("imager.internal.enabled")) {
            if (this.reload()) {
                LOGGER.info("Badge Imager -> Loaded!");
            } else {
                LOGGER.warn("Badge Imager -> Disabled! Please check your configuration!");
            }
        }
    }

    /** The one place this class reads the hotel configuration. */
    private static ConfigurationManager config() {
        return Emulator.getConfig();
    }

    private static File outputFolder() {
        String folder = config().getValue("imager.location.output.badges", "");
        return (folder == null || folder.isEmpty()) ? null : new File(folder);
    }

    public static BufferedImage deepCopy(BufferedImage bi) {
        if (bi == null) return null;

        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public static void recolor(BufferedImage image, Color maskColor) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getRGB(x, y);

                if ((pixel >> 24) == 0x00) continue;

                Color color = new Color(pixel);

                float alpha = (color.getAlpha() / 255.0F) * (maskColor.getAlpha() / 255.0F);
                float red = (color.getRed() / 255.0F) * (maskColor.getRed() / 255.0F);
                float green = (color.getGreen() / 255.0F) * (maskColor.getGreen() / 255.0F);
                float blue = (color.getBlue() / 255.0F) * (maskColor.getBlue() / 255.0F);

                color = new Color(red, green, blue, alpha);

                int rgb = color.getRGB();
                image.setRGB(x, y, rgb);
            }
        }
    }

    public static Color colorFromHexString(String colorStr) {
        try {
            return new Color(Integer.valueOf(colorStr, 16));
        } catch (Exception e) {
            return new Color(0xffffff);
        }
    }

    public static Point getPoint(BufferedImage image, BufferedImage imagePart, int position) {
        int x = 0;
        int y = 0;

        if (position == 1) {
            x = (image.getWidth() - imagePart.getWidth()) / 2;
            y = 0;
        } else if (position == 2) {
            x = image.getWidth() - imagePart.getWidth();
            y = 0;
        } else if (position == 3) {
            x = 0;
            y = (image.getHeight() / 2) - (imagePart.getHeight() / 2);
        } else if (position == 4) {
            x = (image.getWidth() / 2) - (imagePart.getWidth() / 2);
            y = (image.getHeight() / 2) - (imagePart.getHeight() / 2);
        } else if (position == 5) {
            x = image.getWidth() - imagePart.getWidth();
            y = (image.getHeight() / 2) - (imagePart.getHeight() / 2);
        } else if (position == 6) {
            x = 0;
            y = image.getHeight() - imagePart.getHeight();
        } else if (position == 7) {
            x = ((image.getWidth() - imagePart.getWidth()) / 2);
            y = image.getHeight() - imagePart.getHeight();
        } else if (position == 8) {
            x = image.getWidth() - imagePart.getWidth();
            y = image.getHeight() - imagePart.getHeight();
        }

        return new Point(x, y);
    }

    public static BufferedImage convert32(BufferedImage src) {
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        ColorConvertOp cco = new ColorConvertOp(
                src.getColorModel().getColorSpace(), dest.getColorModel().getColorSpace(), null);
        return cco.filter(src, dest);
    }

    public synchronized boolean reload() {
        String folder = config().getValue("imager.location.badgeparts", "");
        this.partsFolder = folder;
        if (folder == null || folder.isEmpty()) {
            LOGGER.error("BadgeImager: imager.location.badgeparts is not set!");
            return false;
        }
        File file = new File(folder);
        if (!file.exists()) {
            LOGGER.error("BadgeImager output folder: {} does not exist!", folder);
            return false;
        }

        this.cachedImages.clear();
        try {
            for (Map.Entry<GuildPartType, ? extends Map<Integer, GuildPart>> set : Emulator.getGameEnvironment()
                    .getGuildManager()
                    .getGuildParts()
                    .entrySet()) {
                if (set.getKey() == GuildPartType.SYMBOL || set.getKey() == GuildPartType.BASE) {
                    for (Map.Entry<Integer, GuildPart> map : set.getValue().entrySet()) {
                        for (String part : Arrays.asList(map.getValue().valueA, map.getValue().valueB)) {
                            if (part == null || part.isEmpty()) {
                                continue;
                            }
                            this.cachedImages.put(part, readPart(file, part));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return false;
        }

        // New or changed parts: draw every guild badge that has no image yet (delete images to redraw them).
        this.backfill();

        return true;
    }

    /** The image of a part value, read safely, or null (logged) when it is unsafe, missing or too large. */
    private static BufferedImage readPart(File folder, String part) {
        File file = BadgePartFiles.partFile(folder, part);
        if (file == null) {
            LOGGER.warn("[Badge Imager] Ignoring badge part with an unsafe name: {}", part);
            return null;
        }
        try {
            return BadgePartFiles.read(file);
        } catch (Exception e) {
            LOGGER.info("[Badge Imager] Badge part {} not used: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * The image of a badge part value. Parts preloaded by {@link #reload()} come from the cache; any
     * other part is read once from {@code imager.location.badgeparts}, so a badge can be drawn even
     * when {@code imager.internal.enabled} is off (the camera renderer draws group badges this way).
     */
    synchronized BufferedImage partImage(String part) {
        if (part == null || part.isEmpty()) {
            return null;
        }
        if (this.cachedImages.containsKey(part)) {
            return this.cachedImages.get(part);
        }
        if (this.partsFolder == null) {
            this.partsFolder = config().getValue("imager.location.badgeparts", "");
        }
        BufferedImage image = this.partsFolder.isEmpty() ? null : readPart(new File(this.partsFolder), part);
        this.cachedImages.put(part, image);
        return image;
    }

    /**
     * Draws the 39x39 badge for a badge code without writing a file, or returns null when the code is
     * malformed or none of its parts could be drawn.
     */
    public BufferedImage compose(String badge) {
        GuildManager guildManager;
        try {
            guildManager = Emulator.getGameEnvironment().getGuildManager();
        } catch (RuntimeException e) {
            return null;
        }
        if (guildManager == null) {
            return null;
        }
        return GuildBadgeComposer.compose(badge, new GuildBadgeComposer.Parts() {
            @Override
            public GuildPart part(GuildPartType type, int id) {
                try {
                    return guildManager.getPart(type, id);
                } catch (RuntimeException e) {
                    return null;
                }
            }

            @Override
            public BufferedImage image(String partValue) {
                return BadgeImager.this.partImage(partValue);
            }
        });
    }

    /** True when the code names only parts and colours of this hotel ({@code guilds_elements}). */
    public boolean isValidBadge(String badge) {
        GuildManager guildManager;
        try {
            guildManager = Emulator.getGameEnvironment().getGuildManager();
        } catch (RuntimeException e) {
            return GuildBadgeComposer.isBadgeCode(badge);
        }
        if (guildManager == null || guildManager.getBases().isEmpty()) {
            // Parts not loaded: only the shape can be checked.
            return GuildBadgeComposer.isBadgeCode(badge);
        }
        return GuildBadgeValidator.isValid(badge, new GuildBadgeComposer.Parts() {
            @Override
            public GuildPart part(GuildPartType type, int id) {
                return guildManager.getPart(type, id);
            }

            @Override
            public BufferedImage image(String partValue) {
                return null;
            }
        });
    }

    /** Queues the guild's badge image; it is written in the background (see {@link GuildBadgeGenerator}). */
    public void generate(Guild guild) {
        if (guild != null) {
            this.generate(guild.getBadge());
        }
    }

    /** Queues a badge image; false when the code is not a valid badge of this hotel or the queue is full. */
    public boolean generate(String badge) {
        if (!this.isValidBadge(badge)) {
            LOGGER.warn("Not generating guild badge with a malformed or unknown code: {}", badge);
            return false;
        }
        return this.generator.submit(badge);
    }

    /**
     * Where the backfill finds the badges of all guilds (the boot passes the database); starts a backfill.
     */
    public void installGuildBadgeSource(Supplier<List<String>> guildBadges) {
        this.guildBadges = guildBadges;
        this.backfill();
    }

    /**
     * Draws every guild badge that has no image yet, on its own thread at the generator's pace. Runs after the
     * parts load (boot and :update_guildparts) unless {@code imager.badges.backfill} is off.
     */
    public void backfill() {
        if (!config().getBoolean("imager.internal.enabled")
                || !config().getBoolean("imager.badges.backfill", true)
                || outputFolder() == null
                || !this.backfillRunning.compareAndSet(false, true)) {
            return;
        }

        Supplier<List<String>> source = this.guildBadges;
        if (source == null) {
            this.backfillRunning.set(false);
            return;
        }

        Thread thread = new Thread(
                () -> {
                    int queued = 0;
                    try {
                        for (String badge : source.get()) {
                            File file = this.generator.outputFile(badge);
                            if (file == null || file.isFile() || !this.isValidBadge(badge)) {
                                continue;
                            }
                            if (this.generator.submitWaiting(badge)) {
                                queued++;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        LOGGER.error("[Badge Imager] Backfill failed", e);
                    } finally {
                        this.backfillRunning.set(false);
                    }
                    if (queued > 0) {
                        LOGGER.info("[Badge Imager] Drawing {} missing guild badge images", queued);
                    }
                },
                "polaris-badge-backfill");
        thread.setDaemon(true);
        thread.start();
    }
}
