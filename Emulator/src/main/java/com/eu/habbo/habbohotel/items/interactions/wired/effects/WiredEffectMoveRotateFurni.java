package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WiredEffectMoveRotateFurni extends InteractionWiredEffect implements ICycleable {

    public static final WiredEffectType type = WiredEffectType.MOVE_ROTATE;
    // Use LinkedHashSet to preserve insertion order for consistent movement
    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION / 2);
    private int direction;
    private int rotation;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    // Use thread-safe set for cooldowns since execute() can be called from async threads
    private final Set<HabboItem> itemCooldowns = ConcurrentHashMap.newKeySet();
    // Pre-selected directions from simulation (itemId -> direction)
    private final Map<Integer, RoomUserRotation> preSelectedDirections = new ConcurrentHashMap<>();

    public WiredEffectMoveRotateFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveRotateFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null);
        }

        for (HabboItem item : effectiveItems) {
            if(this.itemCooldowns.contains(item))
                continue;

            int newRotation = this.rotation > 0 ? this.getNewRotation(item) : item.getRotation();
            RoomTile newLocation = room.getLayout().getTile(item.getX(), item.getY());
            RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());

            if(this.direction > 0) {
                // Use pre-selected direction if available, otherwise pick random
                RoomUserRotation moveDirection = this.preSelectedDirections.remove(item.getId());
                if (moveDirection == null) {
                    moveDirection = this.getMovementDirection();
                }
                newLocation = room.getLayout().getTile(
                    (short) (item.getX() + ((moveDirection == RoomUserRotation.WEST || moveDirection == RoomUserRotation.NORTH_WEST || moveDirection == RoomUserRotation.SOUTH_WEST) ? -1 : (((moveDirection == RoomUserRotation.EAST || moveDirection == RoomUserRotation.SOUTH_EAST || moveDirection == RoomUserRotation.NORTH_EAST) ? 1 : 0)))),
                    (short) (item.getY() + ((moveDirection == RoomUserRotation.NORTH || moveDirection == RoomUserRotation.NORTH_EAST || moveDirection == RoomUserRotation.NORTH_WEST) ? 1 : ((moveDirection == RoomUserRotation.SOUTH || moveDirection == RoomUserRotation.SOUTH_EAST || moveDirection == RoomUserRotation.SOUTH_WEST) ? -1 : 0)))
                );
            }

            boolean slideAnimation = item.getRotation() == newRotation;

            FurnitureMovementError furniMoveTest = WiredMoveCarryHelper.getMovementError(room, this, item, newLocation, newRotation, ctx);
            if (newLocation != null && newLocation.state != RoomTileState.INVALID && (newLocation != oldLocation || newRotation != item.getRotation())
                    && (furniMoveTest == FurnitureMovementError.NONE
                    || ((furniMoveTest == FurnitureMovementError.TILE_HAS_BOTS
                    || furniMoveTest == FurnitureMovementError.TILE_HAS_HABBOS
                    || furniMoveTest == FurnitureMovementError.TILE_HAS_PETS) && newLocation == oldLocation))) {
                if (WiredMoveCarryHelper.moveFurni(room, this, item, newLocation, newRotation, null, !slideAnimation, ctx) == FurnitureMovementError.NONE) {
                    this.itemCooldowns.add(item);
                }
            }
        }
    }

    @Override
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        // Clear any previous pre-selected directions
        this.preSelectedDirections.clear();
        
        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        for (HabboItem item : effectiveItems) {
            if (item == null) continue;
            
            WiredSimulation.SimulatedPosition currentPos = simulation.getItemPosition(item);
            short newX = currentPos.x;
            short newY = currentPos.y;
            
            if (this.direction > 0) {
                // Pick the actual random direction now (same logic as getMovementDirection)
                RoomUserRotation selectedDirection = this.getMovementDirection();
                
                // Calculate target position for the selected direction
                short testX = (short) (currentPos.x + ((selectedDirection == RoomUserRotation.WEST || selectedDirection == RoomUserRotation.NORTH_WEST || selectedDirection == RoomUserRotation.SOUTH_WEST) ? -1 : 
                    (((selectedDirection == RoomUserRotation.EAST || selectedDirection == RoomUserRotation.SOUTH_EAST || selectedDirection == RoomUserRotation.NORTH_EAST) ? 1 : 0))));
                short testY = (short) (currentPos.y + ((selectedDirection == RoomUserRotation.NORTH || selectedDirection == RoomUserRotation.NORTH_EAST || selectedDirection == RoomUserRotation.NORTH_WEST) ? 1 : 
                    ((selectedDirection == RoomUserRotation.SOUTH || selectedDirection == RoomUserRotation.SOUTH_EAST || selectedDirection == RoomUserRotation.SOUTH_WEST) ? -1 : 0)));
                
                // Validate this specific direction
                if (!simulation.isTileValidForItem(testX, testY, item)) {
                    return false; // This specific move would fail
                }
                
                // Store the pre-selected direction for execution
                this.preSelectedDirections.put(item.getId(), selectedDirection);
                newX = testX;
                newY = testY;
            }
            
            if (newX != currentPos.x || newY != currentPos.y) {
                if (!simulation.moveItem(item, newX, newY, currentPos.z, currentPos.rotation)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsToRemove = new ArrayList<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null))
                itemsToRemove.add(item);
        }

        for (HabboItem item : itemsToRemove) {
            this.items.remove(item);
        }

        return WiredManager.getGson().toJson(new JsonData(
                this.direction,
                this.rotation,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.furniSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.direction = data.direction;
            this.rotation = data.rotation;
            this.furniSource = data.furniSource;
            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 4) {
                try {
                    this.direction = Integer.parseInt(data[0]);
                    this.rotation = Integer.parseInt(data[1]);
                    this.setDelay(Integer.parseInt(data[2]));
                } catch (Exception e) {
                    System.out.println(e);
                }

                for (String s : data[3].split("\r")) {
                    if (s.trim().isEmpty()) continue;
                    try {
                        HabboItem item = room.getHabboItem(Integer.parseInt(s.trim()));

                        if (item != null)
                            this.items.add(item);
                    } catch (NumberFormatException ignored) {
                        // skip malformed furni id token
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.direction = 0;
        this.rotation = 0;
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsToRemove = new ArrayList<>();

        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                itemsToRemove.add(item);
        }

        for (HabboItem item : itemsToRemove) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items)
            message.appendInt(item.getId());
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.direction);
        message.appendInt(this.rotation);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            return false;

        if(settings.getIntParams().length < 3) throw new WiredSaveException("invalid data");

        this.direction = settings.getIntParams()[0];
        this.rotation = settings.getIntParams()[1];
        this.furniSource = settings.getIntParams()[2];

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5)) return false;

        if (count > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
                if (item != null) {
                    this.items.add(item);
                }
            }
        }

        this.setDelay(settings.getDelay());

        return true;
    }


    /**
     * Returns a new rotation for an item based on the wired options
     *
     * @param item HabboItem
     * @return new rotation
     */
    private int getNewRotation(HabboItem item) {
        if(item.getMaximumRotations() == 2) {
            return item.getRotation() == 0 ? 4 : 0;
        }
        else if(item.getMaximumRotations() == 1) {
            return item.getRotation();
        }
        else if(item.getMaximumRotations() > 4) {
            if (this.rotation == 1) {
                return item.getRotation() == item.getMaximumRotations() - 1 ? 0 : item.getRotation() + 1;
            } else if (this.rotation == 2) {
                return item.getRotation() > 0 ? item.getRotation() - 1 : item.getMaximumRotations() - 1;
            } else if (this.rotation == 3) { //Random rotation
                THashSet<Integer> possibleRotations = new THashSet<>();
                for (int i = 0; i < item.getMaximumRotations(); i++)
                {
                    possibleRotations.add(i);
                }

                possibleRotations.remove(item.getRotation());

                if(possibleRotations.size() > 0) {
                    int index = Emulator.getRandom().nextInt(possibleRotations.size());
                    Iterator<Integer> iter = possibleRotations.iterator();
                    for (int i = 0; i < index; i++) {
                        iter.next();
                    }
                    return iter.next();
                }
            }
        }
        else {
            if (this.rotation == 1) {
                return (item.getRotation() + 2) % 8;
            } else if (this.rotation == 2) {
                int rot = (item.getRotation() - 2) % 8;
                if(rot < 0) {
                    rot += 8;
                }
                return rot;
            } else if (this.rotation == 3) { //Random rotation
                THashSet<Integer> possibleRotations = new THashSet<>();
                for (int i = 0; i < item.getMaximumRotations(); i++)
                {
                    possibleRotations.add(i * 2);
                }

                possibleRotations.remove(item.getRotation());

                if(possibleRotations.size() > 0) {
                    int index = Emulator.getRandom().nextInt(possibleRotations.size());
                    Iterator<Integer> iter = possibleRotations.iterator();
                    for (int i = 0; i < index; i++) {
                        iter.next();
                    }
                    return iter.next();
                }
            }
        }

        return item.getRotation();
    }

    /**
     * Returns the direction of movement based on the wired settings
     *
     * @return direction
     */
    private RoomUserRotation getMovementDirection() {
        switch (this.direction) {
            case 1:
                return RoomUserRotation.values()[Emulator.getRandom().nextInt(RoomUserRotation.values().length / 2) * 2];
            case 2:
                return Emulator.getRandom().nextInt(2) == 1 ? RoomUserRotation.EAST : RoomUserRotation.WEST;
            case 3:
                return Emulator.getRandom().nextInt(2) == 1 ? RoomUserRotation.NORTH : RoomUserRotation.SOUTH;
            case 4:
                return RoomUserRotation.SOUTH;
            case 5:
                return RoomUserRotation.EAST;
            case 6:
                return RoomUserRotation.NORTH;
            case 7:
                return RoomUserRotation.WEST;
            case 8:
                return RoomUserRotation.NORTH_EAST;
            case 9:
                return RoomUserRotation.SOUTH_EAST;
            case 10:
                return RoomUserRotation.SOUTH_WEST;
            case 11:
                return RoomUserRotation.NORTH_WEST;
            default:
                return RoomUserRotation.NORTH;
        }
    }

    @Override
    public void cycle(Room room) {
        this.itemCooldowns.clear();
        this.preSelectedDirections.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    static class JsonData {
        int direction;
        int rotation;
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int direction, int rotation, int delay, List<Integer> itemIds, int furniSource) {
            this.direction = direction;
            this.rotation = rotation;
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
