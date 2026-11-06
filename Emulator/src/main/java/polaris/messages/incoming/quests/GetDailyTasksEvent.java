package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** GetDailyTasks (4100): the daily tasks window asks for the tasks of the day. */
public class GetDailyTasksEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Emulator.getGameEnvironment().getDailyTaskManager().sendActiveTasks(this.client.getUser());
    }
}
