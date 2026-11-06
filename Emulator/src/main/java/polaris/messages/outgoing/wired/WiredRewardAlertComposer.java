package polaris.messages.outgoing.wired;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WiredRewardAlertComposer extends MessageComposer {
    public static final int LIMITED_NO_MORE_AVAILABLE = 0;
    public static final int REWARD_ALREADY_RECEIVED = 1;
    public static final int REWARD_ALREADY_RECEIVED_THIS_TODAY = 2;
    public static final int REWARD_ALREADY_RECEIVED_THIS_HOUR = 3;
    public static final int REWARD_ALREADY_RECEIVED_THIS_MINUTE = 8;
    public static final int UNLUCKY_NO_REWARD = 4;
    public static final int REWARD_ALL_COLLECTED = 5;
    public static final int REWARD_RECEIVED_ITEM = 6;
    public static final int REWARD_RECEIVED_BADGE = 7;

    private final int code;

    public WiredRewardAlertComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredRewardAlertComposer);
        this.response.appendInt(this.code);
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
