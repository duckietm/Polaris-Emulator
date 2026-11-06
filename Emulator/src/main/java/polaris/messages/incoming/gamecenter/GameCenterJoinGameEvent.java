package polaris.messages.incoming.gamecenter;

import polaris.Emulator;
import polaris.hotel.games.snowwar.SnowWarConstants;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.gamecenter.Game2GameNotFoundComposer;
import polaris.messages.outgoing.gamecenter.GameCenterAchievementsConfigurationComposer;
import polaris.messages.outgoing.gamecenter.basejump.BaseJumpJoinQueueComposer;
import polaris.messages.outgoing.gamecenter.basejump.BaseJumpLoadGameComposer;
import polaris.messages.outgoing.gamecenter.basejump.BaseJumpLoadGameURLComposer;
import polaris.messages.outgoing.snowwar.SnowStormGenericErrorComposer;

public class GameCenterJoinGameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int gameId = this.packet.readInt();

        if (gameId == 0) // SnowStorm
        {
            if (Emulator.getConfig().getBoolean("gamecenter.snowwar.enabled", true)) {
                SnowWarManager.getInstance().joinQueue(this.client.getUser());
            } else {
                this.client.sendResponse(new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_INTERNAL));
            }
        } else if (gameId == 3) // BaseJump
        {
            this.client.sendResponse(new GameCenterAchievementsConfigurationComposer());
            this.client.sendResponse(new BaseJumpLoadGameURLComposer());
            this.client.sendResponse(new BaseJumpLoadGameComposer(this.client, 3));
        } else if (gameId == 4) {
            this.client.sendResponse(new BaseJumpJoinQueueComposer(4));
            this.client.sendResponse(new BaseJumpLoadGameURLComposer());
        } else {
            // AIR Game2GameNotFound (444): nothing is hosted under this game
            // type, so the hub stops waiting instead of hanging on "joining".
            this.client.sendResponse(new Game2GameNotFoundComposer());
        }
    }
}
