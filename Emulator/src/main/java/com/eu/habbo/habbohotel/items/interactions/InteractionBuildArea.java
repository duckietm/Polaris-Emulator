package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RoomFloorItemsComposer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InteractionBuildArea extends InteractionCustomValues {
    public static Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put("tilesLeft", "0");
        }

        {
            this.put("tilesRight", "0");
        }

        {
            this.put("tilesFront", "0");
        }

        {
            this.put("tilesBack", "0");
        }

        {
            this.put("builders", "");
        }
    };

    private Set<RoomTile> tiles;

    public InteractionBuildArea(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
        tiles = new HashSet<>();
    }

    public InteractionBuildArea(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, defaultValues);
        tiles = new HashSet<>();
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.regenAffectedTiles(room);
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);

        ArrayList<String> builderNames = new ArrayList<>(Arrays.asList(this.values.get("builders").split(";")));
        Set<Integer> canBuild = new HashSet<>();

        for (String builderName : builderNames) {
            Habbo builder = Emulator.getGameEnvironment().getHabboManager().getHabbo(builderName);
            HabboInfo builderInfo;
            if (builder != null) {
                builderInfo = builder.getHabboInfo();
            } else {
                builderInfo = HabboManager.getOfflineHabboInfo(builderName);
            }
            if (builderInfo != null) {
                canBuild.add(builderInfo.getId());
            }
        }

        if (!canBuild.isEmpty()) {
            for (RoomTile tile : this.tiles) {
                Set<HabboItem> tileItems = room.getItemsAt(tile);
                for (HabboItem tileItem : tileItems) {
                    if (canBuild.contains(tileItem.getUserId()) && tileItem != this) {
                        room.pickUpItem(tileItem, null);
                    }
                }
            }
        }

        this.tiles.clear();
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);

        ArrayList<String> builderNames = new ArrayList<>(Arrays.asList(this.values.get("builders").split(";")));
        Set<Integer> canBuild = new HashSet<>();

        for (String builderName : builderNames) {
            Habbo builder = Emulator.getGameEnvironment().getHabboManager().getHabbo(builderName);
            HabboInfo builderInfo;
            if (builder != null) {
                builderInfo = builder.getHabboInfo();
            } else {
                builderInfo = HabboManager.getOfflineHabboInfo(builderName);
            }
            if (builderInfo != null) {
                canBuild.add(builderInfo.getId());
            }
        }

        Set<RoomTile> oldTiles = this.tiles;
        Set<RoomTile> newTiles = new HashSet<>();

        int minX = Math.max(0, newLocation.x - Integer.parseInt(this.values.get("tilesBack")));
        int minY = Math.max(0, newLocation.y - Integer.parseInt(this.values.get("tilesRight")));
        int maxX = Math.min(room.getLayout().getMapSizeX(), newLocation.x + Integer.parseInt(this.values.get("tilesFront")));
        int maxY = Math.min(room.getLayout().getMapSizeY(), newLocation.y + Integer.parseInt(this.values.get("tilesLeft")));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                RoomTile tile = room.getLayout().getTile((short) x, (short) y);
                if (tile != null && tile.state != RoomTileState.INVALID)
                    newTiles.add(tile);
            }
        }

        if (!canBuild.isEmpty()) {
            for (RoomTile tile : oldTiles) {
                Set<HabboItem> tileItems = room.getItemsAt(tile);
                if(newTiles.contains(tile)) continue;
                for (HabboItem tileItem : tileItems) {
                    if (canBuild.contains(tileItem.getUserId()) && tileItem != this) {
                        room.pickUpItem(tileItem, null);
                    }
                }
            }
        }
        this.regenAffectedTiles(room);
    }

    public boolean inSquare(RoomTile location) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null && this.tiles.size() == 0) {
            regenAffectedTiles(room);
        }

        return this.tiles.contains(location);

    }

    private void regenAffectedTiles(Room room) {
        int minX = Math.max(0, this.getX() - Integer.parseInt(this.values.get("tilesBack")));
        int minY = Math.max(0, this.getY() - Integer.parseInt(this.values.get("tilesRight")));
        int maxX = Math.min(room.getLayout().getMapSizeX(), this.getX() + Integer.parseInt(this.values.get("tilesFront")));
        int maxY = Math.min(room.getLayout().getMapSizeY(), this.getY() + Integer.parseInt(this.values.get("tilesLeft")));

        this.tiles.clear();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                RoomTile tile = room.getLayout().getTile((short) x, (short) y);
                if (tile != null && tile.state != RoomTileState.INVALID)
                    this.tiles.add(tile);
            }
        }
    }

    @Override
    public void onCustomValuesSaved(Room room, GameClient client, Map<String, String> oldValues) {
        regenAffectedTiles(room);
        ArrayList<String> builderNames = new ArrayList<>(Arrays.asList(this.values.get("builders").split(";")));
        Set<Integer> canBuild = new HashSet<>();

        for (String builderName : builderNames) {
            Habbo builder = Emulator.getGameEnvironment().getHabboManager().getHabbo(builderName);
            HabboInfo builderInfo;
            if (builder != null) {
                builderInfo = builder.getHabboInfo();
            } else {
                builderInfo = HabboManager.getOfflineHabboInfo(builderName);
            }
            if (builderInfo != null) {
                canBuild.add(builderInfo.getId());
            }
        }

        Set<RoomTile> oldTiles = new HashSet<>();

        int minX = Math.max(0, this.getX() - Integer.parseInt(oldValues.get("tilesBack")));
        int minY = Math.max(0, this.getY() - Integer.parseInt(oldValues.get("tilesRight")));
        int maxX = Math.min(room.getLayout().getMapSizeX(), this.getX() + Integer.parseInt(oldValues.get("tilesFront")));
        int maxY = Math.min(room.getLayout().getMapSizeY(), this.getY() + Integer.parseInt(oldValues.get("tilesLeft")));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                RoomTile tile = room.getLayout().getTile((short) x, (short) y);
                if (tile != null && tile.state != RoomTileState.INVALID && !this.tiles.contains(tile))
                    oldTiles.add(tile);
            }
        }
        if (!canBuild.isEmpty()) {
            for (RoomTile tile : oldTiles) {
                Set<HabboItem> tileItems = room.getItemsAt(tile);
                for (HabboItem tileItem : tileItems) {
                    if (canBuild.contains(tileItem.getUserId()) && tileItem != this) {
                        room.pickUpItem(tileItem, null);
                    }
                }
            }
        }

        // show the effect
        Item effectItem = Emulator.getGameEnvironment().getItemManager().getItem("mutearea_sign2");

        if(effectItem != null) {
            Int2ObjectMap<String> ownerNames = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
            ownerNames.put(-1, "System");
            Set<HabboItem> items = new HashSet<>();

            int id = 0;
            for(RoomTile tile : this.tiles) {
                id--;
                HabboItem item = new InteractionDefault(id, -1, effectItem, "1", 0, 0);
                item.setX(tile.x);
                item.setY(tile.y);
                item.setZ(tile.relativeHeight());
                items.add(item);
            }

            client.sendResponse(new RoomFloorItemsComposer(ownerNames, items));
            Emulator.getThreading().run(() -> {
                for(HabboItem item : items) {
                    client.sendResponse(new RemoveFloorItemComposer(item, true));
                }
            }, 3000);
        }
    }

    public boolean isBuilder(String Username){
        return Arrays.asList(this.values.get("builders").split(";")).contains(Username);
    }
}
