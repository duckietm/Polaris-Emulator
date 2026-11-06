package polaris.messages.outgoing.generic.alerts;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CustomNotificationComposer extends MessageComposer {
    public static final int HOPPER_NO_COSTUME = 1;
    public static final int HOPPER_NO_HC = 2;
    public static final int GATE_NO_HC = 3;
    public static final int STARS_NOT_CANDIDATE = 4;
    public static final int STARS_NOT_ENOUGH_USERS = 5;

    private final int type;

    public CustomNotificationComposer(int type) {
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CustomNotificationComposer);
        this.response.appendInt(this.type);
        return this.response;
    }

    public int getType() {
        return type;
    }
}
