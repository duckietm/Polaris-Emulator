package polaris.messages.outgoing.quests;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** QuestCancelled (3027): the tracked quest stops; `expired` makes the client show the expiry alert. */
public class QuestExpiredComposer extends MessageComposer {
    private final boolean expired;
    private final QuestsComposer.Quest quest;

    public QuestExpiredComposer(boolean expired, QuestsComposer.Quest quest) {
        this.expired = expired;
        this.quest = quest;
    }

    /** @deprecated plugin ABI; sends the flag alone, as before the quest block was added. */
    @Deprecated
    public QuestExpiredComposer(boolean expired) {
        this(expired, null);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.QuestExpiredComposer);
        this.response.appendBoolean(this.expired);
        if (this.quest != null) this.response.append(this.quest);
        return this.response;
    }

    public boolean isExpired() {
        return expired;
    }

    public QuestsComposer.Quest getQuest() {
        return quest;
    }
}
