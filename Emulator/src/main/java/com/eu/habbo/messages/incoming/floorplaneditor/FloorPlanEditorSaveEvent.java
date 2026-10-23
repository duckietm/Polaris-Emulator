package com.eu.habbo.messages.incoming.floorplaneditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.CustomRoomLayout;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FloorPlanEditorSaveEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FloorPlanEditorSaveEvent.class);

    public static volatile int MAXIMUM_FLOORPLAN_WIDTH_LENGTH = 64;
    public static volatile int MAXIMUM_FLOORPLAN_SIZE = 64 * 64;

    private static final int SAVE_COOLDOWN_SECONDS = 3;
    private static final int MAX_AUTO_PICKUP_ITEMS = 500;
    private static final Pattern ALLOWED_MAP_CHARS = Pattern.compile("[a-zA-Z0-9\r]+");

    /** A floor plan the packet carried and every check accepted. */
    private record FloorPlan(
            String map,
            String[] rows,
            int width,
            int doorX,
            int doorY,
            int doorDirection,
            int wallSize,
            int floorSize,
            int wallHeight,
            boolean autoPickup) {}

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.editableRoom();

        if (room == null) return;

        long now = Emulator.getIntUnixTimestamp();
        if (now - this.client.getHabbo().getHabboStats().lastFloorplanSaveTimestamp < SAVE_COOLDOWN_SECONDS) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FLOORPLAN_EDITOR_ERROR.key, "Please wait a few seconds before saving again."));
            return;
        }

        FloorPlan plan = this.readFloorPlan(room);

        if (plan == null) return;

        Set<HabboItem> itemsToPickup = this.findItemsToPickup(room, plan);

        if (itemsToPickup == null || !this.pickUpItems(room, itemsToPickup)) return;

        RoomLayout layout = this.applyLayout(room, plan);

        if (layout != null) {
            this.saveRoom(room, layout, plan, now);
        }
    }

    /** The room the user stands in, when they may edit its floor plan; null otherwise. */
    private Room editableRoom() {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_FLOORPLAN_EDITOR)) {
            this.client.sendResponse(
                    new GenericAlertComposer(Emulator.getTexts().getValue("floorplan.permission")));
            return null;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return null;

        if (!(room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()
                || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER))) {
            return null;
        }

        return room;
    }

    /**
     * Reads the map and the fields after it. A malformed map is dropped without an answer; fields out of range are
     * reported to the user together. Null when the plan cannot be saved.
     */
    private FloorPlan readFloorPlan(Room room) {
        StringJoiner errors = new StringJoiner("<br />");
        String map = this.packet.readString();

        if (map == null || map.length() > MAXIMUM_FLOORPLAN_SIZE) {
            LOGGER.warn(
                    "Floorplan save rejected (oversize): user={} room={} mapLen={}",
                    this.client.getHabbo().getHabboInfo().getId(),
                    room.getId(),
                    map == null ? 0 : map.length());
            return null;
        }

        if (!ALLOWED_MAP_CHARS.matcher(map).matches()) {
            LOGGER.warn(
                    "Floorplan save rejected (illegal chars): user={} room={}",
                    this.client.getHabbo().getHabboInfo().getId(),
                    room.getId());
            return null;
        }

        map = map.replace("X", "x");

        String[] mapRows = map.split("\r");

        if (mapRows.length == 0 || mapRows.length > MAXIMUM_FLOORPLAN_WIDTH_LENGTH) {
            return null;
        }

        int firstRowSize = mapRows[0].length();

        if (firstRowSize == 0 || firstRowSize > MAXIMUM_FLOORPLAN_WIDTH_LENGTH) {
            return null;
        }

        for (String row : mapRows) {
            if (row.length() != firstRowSize) {
                return null;
            }
        }

        if (Emulator.getConfig().getBoolean("hotel.room.floorplan.check.enabled")) {
            if (map.replace("x", "").replace("\r", "").isEmpty()) {
                errors.add("${notification.floorplan_editor.error.message.effective_height_is_0}");
            }
        }

        int doorX = this.packet.readInt();
        int doorY = this.packet.readInt();

        if (doorX < 0 || doorX >= firstRowSize || doorY < 0 || doorY >= mapRows.length) {
            errors.add("${notification.floorplan_editor.error.message.entry_tile_outside_map}");
        } else if (mapRows[doorY].charAt(doorX) == 'x') {
            errors.add("${notification.floorplan_editor.error.message.entry_not_on_tile}");
        }

        int doorRotation = this.packet.readInt();
        if (doorRotation < 0 || doorRotation > 7) {
            errors.add("${notification.floorplan_editor.error.message.invalid_entry_tile_direction}");
        }

        int wallSize = this.packet.readInt();
        if (wallSize < -2 || wallSize > 1) {
            errors.add("${notification.floorplan_editor.error.message.invalid_wall_thickness}");
        }
        int floorSize = this.packet.readInt();
        if (floorSize < -2 || floorSize > 1) {
            errors.add("${notification.floorplan_editor.error.message.invalid_floor_thickness}");
        }

        int wallHeight = -1;
        if (this.packet.bytesAvailable() >= 4) wallHeight = this.packet.readInt();

        if (wallHeight < -1 || wallHeight > 15) {
            errors.add("${notification.floorplan_editor.error.message.invalid_walls_fixed_height}");
        }

        boolean autoPickup = false;
        if (this.packet.bytesAvailable() >= 1) {
            autoPickup = this.packet.readBoolean();
        }

        if (errors.length() > 0) {
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FLOORPLAN_EDITOR_ERROR.key, errors.toString()));
            return null;
        }

        return new FloorPlan(
                map, mapRows, firstRowSize, doorX, doorY, doorRotation, wallSize, floorSize, wallHeight, autoPickup);
    }

    /**
     * The furni the new plan takes away from under them: on a tile that becomes void, on a tile whose height changes
     * and on a tile the plan leaves out. With auto pickup they are returned to be picked up; without it the first
     * blocking tile is reported and null is returned. Null as well when a tile character cannot be read.
     */
    private Set<HabboItem> findItemsToPickup(Room room, FloorPlan plan) {
        Set<RoomTile> locked_tileList = room.getLockedTiles();
        Set<RoomTile> new_tileList = new HashSet<>();
        Set<HabboItem> itemsToPickup = new HashSet<>();
        int blockedX = -1;
        int blockedY = -1;
        blockingRoomItemScan:
        for (int y = 0; y < plan.rows().length; y++) {
            for (int x = 0; x < plan.width(); x++) {

                RoomTile tile = room.getLayout().getTile((short) x, (short) y);
                new_tileList.add(tile);
                String square = String.valueOf(plan.rows()[y].charAt(x));
                short height;

                if (square.equalsIgnoreCase("x") && room.getTopItemAt(x, y) != null) {
                    if (plan.autoPickup()) {
                        Set<HabboItem> here = room.getItemsAt(x, y);
                        if (here != null) itemsToPickup.addAll(here);
                        continue;
                    }
                    blockedX = x;
                    blockedY = y;
                    break blockingRoomItemScan;
                }

                try {
                    if (square.isEmpty()) {
                        height = 0;
                    } else if (Emulator.isNumeric(square)) {
                        height = Short.parseShort(square);
                    } else {
                        int idx = "abcdefghijklmnopqrstuvwxyz".indexOf(square.toLowerCase());
                        if (idx < 0) {
                            return null;
                        }
                        height = (short) Math.min(26, 10 + idx);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }

                if (tile != null
                        && tile.state != RoomTileState.INVALID
                        && height != tile.z
                        && room.getTopItemAt(x, y) != null) {
                    if (plan.autoPickup()) {
                        Set<HabboItem> here = room.getItemsAt(x, y);
                        if (here != null) itemsToPickup.addAll(here);
                        continue;
                    }
                    blockedX = x;
                    blockedY = y;
                    break blockingRoomItemScan;
                }
            }
        }

        if (blockedX < 0) {
            locked_tileList.removeAll(new_tileList);
            if (!locked_tileList.isEmpty()) {
                if (plan.autoPickup()) {
                    for (RoomTile lt : locked_tileList) {
                        Set<HabboItem> here = room.getItemsAt(lt);
                        if (here != null) itemsToPickup.addAll(here);
                    }
                } else {
                    RoomTile first = locked_tileList.iterator().next();
                    blockedX = first.x;
                    blockedY = first.y;
                }
            }
        }

        if (blockedX >= 0) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FLOORPLAN_EDITOR_ERROR.key,
                    "${notification.floorplan_editor.error.message.change_blocked_by_room_item} (" + blockedX + ", "
                            + blockedY + ")"));
            return null;
        }

        return itemsToPickup;
    }

    /** Picks the furni up for their owners; false when there are more than the cap allows. */
    private boolean pickUpItems(Room room, Set<HabboItem> itemsToPickup) {
        if (itemsToPickup.isEmpty()) return true;

        if (itemsToPickup.size() > MAX_AUTO_PICKUP_ITEMS) {
            LOGGER.warn(
                    "Floorplan auto-pickup rejected (over cap): user={} room={} itemCount={} cap={}",
                    this.client.getHabbo().getHabboInfo().getId(),
                    room.getId(),
                    itemsToPickup.size(),
                    MAX_AUTO_PICKUP_ITEMS);
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FLOORPLAN_EDITOR_ERROR.key,
                    "Too many items would be picked up (" + itemsToPickup.size() + " > " + MAX_AUTO_PICKUP_ITEMS
                            + "). Remove some furniture manually and save again."));
            return false;
        }

        Map<Integer, ArrayList<HabboItem>> byOwner = new HashMap<>();
        for (HabboItem itm : itemsToPickup) {
            if (itm == null) continue;
            byOwner.computeIfAbsent(itm.getUserId(), k -> new ArrayList<>()).add(itm);
            room.pickUpItem(itm, null);
        }

        for (Map.Entry<Integer, ArrayList<HabboItem>> entry : byOwner.entrySet()) {
            Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(entry.getKey());
            if (owner == null) continue;
            for (HabboItem itm : entry.getValue()) {
                owner.getClient().sendResponse(new AddHabboItemComposer(itm));
            }
            owner.getClient().sendResponse(new InventoryRefreshComposer());
        }

        LOGGER.info(
                "Floorplan auto-pickup: user={} room={} itemCount={} owners={}",
                this.client.getHabbo().getHabboInfo().getId(),
                room.getId(),
                itemsToPickup.size(),
                byOwner.size());
        return true;
    }

    /**
     * Writes the plan into the room's custom layout, or creates one for a room on a model layout. Null when the room
     * has no layout to show afterwards.
     */
    private RoomLayout applyLayout(Room room, FloorPlan plan) {
        RoomLayout layout = room.getLayout();

        if (layout instanceof CustomRoomLayout) {
            layout.setDoorX((short) plan.doorX());
            layout.setDoorY((short) plan.doorY());
            layout.setDoorDirection(plan.doorDirection());
            layout.setHeightmap(plan.map());
            layout.parse();

            if (layout.getDoorTile() == null) {
                this.client.getHabbo().alert("Error");
                ((CustomRoomLayout) layout).needsUpdate(false);
                Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);
                return null;
            }
            ((CustomRoomLayout) layout).needsUpdate(true);
            Emulator.getThreading().run((CustomRoomLayout) layout);
            return layout;
        }

        return Emulator.getGameEnvironment()
                .getRoomManager()
                .insertCustomLayout(room, plan.map(), plan.doorX(), plan.doorY(), plan.doorDirection());
    }

    /** Stores the new layout and settings, then reloads the room and sends everyone in it back in. */
    private void saveRoom(Room room, RoomLayout layout, FloorPlan plan, long now) {
        room.setHasCustomLayout(true);
        room.setNeedsUpdate(true);
        room.setLayout(layout);
        room.setWallSize(plan.wallSize());
        room.setFloorSize(plan.floorSize());
        room.setWallHeight(plan.wallHeight());
        room.save();

        this.client.getHabbo().getHabboStats().lastFloorplanSaveTimestamp = now;
        LOGGER.info(
                "Floorplan saved: user={} room={} mapLen={} rows={} cols={}",
                this.client.getHabbo().getHabboInfo().getId(),
                room.getId(),
                plan.map().length(),
                plan.rows().length,
                plan.width());

        // The id is kept before the unload: a room that does not load back still sends everyone to it, so nobody is
        // left in a room that is gone.
        int roomId = room.getId();
        Collection<Habbo> habbos = new ArrayList<>(room.getUserCount());
        habbos.addAll(room.getHabbos());
        Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);
        Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
        ServerMessage message = new ForwardToRoomComposer(roomId).compose();
        for (Habbo habbo : habbos) {
            habbo.getClient().sendResponse(message);
        }
    }
}
