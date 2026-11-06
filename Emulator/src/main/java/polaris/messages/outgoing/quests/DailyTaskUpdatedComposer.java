package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** DailyTaskUpdated (9450, the official 2392 collides): repeats and status of one task. */
public class DailyTaskUpdatedComposer extends MessageComposer {
    private final long taskId;
    private final int repeats;
    private final int status;
    private final int secondsLeft;

    public DailyTaskUpdatedComposer(long taskId, int repeats, int status, int secondsLeft) {
        this.taskId = taskId;
        this.repeats = repeats;
        this.status = status;
        this.secondsLeft = secondsLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.DailyTaskUpdatedComposer);
        this.response.appendInt((int) (this.taskId >>> 32));
        this.response.appendInt((int) this.taskId);
        this.response.appendInt(this.repeats);
        this.response.appendByte(this.status);
        this.response.appendInt(this.secondsLeft);
        return this.response;
    }

    public long getTaskId() {
        return taskId;
    }

    public int getRepeats() {
        return repeats;
    }

    public int getStatus() {
        return status;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }
}
