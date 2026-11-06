package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserPointsComposer extends MessageComposer {
    private final int currentAmount;
    private final int amountAdded;
    private final int type;

    public UserPointsComposer(int currentAmount, int amountAdded, int type) {
        this.currentAmount = currentAmount;
        this.amountAdded = amountAdded;
        this.type = type;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserPointsComposer);
        this.response.appendInt(this.currentAmount);
        this.response.appendInt(this.amountAdded);
        this.response.appendInt(this.type);
        return this.response;
    }

    public int getCurrentAmount() {
        return currentAmount;
    }

    public int getAmountAdded() {
        return amountAdded;
    }

    public int getType() {
        return type;
    }
}
