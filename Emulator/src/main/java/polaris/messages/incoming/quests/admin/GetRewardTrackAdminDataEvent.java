package polaris.messages.incoming.quests.admin;

public class GetRewardTrackAdminDataEvent extends RewardTrackAdminEvent {
    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        this.sendAdminData();
    }
}
