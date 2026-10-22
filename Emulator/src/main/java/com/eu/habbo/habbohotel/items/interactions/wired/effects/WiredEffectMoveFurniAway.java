package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectMoveFurniAway extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.FLEE;

    private Set<HabboItem> items = new LinkedHashSet<>();
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMoveFurniAway(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniAway(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room.getLayout() == null) return;

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            Set<HabboItem> toRemove = new HashSet<>();
            for (HabboItem item : effectiveItems) {
                if (item != null && item.getRoomId() == 0) {
                    toRemove.add(item);
                }
            }
            this.items.removeAll(toRemove);
        }

        for (HabboItem item : effectiveItems) {
            if (item == null) continue;

            RoomTile t = room.getLayout().getTile(item.getX(), item.getY());
            if (t == null) continue;

            RoomUnit target = room.getRoomUnits().stream()
                    .min(Comparator.comparingDouble(a -> a.getCurrentLocation().distance(t)))
                    .orElse(null);

            if (target == null) continue;

            // Someone right next to the furni still counts as bumping into it, and it still
            // flees: before, it stood still and let itself be caught.
            // As in Habbo, the collision runs in the pass of this flee.
            if (target.getCurrentLocation().distance(t) <= 1) {
                WiredManager.dispatchEffectTriggeredEvent(WiredEvents.botCollision(room, target, item));
            }

            RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());

            // The step straight away along the longer axis first, then along the other one, so a
            // wall or a furni in the way no longer leaves the furni standing still.
            for (int[] step : stepsAway(item.getX(), item.getY(), target.getX(), target.getY())) {
                RoomTile newLocation =
                        room.getLayout().getTile((short) (item.getX() + step[0]), (short) (item.getY() + step[1]));

                if (newLocation != null
                        && newLocation.state != RoomTileState.INVALID
                        && newLocation != oldLocation
                        && WiredMoveCarryHelper.getMovementError(room, this, item, newLocation, item.getRotation(), ctx)
                                == FurnitureMovementError.NONE
                        && WiredMoveCarryHelper.moveFurni(
                                        room, this, item, newLocation, item.getRotation(), null, false, ctx)
                                == FurnitureMovementError.NONE) {
                    break;
                }
            }
        }
    }

    /**
     * The straight steps that take a furni at (x, y) further from (awayX, awayY): along the axis
     * with more distance between them first, then the other. Someone on the furni's own tile
     * leaves no direction, so no steps.
     */
    static List<int[]> stepsAway(int x, int y, int awayX, int awayY) {
        int dx = Integer.signum(x - awayX);
        int dy = Integer.signum(y - awayY);
        List<int[]> steps = new ArrayList<>(2);
        int[] alongX = {dx, 0};
        int[] alongY = {0, dy};

        boolean xFirst = Math.abs(x - awayX) >= Math.abs(y - awayY);
        for (int[] step : xFirst ? new int[][] {alongX, alongY} : new int[][] {alongY, alongX}) {
            if (step[0] != 0 || step[1] != 0) {
                steps.add(step);
            }
        }

        return steps;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        Room room = ctx.room();
        if (room.getLayout() == null) return true;

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        for (HabboItem item : effectiveItems) {
            if (item == null) continue;

            WiredSimulation.SimulatedPosition currentPos = simulation.getItemPosition(item);
            RoomTile t = room.getLayout().getTile(currentPos.x, currentPos.y);
            if (t == null) continue;

            RoomUnit target = room.getRoomUnits().stream()
                    .min(Comparator.comparingDouble(a -> a.getCurrentLocation().distance(t)))
                    .orElse(null);

            if (target == null) continue;

            List<int[]> steps = stepsAway(currentPos.x, currentPos.y, target.getX(), target.getY());
            if (steps.isEmpty()) continue;

            boolean moved = false;
            for (int[] step : steps) {
                short newX = (short) (currentPos.x + step[0]);
                short newY = (short) (currentPos.y + step[1]);

                if (simulation.isTileValidForItem(newX, newY, item)
                        && simulation.moveItem(item, newX, newY, currentPos.z, currentPos.rotation)) {
                    moved = true;
                    break;
                }
            }

            if (!moved) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new LinkedHashSet<>();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.furniSource = data.furniSource;
            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (String s : wiredDataOld[1].split(";")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(s));

                        if (item != null) this.items.add(item);
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
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
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getHabboItem(item.getId())
                            == null) items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
        itemsSnapshot = new ArrayList<>(this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot) message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        // Official: no int params; the furni source is the official source slot.
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<HabboItem> newItems = new ArrayList<>();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                HabboItem it = Emulator.getGameEnvironment()
                        .getRoomManager()
                        .getRoom(this.getRoomId())
                        .getHabboItem(itemId);

                if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

                newItems.add(it);
            }
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.addAll(newItems);
        }
        this.setDelay(delay);

        return true;
    }

    @Override
    protected long requiredCooldown() {
        return 495;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}
