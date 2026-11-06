package polaris.hotel.items.interactions;

import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.Item;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

/**
 * Water furni (bw_water_1/2, val13_water, stackable_water). Each piece stores a mask of which
 * neighbouring tiles hold the same kind of water, so the client draws touching pieces as one pool.
 * Different kinds keep their shore edge between them; shallow and deep bw water still join at the
 * corners. Stackable water may also be placed on other furni.
 */
public class InteractionWater extends InteractionDefault {

    private static final String SHALLOW_WATER_NAME = "bw_water_1";
    private static final String DEEP_WATER_NAME = "bw_water_2";
    private static final String STACKABLE_WATER_NAME = "stackable_water";

    private final boolean isDeepWater;
    private boolean isInRoom;

    public InteractionWater(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.isDeepWater = isNamed(baseItem, DEEP_WATER_NAME);
        this.isInRoom = this.getRoomId() != 0;
    }

    public InteractionWater(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.isDeepWater = isNamed(item, DEEP_WATER_NAME);
        this.isInRoom = this.getRoomId() != 0;
    }

    private static boolean isNamed(Item item, String name) {
        return item != null && item.getName() != null && item.getName().equalsIgnoreCase(name);
    }

    private static boolean isBwWater(Item item) {
        return isNamed(item, SHALLOW_WATER_NAME) || isNamed(item, DEEP_WATER_NAME);
    }

    /** Recomputes this piece's mask, for water that was placed before its kind joined (room load). */
    public void refreshMask(Room room) {
        if (room != null && this.isInRoom) {
            this.updateWater(room);
        }
    }

    /**
     * Whether this piece joins the other water piece at a neighbouring tile: the same kind always
     * does; shallow bw water also takes deep bw water at its corners to avoid clipping.
     */
    boolean joins(InteractionWater other, boolean corner) {
        Item mine = this.getBaseItem();
        Item theirs = other.getBaseItem();
        if (mine != null && theirs != null && mine.getId() == theirs.getId()) {
            return true;
        }
        return isBwWater(mine) && isBwWater(theirs) && corner && !this.isDeepWater;
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.updateWaters(room, oldLocation);
    }

    @Override
    public void onPickUp(Room room) {
        this.isInRoom = false;
        this.updateWaters(room, null);

        Object[] empty = new Object[] {};
        for (User user : room.getUsersOnItem(this)) {
            try {
                this.onWalkOff(user.getRoomUnit(), room, empty);
            } catch (Exception e) {

            }
        }

        for (Bot bot : room.getBotsOnItem(this)) {
            try {
                this.onWalkOff(bot.getRoomUnit(), room, empty);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onPlace(Room room) {
        this.isInRoom = true;
        this.updateWaters(room, null);
        super.onPlace(room);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if (pet == null) return;

        if (!pet.getRoomUnit().hasStatus(RoomUnitStatus.SWIM) && pet.getPetData().canSwim) {
            pet.getRoomUnit().setStatus(RoomUnitStatus.SWIM, "");
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if (pet == null) return;

        pet.getRoomUnit().removeStatus(RoomUnitStatus.SWIM);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean canToggle(User user, Room room) {
        return false;
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<UserItem>>> itemsAtLocation) {
        if (isNamed(this.getBaseItem(), STACKABLE_WATER_NAME)) {
            return super.canStackAt(room, itemsAtLocation);
        }

        for (Pair<RoomTile, Set<UserItem>> set : itemsAtLocation) {
            for (UserItem item : set.getValue()) {
                if (!(item instanceof InteractionWater)) {
                    return false;
                }
            }
        }

        return super.canStackAt(room, itemsAtLocation);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        if (!super.canWalkOn(roomUnit, room, objects)) return false;

        Pet pet = room.getPet(roomUnit);

        return pet == null || pet.getPetData().canSwim;
    }

    private void updateWaters(Room room, RoomTile oldLocation) {
        // Update ourself.
        this.updateWater(room);

        // Find targets containing furni to update.
        Rectangle target = this.getRectangle(1, 1);
        Rectangle targetOld = null;

        if (oldLocation != null) {
            targetOld = RoomLayout.getRectangle(
                    oldLocation.x - 1,
                    oldLocation.y - 1,
                    this.getBaseItem().getWidth() + 2,
                    this.getBaseItem().getLength() + 2,
                    this.getRotation());
        }

        // Update neighbouring water.
        for (UserItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWater.class)) {
            // We already updated ourself.
            if (item == this) {
                continue;
            }

            // Check if found water furni is touching or intersecting our water furni.
            // Check the same for the old location
            Rectangle itemRectangle = item.getRectangle();

            if (target.intersects(itemRectangle) || (targetOld != null && targetOld.intersects(itemRectangle))) {
                ((InteractionWater) item).updateWater(room);
            }
        }

        // Update water items we might have missed in the old location.
        if (targetOld != null) {
            for (UserItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWaterItem.class)) {
                if (targetOld.intersects(item.getRectangle())) {
                    ((InteractionWaterItem) item).update();
                }
            }
        }
    }

    private void updateWater(Room room) {
        Rectangle target = this.getRectangle();

        // Only update water item furnis that are intersecting with us.
        for (UserItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWaterItem.class)) {
            if (target.intersects(item.getRectangle())) {
                ((InteractionWaterItem) item).update();
            }
        }

        // Prepare bits for cutting off water.
        byte _1 = 0;
        byte _2 = 0;
        byte _3 = 0;
        byte _4 = 0;
        byte _5 = 0;
        byte _6 = 0;
        byte _7 = 0;
        byte _8 = 0;
        byte _9 = 0;
        byte _10 = 0;
        byte _11 = 0;
        byte _12 = 0;

        // Check if we are touching a water tile.
        if (this.isValidForMask(room, this.getX() - 1, this.getY() - 1, this.getZ(), true)) {
            _1 = 1;
        }
        if (this.isValidForMask(room, this.getX(), this.getY() - 1, this.getZ())) {
            _2 = 1;
        }
        if (this.isValidForMask(room, this.getX() + 1, this.getY() - 1, this.getZ())) {
            _3 = 1;
        }
        if (this.isValidForMask(room, this.getX() + 2, this.getY() - 1, this.getZ(), true)) {
            _4 = 1;
        }
        if (this.isValidForMask(room, this.getX() - 1, this.getY(), this.getZ())) {
            _5 = 1;
        }
        if (this.isValidForMask(room, this.getX() + 2, this.getY(), this.getZ())) {
            _6 = 1;
        }
        if (this.isValidForMask(room, this.getX() - 1, this.getY() + 1, this.getZ())) {
            _7 = 1;
        }
        if (this.isValidForMask(room, this.getX() + 2, this.getY() + 1, this.getZ())) {
            _8 = 1;
        }
        if (this.isValidForMask(room, this.getX() - 1, this.getY() + 2, this.getZ(), true)) {
            _9 = 1;
        }
        if (this.isValidForMask(room, this.getX(), this.getY() + 2, this.getZ())) {
            _10 = 1;
        }
        if (this.isValidForMask(room, this.getX() + 1, this.getY() + 2, this.getZ())) {
            _11 = 1;
        }
        if (this.isValidForMask(room, this.getX() + 2, this.getY() + 2, this.getZ(), true)) {
            _12 = 1;
        }

        // Check if we are touching invalid tiles.
        // if (_1  == 0 && room.getLayout().isVoidTile((short)(this.getX() -1), (short) (this.getY() -1))) _1  = 1;
        if (_2 == 0 && room.getLayout().isVoidTile(this.getX(), (short) (this.getY() - 1))) _2 = 1;
        if (_3 == 0 && room.getLayout().isVoidTile((short) (this.getX() + 1), (short) (this.getY() - 1))) _3 = 1;
        // if (_4  == 0 && room.getLayout().isVoidTile((short) (this.getX() + 2), (short) (this.getY() - 1))) _4  = 1;
        if (_5 == 0 && room.getLayout().isVoidTile((short) (this.getX() - 1), this.getY())) _5 = 1;
        if (_6 == 0 && room.getLayout().isVoidTile((short) (this.getX() + 2), this.getY())) _6 = 1;
        if (_7 == 0 && room.getLayout().isVoidTile((short) (this.getX() - 1), (short) (this.getY() + 1))) _7 = 1;
        if (_8 == 0 && room.getLayout().isVoidTile((short) (this.getX() + 2), (short) (this.getY() + 1))) _8 = 1;
        // if (_9  == 0 && room.getLayout().isVoidTile((short)(this.getX() -1), (short) (this.getY() + 2))) _9 = 1;
        if (_10 == 0 && room.getLayout().isVoidTile(this.getX(), (short) (this.getY() + 2))) _10 = 1;
        if (_11 == 0 && room.getLayout().isVoidTile((short) (this.getX() + 1), (short) (this.getY() + 2))) _11 = 1;
        // if (_12 == 0 && room.getLayout().isVoidTile((short) (this.getX() + 2), (short) (this.getY() + 2))) _12 = 1;

        // Update water.
        int result = (_1 << 11)
                | (_2 << 10)
                | (_3 << 9)
                | (_4 << 8)
                | (_5 << 7)
                | (_6 << 6)
                | (_7 << 5)
                | (_8 << 4)
                | (_9 << 3)
                | (_10 << 2)
                | (_11 << 1)
                | _12;

        String updatedData = String.valueOf(result);

        if (!this.getExtradata().equals(updatedData)) {
            this.setExtradata(updatedData);
            this.needsUpdate(true);
            room.updateItem(this);
        }
    }

    private boolean isValidForMask(Room room, int x, int y, double z) {
        return this.isValidForMask(room, x, y, z, false);
    }

    private boolean isValidForMask(Room room, int x, int y, double z, boolean corner) {
        for (UserItem item : room.getItemsAt(x, y, z)) {
            if (item instanceof InteractionWater) {
                InteractionWater water = (InteractionWater) item;

                // Take out picked up water from the recalculation.
                if (!water.isInRoom) {
                    continue;
                }

                if (this.joins(water, corner)) {
                    return true;
                }
            }
        }

        return false;
    }
}
