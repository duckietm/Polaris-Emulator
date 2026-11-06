package polaris.messages.incoming.inventory.prefixes;

import polaris.Emulator;
import polaris.hotel.users.UserPrefix;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.prefixes.UserPrefixesComposer;

public class DeletePrefixEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int prefixId = this.packet.readInt();

        UserPrefix prefix =
                this.client.getUser().getInventory().getPrefixesComponent().getPrefix(prefixId);

        if (prefix == null) return;

        this.client.getUser().getInventory().getPrefixesComponent().removePrefix(prefix);
        prefix.needsDelete(true);
        Emulator.getThreading().run(prefix);

        this.client.sendResponse(new UserPrefixesComposer(this.client.getUser()));
    }
}
