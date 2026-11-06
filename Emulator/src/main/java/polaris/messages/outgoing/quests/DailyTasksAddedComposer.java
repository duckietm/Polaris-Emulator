package polaris.messages.outgoing.quests;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** DailyTasksAdded (670): tasks that appeared while the window was open (a bonus task unlocking). */
public class DailyTasksAddedComposer extends MessageComposer {
    private final List<ActiveDailyTasksComposer.Task> tasks;

    public DailyTasksAddedComposer(List<ActiveDailyTasksComposer.Task> tasks) {
        this.tasks = tasks;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.DailyTasksAddedComposer);
        this.response.appendInt(this.tasks.size());
        for (ActiveDailyTasksComposer.Task task : this.tasks) {
            this.response.append(task);
        }
        return this.response;
    }

    public List<ActiveDailyTasksComposer.Task> getTasks() {
        return tasks;
    }
}
