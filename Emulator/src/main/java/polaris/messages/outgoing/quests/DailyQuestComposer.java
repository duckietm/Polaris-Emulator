package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** QuestDaily (1878): the daily quest of the landing widget with the open easy/hard counts. */
public class DailyQuestComposer extends MessageComposer {
    private final QuestsComposer.Quest quest;
    private final int easyQuestCount;
    private final int hardQuestCount;

    public DailyQuestComposer(QuestsComposer.Quest quest, int easyQuestCount, int hardQuestCount) {
        this.quest = quest;
        this.easyQuestCount = easyQuestCount;
        this.hardQuestCount = hardQuestCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.DailyQuestComposer);
        this.response.appendBoolean(this.quest != null);
        if (this.quest != null) {
            this.response.append(this.quest);
            this.response.appendInt(this.easyQuestCount);
            this.response.appendInt(this.hardQuestCount);
        }
        return this.response;
    }

    public QuestsComposer.Quest getQuest() {
        return quest;
    }

    public int getEasyQuestCount() {
        return easyQuestCount;
    }

    public int getHardQuestCount() {
        return hardQuestCount;
    }
}
