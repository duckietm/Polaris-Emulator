package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.PlantConfig;
import com.eu.habbo.habbohotel.items.PlantData;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.threading.runnables.QueryDeletePlantData;
import com.eu.habbo.threading.runnables.QuerySavePlantData;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionPlant extends InteractionDefault implements ICycleable {
    private static final int WATER_CAN_EFFECT = 192;
    private static final String CFG_WATER_COOLDOWN_SECONDS = "plant_water_secconds";
    private static final String CFG_DEATH_HOURS = "plant_water_deathtime_hour";
    private static final int DEFAULT_WATER_COOLDOWN_SECONDS = 30 * 60;
    private static final int DEFAULT_DEATH_HOURS = 24;
    private static final long CHECK_INTERVAL_SECONDS = 3L; // throttle the per-cycle scan
    private static final long REFRESH_THROTTLE_MILLIS = 250L;

    private long nextCheckTimestamp;
    private long lastRefreshMillis;
    private PlantData cachedData;

    public InteractionPlant(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.normalizeExtradata();
    }

    public InteractionPlant(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.normalizeExtradata();
    }

    private void normalizeExtradata() {
        String raw = this.getExtradata();
        if (raw != null) {
            int colon = raw.indexOf(':');
            if (colon >= 0) {
                this.setExtradata(raw.substring(0, colon));
            }
        }
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getRoomId() != 0) {
            boolean dead = this.data().isDead();
            serverMessage.appendInt(7 + (this.isLimited() ? 256 : 0));
            serverMessage.appendString(Integer.toString(this.displayFrame()));
            serverMessage.appendInt(clampToInt(this.secondsUntilRewater()));
            serverMessage.appendInt(dead ? -1 : clampToInt(this.secondsUntilDeath()));

            if (this.isLimited()) {
                serverMessage.appendInt(this.getLimitedSells());
                serverMessage.appendInt(this.getLimitedStack());
            }
            return;
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public String getDatabaseExtraData() {
        if (this.getRoomId() == 0) {
            return "0";
        }
        return Integer.toString(this.displayFrame());
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || client.getHabbo() == null || room == null) {
            return;
        }
        RoomUnit unit = client.getHabbo().getRoomUnit();

        long nowMillis = System.currentTimeMillis();
        if (nowMillis - this.lastRefreshMillis >= REFRESH_THROTTLE_MILLIS) {
            this.lastRefreshMillis = nowMillis;
            client.sendResponse(new FloorItemUpdateComposer(this));
        }

        if (unit == null || unit.getEffectId() != WATER_CAN_EFFECT) {
            return; // no water can — only the refresh above
        }

        RoomTile plantTile = room.getLayout() == null ? null : room.getLayout().getTile(this.getX(), this.getY());
        RoomTile userTile = unit.getCurrentLocation();
        if (plantTile == null || userTile == null || userTile.distance(plantTile) > 1.5) {
            return; // too far away — do nothing
        }

        this.water(room);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        PlantData data = new PlantData(0, unixSeconds(), false);
        Emulator.getGameEnvironment().getItemManager().putPlantData(this.getId(), data);
        this.cachedData = data;
        this.nextCheckTimestamp = 0L;
        this.persistData(data);

        if (room != null) {
            this.redraw(room);
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);

        Emulator.getGameEnvironment().getItemManager().removePlantData(this.getId());
        this.cachedData = null;
        Emulator.getThreading().run(new QueryDeletePlantData(this.getId()));
    }

    public boolean isDead() {
        return this.data().isDead();
    }

    @Override
    public void cycle(Room room) {
        if (room == null) {
            return;
        }

        long now = unixSeconds();
        if (now < this.nextCheckTimestamp) {
            return;
        }
        this.nextCheckTimestamp = now + CHECK_INTERVAL_SECONDS;

        PlantData data = this.data();
        if (data.isDead()) {
            return; // already dead — terminal
        }

        long anchor = data.getLastWaterDate() > 0 ? data.getLastWaterDate() : now;
        if (now - anchor >= deathSeconds()) {
            data.setDead(true);
            this.persistData(data);
            this.redraw(room);
        }
    }

    private void water(Room room) {
        if (room == null) {
            return;
        }

        PlantConfig config = this.config();
        PlantData data = this.data();
        if (data.isDead()) {
            return; // already dead — terminal
        }

        long now = unixSeconds();
        if (data.getLastWaterDate() > 0 && now - data.getLastWaterDate() < waterCooldownSeconds()) {
            return;
        }

        int grown = Math.min(data.getCountState() + 1, config.getGrowCounts());
        data.setCountState(grown);
        data.setLastWaterDate(now);
        this.persistData(data);
        this.redraw(room);
    }

    private int displayFrame() {
        PlantData data = this.data();
        return data.isDead() ? this.config().getDeathCount() : data.getCountState();
    }

    private long secondsUntilRewater() {
        PlantData data = this.data();
        if (data.isDead() || data.getLastWaterDate() <= 0) {
            return 0L;
        }
        long remaining = waterCooldownSeconds() - (unixSeconds() - data.getLastWaterDate());
        return remaining > 0 ? remaining : 0L;
    }

    private long secondsUntilDeath() {
        PlantData data = this.data();
        if (data.isDead()) {
            return 0L;
        }
        long anchor = data.getLastWaterDate() > 0 ? data.getLastWaterDate() : unixSeconds();
        long remaining = deathSeconds() - (unixSeconds() - anchor);
        return remaining > 0 ? remaining : 0L;
    }

    private PlantConfig config() {
        PlantConfig config = Emulator.getGameEnvironment()
                .getItemManager()
                .getPlantConfig(this.getBaseItem().getName());
        if (config != null) {
            return config;
        }
        int stateCount = this.getBaseItem().getStateCount();
        int deathCount = Math.max(1, stateCount - 1);
        int growCounts = Math.max(0, stateCount - 2);
        return new PlantConfig(0, this.getBaseItem().getName(), growCounts, deathCount);
    }

    private PlantData data() {
        if (this.cachedData == null) {
            ItemManager itemManager = Emulator.getGameEnvironment().getItemManager();
            PlantData data = itemManager.getPlantData(this.getId());
            if (data == null) {
                data = new PlantData(0, unixSeconds(), false);
                itemManager.putPlantData(this.getId(), data);
                this.persistData(data);
            }
            this.cachedData = data;
        }
        return this.cachedData;
    }

    private void persistData(PlantData data) {
        Emulator.getThreading().run(new QuerySavePlantData(this.getId(), data));
    }

    private void redraw(Room room) {
        this.setExtradata(Integer.toString(this.displayFrame()));
        this.needsUpdate(true);
        room.updateItem(this);
        room.scheduledTasks.add(this);
    }

    private static long unixSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private static int clampToInt(long seconds) {
        if (seconds <= 0L) {
            return 0;
        }
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private static long waterCooldownSeconds() {
        return Emulator.getConfig().getInt(CFG_WATER_COOLDOWN_SECONDS, DEFAULT_WATER_COOLDOWN_SECONDS);
    }

    private static long deathSeconds() {
        return (long) Emulator.getConfig().getInt(CFG_DEATH_HOURS, DEFAULT_DEATH_HOURS) * 3600L;
    }
}
