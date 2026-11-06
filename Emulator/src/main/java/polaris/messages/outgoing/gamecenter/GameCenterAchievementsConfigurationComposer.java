package polaris.messages.outgoing.gamecenter;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;

public class GameCenterAchievementsConfigurationComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(2265);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(3);
        this.response.appendInt(1);
        this.response.appendInt(1);
        this.response.appendString("BaseJumpBigParachute");
        this.response.appendInt(1);
        return this.response;
    }
}
