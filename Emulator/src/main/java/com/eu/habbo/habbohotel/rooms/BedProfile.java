package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * Bed Profile Configuration
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  EDIT THESE VALUES TO ADJUST BED POSITIONING                           │
 * ├──────────────────┬──────────┬────────────┬────────────┬────────────────┤
 * │ Bed Type         │ Width    │ Lay X      │ Lay Y      │ Lay Z          │
 * ├──────────────────┼──────────┼────────────┼────────────┼────────────────┤
 * │ Flat single      │ single   │  0         │  0         │ -0.5           │
 * │ Flat double      │ double   │  0         │  0         │ -0.5           │
 * │ Raised single    │ single   │  0         │  0         │ -0.5           │
 * │ Raised double    │ double   │  0         │  0         │ -0.5           │
 * └──────────────────┴──────────┴────────────┴────────────┴────────────────┘
 */
public class BedProfile {

    // ===== HEIGHT THRESHOLD =====
    private static final double FLAT_BED_HEIGHT_THRESHOLD = 1.0;

    // ===== FLAT BED OFFSETS (sleeping bags, mats, etc.) =====
    private static final double FLAT_SINGLE_LAY_X_OFFSET = 0;
    private static final double FLAT_SINGLE_LAY_Y_OFFSET = 15;
    private static final double FLAT_SINGLE_LAY_Z_OFFSET = -0.5;

    private static final double FLAT_DOUBLE_LAY_X_OFFSET = 0;
    private static final double FLAT_DOUBLE_LAY_Y_OFFSET = 0;
    private static final double FLAT_DOUBLE_LAY_Z_OFFSET = -0.5;

    // ===== RAISED BED OFFSETS (normal beds with frames) =====
    private static final double RAISED_SINGLE_LAY_X_OFFSET = 5;
    private static final double RAISED_SINGLE_LAY_Y_OFFSET = 0;
    private static final double RAISED_SINGLE_LAY_Z_OFFSET = 0;

    private static final double RAISED_DOUBLE_LAY_X_OFFSET = 0;
    private static final double RAISED_DOUBLE_LAY_Y_OFFSET = 0;
    private static final double RAISED_DOUBLE_LAY_Z_OFFSET = -0;

    private final boolean isDouble;
    private final boolean isFlat;
    private final int rotation;
    private final double layXOffset;
    private final double layYOffset;
    private final double layZOffset;

    public BedProfile(HabboItem bed) {
        this.rotation = bed.getRotation();

        this.isDouble = bed.getBaseItem().getWidth() >= 2;
        this.isFlat = bed.getBaseItem().getHeight() < FLAT_BED_HEIGHT_THRESHOLD;

        if (this.isFlat) {
            if (this.isDouble) {
                this.layXOffset = FLAT_DOUBLE_LAY_X_OFFSET;
                this.layYOffset = FLAT_DOUBLE_LAY_Y_OFFSET;
                this.layZOffset = FLAT_DOUBLE_LAY_Z_OFFSET;
            } else {
                this.layXOffset = FLAT_SINGLE_LAY_X_OFFSET;
                this.layYOffset = FLAT_SINGLE_LAY_Y_OFFSET;
                this.layZOffset = FLAT_SINGLE_LAY_Z_OFFSET;
            }
        } else {
            if (this.isDouble) {
                this.layXOffset = RAISED_DOUBLE_LAY_X_OFFSET;
                this.layYOffset = RAISED_DOUBLE_LAY_Y_OFFSET;
                this.layZOffset = RAISED_DOUBLE_LAY_Z_OFFSET;
            } else {
                this.layXOffset = RAISED_SINGLE_LAY_X_OFFSET;
                this.layYOffset = RAISED_SINGLE_LAY_Y_OFFSET;
                this.layZOffset = RAISED_SINGLE_LAY_Z_OFFSET;
            }
        }
    }

    public double getLayXOffset() {
        return this.layXOffset;
    }

    public double getLayYOffset() {
        return this.layYOffset;
    }

    public double getLayZOffset() {
        return this.layZOffset;
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

    public RoomTile getPillow(Room room, short clickX, short clickY, HabboItem bed) {
        if (isLengthAlongY()) {
            return room.getLayout().getTile(clickX, bed.getY());
        } else {
            return room.getLayout().getTile(bed.getX(), clickY);
        }
    }

    /** Whether nobody can lie on {@code pillow}: furni blocks it, or another user, bot or pet is on it. */
    public static boolean isPillowTaken(Room room, RoomTile pillow, RoomUnit walker) {
        if (!room.canLayAt(pillow.x, pillow.y)) return true;

        for (RoomUnit unit : room.getRoomUnitsAt(pillow)) {
            if (unit != null && unit != walker) return true;
        }
        return false;
    }

    public RoomTile snapToLay(Room room, HabboItem bed, short unitX, short unitY) {
        if (isLengthAlongY()) {
            return room.getLayout().getTile(unitX, bed.getY());
        } else {
            return room.getLayout().getTile(bed.getX(), unitY);
        }
    }

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
