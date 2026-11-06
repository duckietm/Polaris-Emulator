package polaris.messages.incoming.catalog;

import org.apache.commons.lang3.StringUtils;
import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.PetNameErrorComposer;

public class CheckPetNameEvent extends MessageHandler {
    public static volatile int PET_NAME_LENGTH_MINIMUM = Emulator.getConfig().getInt("hotel.pets.name.length.min");
    public static volatile int PET_NAME_LENGTH_MAXIMUM = Emulator.getConfig().getInt("hotel.pets.name.length.max");

    @Override
    public void handle() throws Exception {
        String petName = this.packet.readString();
        this.packet.readInt(); // Name-validation type; this handler validates pet names only.

        if (petName.length() < PET_NAME_LENGTH_MINIMUM) {
            this.client.sendResponse(
                    new PetNameErrorComposer(PetNameErrorComposer.NAME_TO_SHORT, PET_NAME_LENGTH_MINIMUM + ""));
        } else if (petName.length() > PET_NAME_LENGTH_MAXIMUM) {
            this.client.sendResponse(
                    new PetNameErrorComposer(PetNameErrorComposer.NAME_TO_LONG, PET_NAME_LENGTH_MAXIMUM + ""));
        } else if (!StringUtils.isAlphanumeric(petName)) {
            this.client.sendResponse(new PetNameErrorComposer(PetNameErrorComposer.FORBIDDEN_CHAR, petName));
        } else {
            this.client.sendResponse(new PetNameErrorComposer(PetNameErrorComposer.NAME_OK, petName));
        }
    }
}
