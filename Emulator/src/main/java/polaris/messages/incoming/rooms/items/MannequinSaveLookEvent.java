package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionMannequin;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class MannequinSaveLookEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null || !room.isOwner(user)) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        UserItem item = room.getUserItem(itemId);
        // Only a mannequin takes a look: other furni read their extradata as a number or a state.
        if (!(item instanceof InteractionMannequin)) return;

        String[] data = item.getExtradata().split(":");
        // Extract only clothing parts, excluding head/face features (hr, hd, he, ea, ha, fa)

        StringBuilder look = new StringBuilder();

        for (String s : user.getUserInfo().getLook().split("\\.")) {
            if (!s.contains("hr")
                    && !s.contains("hd")
                    && !s.contains("he")
                    && !s.contains("ea")
                    && !s.contains("ha")
                    && !s.contains("fa")) {
                look.append(s).append(".");
            }
        }

        if (look.length() > 0) {
            look = new StringBuilder(look.substring(0, look.length() - 1));
        }

        if (data.length == 3) {
            item.setExtradata(user.getUserInfo().getGender().name().toLowerCase() + ":" + look + ":" + data[2]);
        } else {
            item.setExtradata(user.getUserInfo().getGender().name().toLowerCase() + ":" + look + ":"
                    + user.getUserInfo().getUsername() + "'s look.");
        }

        item.needsUpdate(true);
        Emulator.getThreading().run(item);
        room.updateItem(item);
    }
}
