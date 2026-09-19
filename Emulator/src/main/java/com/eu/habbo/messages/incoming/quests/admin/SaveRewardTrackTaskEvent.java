package com.eu.habbo.messages.incoming.quests.admin;

import com.eu.habbo.habbohotel.quests.RewardTrack;
import com.eu.habbo.habbohotel.quests.RewardTrackAdmin;
import java.util.ArrayList;
import java.util.List;

/** Creates or updates a task with its levels; the levels sent replace the stored ones. */
public class SaveRewardTrackTaskEvent extends RewardTrackAdminEvent {
    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        String trackId = this.packet.readString().trim();
        String id = this.packet.readString().trim();
        String actionType = this.packet.readString().trim();
        String parameter = this.packet.readString();
        boolean premium = this.packet.readBoolean();
        int sortOrder = this.packet.readInt();
        int levelCount = Math.max(0, Math.min(this.packet.readInt(), RewardTrackAdmin.MAX_LEVELS));
        List<RewardTrack.Level> levels = new ArrayList<>();
        for (int i = 0; i < levelCount; i++) {
            levels.add(new RewardTrack.Level(this.packet.readInt(), this.packet.readInt(), this.packet.readBoolean()));
        }
        RewardTrackAdmin.TaskInput input =
                new RewardTrackAdmin.TaskInput(trackId, id, actionType, parameter, premium, sortOrder, levels);
        this.apply(
                RewardTrackAdmin.ENTITY_TASK,
                trackId,
                id,
                RewardTrackAdmin.validate(input),
                () -> RewardTrackAdmin.saveTask(input));
    }
}
