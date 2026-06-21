package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import gnu.trove.set.hash.THashSet;
import org.apache.commons.math3.util.Pair;

import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InteractionWater extends InteractionDefault {

    private static final String DEEP_WATER_NAME = "bw_water_2";

    private final boolean isDeepWater;
    private boolean isInRoom;

    public InteractionWater(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.isDeepWater = baseItem.getName().equalsIgnoreCase(DEEP_WATER_NAME);
        this.isInRoom = this.getRoomId() != 0;
    }

    public InteractionWater(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.isDeepWater = false;
        this.isInRoom = this.getRoomId() != 0;
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

        Object[] empty = new Object[]{};
        for (Habbo habbo : room.getHabbosOnItem(this)) {
            try {
                this.onWalkOff(habbo.getRoomUnit(), room, empty);
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

        if(pet == null)
            return;

        if (!pet.getRoomUnit().hasStatus(RoomUnitStatus.SWIM) && pet.getPetData().canSwim) {
            pet.getRoomUnit().setStatus(RoomUnitStatus.SWIM, "");
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Pet pet = room.getPet(roomUnit);

        if(pet == null)
            return;

        pet.getRoomUnit().removeStatus(RoomUnitStatus.SWIM);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean canToggle(Habbo habbo, Room room) {
        return false;
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, THashSet<HabboItem>>> itemsAtLocation) {
        for (Pair<RoomTile, THashSet<HabboItem>> set : itemsAtLocation) {
            for (HabboItem item : set.getValue()) {
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
        for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWater.class)) {
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
            for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWaterItem.class)) {
                if (targetOld.intersects(item.getRectangle())) {
                    ((InteractionWaterItem) item).update();
                }
            }
        }
    }

    private void updateWater(Room room) {
        Rectangle target = this.getRectangle();

        // Only update water item furnis that are intersecting with us.
        for (HabboItem item : room.getRoomSpecialTypes().getItemsOfType(InteractionWaterItem.class)) {
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
        for (HabboItem item : room.getItemsAt(x, y, z)) {
            if (item instanceof InteractionWater water) {

                // Take out picked up water from the recalculation.
                if (!water.isInRoom) {
                    continue;
                }

                // Allow:
                // - masking if both are deepwater or both not.
                // - corners too because otherwise causes ugly clipping issues.
                // This allows deepwater and normal water to look nice.
                if (corner && !this.isDeepWater || water.isDeepWater == this.isDeepWater) {
                    return true;
                }
            }
        }

        return false;
    }

}
