package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.util.Arrays;
import java.util.Set;

/**
 * The source rules the official server applies to a wired save, before the box reads its settings.
 *
 * <ul>
 *   <li>A box with an input sources conf gets its missing furni and user slots filled with the defaults.
 *   <li>A furni source outside its slot's allowed list is refused with {@link #INVALID_FURNI_SOURCE}, unless the box
 *       already holds it in that slot.
 *   <li>The clicked user (11) and the reached user (10) are only accepted when the box's stack has a Click User or a
 *       Bot Reaches User trigger. Stored rows that already hold them still load.
 * </ul>
 *
 * <p>The official server refuses slots beyond the box's count. Our editors still send a slot for every source field
 * they keep, so extra slots are dropped instead.
 */
public final class WiredSourceSaveRules {
    public static final String INVALID_FURNI_SOURCE = "wiredfurni.error.invalid_furni_source";
    /** A clicked or reached user source under a stack without the trigger that names that user. */
    public static final String INVALID_USER_SOURCE = "wiredfurni.error.invalid_user_source";

    private static final String CLICK_USER_TRIGGER = "wf_trg_click_user";
    private static final String BOT_REACHED_USER_TRIGGER = "wf_trg_bot_reached_avtr";

    private WiredSourceSaveRules() {}

    /**
     * Fills and checks the sources of {@code settings} for {@code box}.
     *
     * @return the refusal key, or {@code null} when the save may go on
     */
    public static String apply(Object box, WiredSettings settings, Room room) {
        if (settings == null) return null;

        WiredInputSourcesConf conf = WiredInputSourcesConf.of(box);

        int[] furni = fill(settings.getFurniSourceTypes(), conf.allowedFurni().length, conf.defaultFurni());
        int[] users = fill(settings.getUserSourceTypes(), conf.allowedUsers().length, conf.defaultUsers());

        int[][] allowedFurni = conf.allowedFurni();
        int[] stored = storedFurniSources(box);
        for (int i = 0; i < allowedFurni.length; i++) {
            // A source the box already holds stays saveable, so rows stored before these rules can be re-saved.
            boolean kept = i < stored.length && stored[i] == furni[i];
            if (!kept && !contains(allowedFurni[i], furni[i])) return INVALID_FURNI_SOURCE;
        }

        for (int source : users) {
            if (source == WiredSourceUtil.SOURCE_CLICKED_USER && !stackHas(box, room, CLICK_USER_TRIGGER)) {
                return INVALID_USER_SOURCE;
            }
            if (source == WiredSourceUtil.SOURCE_REACHED_USER && !stackHas(box, room, BOT_REACHED_USER_TRIGGER)) {
                return INVALID_USER_SOURCE;
            }
        }

        settings.setFurniSourceTypes(furni);
        settings.setUserSourceTypes(users);
        return null;
    }

    /** {@code sent} with exactly {@code slots} entries, the missing ones taken from {@code defaults}. */
    static int[] fill(int[] sent, int slots, int[] defaults) {
        return fill(sent, slots, defaults, sent);
    }

    /**
     * {@code sent} with exactly {@code slots} entries, the missing ones taken from {@code defaults}; a box without
     * slots in its conf keeps {@code unchanged}.
     */
    static int[] fill(int[] sent, int slots, int[] defaults, int[] unchanged) {
        int[] values = sent != null ? sent : new int[0];
        if (slots == 0) return unchanged != null ? unchanged : values;

        int[] filled = Arrays.copyOf(values, slots);
        for (int i = values.length; i < slots; i++) {
            filled[i] = i < defaults.length ? defaults[i] : WiredSourceUtil.SOURCE_TRIGGER;
        }
        return filled;
    }

    private static int[] storedFurniSources(Object box) {
        if (box instanceof WiredOfficialSources sources) return sources.officialFurniSources();
        return WiredSourceFields.furniSources(box);
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) if (candidate == value) return true;
        return false;
    }

    /** Whether a trigger of interaction type {@code interaction} stands on the box's tile. */
    private static boolean stackHas(Object box, Room room, String interaction) {
        if (!(box instanceof HabboItem item) || room == null || room.getRoomSpecialTypes() == null) return false;

        if (isInteraction(item, interaction)) return true;

        Set<InteractionWiredTrigger> triggers = room.getRoomSpecialTypes().getTriggers(item.getX(), item.getY());
        for (InteractionWiredTrigger trigger : triggers) {
            if (isInteraction(trigger, interaction)) return true;
        }
        return false;
    }

    private static boolean isInteraction(HabboItem item, String interaction) {
        return item.getBaseItem() != null
                && item.getBaseItem().getInteractionType() != null
                && interaction.equalsIgnoreCase(
                        item.getBaseItem().getInteractionType().getName());
    }
}
