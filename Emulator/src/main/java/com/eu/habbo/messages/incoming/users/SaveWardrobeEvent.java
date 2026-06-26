package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.inventory.WardrobeComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserSavedWardrobeEvent;

public class SaveWardrobeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        final int slotId = this.packet.readInt();
        final String look = this.packet.readString();
        final String gender = this.packet.readString();

        if (slotId < 0 || slotId > 8 || look.isEmpty()) return;
        if (!gender.equals("M") && !gender.equals("F")) return;

        WardrobeComponent.WardrobeItem wardrobeItem;
        if (this.client.getHabbo().getInventory().getWardrobeComponent().getLooks().containsKey(slotId)) {
            wardrobeItem = this.client.getHabbo().getInventory().getWardrobeComponent().getLooks().get(slotId);
            wardrobeItem.setGender(HabboGender.valueOf(gender));
            wardrobeItem.setLook(look);
            wardrobeItem.setNeedsUpdate(true);
        } else {
            wardrobeItem = this.client.getHabbo().getInventory().getWardrobeComponent().createLook(this.client.getHabbo(), slotId, look);
            wardrobeItem.setGender(HabboGender.valueOf(gender));
            wardrobeItem.setNeedsInsert(true);
            this.client.getHabbo().getInventory().getWardrobeComponent().getLooks().put(slotId, wardrobeItem);
        }

        var wardrobeEvent = new UserSavedWardrobeEvent(this.client.getHabbo(), wardrobeItem);
        Emulator.getPluginManager().fireEvent(wardrobeEvent);

        Emulator.getThreading().run(wardrobeItem);
    }
}
