package polaris.messages.outgoing.wheel;

import java.util.List;
import polaris.hotel.wheel.WheelPrize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

// Raw editable prize list for the in-client admin editor (sends value/amount/
// pointsType as stored, unlike WheelDataComposer which resolves icons for players).
public class WheelAdminPrizesComposer extends MessageComposer {
    private final List<WheelPrize> prizes;

    public WheelAdminPrizesComposer(List<WheelPrize> prizes) {
        this.prizes = prizes;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WheelAdminPrizesComposer);
        this.response.appendInt(this.prizes.size());
        for (WheelPrize prize : this.prizes) {
            this.response.appendInt(prize.id);
            this.response.appendString(prize.type);
            this.response.appendString(prize.value == null ? "" : prize.value);
            this.response.appendInt(prize.amount);
            this.response.appendInt(prize.pointsType);
            this.response.appendInt(prize.weight);
            this.response.appendString(prize.label == null ? "" : prize.label);
        }
        return this.response;
    }
}
