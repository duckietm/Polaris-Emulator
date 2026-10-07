package com.eu.habbo.habbohotel.quests;

/** One row of `users_daily_tasks`: a user's repeats and status of one task for one day. */
public final class UserDailyTaskState {
    /** The official DailyTask status byte: 0 in progress, 1 completed (claimable), 2 claimed. */
    public static final int STATUS_OPEN = 0;

    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_CLAIMED = 2;

    private final int taskId;
    private final String day;
    // Written under the manager's lock, read by the save that runs later on a worker.
    private volatile int repeats;
    private volatile int status;

    public UserDailyTaskState(int taskId, String day, int repeats, int status) {
        this.taskId = taskId;
        this.day = day;
        this.repeats = Math.max(0, repeats);
        this.status = status;
    }

    public int getTaskId() {
        return this.taskId;
    }

    public String getDay() {
        return this.day;
    }

    public int getRepeats() {
        return this.repeats;
    }

    public void setRepeats(int repeats) {
        this.repeats = Math.max(0, repeats);
    }

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isOpen() {
        return this.status == STATUS_OPEN;
    }

    public boolean isClaimable() {
        return this.status == STATUS_COMPLETED;
    }
}
