package polaris.messages.outgoing.quests;

import java.util.List;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** ActiveDailyTasks (2900): the tasks of the day with the user's repeats and status. */
public class ActiveDailyTasksComposer extends MessageComposer {
    private final List<Task> tasks;

    public ActiveDailyTasksComposer(List<Task> tasks) {
        this.tasks = tasks;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ActiveDailyTasksComposer);
        this.response.appendInt(this.tasks.size());
        for (Task task : this.tasks) {
            this.response.append(task);
        }
        return this.response;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    /** The official reward block: productItemTypeId (short), rewardTypeId, extraParams, amount. */
    public record Reward(int productItemTypeId, String rewardTypeId, String extraParams, int amount)
            implements ISerialize {
        @Override
        public void serialize(ServerMessage message) {
            message.appendShort(this.productItemTypeId);
            message.appendString(this.rewardTypeId);
            message.appendString(this.extraParams);
            message.appendInt(this.amount);
        }
    }

    /** The official DailyTask block (AIR 13 class_2786); the id travels as a 64-bit long. */
    public record Task(
            long taskId,
            String taskCode,
            String questTypeCode,
            boolean bonus,
            String imageVersion,
            String catalogName,
            int requiredRepeats,
            int repeats,
            int status,
            int secondsLeft,
            List<Reward> rewards)
            implements ISerialize {
        @Override
        public void serialize(ServerMessage message) {
            message.appendInt((int) (this.taskId >>> 32));
            message.appendInt((int) this.taskId);
            message.appendString(this.taskCode);
            message.appendString(this.questTypeCode);
            message.appendBoolean(this.bonus);
            message.appendString(this.imageVersion);
            message.appendString(this.catalogName);
            message.appendInt(this.requiredRepeats);
            message.appendInt(this.repeats);
            message.appendByte(this.status);
            message.appendInt(this.secondsLeft);
            message.appendInt(this.rewards.size());
            for (Reward reward : this.rewards) {
                message.append(reward);
            }
        }
    }
}
