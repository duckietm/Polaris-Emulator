package com.eu.habbo.util.imager.badges;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildPart;
import com.eu.habbo.habbohotel.guilds.GuildPartType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BadgeImager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeImager.class);

    final Map<String, BufferedImage> cachedImages = new HashMap<>();

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
        if (bi == null)
            return null;

        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public static void recolor(BufferedImage image, Color maskColor) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getRGB(x, y);

                if ((pixel >> 24) == 0x00)
                    continue;

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
            return new Color(
                    Integer.valueOf(colorStr, 16));
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
        ColorConvertOp cco = new ColorConvertOp(src.getColorModel().getColorSpace(), dest.getColorModel().getColorSpace(), null);
        return cco.filter(src, dest);
    }

    public synchronized boolean reload() {
        File file = new File(Emulator.getConfig().getValue("imager.location.badgeparts"));
        if (!file.exists()) {
            LOGGER.error("BadgeImager output folder: {} does not exist!", Emulator.getConfig().getValue("imager.location.badgeparts"));
            return false;
        }

        this.cachedImages.clear();
        try {
            for (Map.Entry<GuildPartType, ? extends Map<Integer, GuildPart>> set : Emulator.getGameEnvironment().getGuildManager().getGuildParts().entrySet()) {
                if (set.getKey() == GuildPartType.SYMBOL || set.getKey() == GuildPartType.BASE) {
                    for (Map.Entry<Integer, GuildPart> map : set.getValue().entrySet()) {
                        for (String part : Arrays.asList(map.getValue().valueA, map.getValue().valueB)) {
                            if (!part.isEmpty()) {
                                try {
                                    this.cachedImages.put(part, ImageIO.read(new File(Emulator.getConfig().getValue("imager.location.badgeparts"), "badgepart_" + part.replace(".gif", ".png"))));
                                } catch (Exception e) {
                                    LOGGER.info("[Badge Imager] Missing Badge Part: {}/badgepart_{}", Emulator.getConfig().getValue("imager.location.badgeparts"), part.replace(".gif", ".png"));
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

    public void generate(Guild guild) {
        String badge = guild.getBadge();
        File outputFile;
        try {
            outputFile = new File(Emulator.getConfig().getValue("imager.location.output.badges"), badge + ".png");

            if (outputFile.exists())
                return;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return;
        }

        String[] parts = new String[]{"", "", "", "", ""};

        int count = 0;

        for (int i = 0; i < badge.length(); ) {
            if (i > 0) {
                if (i % 7 == 0) {
                    count++;
                }
            }

            for (int j = 0; j < 7; j++) {
                parts[count] += badge.charAt(i);
                i++;
            }
        }


        BufferedImage image = new BufferedImage(39, 39, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = image.getGraphics();

        for (String s : parts) {
            if (s.isEmpty())
                continue;

            String type = s.charAt(0) + "";
            int id = Integer.parseInt(s.substring(1, 4));
            int c = Integer.parseInt(s.substring(4, 6));
            int position = Integer.parseInt(s.substring(6));

            GuildPart part;
            GuildPart color = Emulator.getGameEnvironment().getGuildManager().getPart(GuildPartType.BASE_COLOR, c);

            if (type.equalsIgnoreCase("b")) {
                part = Emulator.getGameEnvironment().getGuildManager().getPart(GuildPartType.BASE, id);
            } else {
                part = Emulator.getGameEnvironment().getGuildManager().getPart(GuildPartType.SYMBOL, id);
            }

            if (part == null) continue;

            BufferedImage imagePart = BadgeImager.deepCopy(this.cachedImages.get(part.valueA));

            Point point;

            if (imagePart != null) {
                if (imagePart.getColorModel().getPixelSize() < 32) {
                    imagePart = convert32(imagePart);
                }

                point = getPoint(image, imagePart, position);

                recolor(imagePart, colorFromHexString(color.valueA));

                graphics.drawImage(imagePart, point.x, point.y, null);
            }

            if (!part.valueB.isEmpty()) {
                imagePart = BadgeImager.deepCopy(this.cachedImages.get(part.valueB));

                if (imagePart != null) {
                    if (imagePart.getColorModel().getPixelSize() < 32) {
                        imagePart = convert32(imagePart);
                    }

                    point = getPoint(image, imagePart, position);
                    graphics.drawImage(imagePart, point.x, point.y, null);
                }
            }
        }

        try {
            ImageIO.write(image, "PNG", outputFile);
        } catch (Exception e) {
            LOGGER.error("Failed to generate guild badge: {}.png Make sure the output folder exists and is writable!", outputFile);
        }

        graphics.dispose();
    }
}
