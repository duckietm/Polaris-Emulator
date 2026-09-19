package com.eu.habbo.messages.incoming.quests.admin;

import com.eu.habbo.habbohotel.quests.RewardTrackAdmin;

/** Creates or updates a track. The premium boost arrives in hundredths (150 is 1.5x). */
public class SaveRewardTrackEvent extends RewardTrackAdminEvent {
    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        RewardTrackAdmin.TrackInput input = new RewardTrackAdmin.TrackInput(
                this.packet.readString().trim(),
                this.packet.readString().trim(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readBoolean(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readBoolean());
        this.apply(
                RewardTrackAdmin.ENTITY_TRACK,
                input.id(),
                input.id(),
                RewardTrackAdmin.validate(input),
                () -> RewardTrackAdmin.saveTrack(input));
    }
}
