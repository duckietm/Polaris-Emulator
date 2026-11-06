package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.interfaces.ConditionalGate;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.items.ItemIntStateComposer;
import polaris.threading.runnables.RoomUnitWalkToLocation;

public class InteractionOneWayGate extends UserItem implements ConditionalGate {

    private boolean walkable = false;
    /**
     * The unit the open gate lets through. The gate tile is walkable while it is open, so anyone could step on it from
     * either side; only this unit may, and anyone else is stopped where they stand, as at a club gate.
     */
    private volatile RoomUnit passingUnit;

    public InteractionOneWayGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionOneWayGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        RoomUnit passing = this.passingUnit;
        if (passing != null && roomUnit != passing) {
            return false;
        }

        return this.getBaseItem().allowWalk();
    }

    @Override
    public void onRejected(RoomUnit roomUnit, Room room, Object[] objects) {
        // The unit simply stays where it is; the gate has nothing to say.
    }

    @Override
    public boolean isWalkable() {
        return walkable;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().length() == 0) {
            this.setExtradata("0");
            this.needsUpdate(true);
        }

        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onClick(final GameClient client, final Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null) {
            RoomTile tileInfront = room.getLayout()
                    .getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), this.getRotation());
            if (tileInfront == null) return;

            RoomTile currentLocation = room.getLayout().getTile(this.getX(), this.getY());
            if (currentLocation == null) return;

            RoomUnit unit = client.getUser().getRoomUnit();
            if (unit == null) return;

            if (tileInfront.x == unit.getX() && tileInfront.y == unit.getY()) {
                if (!currentLocation.hasUnits()) {
                    List<Runnable> onSuccess = new ArrayList<Runnable>();
                    List<Runnable> onFail = new ArrayList<Runnable>();

                    onSuccess.add(() -> {
                        unit.setCanLeaveRoomByDoor(false);
                        walkable = this.getBaseItem().allowWalk();
                        RoomTile tile = room.getLayout()
                                .getTileInFront(
                                        room.getLayout().getTile(this.getX(), this.getY()), this.getRotation() + 4);
                        unit.setGoalLocation(tile);
                        Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, tile, room, onFail, onFail));

                        Emulator.getThreading()
                                .run(
                                        () -> {
                                            if (room.isLoaded()) {
                                                WiredManager.triggerUserWalksOn(room, unit, this);
                                            }
                                        },
                                        500);
                    });

                    onFail.add(() -> {
                        this.passingUnit = null;
                        unit.setCanLeaveRoomByDoor(true);
                        walkable = this.getBaseItem().allowWalk();
                        room.updateTile(currentLocation);
                        room.sendComposer(new ItemIntStateComposer(this.getId(), 0).compose());
                        unit.removeOverrideTile(currentLocation);
                    });

                    this.passingUnit = unit;
                    walkable = true;
                    room.updateTile(currentLocation);
                    unit.addOverrideTile(currentLocation);
                    unit.setGoalLocation(currentLocation);
                    Emulator.getThreading()
                            .run(new RoomUnitWalkToLocation(unit, currentLocation, room, onSuccess, onFail));
                    room.sendComposer(new ItemIntStateComposer(this.getId(), 1).compose());

                    /*
                    room.scheduledTasks.add(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            gate.roomUnitID = client.getUser().getRoomUnit().getId();
                            room.updateTile(gatePosition);
                            client.getUser().getRoomUnit().setGoalLocation(room.getLayout().getTileInFront(room.getLayout().getTile(InteractionOneWayGate.this.getX(), InteractionOneWayGate.this.getY()), InteractionOneWayGate.this.getRotation() + 4));
                        }
                    });
                    */
                }
            }
        }
    }

    private void refresh(Room room) {
        this.passingUnit = null;
        this.setExtradata("0");
        room.sendComposer(new ItemIntStateComposer(this.getId(), 0).compose());
        room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
        this.refresh(room);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
        this.refresh(room);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.refresh(room);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.refresh(room);
    }
}
