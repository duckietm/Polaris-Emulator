package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.RoomUnitGiveHanditem;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import com.eu.habbo.util.pathfinding.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InteractionVendingMachine extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionVendingMachine.class);

    public InteractionVendingMachine(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionVendingMachine(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }
    
    public Set<RoomTile> getActivatorTiles(Room room) {
        Set<RoomTile> tiles = new HashSet<>();
        RoomTile tileInFront = getSquareInFront(room.getLayout(), this);

        if (tileInFront != null)
            tiles.add(tileInFront);

        tiles.add(room.getLayout().getTile(this.getX(), this.getY()));
        return tiles;
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    private void tryInteract(GameClient client, Room room, RoomUnit unit) {
        Set<RoomTile> activatorTiles = getActivatorTiles(room);

        if(activatorTiles.size() == 0)
            return;

        boolean inActivatorSpace = false;

        for(RoomTile tile : activatorTiles) {
            if(unit.getCurrentLocation().is(tile.x, tile.y)) {
                inActivatorSpace = true;
            }
        }

        if(inActivatorSpace) {
            useVendingMachine(client, room, unit);
        }
    }

    private void useVendingMachine(GameClient client, Room room, RoomUnit unit) {
        this.setExtradata("1");
        room.updateItem(this);

        try {
            super.onClick(client, room, new Object[]{"TOGGLE_OVERRIDE"});
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        if(!unit.isWalking() && !unit.hasStatus(RoomUnitStatus.SIT) && !unit.hasStatus(RoomUnitStatus.LAY)) {
            this.rotateToMachine(room, unit);
        }

        Emulator.getThreading().run(() -> {

            giveVendingMachineItem(room, unit);

            if (this.getBaseItem().getEffectM() > 0 && client.getHabbo().getHabboInfo().getGender() == HabboGender.M)
                room.giveEffect(client.getHabbo(), this.getBaseItem().getEffectM(), -1);
            if (this.getBaseItem().getEffectF() > 0 && client.getHabbo().getHabboInfo().getGender() == HabboGender.F)
                room.giveEffect(client.getHabbo(), this.getBaseItem().getEffectF(), -1);

            Emulator.getThreading().run(this, 500);
        }, 1500);
    }

    public void giveVendingMachineItem(Room room, RoomUnit unit) {
        Emulator.getThreading().run(new RoomUnitGiveHanditem(unit, room, this.getBaseItem().getRandomVendingItem()));
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null) {
            return;
        }

        RoomUnit unit = client.getHabbo().getRoomUnit();

        Set<RoomTile> activatorTiles = getActivatorTiles(room);

        if(activatorTiles.size() == 0)
            return;

        boolean inActivatorSpace = false;

        for(RoomTile tile : activatorTiles) {
            if(unit.getCurrentLocation().is(tile.x, tile.y)) {
                inActivatorSpace = true;
            }
        }

        if(!inActivatorSpace) {
            RoomTile tileToWalkTo = null;
            for(RoomTile tile : activatorTiles) {
                if((tile.state == RoomTileState.OPEN || tile.state == RoomTileState.SIT) && (tileToWalkTo == null || tileToWalkTo.distance(unit.getCurrentLocation()) > tile.distance(unit.getCurrentLocation()))) {
                    tileToWalkTo = tile;
                }
            }

            if(tileToWalkTo != null) {
                List<Runnable> onSuccess = new ArrayList<Runnable>();
                List<Runnable> onFail = new ArrayList<Runnable>();

                onSuccess.add(() -> {
                    tryInteract(client, room, unit);
                });

                unit.setGoalLocation(tileToWalkTo);
                Emulator.getThreading().run(new RoomUnitWalkToLocation(unit, tileToWalkTo, room, onSuccess, onFail));
            }
        }
        else {
            useVendingMachine(client, room, unit);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void run() {
        super.run();
        if (this.getExtradata().equals("1")) {
            this.setExtradata("0");
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room != null) {
                room.updateItem(this);
            }
        }
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
    public boolean isUsable() {
        return true;
    }

    private void rotateToMachine(Room room, RoomUnit unit) {
        RoomUserRotation rotation = RoomUserRotation.values()[Rotation.Calculate(unit.getX(), unit.getY(), this.getX(), this.getY())];

        if(Math.abs(unit.getBodyRotation().getValue() - rotation.getValue()) > 1) {
            unit.setRotation(rotation);
            unit.statusUpdate(true);
        }
    }
}
