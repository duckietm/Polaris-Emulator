package com.eu.habbo.util.imager.badges;

import com.eu.habbo.Emulator;
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
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadgeImager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeImager.class);

    final Map<String, BufferedImage> cachedImages = new HashMap<>();
    private String partsFolder;

    public BadgeImager() {
        if (Emulator.getConfig().getBoolean("imager.internal.enabled")) {
            if (this.reload()) {
                LOGGER.info("Badge Imager -> Loaded!");
            } else {
                LOGGER.warn("Badge Imager -> Disabled! Please check your configuration!");
            }
        }
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
        String folder = Emulator.getConfig().getValue("imager.location.badgeparts");
        this.partsFolder = folder;
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
                            if (!part.isEmpty()) {
                                try {
                                    this.cachedImages.put(part, ImageIO.read(partFile(folder, part)));
                                } catch (Exception e) {
                                    LOGGER.info(
                                            "[Badge Imager] Missing Badge Part: {}/badgepart_{}",
                                            folder,
                                            part.replace(".gif", ".png"));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return false;
        }

        return true;
    }

    private static File partFile(String folder, String part) {
        return new File(folder, "badgepart_" + part.replace(".gif", ".png"));
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
            this.partsFolder = Emulator.getConfig().getValue("imager.location.badgeparts", "");
        }
        BufferedImage image = null;
        File file = partFile(this.partsFolder, part);
        if (!this.partsFolder.isEmpty()
                && file.getName().equals("badgepart_" + part.replace(".gif", ".png"))
                && file.isFile()) {
            try {
                image = ImageIO.read(file);
            } catch (Exception e) {
                LOGGER.debug("[Badge Imager] Could not read badge part {}", file, e);
            }
        }
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

    public void generate(Guild guild) {
        String badge = guild.getBadge();
        File outputFile;
        try {
            outputFile = new File(Emulator.getConfig().getValue("imager.location.output.badges"), badge + ".png");

            if (outputFile.exists()) return;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return;
        }

        if (!GuildBadgeComposer.isBadgeCode(badge)) {
            LOGGER.warn("Not generating guild badge with a malformed code: {}", badge);
            return;
        }

        BufferedImage image = this.compose(badge);
        if (image == null) {
            image = new BufferedImage(GuildBadgeComposer.SIZE, GuildBadgeComposer.SIZE, BufferedImage.TYPE_INT_ARGB);
        }

        try {
            ImageIO.write(image, "PNG", outputFile);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to generate guild badge: {}.png Make sure the output folder exists and is writable!",
                    outputFile);
        }
    }
}
