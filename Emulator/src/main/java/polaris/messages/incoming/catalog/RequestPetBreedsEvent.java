package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.PetBreedsComposer;

public class RequestPetBreedsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        final String petName = this.packet.readString();
        this.client.sendResponse(new PetBreedsComposer(
                petName, Emulator.getGameEnvironment().getPetManager().getBreeds(petName)));
    }
}
