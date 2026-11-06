package polaris.messages.outgoing.hotelview;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HotelViewConcurrentUsersComposer extends MessageComposer {
    public static final int ACTIVE = 0;
    public static final int HIDDEN = 2;
    public static final int ACHIEVED = 3;

    private final int state;
    private final int userCount;
    private final int goal;

    public HotelViewConcurrentUsersComposer(int state, int userCount, int goal) {
        this.state = state;
        this.userCount = userCount;
        this.goal = goal;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewConcurrentUsersComposer);
        this.response.appendInt(this.state);
        this.response.appendInt(this.userCount);
        this.response.appendInt(this.goal);
        return this.response;
    }

    public int getState() {
        return state;
    }

    public int getUserCount() {
        return userCount;
    }

    public int getGoal() {
        return goal;
    }
}
