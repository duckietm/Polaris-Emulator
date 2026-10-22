package com.eu.habbo.habbohotel.guilds;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.util.imager.badges.BadgeImager;
import com.eu.habbo.util.imager.badges.GuildBadgeComposer;

/**
 * The check a badge from a client must pass before a guild is created with it or switches to it: only parts
 * and colours of this hotel ({@code guilds_elements}). {@code hotel.guilds.badge.validate_parts} = 0 turns the
 * part check off and only keeps the code shape.
 */
public final class GuildBadgeInput {
    private GuildBadgeInput() {}

    public static boolean isAcceptable(String badge, ConfigurationManager config, BadgeImager imager) {
        if (!GuildBadgeComposer.isBadgeCode(badge)) {
            return false;
        }
        if (config != null && !config.getBoolean("hotel.guilds.badge.validate_parts", true)) {
            return true;
        }
        return imager == null || imager.isValidBadge(badge);
    }
}
