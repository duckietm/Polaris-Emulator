package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.items.ChestNotificationComposer;

/**
 * Tells a chest's owner what happened to it, when they asked to be told.
 *
 * <p>Every switch on the notification panel was saved and pushed to the client and read by nobody,
 * so the panel promised things the server never did. This is the one place that reads them.
 *
 * <p>Nothing here reaches for the hotel: the owner is asked for, and the decision about whether they
 * want this at all is made from their own chest's settings.
 */
public final class ChestNotifications {
    /** Tell me wherever I am. */
    private static final int MODE_ALWAYS = 0;

    /** Only tell me while I am standing in the room. */
    private static final int MODE_IN_ROOM = 1;

    /** Never tell me. */
    private static final int MODE_NEVER = 2;

    private ChestNotifications() {}

    /** A player put something in that they do not own the chest for. */
    public static void donation(InteractionWiredChest chest, Room room, Habbo actor, int amount) {
        if (isOwner(chest, actor)) return;

        send(
                chest,
                room,
                ChestNotificationComposer.REASON_DONATION,
                actor,
                amount,
                contents(chest).isNotifyDonation());
    }

    /** A player took something out. */
    public static void withdrawal(InteractionWiredChest chest, Room room, Habbo actor, int amount) {
        send(
                chest,
                room,
                ChestNotificationComposer.REASON_WITHDRAW,
                actor,
                amount,
                contents(chest).isNotifyWithdraw());
    }

    /** A wired effect moved something in or out. */
    public static void wired(InteractionWiredChest chest, Room room, int amount) {
        send(
                chest,
                room,
                ChestNotificationComposer.REASON_WIRED,
                null,
                amount,
                contents(chest).isNotifyWired());
    }

    /**
     * The two that depend on where the chest ended up rather than on what was done to it. Called after
     * a change has landed, so {@code storedBefore} is what it held a moment ago -- a chest that was
     * already full does not announce being full again on every deposit.
     */
    public static void afterChange(InteractionWiredChest chest, Room room, int storedBefore, int storedNow) {
        ChestStorage contents = contents(chest);

        if (storedNow >= contents.getCapacity() && storedBefore < contents.getCapacity()) {
            send(chest, room, ChestNotificationComposer.REASON_FULL, null, storedNow, contents.isNotifyFull());
        }

        if (storedNow <= 0 && storedBefore > 0) {
            send(chest, room, ChestNotificationComposer.REASON_EMPTY, null, 0, contents.isNotifyEmpty());
        }
    }

    private static void send(
            InteractionWiredChest chest, Room room, int reason, Habbo actor, int amount, boolean wanted) {
        if (chest == null || !wanted) return;

        Habbo owner = chest.owner();
        if (owner == null || owner.getClient() == null) return;

        int mode = contents(chest).getNotifyMode();
        if (mode == MODE_NEVER) return;
        if (mode == MODE_IN_ROOM
                && (room == null || room.getHabbo(owner.getHabboInfo().getId()) == null)) return;
        if (mode != MODE_ALWAYS && mode != MODE_IN_ROOM) return;

        owner.getClient()
                .sendResponse(new ChestNotificationComposer(
                        chest.getId(),
                        reason,
                        contents(chest).getName(),
                        actor == null ? "" : actor.getHabboInfo().getUsername(),
                        amount));
    }

    private static boolean isOwner(InteractionWiredChest chest, Habbo habbo) {
        return chest != null && habbo != null && habbo.getHabboInfo().getId() == chest.getUserId();
    }

    private static ChestStorage contents(InteractionWiredChest chest) {
        return chest.getContents();
    }
}
