package polaris.messages.incoming.quests.admin;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.quests.RewardTrackAdmin;
import polaris.hotel.quests.RewardTrackManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.quests.RewardTrackAdminDataComposer;
import polaris.messages.outgoing.quests.RewardTrackAdminResultComposer;

/** Shared by the staff editor handlers: the permission gate, the answer and the refresh after a write. */
abstract class RewardTrackAdminEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardTrackAdminEvent.class);

    /** Every write reloads the tracks for everyone online, so a client may not fire more than two a second. */
    @Override
    public int getRatelimit() {
        return 500;
    }

    final boolean authorize() {
        if (this.client != null
                && this.client.getUser() != null
                && this.client.getUser().hasPermission(Permission.ACC_REWARDTRACK)) {
            return true;
        }
        if (this.client != null) {
            this.client.sendResponse(new RewardTrackAdminResultComposer(false, "No permission", "", "", ""));
        }
        return false;
    }

    /** Sends every stored track to the editor. */
    final void sendAdminData() {
        this.client.sendResponse(new RewardTrackAdminDataComposer(
                RewardTrackAdmin.actionTypes(),
                RewardTrackAdmin.rewardTypes(),
                RewardTrackManager.loadFromDatabase(false),
                RewardTrackAdmin.claimCounts(),
                RewardTrackAdmin.loadTexts()));
    }

    /** Runs the write, reloads the hotel's tracks for everyone and answers the editor. */
    final void apply(String entity, String trackId, String id, String problem, Write write) {
        if (problem != null) {
            this.client.sendResponse(new RewardTrackAdminResultComposer(false, problem, entity, trackId, id));
            return;
        }
        try {
            write.run();
        } catch (SQLException exception) {
            LOGGER.error("Reward track editor could not save {} {}/{}", entity, trackId, id, exception);
            this.client.sendResponse(new RewardTrackAdminResultComposer(
                    false, "The change could not be saved, see the emulator log", entity, trackId, id));
            return;
        }
        Emulator.getGameEnvironment().getRewardTrackManager().reloadAndBroadcast();
        this.sendAdminData();
        this.client.sendResponse(new RewardTrackAdminResultComposer(true, "", entity, trackId, id));
    }

    @FunctionalInterface
    interface Write {
        void run() throws SQLException;
    }
}
