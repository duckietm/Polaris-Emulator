package polaris.hotel.wired.label;

import java.util.ArrayList;
import java.util.List;
import polaris.WiredPlatform;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraLabel;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.outgoing.wired.WiredLabelsComposer;
import polaris.messages.outgoing.wired.WiredLabelsUpdateComposer;
import polaris.threading.ThreadPooling;

/**
 * Sends the room's furniture labels (wf_label) the way the official client expects them: every
 * label a player may see when they enter the room or their wired rights change, and afterwards only
 * what changed. A label only wired inspectors may see is left out for everybody else, and reaches
 * them as removed when it is hidden from them.
 */
public final class WiredLabelSupport {
    private WiredLabelSupport() {}

    /** Every label in the room, in placement order. */
    public static List<WiredExtraLabel> labels(Room room) {
        List<WiredExtraLabel> labels = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null) return labels;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraLabel label) labels.add(label);
        }
        labels.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return labels;
    }

    /** The labels this player may see. */
    public static List<WiredExtraLabel> visibleTo(Room room, User user) {
        List<WiredExtraLabel> visible = new ArrayList<>();
        for (WiredExtraLabel label : labels(room)) {
            if (label.isVisibleTo(room, user)) visible.add(label);
        }
        return visible;
    }

    /** The full list for one player: on entering the room, or when their wired rights changed. */
    public static void sendAll(Room room, User user) {
        if (room == null || user == null || user.getClient() == null) return;

        user.getClient().sendResponse(new WiredLabelsComposer(visibleTo(room, user)));
    }

    /** Everyone in the room gets their full list again. */
    public static void republish(Room room) {
        if (room == null) return;

        for (User user : room.getUsers()) sendAll(room, user);
    }

    /** A label was placed or changed: those who may see it get it, everyone else loses it. */
    public static void broadcastChanged(Room room, WiredExtraLabel label) {
        if (room == null || label == null) return;

        for (User user : room.getUsers()) {
            if (user == null || user.getClient() == null) continue;

            user.getClient()
                    .sendResponse(
                            label.isVisibleTo(room, user)
                                    ? new WiredLabelsUpdateComposer(List.of(label), List.of())
                                    : new WiredLabelsUpdateComposer(List.of(), List.of(label.getId())));
        }
    }

    /** Writes a changed label to the database on the worker pool, as a saved wired box is. */
    public static void save(WiredExtraLabel label) {
        if (label == null) return;

        label.needsUpdate(true);
        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) threading.run(label);
    }

    /** A label was picked up. */
    public static void broadcastRemoved(Room room, int labelId) {
        if (room == null) return;

        room.sendComposer(new WiredLabelsUpdateComposer(List.of(), List.of(labelId)).compose());
    }
}
