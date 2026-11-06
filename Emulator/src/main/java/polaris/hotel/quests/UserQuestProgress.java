package polaris.hotel.quests;

/** One row of `users_quests`: what a user did with one quest. */
public final class UserQuestProgress {
    private final int questId;
    private int progress;
    private boolean accepted;
    private int acceptedAt;
    private int completedAt;

    public UserQuestProgress(int questId, int progress, boolean accepted, int acceptedAt, int completedAt) {
        this.questId = questId;
        this.progress = progress;
        this.accepted = accepted;
        this.acceptedAt = acceptedAt;
        this.completedAt = completedAt;
    }

    public int getQuestId() {
        return this.questId;
    }

    public int getProgress() {
        return this.progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, progress);
    }

    public boolean isAccepted() {
        return this.accepted;
    }

    public void setAccepted(boolean accepted, int now) {
        this.accepted = accepted;
        this.acceptedAt = accepted ? now : this.acceptedAt;
    }

    public int getAcceptedAt() {
        return this.acceptedAt;
    }

    public boolean isCompleted() {
        return this.completedAt > 0;
    }

    public int getCompletedAt() {
        return this.completedAt;
    }

    public void setCompletedAt(int completedAt) {
        this.completedAt = completedAt;
    }
}
