package polaris.messages.outgoing.gamecenter.basejump;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class BaseJumpLoadGameURLComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BaseJumpLoadGameURLComposer);
        this.response.appendInt(4);
        this.response.appendString("1351418858673");
        this.response.appendString(
                "http://images.habbo.com/speedway/200912/index.html?accessToken=ff5d09d1-ef22-4ee5-8b1b-8260d13d0d6f&gameServerHost=localhost&gameServerPort=30000&socketPolicyPort=30000");
        return this.response;
    }
}
