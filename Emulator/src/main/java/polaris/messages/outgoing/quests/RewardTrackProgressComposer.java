package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** RewardTrackProgress (9452, the official 2142 collides): a task moved and the points it yielded. */
public class RewardTrackProgressComposer extends MessageComposer {
    private final String trackId;
    private final String taskId;
    private final int progressCount;
    private final int points;

    public RewardTrackProgressComposer(String trackId, String taskId, int progressCount, int points) {
        this.trackId = trackId;
        this.taskId = taskId;
        this.progressCount = progressCount;
        this.points = points;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackProgressComposer);
        this.response.appendString(this.trackId);
        this.response.appendString(this.taskId);
        this.response.appendInt(this.progressCount);
        this.response.appendInt(this.points);
        return this.response;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getProgressCount() {
        return progressCount;
    }

    public int getPoints() {
        return points;
    }
}
