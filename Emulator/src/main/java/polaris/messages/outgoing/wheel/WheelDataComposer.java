package polaris.messages.outgoing.wheel;

import java.util.List;
import polaris.hotel.wheel.WheelPrize;
import polaris.hotel.wheel.WheelUserState;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

// User spin balance + cost + the full prize list (one entry per slice).
public class WheelDataComposer extends MessageComposer {
    private final WheelUserState state;
    private final int spinCost;
    private final int spinCostType;
    private final List<WheelPrize> prizes;

    public WheelDataComposer(WheelUserState state, int spinCost, int spinCostType, List<WheelPrize> prizes) {
        this.state = state;
        this.spinCost = spinCost;
        this.spinCostType = spinCostType;
        this.prizes = prizes;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WheelDataComposer);
        this.response.appendInt(this.state.freeSpins);
        this.response.appendInt(this.state.extraSpins);
        this.response.appendInt(this.spinCost);
        this.response.appendInt(this.spinCostType);

        this.response.appendInt(this.prizes.size());
        for (WheelPrize prize : this.prizes) {
            this.response.appendInt(prize.id);
            this.response.appendString(prize.type);
            this.response.appendInt(prize.spriteId); // item only, else 0
            this.response.appendString(prize.badgeCode()); // badge only, else ""
            this.response.appendInt(prize.amount);
            this.response.appendInt(prize.pointsType);
            this.response.appendString(prize.label == null ? "" : prize.label);
        }

        return this.response;
    }
}
