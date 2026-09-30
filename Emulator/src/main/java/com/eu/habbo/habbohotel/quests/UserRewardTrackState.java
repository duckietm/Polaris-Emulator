package com.eu.habbo.habbohotel.quests;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A user's progress on one reward track: `users_reward_tracks` with its task counts and claims. */
public final class UserRewardTrackState {
    private final String trackId;
    private int points;
    private boolean premium;
    private final Map<String, Integer> taskProgress = new ConcurrentHashMap<>();
    private final Map<String, Integer> taskPeaks = new ConcurrentHashMap<>();
    private final Set<String> claimedPrizes = ConcurrentHashMap.newKeySet();
    private final Object persistenceLock = new Object();

    public UserRewardTrackState(String trackId, int points, boolean premium) {
        this.trackId = trackId;
        this.points = Math.max(0, points);
        this.premium = premium;
    }

    /**
     * Held while a save reads this state and writes it, so the rows are always written in the order the
     * state was read: a slow older save can never land after, and overwrite, a newer one.
     */
    Object persistenceLock() {
        return this.persistenceLock;
    }

    public String getTrackId() {
        return this.trackId;
    }

    public int getPoints() {
        return this.points;
    }

    public void addPoints(int points) {
        this.points = Math.max(0, this.points + points);
    }

    public boolean isPremium() {
        return this.premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public int progressOf(String taskId) {
        return this.taskProgress.getOrDefault(taskId, 0);
    }

    public void setProgress(String taskId, int count) {
        int progress = Math.max(0, count);
        this.taskProgress.put(taskId, progress);
        this.taskPeaks.merge(taskId, progress, Math::max);
    }

    /** The highest count the task ever reached; its levels pay only once, below it nothing pays again. */
    public int peakOf(String taskId) {
        return Math.max(this.taskPeaks.getOrDefault(taskId, 0), this.progressOf(taskId));
    }

    public void setPeak(String taskId, int peak) {
        this.taskPeaks.merge(taskId, Math.max(0, peak), Math::max);
    }

    public boolean isClaimed(String prizeId) {
        return this.claimedPrizes.contains(prizeId);
    }

    public void markClaimed(String prizeId) {
        this.claimedPrizes.add(prizeId);
    }

    public boolean isPrizeLocked(RewardTrack.Prize prize) {
        return prize.isPremium() && !this.premium;
    }

    public boolean isPrizeAvailable(RewardTrack.Prize prize) {
        return !this.isPrizeLocked(prize) && this.points >= prize.getRequiredPoints();
    }

    public boolean isPrizeClaimable(RewardTrack.Prize prize) {
        return this.isPrizeAvailable(prize) && !this.isClaimed(prize.getId());
    }
}
