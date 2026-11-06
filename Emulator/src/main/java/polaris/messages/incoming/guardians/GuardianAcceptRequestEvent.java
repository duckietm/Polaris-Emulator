package polaris.messages.incoming.guardians;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;

public class GuardianAcceptRequestEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Emulator.getGameEnvironment().getGuideManager().acceptTicket(this.client.getUser(), this.packet.readBoolean());
    }
}
