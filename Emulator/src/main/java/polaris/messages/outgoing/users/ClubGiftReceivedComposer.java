package polaris.messages.outgoing.users;

import java.util.Set;
import polaris.hotel.items.Item;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ClubGiftReceivedComposer extends MessageComposer {
    // :test 735 s:t i:1 s:s i:230 s:throne i:1 b:1 i:1 i:10;
    private final String name;
    private final Set<Item> items;

    public ClubGiftReceivedComposer(String name, Set<Item> items) {
        this.name = name;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ClubGiftReceivedComposer);

        this.response.appendString(this.name);
        this.response.appendInt(this.items.size());

        for (Item item : this.items) {
            item.serialize(this.response);
        }

        return this.response;
    }

    public String getName() {
        return name;
    }

    public Set<Item> getItems() {
        return items;
    }
}
