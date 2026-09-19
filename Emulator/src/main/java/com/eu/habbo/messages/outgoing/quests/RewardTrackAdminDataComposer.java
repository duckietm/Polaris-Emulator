package com.eu.habbo.messages.outgoing.quests;

import com.eu.habbo.habbohotel.quests.RewardTrack;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Map;

/**
 * Every reward track as stored, for the staff editor: the choices it may pick from, then each track
 * with its rows (disabled ones included), how many users claimed each prize, and the track's texts.
 * The premium boost travels in hundredths (150 is 1.5x).
 */
public class RewardTrackAdminDataComposer extends MessageComposer {
    private final List<String> actionTypes;
    private final List<String> rewardTypes;
    private final List<RewardTrackManager.LoadedTrack> tracks;
    private final Map<String, Integer> claimCounts;
    private final Map<String, Map<String, String>> texts;

    public RewardTrackAdminDataComposer(
            List<String> actionTypes,
            List<String> rewardTypes,
            List<RewardTrackManager.LoadedTrack> tracks,
            Map<String, Integer> claimCounts,
            Map<String, Map<String, String>> texts) {
        this.actionTypes = actionTypes;
        this.rewardTypes = rewardTypes;
        this.tracks = tracks;
        this.claimCounts = claimCounts;
        this.texts = texts;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackAdminDataComposer);
        this.response.appendInt(this.actionTypes.size());
        for (String actionType : this.actionTypes) {
            this.response.appendString(actionType);
        }
        this.response.appendInt(this.rewardTypes.size());
        for (String rewardType : this.rewardTypes) {
            this.response.appendString(rewardType);
        }
        this.response.appendInt(this.tracks.size());
        for (RewardTrackManager.LoadedTrack loaded : this.tracks) {
            RewardTrack track = loaded.track();
            this.response.appendString(track.getId());
            this.response.appendString(track.getTheme());
            this.response.appendInt(track.getSortOrder());
            this.response.appendInt(track.getStartsAt());
            this.response.appendInt(track.getEndsAt());
            this.response.appendBoolean(track.hasPremium());
            this.response.appendInt((int) Math.round(track.getPremiumTaskPointsBoost() * 100));
            this.response.appendInt(track.getPremiumInstantPoints());
            this.response.appendInt(track.getPremiumCostDiamonds());
            this.response.appendInt(track.getPremiumCostCredits());
            this.response.appendBoolean(loaded.enabled());
            this.response.appendInt(track.getTasks().size());
            for (RewardTrack.Task task : track.getTasks()) {
                this.response.appendString(task.getId());
                this.response.appendString(task.getActionType());
                this.response.appendString(task.getParameter());
                this.response.appendBoolean(task.isPremium());
                this.response.appendInt(task.getSortOrder());
                this.response.appendInt(task.getLevels().size());
                for (RewardTrack.Level level : task.getLevels()) {
                    this.response.appendInt(level.requiredCount());
                    this.response.appendInt(level.pointsReward());
                    this.response.appendBoolean(level.premium());
                }
            }
            this.response.appendInt(track.getPrizes().size());
            for (RewardTrack.Prize prize : track.getPrizes()) {
                this.response.appendString(prize.getId());
                this.response.appendInt(prize.getRequiredPoints());
                this.response.appendInt(prize.getProductItemTypeId());
                this.response.appendString(prize.getRewardType());
                this.response.appendString(prize.getExtraParams());
                this.response.appendInt(prize.getRewardAmount());
                this.response.appendBoolean(prize.isPremium());
                this.response.appendInt(prize.getSortOrder());
                this.response.appendInt(this.claimCounts.getOrDefault(track.getId() + "/" + prize.getId(), 0));
            }
            Map<String, String> trackTexts = this.texts.getOrDefault(track.getId(), Map.of());
            this.response.appendInt(trackTexts.size());
            for (Map.Entry<String, String> entry : trackTexts.entrySet()) {
                this.response.appendString(entry.getKey());
                this.response.appendString(entry.getValue());
            }
        }
        return this.response;
    }
}
