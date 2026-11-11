package polaris.messages.incoming.inventory.prefixes;

import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;
import polaris.hotel.users.inventory.PrefixUpdates;
import polaris.messages.incoming.MessageHandler;

public class DeletePrefixEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int prefixId = this.packet.readInt();
        User user = this.client.getUser();
        if (user == null) return;

        UserPrefix prefix = user.getInventory().getPrefixesComponent().getPrefix(prefixId);

        if (prefix == null) return;

        // Deleting the worn prefix also takes it off the name the room sees.
        boolean activeRemoved = user.getInventory().getPrefixesComponent().deletePrefix(prefix);
        PrefixUpdates.removed(user, activeRemoved);
    }
}
