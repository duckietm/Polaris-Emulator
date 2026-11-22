package polaris.hotel.rooms;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.users.UserItem;

/**
 * Where every furni of a room stood, and in which state, when the room last loaded; a furni placed since is
 * remembered as it was placed. The wired menu's Rollback puts the furni that are still in the room back to this.
 */
public final class RoomRestorePoints {
    /** One furni's place and state. */
    record Point(short x, short y, double z, int rotation, String wallPosition, String extradata) {
        static Point of(UserItem item) {
            return new Point(
                    item.getX(),
                    item.getY(),
                    item.getZ(),
                    item.getRotation(),
                    item.getWallPosition(),
                    item.getExtradata());
        }
    }

    private final Map<Integer, Point> points = new ConcurrentHashMap<>();

    /** Starts over with the furni of a room that just loaded. */
    public void captureLoaded(Collection<UserItem> items) {
        this.points.clear();
        if (items == null) return;
        for (UserItem item : items) {
            if (item != null) this.points.put(item.getId(), Point.of(item));
        }
    }

    /** Remembers a furni that was placed after the load; one the room already knows keeps its older point. */
    public void capturePlaced(UserItem item) {
        if (item != null) this.points.putIfAbsent(item.getId(), Point.of(item));
    }

    public int size() {
        return this.points.size();
    }

    /**
     * Puts the furni back and marks the changed ones for saving. A furni without a point (it was never in the
     * room at a load or placement) is left alone, and so is one that left the room since: it is not in
     * {@code items}. A high score board goes back to its place but keeps its scores.
     *
     * @return how many furni changed
     */
    public int restore(Collection<UserItem> items) {
        int changed = 0;
        if (items == null) return changed;

        for (UserItem item : items) {
            Point point = item == null ? null : this.points.get(item.getId());
            if (point == null) continue;

            boolean keepsData = item instanceof InteractionWiredHighscore;
            boolean same = item.getX() == point.x()
                    && item.getY() == point.y()
                    && Double.compare(item.getZ(), point.z()) == 0
                    && item.getRotation() == point.rotation()
                    && java.util.Objects.equals(item.getWallPosition(), point.wallPosition())
                    && (keepsData || java.util.Objects.equals(item.getExtradata(), point.extradata()));
            if (same) continue;

            item.setX(point.x());
            item.setY(point.y());
            item.setZ(point.z());
            item.setRotation(point.rotation());
            if (point.wallPosition() != null) item.setWallPosition(point.wallPosition());
            if (!keepsData && point.extradata() != null) item.setExtradata(point.extradata());
            item.needsUpdate(true);
            changed++;
        }

        return changed;
    }
}
