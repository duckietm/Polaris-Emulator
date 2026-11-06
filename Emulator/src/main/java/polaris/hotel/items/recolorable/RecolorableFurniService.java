package polaris.hotel.items.recolorable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import polaris.WiredPlatform;
import polaris.hotel.items.interactions.InteractionRecolorable;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.items.RecolorFurniUpdateComposer;
import polaris.threading.ThreadPooling;

/**
 * Recolours placed recolourable Builders Club furni as the official recolour window asks (AIR 2852):
 * one furni, or every furni in the room that matches it, to a colour of its palette. Everyone in the
 * room is told with AIR 181.
 */
public final class RecolorableFurniService {
    /** The official window's scopes. */
    public static final int SCOPE_THIS = 0;

    public static final int SCOPE_SAME_COLOR = 1;
    public static final int SCOPE_SAME_TYPE = 2;
    public static final int SCOPE_SAME_TYPE_AND_COLOR = 3;

    static final int MAX_ITEMS_PER_REQUEST = 500;
    static final long COOLDOWN_MS = 250L;
    /** After recolouring more than one furni at once, the next request waits longer. */
    static final long MASS_COOLDOWN_MS = 2_000L;

    private static final int MAX_TRACKED = 10_000;
    private static final ConcurrentHashMap<Integer, Long> USER_NEXT = new ConcurrentHashMap<>();

    public enum Result {
        DONE,
        TOO_FAST,
        NO_RIGHTS,
        NOT_FOUND,
        NOT_RECOLORABLE,
        NO_SUCH_COLOR
    }

    private RecolorableFurniService() {}

    public static Result recolor(User user, int furniId, int scope, int red, int green, int blue) {
        if (user == null || user.getUserInfo() == null) return Result.NO_RIGHTS;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null || !room.isLoaded()) return Result.NO_RIGHTS;
        if (!room.isOwner(user) && !room.hasRights(user)) return Result.NO_RIGHTS;
        if (!admit(user.getUserInfo().getId(), System.currentTimeMillis())) return Result.TOO_FAST;

        UserItem clicked = room.getUserItem(furniId);
        if (clicked == null || clicked.getBaseItem() == null) return Result.NOT_FOUND;
        if (!(clicked instanceof InteractionRecolorable source)) return Result.NOT_RECOLORABLE;

        if (!isChannel(red) || !isChannel(green) || !isChannel(blue)) return Result.NO_SUCH_COLOR;
        int rgb = (red << 16) | (green << 8) | blue;
        if (!source.palette().contains(rgb)) return Result.NO_SUCH_COLOR;

        List<InteractionRecolorable> targets = targets(room, source, scope, rgb);
        // Furni that already have the colour are not saved or sent again.
        targets.removeIf(item -> item.getColor() == rgb);
        apply(room, targets, rgb);
        if (targets.size() > 1) holdOff(user.getUserInfo().getId(), System.currentTimeMillis() + MASS_COOLDOWN_MS);
        return Result.DONE;
    }

    /** The furni a scope reaches; each must offer the colour in its own palette. */
    static List<InteractionRecolorable> targets(Room room, InteractionRecolorable source, int scope, int rgb) {
        List<InteractionRecolorable> targets = new ArrayList<>();
        if (scope == SCOPE_THIS || scope < SCOPE_THIS || scope > SCOPE_SAME_TYPE_AND_COLOR) {
            targets.add(source);
            return targets;
        }

        int sourceColor = source.getColor();
        int sourceBase = source.getBaseItem().getId();
        for (UserItem item : room.getFloorItems()) {
            if (targets.size() >= MAX_ITEMS_PER_REQUEST) break;
            if (!(item instanceof InteractionRecolorable recolorable) || recolorable.getBaseItem() == null) continue;
            if (!matches(scope, recolorable, sourceBase, sourceColor)) continue;
            if (recolorable.palette().contains(rgb)) targets.add(recolorable);
        }
        if (!targets.contains(source)) targets.add(source);
        return targets;
    }

    static boolean matches(int scope, InteractionRecolorable item, int sourceBase, int sourceColor) {
        boolean sameColor = item.getColor() == sourceColor;
        boolean sameType = item.getBaseItem().getId() == sourceBase;
        return switch (scope) {
            case SCOPE_SAME_COLOR -> sameColor;
            case SCOPE_SAME_TYPE -> sameType;
            case SCOPE_SAME_TYPE_AND_COLOR -> sameType && sameColor;
            default -> false;
        };
    }

    /** Gives the furni the colour, saves them and tells the room. */
    public static void apply(Room room, List<InteractionRecolorable> items, int rgb) {
        List<Integer> ids = new ArrayList<>(items.size());
        ThreadPooling threading = WiredPlatform.threading();
        for (InteractionRecolorable item : items) {
            item.setColor(rgb);
            item.needsUpdate(true);
            if (threading != null) threading.run(item);
            ids.add(item.getId());
        }
        if (!ids.isEmpty()) {
            room.sendComposer(
                    new RecolorFurniUpdateComposer(List.of(new RecolorFurniUpdateComposer.Update(ids, rgb))).compose());
        }
    }

    static boolean isChannel(int value) {
        return value >= 0 && value <= 255;
    }

    private static void holdOff(int userId, long until) {
        USER_NEXT.merge(userId, until, Math::max);
    }

    private static boolean admit(int userId, long now) {
        Long next = USER_NEXT.get(userId);
        if (next != null && now < next) return false;

        if (USER_NEXT.size() > MAX_TRACKED) USER_NEXT.clear();
        USER_NEXT.put(userId, now + COOLDOWN_MS);
        return true;
    }
}
