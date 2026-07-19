package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GiftConfigurationComposer extends MessageComposer {
    public static volatile List<Integer> BOX_TYPES = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 8);
    public static volatile List<Integer> RIBBON_TYPES = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GiftConfigurationComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(Emulator.getConfig().getInt("hotel.gifts.special.price", 2));

        this.response.appendInt(Emulator.getGameEnvironment().getCatalogManager().giftWrappers.size());
        for (Integer i : Emulator.getGameEnvironment().getCatalogManager().giftWrappers.keySet()) {
            this.response.appendInt(i);
        }

        this.response.appendInt(BOX_TYPES.size());
        for (Integer type : BOX_TYPES) {
            this.response.appendInt(type);
        }

        this.response.appendInt(RIBBON_TYPES.size());
        for (Integer type : RIBBON_TYPES) {
            this.response.appendInt(type);
        }

        this.response.appendInt(Emulator.getGameEnvironment().getCatalogManager().giftFurnis.size());

        for (Map.Entry<Integer, Integer> set : Emulator.getGameEnvironment().getCatalogManager().giftFurnis.entrySet()) {
            this.response.appendInt(set.getKey());
        }

        return this.response;
    }
}
