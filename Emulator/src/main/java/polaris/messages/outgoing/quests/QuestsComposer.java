package polaris.messages.outgoing.quests;

import java.util.List;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** Quests (3625): one entry per campaign plus the "open the window" flag. */
public class QuestsComposer extends MessageComposer {
    private final List<Quest> quests;
    private final boolean openWindow;

    public QuestsComposer(List<Quest> quests, boolean openWindow) {
        this.quests = quests;
        this.openWindow = openWindow;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.QuestsComposer);
        this.response.appendInt(this.quests.size());
        for (Quest quest : this.quests) {
            this.response.append(quest);
        }
        this.response.appendBoolean(this.openWindow);
        return this.response;
    }

    /** The official QuestMessageData layout (AIR 13 class_1894), shared by every quest packet. */
    public static class Quest implements ISerialize {
        private final String campaignCode;
        private final int completedQuestsInCampaign;
        private final int questCountInCampaign;
        private final int activityPointType;
        private final int id;
        private final boolean accepted;
        private final String type;
        private final String imageVersion;
        private final int rewardCurrencyAmount;
        private final String localizationCode;
        private final int completedSteps;
        private final int totalSteps;
        private final int sortOrder;
        private final String catalogPageName;
        private final String chainCode;
        private final boolean easy;
        private final boolean seasonal;
        private final int secondsLeft;

        /** @deprecated plugin ABI; a quest that is not seasonal. */
        @Deprecated
        public Quest(
                String campaignCode,
                int completedQuestsInCampaign,
                int questCountInCampaign,
                int activityPointType,
                int id,
                boolean accepted,
                String type,
                String imageVersion,
                int rewardCurrencyAmount,
                String localizationCode,
                int completedSteps,
                int totalSteps,
                int sortOrder,
                String catalogPageName,
                String chainCode,
                boolean easy) {
            this(
                    campaignCode,
                    completedQuestsInCampaign,
                    questCountInCampaign,
                    activityPointType,
                    id,
                    accepted,
                    type,
                    imageVersion,
                    rewardCurrencyAmount,
                    localizationCode,
                    completedSteps,
                    totalSteps,
                    sortOrder,
                    catalogPageName,
                    chainCode,
                    easy,
                    false,
                    0);
        }

        public Quest(
                String campaignCode,
                int completedQuestsInCampaign,
                int questCountInCampaign,
                int activityPointType,
                int id,
                boolean accepted,
                String type,
                String imageVersion,
                int rewardCurrencyAmount,
                String localizationCode,
                int completedSteps,
                int totalSteps,
                int sortOrder,
                String catalogPageName,
                String chainCode,
                boolean easy,
                boolean seasonal,
                int secondsLeft) {
            this.campaignCode = campaignCode;
            this.completedQuestsInCampaign = completedQuestsInCampaign;
            this.questCountInCampaign = questCountInCampaign;
            this.activityPointType = activityPointType;
            this.id = id;
            this.accepted = accepted;
            this.type = type;
            this.imageVersion = imageVersion;
            this.rewardCurrencyAmount = rewardCurrencyAmount;
            this.localizationCode = localizationCode;
            this.completedSteps = completedSteps;
            this.totalSteps = totalSteps;
            this.sortOrder = sortOrder;
            this.catalogPageName = catalogPageName;
            this.chainCode = chainCode;
            this.easy = easy;
            this.seasonal = seasonal;
            this.secondsLeft = secondsLeft;
        }

        @Override
        public void serialize(ServerMessage message) {
            message.appendString(this.campaignCode);
            message.appendInt(this.completedQuestsInCampaign);
            message.appendInt(this.questCountInCampaign);
            message.appendInt(this.activityPointType);
            message.appendInt(this.id);
            message.appendBoolean(this.accepted);
            message.appendString(this.type);
            message.appendString(this.imageVersion);
            message.appendInt(this.rewardCurrencyAmount);
            message.appendString(this.localizationCode);
            message.appendInt(this.completedSteps);
            message.appendInt(this.totalSteps);
            message.appendInt(this.sortOrder);
            message.appendString(this.catalogPageName);
            message.appendString(this.chainCode);
            message.appendBoolean(this.easy);
            message.appendBoolean(this.seasonal);
            if (this.seasonal) {
                message.appendInt(this.secondsLeft);
            }
        }

        public String getCampaignCode() {
            return campaignCode;
        }

        public int getCompletedQuestsInCampaign() {
            return completedQuestsInCampaign;
        }

        public int getQuestCountInCampaign() {
            return questCountInCampaign;
        }

        public int getActivityPointType() {
            return activityPointType;
        }

        public int getId() {
            return id;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getType() {
            return type;
        }

        public String getImageVersion() {
            return imageVersion;
        }

        public int getRewardCurrencyAmount() {
            return rewardCurrencyAmount;
        }

        public String getLocalizationCode() {
            return localizationCode;
        }

        public int getCompletedSteps() {
            return completedSteps;
        }

        public int getTotalSteps() {
            return totalSteps;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public String getCatalogPageName() {
            return catalogPageName;
        }

        public String getChainCode() {
            return chainCode;
        }

        public boolean isEasy() {
            return easy;
        }

        public boolean isSeasonal() {
            return seasonal;
        }

        public int getSecondsLeft() {
            return secondsLeft;
        }
    }

    /** @deprecated plugin ABI; see {@link #isOpenWindow()}. */
    @Deprecated
    public boolean isUnknownBoolean() {
        return openWindow;
    }

    public List<Quest> getQuests() {
        return quests;
    }

    public boolean isOpenWindow() {
        return openWindow;
    }
}
