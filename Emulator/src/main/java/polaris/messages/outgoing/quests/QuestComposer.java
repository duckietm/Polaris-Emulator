package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class QuestComposer extends MessageComposer {
    private final QuestsComposer.Quest quest;

    public QuestComposer(QuestsComposer.Quest quest) {
        this.quest = quest;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.QuestComposer);
        this.response.append(this.quest);
        return this.response;
    }

    public QuestsComposer.Quest getQuest() {
        return quest;
    }
}
