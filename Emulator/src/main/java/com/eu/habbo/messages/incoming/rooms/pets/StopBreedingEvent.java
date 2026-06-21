package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class StopBreedingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        HabboItem item = this.client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(itemId);

        if (item instanceof InteractionPetBreedingNest nest) {
            nest.stopBreeding(this.client.getHabbo());
        }
    }
}