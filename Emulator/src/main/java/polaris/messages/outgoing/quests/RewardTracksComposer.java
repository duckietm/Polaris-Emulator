package polaris.messages.outgoing.quests;

import java.util.List;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** RewardTracks (2327): every active track with the user's points, progress and claims. */
public class RewardTracksComposer extends MessageComposer {
    private final boolean disabled;
    private final List<Track> tracks;
    private final boolean reload;

    public RewardTracksComposer(boolean disabled, List<Track> tracks, boolean reload) {
        this.disabled = disabled;
        this.tracks = tracks;
        this.reload = reload;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTracksComposer);
        this.response.appendBoolean(this.disabled);
        this.response.appendInt(this.tracks.size());
        for (Track track : this.tracks) {
            this.response.append(track);
        }
        this.response.appendBoolean(this.reload);
        return this.response;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public boolean isReload() {
        return reload;
    }

    /** AIR 13 class_4130. */
    public record Level(int requiredCount, int pointsReward, boolean premium) implements ISerialize {
        @Override
        public void serialize(ServerMessage message) {
            message.appendInt(this.requiredCount);
            message.appendInt(this.pointsReward);
            message.appendBoolean(this.premium);
        }
    }

    /** AIR 13 class_4037. */
    public record Task(
            String id, String actionType, String parameter, int progressCount, boolean premium, List<Level> levels)
            implements ISerialize {
        @Override
        public void serialize(ServerMessage message) {
            message.appendString(this.id);
            message.appendString(this.actionType);
            message.appendString(this.parameter);
            message.appendInt(this.progressCount);
            message.appendBoolean(this.premium);
            message.appendInt(this.levels.size());
            for (Level level : this.levels) {
                message.append(level);
            }
        }
    }

    /** AIR 13 class_3945. */
    public record Prize(
            String id,
            int requiredPoints,
            int productItemTypeId,
            String rewardTypeId,
            String extraParams,
            int rewardAmount,
            boolean premium,
            boolean available,
            boolean claimed)
            implements ISerialize {
        @Override
        public void serialize(ServerMessage message) {
            message.appendString(this.id);
            message.appendInt(this.requiredPoints);
            message.appendShort(this.productItemTypeId);
            message.appendString(this.rewardTypeId);
            message.appendString(this.extraParams);
            message.appendInt(this.rewardAmount);
            message.appendBoolean(this.premium);
            message.appendBoolean(this.available);
            message.appendBoolean(this.claimed);
        }
    }

    /** AIR 13 class_2464. */
    public record Track(
            String id,
            String theme,
            int points,
            boolean hasPremiumConfig,
            double taskPointsBoost,
            int instantPoints,
            int costDiamonds,
            int costCredits,
            boolean premium,
            boolean complete,
            boolean premiumComplete,
            List<Task> tasks,
            List<Prize> prizes)
            implements ISerialize {
        @Override
        public void serialize(ServerMessage message) {
            message.appendString(this.id);
            message.appendString(this.theme);
            message.appendInt(this.points);
            message.appendBoolean(this.hasPremiumConfig);
            if (this.hasPremiumConfig) {
                message.appendDouble(this.taskPointsBoost);
                message.appendInt(this.instantPoints);
                message.appendInt(this.costDiamonds);
                message.appendInt(this.costCredits);
            }
            message.appendBoolean(this.premium);
            message.appendBoolean(this.complete);
            message.appendBoolean(this.premiumComplete);
            message.appendInt(this.tasks.size());
            for (Task task : this.tasks) {
                message.append(task);
            }
            message.appendInt(this.prizes.size());
            for (Prize prize : this.prizes) {
                message.append(prize);
            }
        }
    }
}
