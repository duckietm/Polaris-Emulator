package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiredEffectMatchFurni extends InteractionWiredEffect implements InteractionWiredMatchFurniSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectMatchFurni.class);

    private static final WiredEffectType type = WiredEffectType.MATCH_SSHOT;
    public boolean checkForWiredResetPermission = true;
    private Set<WiredMatchFurniSetting> settings;
    private boolean state = false;
    private boolean direction = false;
    private boolean position = false;
    private boolean altitude = false;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMatchFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new HashSet<>(0);
    }

    public WiredEffectMatchFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new HashSet<>(0);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        this.refresh();

        if (this.settings.isEmpty()) return;

        if (room.getLayout() == null) return;

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (WiredMatchFurniSetting setting : this.settings) {
                HabboItem item = room.getHabboItem(setting.item_id);
                if (item != null) {
                    this.applySetting(room, item, setting, ctx);
                }
            }

            return;
        }

        List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, null);
        if (targets.isEmpty()) {
            return;
        }

        List<WiredMatchFurniSetting> spots = dealSpots(this.settings, targets.size(), ThreadLocalRandom.current());
        int index = 0;
        for (HabboItem item : targets) {
            if (item == null) continue;
            this.applySetting(room, item, spots.get(index++), ctx);
        }
    }

    /**
     * The snapshot spots for {@code targets} furni that were not picked: the saved entries in random order, dealt out
     * one per furni whatever its type, and reused from the start when there are more furni than entries. A one-furni
     * snapshot so gives its entry to every target.
     */
    static List<WiredMatchFurniSetting> dealSpots(
            Collection<WiredMatchFurniSetting> settings, int targets, Random random) {
        List<WiredMatchFurniSetting> shuffled = new ArrayList<>(settings);
        Collections.shuffle(shuffled, random);

        List<WiredMatchFurniSetting> dealt = new ArrayList<>(targets);
        for (int i = 0; i < targets; i++) dealt.add(shuffled.get(i % shuffled.size()));
        return dealt;
    }

    void applySetting(Room room, HabboItem item, WiredMatchFurniSetting setting, WiredContext ctx) {
        if (this.state && (this.checkForWiredResetPermission && item.allowWiredResetState())) {
            if (!setting.state.equals(" ") && !item.getExtradata().equals(setting.state)) {
                item.setExtradata(setting.state);
                item.needsUpdate(true);
                room.updateItemState(item);
                WiredManager.triggerFurniStateUpdated(room, ctx.actor().orElse(null), item, true);
            }
        }

        RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
        if (oldLocation == null) return;

        // Altitude first: with direction also on, this branch restores both. Checking direction first
        // turned the furni and never gave it its height back.
        if (this.altitude && !this.position) {
            int newRotation = this.direction ? setting.rotation : item.getRotation();
            if (BigDecimal.valueOf(item.getZ()).compareTo(BigDecimal.valueOf(setting.z)) != 0
                    || newRotation != item.getRotation()) {
                WiredMoveCarryHelper.moveFurni(room, this, item, oldLocation, newRotation, setting.z, null, true, ctx);
            }
        } else if (this.direction && !this.position) {
            if (item.getRotation() != setting.rotation
                    && room.furnitureFitsAt(oldLocation, item, setting.rotation, false)
                            == FurnitureMovementError.NONE) {
                WiredMoveCarryHelper.moveFurni(room, this, item, oldLocation, setting.rotation, null, true, ctx);
            }
        } else if (this.position) {
            boolean slideAnimation = !this.direction || item.getRotation() == setting.rotation;
            RoomTile newLocation = room.getLayout().getTile((short) setting.x, (short) setting.y);
            int newRotation = this.direction ? setting.rotation : item.getRotation();
            double newZ = this.altitude ? setting.z : item.getZ();

            if (newLocation != null
                    && newLocation.state != RoomTileState.INVALID
                    && (newLocation != oldLocation
                            || newRotation != item.getRotation()
                            || BigDecimal.valueOf(item.getZ()).compareTo(BigDecimal.valueOf(newZ)) != 0)
                    && WiredMoveCarryHelper.getMovementError(room, this, item, newLocation, newRotation, ctx)
                            == FurnitureMovementError.NONE) {
                if (WiredMoveCarryHelper.moveFurni(
                                room, this, item, newLocation, newRotation, newZ, null, !slideAnimation, ctx)
                        == FurnitureMovementError.NONE) {}
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.state,
                        this.direction,
                        this.position,
                        this.altitude,
                        new ArrayList<WiredMatchFurniSetting>(this.settings),
                        this.getDelay(),
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.state = data.state;
            this.direction = data.direction;
            this.position = data.position;
            this.altitude = data.altitude;
            this.settings.clear();
            this.settings.addAll(data.items);
            this.furniSource = data.furniSource;
        } else {
            String[] data = wiredData.split(":");

            // A legacy row has six colon-separated fields; anything shorter keeps the defaults
            // rather than failing the whole furni load on a missing index.
            if (data.length < 6) {
                this.needsUpdate(true);
                return;
            }

            String[] items = data[1].split(Pattern.quote(";"));

            for (int i = 0; i < items.length; i++) {
                try {

                    String[] stuff = items[i].split(Pattern.quote("-"));

                    if (stuff.length >= 6) {
                        this.settings.add(new WiredMatchFurniSetting(
                                Integer.parseInt(stuff[0]),
                                stuff[1],
                                Integer.parseInt(stuff[2]),
                                Integer.parseInt(stuff[3]),
                                Integer.parseInt(stuff[4]),
                                Double.parseDouble(stuff[5])));
                    } else if (stuff.length >= 5) {
                        this.settings.add(new WiredMatchFurniSetting(
                                Integer.parseInt(stuff[0]),
                                stuff[1],
                                Integer.parseInt(stuff[2]),
                                Integer.parseInt(stuff[3]),
                                Integer.parseInt(stuff[4])));
                    }

                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }

            this.state = data[2].equals("1");
            this.direction = data[3].equals("1");
            this.position = data[4].equals("1");
            this.altitude = false;
            this.setDelay(Integer.parseInt(data[5]));
            this.furniSource =
                    this.settings.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.state = false;
        this.direction = false;
        this.position = false;
        this.altitude = false;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.settings.size());

        for (WiredMatchFurniSetting item : this.settings) message.appendInt(item.item_id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.state ? 1 : 0);
        message.appendInt(this.direction ? 1 : 0);
        message.appendInt(this.position ? 1 : 0);
        message.appendInt(this.altitude ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Official layout: [state, direction, position, altitude]; the furni source is the official
        // source slot.
        if (settings.getIntParams().length < 4) throw new WiredSaveException("Invalid data");
        boolean setState = settings.getIntParams()[0] == 1;
        boolean setDirection = settings.getIntParams()[1] == 1;
        boolean setPosition = settings.getIntParams()[2] == 1;
        boolean setAltitude = settings.getIntParams()[3] == 1;
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) throw new WiredSaveException("Trying to save wired in unloaded room");

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<WiredMatchFurniSetting> newSettings = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = room.getHabboItem(itemId);

            if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

            newSettings.add(new WiredMatchFurniSetting(
                    it.getId(),
                    this.checkForWiredResetPermission && it.allowWiredResetState() ? it.getExtradata() : " ",
                    it.getRotation(),
                    it.getX(),
                    it.getY(),
                    it.getZ()));
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        // A snapshot exists only for picked furni; with the dropdown left on "triggering furni" it
        // was applied to the trigger item instead of the furni it was taken from.
        if (!newSettings.isEmpty() && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        this.state = setState;
        this.direction = setDirection;
        this.position = setPosition;
        this.altitude = setAltitude;
        this.settings.clear();
        this.settings.addAll(newSettings);
        this.setDelay(delay);

        return true;
    }

    private void refresh() {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return;
        }

        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null && room.isLoaded()) {
            // Use removeIf for O(n) instead of O(n²) with separate remove set
            this.settings.removeIf(setting -> room.getHabboItem(setting.item_id) == null);
        }
    }

    @Override
    public Set<WiredMatchFurniSetting> getMatchFurniSettings() {
        return this.settings;
    }

    @Override
    public boolean shouldMatchState() {
        return this.state;
    }

    @Override
    public boolean shouldMatchRotation() {
        return this.direction;
    }

    @Override
    public boolean shouldMatchPosition() {
        return this.position;
    }

    @Override
    public boolean shouldMatchAltitude() {
        return this.altitude;
    }

    static class JsonData {
        boolean state;
        boolean direction;
        boolean position;
        boolean altitude;
        List<WiredMatchFurniSetting> items;
        int delay;
        int furniSource;

        public JsonData(
                boolean state,
                boolean direction,
                boolean position,
                boolean altitude,
                List<WiredMatchFurniSetting> items,
                int delay,
                int furniSource) {
            this.state = state;
            this.direction = direction;
            this.position = position;
            this.altitude = altitude;
            this.items = items;
            this.delay = delay;
            this.furniSource = furniSource;
        }
    }
}
