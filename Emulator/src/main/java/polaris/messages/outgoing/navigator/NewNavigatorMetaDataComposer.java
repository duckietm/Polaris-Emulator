package polaris.messages.outgoing.navigator;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewNavigatorMetaDataComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorMetaDataComposer);
        this.response.appendInt(4);
        this.response.appendString("official_view");
        this.response.appendInt(0);
        this.response.appendString("hotel_view");
        this.response.appendInt(0);
        this.response.appendString("roomads_view");
        this.response.appendInt(0);
        this.response.appendString("myworld_view");
        this.response.appendInt(0);

        return this.response;
    }
}
