package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.hotel.users.inventory.WardrobeComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserSavedWardrobeEvent;

public class SaveWardrobeEvent extends MessageHandler {
    // The client offers 10 slots (avatar.wardrobe.max.slots), numbered from 1.
    static final int MIN_SLOT_ID = 1;
    static final int MAX_SLOT_ID = 10;
    // users_wardrobe.look is varchar(256).
    static final int MAX_LOOK_LENGTH = 256;

    @Override
    public int getRatelimit() {
        return 500;
    }

    /** A slot the client offers, a figure that is structurally valid and fits the column, and a known gender. */
    static boolean isAcceptable(int slotId, String look, String gender) {
        if (slotId < MIN_SLOT_ID || slotId > MAX_SLOT_ID) return false;
        if (look == null || look.isEmpty() || look.length() > MAX_LOOK_LENGTH) return false;
        if (!ClothingValidationManager.isStructurallyValidFigure(look)) return false;
        return "M".equals(gender) || "F".equals(gender);
    }

    @Override
    public void handle() throws Exception {
        final int slotId = this.packet.readInt();
        final String look = this.packet.readString();
        final String gender = this.packet.readString();

        if (!isAcceptable(slotId, look, gender)) return;

        // The same wearability check as saving the current look, when the hotel turns it on.
        final String storedLook = ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS
                ? ClothingValidationManager.validateLook(this.client.getUser(), look, gender)
                : look;

        WardrobeComponent.WardrobeItem wardrobeItem = this.client
                .getUser()
                .getInventory()
                .getWardrobeComponent()
                .saveLook(this.client.getUser(), slotId, storedLook, UserGender.valueOf(gender));

        UserSavedWardrobeEvent wardrobeEvent = new UserSavedWardrobeEvent(this.client.getUser(), wardrobeItem);
        Emulator.getPluginManager().fireEvent(wardrobeEvent);

        Emulator.getThreading().run(wardrobeItem);
    }
}
