package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Map;
import java.util.Set;

public class RecyclerLogicComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecyclerLogicComposer);
        Map<Integer, Set<Item>> prizes = Emulator.getGameEnvironment().getCatalogManager()
                .getRecyclerPrizesSnapshot();
        this.response.appendInt(prizes.size());
        for (Map.Entry<Integer, Set<Item>> map : prizes.entrySet()) {
            this.response.appendInt(map.getKey());
            this.response.appendInt(Integer.valueOf(Emulator.getConfig().getValue("hotel.ecotron.rarity.chance." + map.getKey())));
            this.response.appendInt(map.getValue().size());
            for (Item item : map.getValue()) {
                this.response.appendString(item.getName());
                this.response.appendInt(1);
                this.response.appendString(item.getType().code.toLowerCase());
                this.response.appendInt(item.getSpriteId());
            }
        }
        return this.response;
    }
}
