package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.quests.RewardTrack;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.habbohotel.quests.UserRewardTrackState;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the achievement and reward-track wired boxes do once they fire. Every gate is checked here,
 * again on the worker that does the work: the hotel switch and allow-list, the room's own
 * declaration, the user still being in the room, and the per-user allowance of the window.
 */
public final class WiredHotelProgress {
    /** Progress mode: raise the progress to the amount; below it, add the difference. */
    public static final int MODE_RAISE_TO = 0;

    /** Progress mode: add the amount. */
    public static final int MODE_ADD = 1;

    public static final int MAX_AMOUNT = 1_000_000;

    /** Reward track and task ids, as the staff editor stores them. */
    public static final int MAX_REWARD_TRACK_ID_LENGTH = 64;

    /** The track's allow-list entry for one of its tasks: {@code track:task}. */
    public static final char TASK_SEPARATOR = ':';

    /** Hands the granted amount to the achievement system; the hotel's is {@link AchievementManager}. */
    @FunctionalInterface
    public interface AchievementProgressor {
        void progress(Habbo habbo, Achievement achievement, int amount);
    }

    private WiredHotelProgress() {}

    public static int normalizeMode(int mode) {
        return mode == MODE_RAISE_TO ? MODE_RAISE_TO : MODE_ADD;
    }

    public static int normalizeAmount(int amount) {
        return Math.clamp(amount, 1, MAX_AMOUNT);
    }

    /** A reward track or task id as the staff editor accepts it (letters, digits, '_', '-', '.'), or empty. */
    public static String normalizeRewardTrackId(String value) {
        if (value == null) {
            return "";
        }
        String id = value.trim();
        if (id.isEmpty() || id.length() > MAX_REWARD_TRACK_ID_LENGTH) {
            return "";
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.')) {
                return "";
            }
        }
        return id;
    }

    /** The users behind the units who are still in the room, at most {@link WiredHotelProgressPolicy#MAX_USERS_PER_FIRING}. */
    public static List<Habbo> presentUsers(Room room, Collection<RoomUnit> units) {
        List<Habbo> users = new ArrayList<>();
        if (room == null || units == null) {
            return users;
        }

        Set<Integer> seen = new LinkedHashSet<>();
        for (RoomUnit unit : units) {
            if (users.size() >= WiredHotelProgressPolicy.MAX_USERS_PER_FIRING) {
                break;
            }
            Habbo habbo = unit == null ? null : room.getHabbo(unit);
            if (isPresent(habbo, room) && seen.add(habbo.getHabboInfo().getId())) {
                users.add(habbo);
            }
        }

        return users;
    }

    public static boolean isPresent(Habbo habbo, Room room) {
        return habbo != null
                && room != null
                && habbo.getHabboInfo() != null
                && habbo.getHabboInfo().getId() > 0
                && habbo.getHabboInfo().getCurrentRoom() == room
                && habbo.getRoomUnit() != null
                && habbo.getRoomUnit().isInRoom();
    }

    /**
     * Progresses {@code achievement} for the users. Nothing happens unless the hotel allows it, the
     * room declares it with an achievement enabler, and the user is still in the room. Returns the
     * units handed out.
     */
    public static int progressAchievement(
            Room room,
            List<Habbo> users,
            Achievement achievement,
            Set<String> declared,
            int mode,
            int amount,
            WiredHotelProgressPolicy policy,
            WiredProgressLimiter limiter,
            AchievementProgressor progressor) {
        if (achievement == null
                || policy == null
                || limiter == null
                || progressor == null
                || declared == null
                || !policy.allows(achievement.name)
                || !declared.contains(achievement.name)) {
            return 0;
        }

        int wanted = normalizeAmount(amount);
        int given = 0;
        for (Habbo habbo : bounded(users)) {
            if (!isPresent(habbo, room)) {
                continue;
            }
            int requested = wanted;
            if (normalizeMode(mode) == MODE_RAISE_TO) {
                int current = habbo.getHabboStats() == null
                        ? 0
                        : Math.max(0, habbo.getHabboStats().getAchievementProgress(achievement));
                requested = wanted - current;
            }
            if (requested <= 0) {
                continue;
            }
            int granted = limiter.acquire(
                    habbo.getHabboInfo().getId(),
                    "achievement:" + achievement.name,
                    requested,
                    policy.maxPerWindow(),
                    policy.windowMs());
            if (granted > 0) {
                progressor.progress(habbo, achievement, granted);
                given += granted;
            }
        }
        return given;
    }

    /** Whether the hotel lets wired move {@code task} of {@code trackId}: the whole track or that task is listed. */
    public static boolean allowsTask(WiredHotelProgressPolicy policy, String trackId, String taskId) {
        return policy != null
                && trackId != null
                && taskId != null
                && (policy.allows(trackId) || policy.allows(trackId + TASK_SEPARATOR + taskId));
    }

    /**
     * Moves a reward-track task for the users: {@code add} adds the amount, otherwise the progress
     * becomes the amount. Every unit above the current progress counts against the allowance.
     * Returns the units handed out.
     */
    public static int progressRewardTrack(
            Room room,
            List<Habbo> users,
            RewardTrackManager manager,
            String trackId,
            String taskId,
            boolean add,
            int amount,
            WiredHotelProgressPolicy policy,
            WiredProgressLimiter limiter) {
        if (manager == null || limiter == null || !allowsTask(policy, trackId, taskId)) {
            return 0;
        }

        RewardTrack track = activeTrack(manager, trackId);
        RewardTrack.Task task = track == null ? null : track.getTask(taskId);
        if (task == null) {
            return 0;
        }

        int wanted = normalizeAmount(amount);
        int given = 0;
        for (Habbo habbo : bounded(users)) {
            if (!isPresent(habbo, room)) {
                continue;
            }
            UserRewardTrackState state = manager.stateFor(habbo, track);
            if (state == null || (task.isPremium() && !state.isPremium())) {
                continue;
            }
            int current = state.progressOf(taskId);
            long target = add ? (long) current + wanted : wanted;
            int requested = (int) Math.min(Integer.MAX_VALUE, target - current);
            if (requested > 0) {
                requested = limiter.acquire(
                        habbo.getHabboInfo().getId(),
                        "reward_track:" + trackId,
                        requested,
                        policy.maxPerWindow(),
                        policy.windowMs());
                if (requested <= 0) {
                    continue;
                }
                given += requested;
            }
            manager.setTaskProgress(habbo, track, task, current + requested);
        }
        return given;
    }

    /**
     * Puts back to zero the tasks of the track the hotel lets wired move, for the users. Points and
     * claimed prizes stay, and levels already paid do not pay again. Returns the users reset.
     */
    public static int resetRewardTrack(
            Room room, List<Habbo> users, RewardTrackManager manager, String trackId, WiredHotelProgressPolicy policy) {
        if (manager == null || policy == null || !policy.enabled()) {
            return 0;
        }

        RewardTrack track = activeTrack(manager, trackId);
        if (track == null) {
            return 0;
        }

        List<RewardTrack.Task> tasks = new ArrayList<>();
        for (RewardTrack.Task task : track.getTasks()) {
            if (allowsTask(policy, trackId, task.getId())) {
                tasks.add(task);
            }
        }
        if (tasks.isEmpty()) {
            return 0;
        }

        int reset = 0;
        for (Habbo habbo : bounded(users)) {
            if (isPresent(habbo, room)) {
                manager.resetTasks(habbo, track, tasks);
                reset++;
            }
        }
        return reset;
    }

    /** Runs the work on the hotel's worker pool, so the wired thread never waits on the database. */
    public static void dispatch(Runnable work) {
        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) {
            threading.run(work);
        }
    }

    private static RewardTrack activeTrack(RewardTrackManager manager, String trackId) {
        if (trackId == null) {
            return null;
        }
        for (RewardTrack track : manager.activeTracks()) {
            if (track.getId().equals(trackId)) {
                return track;
            }
        }
        return null;
    }

    private static List<Habbo> bounded(List<Habbo> users) {
        if (users == null) {
            return List.of();
        }
        return users.size() > WiredHotelProgressPolicy.MAX_USERS_PER_FIRING
                ? users.subList(0, WiredHotelProgressPolicy.MAX_USERS_PER_FIRING)
                : users;
    }
}
