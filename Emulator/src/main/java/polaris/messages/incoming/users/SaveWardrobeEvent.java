package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.inventory.WardrobeComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedWardrobeEvent;

public class SaveWardrobeEvent extends MessageHandler {
    // The client offers 10 slots (avatar.wardrobe.max.slots), numbered from 1.
    static final int MAX_SLOT_ID = 10;
    // users_wardrobe.look is varchar(256).
    static final int MAX_LOOK_LENGTH = 256;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        final int slotId = this.packet.readInt();
        final String look = this.packet.readString();
        final String gender = this.packet.readString();

        if (slotId < 0 || slotId > MAX_SLOT_ID || look.isEmpty() || look.length() > MAX_LOOK_LENGTH) return;
        if (!gender.equals("M") && !gender.equals("F")) return;

        WardrobeComponent.WardrobeItem wardrobeItem;
        if (this.client
                .getUser()
                .getInventory()
                .getWardrobeComponent()
                .getLooks()
                .containsKey(slotId)) {
            wardrobeItem = this.client
                    .getUser()
                    .getInventory()
                    .getWardrobeComponent()
                    .getLooks()
                    .get(slotId);
            wardrobeItem.setGender(UserGender.valueOf(gender));
            wardrobeItem.setLook(look);
            wardrobeItem.setNeedsUpdate(true);
        } else {
            wardrobeItem = this.client
                    .getUser()
                    .getInventory()
                    .getWardrobeComponent()
                    .createLook(this.client.getUser(), slotId, look);
            wardrobeItem.setGender(UserGender.valueOf(gender));
            wardrobeItem.setNeedsInsert(true);
            this.client
                    .getUser()
                    .getInventory()
                    .getWardrobeComponent()
                    .getLooks()
                    .put(slotId, wardrobeItem);
        }

        UserSavedWardrobeEvent wardrobeEvent = new UserSavedWardrobeEvent(this.client.getUser(), wardrobeItem);
        Emulator.getPluginManager().fireEvent(wardrobeEvent);

        Emulator.getThreading().run(wardrobeItem);
    }
}
