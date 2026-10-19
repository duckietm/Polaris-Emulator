package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Where and how an avatar lies on a bed, the way Habbo does it.
 *
 * <p>A bed has one lane per tile of its width. Each lane's pillow is on the bed's leading row: {@code y == bed.y}
 * when the bed runs along y (rotation 0/4), {@code x == bed.x} when it runs along x (rotation 2/6). An avatar
 * lies on a pillow tile, at the bed's z, facing rotation {@code bed.rotation % 4}, with the status {@code lay
 * <bed height>}. The client draws the rest: AIR turns the lay posture to its direction 4 or 2 image and moves it
 * by the action_offset_lay canvas offset (AvatarImage.appendAction, AvatarActionManager.parseActionOffsets), so
 * the server sends no per-bed screen offset.
 */
public class BedProfile {

    private static final double FLAT_BED_HEIGHT_THRESHOLD = 1.0;

    private final boolean isDouble;
    private final boolean isFlat;
    private final int rotation;
    private final int lanes;

    public BedProfile(HabboItem bed) {
        this.rotation = bed.getRotation();
        this.lanes = Math.max(1, bed.getBaseItem().getWidth());

        this.isDouble = bed.getBaseItem().getWidth() >= 2;
        this.isFlat = bed.getBaseItem().getHeight() < FLAT_BED_HEIGHT_THRESHOLD;
    }

    /** The {@code lay} status parameter for an avatar on {@code bed}: the bed's height, as Habbo sends it. */
    public static String layStatus(HabboItem bed) {
        return String.valueOf(Item.getCurrentHeight(bed));
    }

    /**
     * Lays {@code unit} on {@code bed}: onto its lane's pillow (the nearest free one when that is taken), at the
     * bed's z, facing along the bed, with Habbo's lay status.
     */
    public static void lay(Room room, HabboItem bed, RoomUnit unit) {
        BedProfile profile = new BedProfile(bed);
        RoomTile pillow = profile.snapToLay(room, bed, unit.getX(), unit.getY());

        if (pillow != null) {
            RoomTile free = profile.findFreePillow(room, bed, pillow, unit);
            unit.setLocation(free != null ? free : pillow);
        }

        layInPlace(bed, unit);
    }

    /** Lays {@code unit} on {@code bed} on the tile it is on: the bed's z, facing along the bed, lay status. */
    public static void layInPlace(HabboItem bed, RoomUnit unit) {
        unit.setZ(bed.getZ());
        unit.setPreviousLocationZ(bed.getZ());
        unit.setRotation(RoomUserRotation.fromValue(bed.getRotation() % 4));
        unit.setStatus(RoomUnitStatus.LAY, layStatus(bed));
    }

    /**
     * Always 0: Habbo sends no per-bed screen offset, the client places the lay posture itself.
     *
     * @deprecated kept for plugins; the lay status carries only the bed height.
     */
    @Deprecated
    public double getLayXOffset() {
        return 0;
    }

    /**
     * Always 0: Habbo sends no per-bed screen offset, the client places the lay posture itself.
     *
     * @deprecated kept for plugins; the lay status carries only the bed height.
     */
    @Deprecated
    public double getLayYOffset() {
        return 0;
    }

    /**
     * Always 0: an avatar lies at the bed's height, as Habbo sends it.
     *
     * @deprecated kept for plugins; see {@link #layStatus(HabboItem)}.
     */
    @Deprecated
    public double getLayZOffset() {
        return 0;
    }

    public boolean isDouble() {
        return this.isDouble;
    }

    public boolean isFlat() {
        return this.isFlat;
    }

    public boolean isLengthAlongY() {
        return this.rotation == 0 || this.rotation == 4;
    }

    /** The number of lanes (pillows) of the bed: its width. */
    public int getLaneCount() {
        return this.lanes;
    }

    /** The pillow of the lane holding the clicked tile. */
    public RoomTile getPillow(Room room, short clickX, short clickY, HabboItem bed) {
        return this.snapToLay(room, bed, clickX, clickY);
    }

    /** The pillow tiles of {@code bed}, one per lane, in lane order. */
    public List<RoomTile> getPillows(Room room, HabboItem bed) {
        List<RoomTile> pillows = new ArrayList<>(this.lanes);

        for (int lane = 0; lane < this.lanes; lane++) {
            RoomTile pillow = this.isLengthAlongY()
                    ? room.getLayout().getTile((short) (bed.getX() + lane), bed.getY())
                    : room.getLayout().getTile(bed.getX(), (short) (bed.getY() + lane));

            if (pillow != null) pillows.add(pillow);
        }
        return pillows;
    }

    /**
     * {@code wanted} when {@code walker} may lie there, otherwise the free pillow of the nearest other lane; null
     * when every pillow is taken.
     */
    public RoomTile findFreePillow(Room room, HabboItem bed, RoomTile wanted, RoomUnit walker) {
        if (wanted != null && !isPillowTaken(room, wanted, walker)) return wanted;

        int wantedLane = wanted == null ? 0 : this.laneOf(bed, wanted);
        RoomTile best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (RoomTile pillow : this.getPillows(room, bed)) {
            if (pillow.equals(wanted) || isPillowTaken(room, pillow, walker)) continue;

            int distance = Math.abs(this.laneOf(bed, pillow) - wantedLane);
            if (distance < bestDistance) {
                best = pillow;
                bestDistance = distance;
            }
        }
        return best;
    }

    private int laneOf(HabboItem bed, RoomTile tile) {
        return this.isLengthAlongY() ? tile.x - bed.getX() : tile.y - bed.getY();
    }

    /** Whether nobody can lie on {@code pillow}: furni blocks it, or another user, bot or pet is on it. */
    public static boolean isPillowTaken(Room room, RoomTile pillow, RoomUnit walker) {
        if (!room.canLayAt(pillow.x, pillow.y)) return true;

        for (RoomUnit unit : room.getRoomUnitsAt(pillow)) {
            if (unit != null && unit != walker) return true;
        }
        return false;
    }

    /** The pillow of the lane holding the tile ({@code unitX}, {@code unitY}). */
    public RoomTile snapToLay(Room room, HabboItem bed, short unitX, short unitY) {
        if (this.isLengthAlongY()) {
            return room.getLayout().getTile(unitX, bed.getY());
        } else {
            return room.getLayout().getTile(bed.getX(), unitY);
        }
    }

    /** For a double bed, the pillow of the lane next to {@code currentPillow}'s; null for a single bed. */
    public RoomTile getOtherSide(Room room, HabboItem bed, RoomTile currentPillow) {
        if (!this.isDouble) return null;

        short otherX = currentPillow.x;
        short otherY = currentPillow.y;

        // The lane next to the pillow's own lane, inside the bed: from the first lane the second, otherwise the one
        // before.
        if (isLengthAlongY()) {
            otherX = (short) (currentPillow.x == bed.getX() ? bed.getX() + 1 : currentPillow.x - 1);
        } else {
            otherY = (short) (currentPillow.y == bed.getY() ? bed.getY() + 1 : currentPillow.y - 1);
        }

        RoomTile otherTile = room.getLayout().getTile(otherX, otherY);
        if (otherTile == null) return null;

        HabboItem itemAtOther = room.getTopItemAt(otherX, otherY);
        if (itemAtOther != null && itemAtOther.getId() == bed.getId()) {
            return otherTile;
        }
        return null;
    }
}
