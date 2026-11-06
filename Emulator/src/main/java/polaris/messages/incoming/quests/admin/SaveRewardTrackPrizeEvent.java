package polaris.messages.incoming.quests.admin;

import polaris.Emulator;
import polaris.hotel.quests.QuestRewards;
import polaris.hotel.quests.RewardTrackAdmin;

/** Creates or updates a prize. */
public class SaveRewardTrackPrizeEvent extends RewardTrackAdminEvent {
    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        RewardTrackAdmin.PrizeInput input = new RewardTrackAdmin.PrizeInput(
                this.packet.readString().trim(),
                this.packet.readString().trim(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readInt(),
                this.packet.readBoolean(),
                this.packet.readInt());
        String problem = RewardTrackAdmin.validate(input);
        if (problem == null
                && QuestRewards.TYPE_FURNI.equalsIgnoreCase(input.rewardType().trim())
                && Emulator.getGameEnvironment()
                                .getItemManager()
                                .getItem(input.extraParams().trim())
                        == null) {
            problem = "No furni is named " + input.extraParams().trim() + " (items_base.item_name)";
        }
        this.apply(
                RewardTrackAdmin.ENTITY_PRIZE,
                input.trackId(),
                input.id(),
                problem,
                () -> RewardTrackAdmin.savePrize(input));
    }
}
