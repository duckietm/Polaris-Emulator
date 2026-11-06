package polaris.messages.outgoing.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RecyclerLogicComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerLogicComposer);
        Map<Integer, Set<Item>> prizes =
                Emulator.getGameEnvironment().getCatalogManager().getRecyclerPrizesSnapshot();
        List<Map.Entry<Integer, Set<Item>>> levels = new ArrayList<>(prizes.entrySet());
        levels.sort(Map.Entry.comparingByKey());

        this.response.appendInt(levels.size());
        for (Map.Entry<Integer, Set<Item>> map : levels) {
            this.response.appendInt(map.getKey());
            this.response.appendInt(Emulator.getConfig().getInt("hotel.ecotron.rarity.chance." + map.getKey(), 0));

            List<Item> items = new ArrayList<>(map.getValue());
            items.sort(Comparator.comparingInt(Item::getId));
            this.response.appendInt(items.size());
            for (Item item : items) {
                this.response.appendString(item.getName());
                this.response.appendInt(1);
                this.response.appendString(item.getType().code.toLowerCase());
                this.response.appendInt(item.getSpriteId());
            }
        }
        return this.response;
    }
}
