package com.eu.habbo.util.imager.badges;

import com.eu.habbo.habbohotel.guilds.GuildPart;
import com.eu.habbo.habbohotel.guilds.GuildPartType;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.regex.Pattern;

/**
 * Draws a guild badge from its code, the way {@link BadgeImager} always has: a 39x39 picture with
 * up to five parts of seven characters each ({@code b} base or {@code s} symbol, a three digit part
 * id, a two digit colour id and a one digit position). A part draws its first image recoloured with
 * the colour's hex value, then its second image as is.
 *
 * <p>Nothing here touches the emulator: the part definitions (the {@code guilds_elements} table)
 * and the part images ({@code badgepart_<name>.png}) come from the caller.
 */
public final class GuildBadgeComposer {
    public static final int SIZE = 39;
    private static final Pattern BADGE_CODE = Pattern.compile("(?:[A-Za-z]\\d{6}){1,5}");

    /** Where the composer reads part definitions and part images. */
    public interface Parts {
        GuildPart part(GuildPartType type, int id);

        /** The image of a part value ({@code valueA}/{@code valueB}), or null when missing. */
        BufferedImage image(String partValue);
    }

    private GuildBadgeComposer() {}

    /** True for a code of one to five seven-character parts. */
    public static boolean isBadgeCode(String badge) {
        return badge != null && BADGE_CODE.matcher(badge).matches();
    }

    /** The parts of a badge code, seven characters each. */
    static String[] split(String badge) {
        String[] parts = new String[badge.length() / 7];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = badge.substring(i * 7, i * 7 + 7);
        }
        return parts;
    }

    /** The badge picture, or null when the code is malformed or no part image could be drawn. */
    public static BufferedImage compose(String badge, Parts parts) {
        if (!isBadgeCode(badge) || parts == null) {
            return null;
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        boolean drawn = false;
        try {
            for (String s : split(badge)) {
                String type = s.substring(0, 1);
                int id = Integer.parseInt(s.substring(1, 4));
                int colorId = Integer.parseInt(s.substring(4, 6));
                int position = Integer.parseInt(s.substring(6));

                GuildPart part = parts.part(type.equalsIgnoreCase("b") ? GuildPartType.BASE : GuildPartType.SYMBOL, id);
                if (part == null) {
                    continue;
                }
                GuildPart color = parts.part(GuildPartType.BASE_COLOR, colorId);

                BufferedImage first = prepare(parts.image(part.valueA));
                if (first != null) {
                    Point point = BadgeImager.getPoint(image, first, position);
                    BadgeImager.recolor(first, BadgeImager.colorFromHexString(color == null ? "ffffff" : color.valueA));
                    graphics.drawImage(first, point.x, point.y, null);
                    drawn = true;
                }

                if (part.valueB != null && !part.valueB.isEmpty()) {
                    BufferedImage second = prepare(parts.image(part.valueB));
                    if (second != null) {
                        Point point = BadgeImager.getPoint(image, second, position);
                        graphics.drawImage(second, point.x, point.y, null);
                        drawn = true;
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        return drawn ? image : null;
    }

    private static BufferedImage prepare(BufferedImage source) {
        BufferedImage copy = BadgeImager.deepCopy(source);
        if (copy != null && copy.getColorModel().getPixelSize() < 32) {
            copy = BadgeImager.convert32(copy);
        }
        return copy;
    }
}
