package polaris.messages.outgoing.hotelway.nux;

import java.util.List;
import polaris.hotel.items.NewUserGift;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewUserGiftComposer extends MessageComposer {
    private final List<List<NewUserGift>> options;

    public NewUserGiftComposer(List<List<NewUserGift>> options) {
        this.options = options;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewUserGiftComposer);
        this.response.appendInt(this.options.size());
        for (List<NewUserGift> option : this.options) {
            this.response.appendInt(1);
            this.response.appendInt(3);
            this.response.appendInt(option.size());
            for (NewUserGift gift : option) {
                gift.serialize(this.response);
            }
        }
        return this.response;
    }

    public List<List<NewUserGift>> getOptions() {
        return options;
    }
}
