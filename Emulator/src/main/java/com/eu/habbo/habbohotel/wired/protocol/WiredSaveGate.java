package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import java.util.Arrays;

/**
 * The checks every wired save goes through before the box sees it, as AIR's server makes them.
 *
 * <ul>
 *   <li>More than the hotel's pick limit (20) in either furni list, repeats and dead ids counted, drops the save
 *       without an answer: the editor stays open and the box keeps its old picks. At or under the limit a repeated
 *       id is kept once.
 *   <li>An action's delay must be one the editor can send: 0..120 pulses, or -50..-60000 ms in steps of 50.
 *       Anything else is refused with {@link #INVALID_DELAY}. A stored longer delay still loads.
 * </ul>
 */
public final class WiredSaveGate {
    public static final String INVALID_DELAY = "wiredfurni.error.invalid_delay";
    public static final int DEFAULT_PICK_LIMIT = 20;
    public static final int MAX_EDITOR_PULSES = 120;
    public static final int MIN_PRECISE_DELAY_MS = -60_000;
    public static final int PRECISE_DELAY_STEP_MS = 50;

    private WiredSaveGate() {}

    /** Whether the save is dropped without an answer for carrying too many picks. */
    public static boolean dropsSilently(WiredSettings settings) {
        if (settings == null) {
            return false;
        }

        int limit = pickLimit();
        return settings.getRawPickCount() > limit || settings.getRawPickCount2() > limit;
    }

    /** Keeps each picked id once, in the order they were sent. */
    public static void keepPicksOnce(WiredSettings settings) {
        if (settings == null) {
            return;
        }

        settings.setFurniIds(distinct(settings.getFurniIds()));
        settings.setFurniIds2(distinct(settings.getFurniIds2()));
    }

    /** The refusal for an action's delay the editor could not have sent, or null. */
    public static String delayRefusal(WiredSettings settings) {
        if (settings == null || settings.getRawDelay() == null) {
            return null;
        }

        return isEditorDelay(settings.getRawDelay()) ? null : INVALID_DELAY;
    }

    public static boolean isEditorDelay(int delay) {
        if (delay >= 0) {
            return delay <= MAX_EDITOR_PULSES;
        }

        return delay >= MIN_PRECISE_DELAY_MS && delay % PRECISE_DELAY_STEP_MS == 0;
    }

    static int pickLimit() {
        ConfigurationManager config = WiredPlatform.configuration();
        int limit = config == null
                ? DEFAULT_PICK_LIMIT
                : config.getInt("hotel.wired.furni.selection.count", DEFAULT_PICK_LIMIT);
        return Math.max(1, limit);
    }

    private static int[] distinct(int[] ids) {
        return ids == null ? new int[0] : Arrays.stream(ids).distinct().toArray();
    }
}
