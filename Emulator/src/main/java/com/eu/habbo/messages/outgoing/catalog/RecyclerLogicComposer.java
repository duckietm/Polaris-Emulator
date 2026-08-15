package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
