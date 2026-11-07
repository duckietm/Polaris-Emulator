package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.messages.ServerMessage;

public class InteractionMultiHeight extends UserItem implements VariableHeightItem {
    public InteractionMultiHeight(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionMultiHeight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return this.getBaseItem().allowWalk();
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null) {
            if (!room.hasRights(client.getUser())
                    && !(objects.length >= 2
                            && objects[1] instanceof WiredEffectType
                            && objects[1] == WiredEffectType.TOGGLE_STATE)) return;
        }

        if (objects.length > 0) {
            if (objects[0] instanceof Integer && room != null) {
                UserItem topItem = room.getTopItemAt(this.getX(), this.getY());
                if (topItem != null
                        && !topItem.equals(
                                this)) { // multiheight items cannot change height even if there is a stackable item on
                    // top - no items allowed on top
                    return;
                }

                this.needsUpdate(true);

                if (this.getExtradata().length() == 0) this.setExtradata("0");

                if (this.getBaseItem().getMultiHeights().length > 0) {
                    this.setExtradata(""
                            + (Integer.parseInt(this.getExtradata()) + 1)
                                    % (this.getBaseItem().getMultiHeights().length));
                    this.needsUpdate(true);
                    room.updateItem(this);
                    this.updateUnitsOnItem(room);
                }
            }
        }
    }

    @Override
    public void updateUnitsOnItem(Room room) {
        updateUnitsOn(this, room);
    }

    /** Moves the users on a variable-height furni to its current height (a sitter is re-seated). */
    public static void updateUnitsOn(UserItem item, Room room) {
        Set<RoomTile> occupiedTiles = room.getLayout()
                .getTilesAt(
                        room.getLayout().getTile(item.getX(), item.getY()),
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation());

        for (RoomTile tile : occupiedTiles) {
            Collection<RoomUnit> unitsOnItem =
                    room.getRoomUnitsAt(room.getLayout().getTile(tile.x, tile.y));

            for (RoomUnit unit : unitsOnItem) {
                if (unit.hasStatus(RoomUnitStatus.MOVE) && unit.getGoal() != tile) continue;

                if (item.getBaseItem().allowSit() || unit.hasStatus(RoomUnitStatus.SIT)) {
                    unit.sitUpdate = true;
                    unit.statusUpdate(true);
                } else {
                    double z = standingHeight(item, room, tile);
                    unit.setZ(z);
                    unit.setPreviousLocationZ(z);
                    unit.statusUpdate(true);
                }
            }
        }

        // room.sendComposer(new RoomUserStatusComposer(updatedUnits, true).compose());
    }

    /**
     * The height a user stands at on the furni after its state changed: on its top, as a step onto it puts it. The
     * tile's stack height is not used for it: a furni nobody may stack on (allow_stack 0) gives the floor height
     * there, and the user sank to the floor while the furni stayed up.
     */
    static double standingHeight(UserItem item, Room room, RoomTile tile) {
        UserItem top = room.getTopItemAt(tile.x, tile.y);

        if (top == null || top.equals(item)) {
            return item.getZ() + Item.getCurrentHeight(item);
        }

        return tile.getStackHeight();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (roomUnit != null) {
            if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
                if (roomUnit.getRoomUnitType().equals(RoomUnitType.USER)) {
                    User user = room.getUser(roomUnit);

                    if (user != null) {
                        if (user.getUserInfo().getGender().equals(UserGender.M)
                                && this.getBaseItem().getEffectM() > 0
                                && user.getRoomUnit().getEffectId()
                                        != this.getBaseItem().getEffectM()) {
                            room.giveEffect(user, this.getBaseItem().getEffectM(), -1);
                            return;
                        }

                        if (user.getUserInfo().getGender().equals(UserGender.F)
                                && this.getBaseItem().getEffectF() > 0
                                && user.getRoomUnit().getEffectId()
                                        != this.getBaseItem().getEffectF()) {
                            room.giveEffect(user, this.getBaseItem().getEffectF(), -1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        if (roomUnit != null) {
            if (this.getBaseItem().getEffectF() > 0 || this.getBaseItem().getEffectM() > 0) {
                if (roomUnit.getRoomUnitType().equals(RoomUnitType.USER)) {
                    User user = room.getUser(roomUnit);

                    if (user != null) {
                        if (user.getUserInfo().getGender().equals(UserGender.M)
                                && this.getBaseItem().getEffectM() > 0) {
                            room.giveEffect(user, 0, -1);
                            return;
                        }

                        if (user.getUserInfo().getGender().equals(UserGender.F)
                                && this.getBaseItem().getEffectF() > 0) {
                            room.giveEffect(user, 0, -1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean allowWiredResetState() {
        return true;
    }
}
