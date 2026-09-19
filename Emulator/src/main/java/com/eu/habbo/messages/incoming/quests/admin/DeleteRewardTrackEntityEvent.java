package com.eu.habbo.messages.incoming.quests.admin;

import com.eu.habbo.habbohotel.quests.RewardTrackAdmin;

/** Deletes a track, a task or a prize: entity name, track id, then the task or prize id. */
public class DeleteRewardTrackEntityEvent extends RewardTrackAdminEvent {
    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        String entity = this.packet.readString().trim();
        String trackId = this.packet.readString().trim();
        String id = this.packet.readString().trim();
        this.apply(
                entity,
                trackId,
                id,
                RewardTrackAdmin.validateDelete(entity, trackId, id),
                () -> RewardTrackAdmin.delete(entity, trackId, id));
    }
}
