package polaris.messages.incoming.quests;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

/** GetRewardTracks (9450, custom): the client asks for the active reward tracks. */
public class GetRewardTracksEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Emulator.getGameEnvironment().getRewardTrackManager().sendRewardTracks(this.client.getUser(), false);
    }
}
