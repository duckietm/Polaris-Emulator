package polaris.messages.outgoing.quests;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** SeasonalQuests (1122): the current quest of every seasonal campaign. */
public class SeasonalQuestsComposer extends MessageComposer {
    private final List<QuestsComposer.Quest> quests;

    public SeasonalQuestsComposer(List<QuestsComposer.Quest> quests) {
        this.quests = quests;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SeasonalQuestsComposer);
        this.response.appendInt(this.quests.size());
        for (QuestsComposer.Quest quest : this.quests) {
            this.response.append(quest);
        }
        return this.response;
    }

    public List<QuestsComposer.Quest> getQuests() {
        return quests;
    }
}
