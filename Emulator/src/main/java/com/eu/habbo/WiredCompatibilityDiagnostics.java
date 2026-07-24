package com.eu.habbo;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rate-limited, payload-free diagnostics for legacy WIRED compatibility fallbacks. */
public final class WiredCompatibilityDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredCompatibilityDiagnostics.class);
    private static final long LOG_INTERVAL_MS = 60_000L;
    private static final AtomicLongArray NEXT_LOG_AT = new AtomicLongArray(FailurePoint.values().length);

    /** Finite call-site identifiers keep diagnostic state bounded and make every fallback auditable. */
    public enum FailurePoint {
        LEGACY_DATA_LIST_ENTRY,
        CHEST_STORAGE_NUMERIC_FIELD,
        CONTRACT_POSTER_INDEX,
        CONDITION_FURNI_TYPE_ID,
        CONDITION_HABBO_COUNT_LEGACY,
        CONDITION_MATCH_POSITION_ITEM,
        CONDITION_NOT_HABBO_COUNT_LEGACY,
        CONDITION_FURNI_INPUT_ID,
        EFFECT_CHANGE_VARIABLE_SELECTION,
        EFFECT_GIVE_BADGE_LEGACY,
        EFFECT_GIVE_HAND_ITEM,
        EFFECT_GIVE_RESPECT_LEGACY,
        EFFECT_GIVE_REWARD_ROW,
        EFFECT_GIVE_VARIABLE_LEGACY,
        EFFECT_LOG_LEGACY,
        EFFECT_MOVE_FURNI_TO_LEGACY,
        EFFECT_MOVE_ROTATE_LEGACY,
        EFFECT_MOVE_ROTATE_ITEM,
        EFFECT_MUTE_HABBO_LEGACY,
        EFFECT_REMOVE_VARIABLE_LEGACY,
        EFFECT_SEND_SIGNAL_SELECTION,
        EFFECT_USER_FURNI_MOVE_STATUS,
        EXTRA_LEVEL_UP_JSON,
        EXTRA_TEXT_CONNECTOR_INDEX,
        TRIGGER_CLICK_ITEM,
        TRIGGER_WALK_OFF_ITEM,
        TRIGGER_WALK_ON_ITEM,
        TRIGGER_RECEIVE_SIGNAL_SERIALIZE,
        DEFAULT_TOGGLE_STATE,
        DELAYED_ROOM_RESOLVE,
        DELAYED_TASK_CANCEL,
        REWARD_POINTS_TYPE,
        SOURCE_SELECTOR_EFFECT,
        TRIGGER_SOURCE_SELECTOR_EFFECT,
        DELAYED_PLUGIN_SNAPSHOT
    }

    private WiredCompatibilityDiagnostics() {}

    public static void record(FailurePoint point, Throwable failure) {
        record(point, 0, 0, failure);
    }

    public static void record(FailurePoint point, int roomId, int itemId, Throwable failure) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(failure, "failure");
        if (!shouldEmit(point, System.currentTimeMillis())) {
            return;
        }

        LOGGER.warn(
                "WIRED compatibility fallback point={} room={} item={} error={} outcome=legacy_fallback",
                point,
                Math.max(0, roomId),
                Math.max(0, itemId),
                exceptionType(failure));
    }

    static boolean shouldEmit(FailurePoint point, long nowMs) {
        int index = Objects.requireNonNull(point, "point").ordinal();
        long normalizedNow = Math.max(1L, nowMs);
        while (true) {
            long next = NEXT_LOG_AT.get(index);
            if (normalizedNow < next) {
                return false;
            }
            long replacement =
                    normalizedNow > Long.MAX_VALUE - LOG_INTERVAL_MS ? Long.MAX_VALUE : normalizedNow + LOG_INTERVAL_MS;
            if (NEXT_LOG_AT.compareAndSet(index, next, replacement)) {
                return true;
            }
        }
    }

    static String exceptionType(Throwable failure) {
        return Objects.requireNonNull(failure, "failure").getClass().getSimpleName();
    }

    static void resetForTesting() {
        for (int index = 0; index < NEXT_LOG_AT.length(); index++) {
            NEXT_LOG_AT.set(index, 0L);
        }
    }
}
