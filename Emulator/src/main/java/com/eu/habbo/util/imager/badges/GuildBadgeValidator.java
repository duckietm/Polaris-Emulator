package com.eu.habbo.util.imager.badges;

import com.eu.habbo.habbohotel.guilds.GuildPart;
import com.eu.habbo.habbohotel.guilds.GuildPartType;

/**
 * Checks a badge code against the hotel's badge parts ({@code guilds_elements}): the base must be a base, every
 * symbol a symbol (id 0 is an empty slot) and every used part's colour a badge colour. A code that passes only
 * ever names parts the admin installed, so the imager never reads anything else.
 */
public final class GuildBadgeValidator {
    private GuildBadgeValidator() {}

    public static boolean isValid(String badge, GuildBadgeComposer.Parts parts) {
        if (!GuildBadgeComposer.isBadgeCode(badge) || parts == null) {
            return false;
        }

        String[] codes = GuildBadgeComposer.split(badge);
        for (int i = 0; i < codes.length; i++) {
            String code = codes[i];
            char type = Character.toLowerCase(code.charAt(0));
            int id = Integer.parseInt(code.substring(1, 4));
            int color = Integer.parseInt(code.substring(4, 6));

            if (type != 'b' && type != 's') {
                return false;
            }
            // GuildBadgeBuilder writes the base first and symbols after it.
            if ((i == 0) != (type == 'b')) {
                return false;
            }
            if (type == 's' && id == 0) {
                continue;
            }

            GuildPart part = parts.part(type == 'b' ? GuildPartType.BASE : GuildPartType.SYMBOL, id);
            if (part == null || !BadgePartFiles.isSafePartValue(part.valueA)) {
                return false;
            }
            if (parts.part(GuildPartType.BASE_COLOR, color) == null) {
                return false;
            }
        }
        return true;
    }
}
