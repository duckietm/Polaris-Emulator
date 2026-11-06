package polaris.messages.outgoing.quests;

import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** QuestCompleted (949): the finished quest and whether the client shows the completed dialog. */
public class QuestCompletedComposer extends MessageComposer {
    private final QuestsComposer.Quest quest;
    private final boolean showDialog;
    private final UnknownClass unknownClass;

    public QuestCompletedComposer(QuestsComposer.Quest quest, boolean showDialog) {
        this.quest = quest;
        this.showDialog = showDialog;
        this.unknownClass = null;
    }

    /** @deprecated plugin ABI; the legacy fields become a quest with empty texts. */
    @Deprecated
    public QuestCompletedComposer(UnknownClass unknownClass, boolean unknowbOolean) {
        this.quest = unknownClass.toQuest();
        this.showDialog = unknowbOolean;
        this.unknownClass = unknownClass;
    }

    /** @deprecated plugin ABI. */
    @Deprecated
    public UnknownClass getUnknownClass() {
        return unknownClass;
    }

    /** @deprecated plugin ABI; see {@link #isShowDialog()}. */
    @Deprecated
    public boolean isUnknowbOolean() {
        return showDialog;
    }

    /** @deprecated plugin ABI; the pre-2026 shape of the quest block. */
    @Deprecated
    public static class UnknownClass implements ISerialize {
        private final int activityPointsType;
        private final boolean accepted;
        private final int id;
        private final String type;
        private final int sortOrder;
        private final boolean easy;

        public UnknownClass(
                int activityPointsType, boolean accepted, int id, String type, int sortOrder, boolean easy) {
            this.activityPointsType = activityPointsType;
            this.accepted = accepted;
            this.id = id;
            this.type = type;
            this.sortOrder = sortOrder;
            this.easy = easy;
        }

        QuestsComposer.Quest toQuest() {
            return new QuestsComposer.Quest(
                    "",
                    0,
                    0,
                    this.activityPointsType,
                    this.id,
                    this.accepted,
                    this.type,
                    "",
                    0,
                    "",
                    0,
                    0,
                    this.sortOrder,
                    "",
                    "",
                    this.easy);
        }

        @Override
        public void serialize(ServerMessage message) {
            this.toQuest().serialize(message);
        }

        public int getActivityPointsType() {
            return activityPointsType;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public int getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public boolean isEasy() {
            return easy;
        }
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.QuestCompletedComposer);
        this.response.append(this.quest);
        this.response.appendBoolean(this.showDialog);
        return this.response;
    }

    public QuestsComposer.Quest getQuest() {
        return quest;
    }

    public boolean isShowDialog() {
        return showDialog;
    }
}
