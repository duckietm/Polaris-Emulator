package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ItemIntStateComposer extends MessageComposer {
    private final int id;
    private final int value;

    public ItemIntStateComposer(int id, int value) {
        this.id = id;
        this.value = value;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemStateComposer2);
        this.response.appendInt(BuildersClubItemIds.toWire(this.id));
        this.response.appendInt(this.value);
        return this.response;
    }

    public int getId() {
        return id;
    }

    public int getValue() {
        return value;
    }
}
