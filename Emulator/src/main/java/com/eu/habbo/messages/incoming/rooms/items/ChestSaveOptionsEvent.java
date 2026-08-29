package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.ChestDataComposer;

/**
 * The three switches that live on the chest window itself: {@code int itemId, bool locked,
 * bool autoLock, int capacity}.
 *
 * <p>They are separate from the settings dialog because they are used differently. Closing a chest is
 * a thing you do in the moment, next to the contents you are closing, so the window sends this the
 * instant a box is ticked rather than making someone open a dialog and press save.
 *
 * <p>Who may change what is not the same for all three. Anyone with room rights can <em>lock</em> a
 * chest — that is the panic button, and it should not need the owner to be online. Only the owner can
 * unlock it again, or touch the auto-lock and the ceiling, so a lock cannot be undone by the same
 * rights that set it.
 */
public class ChestSaveOptionsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) return;

        int itemId = this.packet.readInt();
        boolean locked = this.packet.readBoolean();
        boolean autoLock = this.packet.readBoolean();
        int capacity = this.packet.readInt();

        HabboItem item = room.getHabboItem(itemId);
        if (!(item instanceof InteractionWiredChest chest)) return;
        if (!room.hasRights(habbo)) return;

        ChestStorage c = chest.getContents();
        boolean isOwner = habbo.getHabboInfo().getId() == chest.getUserId();

        if (isOwner) {
            c.setLocked(locked);
            c.setAutoLock(autoLock);
            c.setCapacity(capacity);
        } else if (locked) {
            // Room rights lock a chest but never unlock one, and never re-tune someone else's chest.
            c.setLocked(true);
        }

        chest.persistContents();
        this.client.sendResponse(new ChestDataComposer(chest, habbo));
    }
}
