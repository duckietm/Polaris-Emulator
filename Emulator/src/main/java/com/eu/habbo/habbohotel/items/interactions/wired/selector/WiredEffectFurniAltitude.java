package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public class WiredEffectFurniAltitude extends InteractionWiredEffect {
    private static final int COMPARISON_LESS = 0;
    private static final int COMPARISON_EQUAL = 1;
    private static final int COMPARISON_GREATER = 2;

    public static final WiredEffectType type = WiredEffectType.FURNI_ALTITUDE_SELECTOR;

    private int comparison = COMPARISON_EQUAL;
    private double altitude = 0.0D;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectFurniAltitude(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        Set<HabboItem> matchingItems = new LinkedHashSet<>();

        room.getFloorItems().forEach(item -> {
            if (item == null || (!includeWiredItems && item instanceof InteractionWired)) {
                return;
            }

            if (this.matchesAltitude(item)) {
                matchingItems.add(item);
            }
        });

        Set<HabboItem> result = new LinkedHashSet<>(matchingItems);

        result = this.applySelectorModifiers(result, this.getSelectableFloorItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);

        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params == null || params.length < 3) {
            throw new WiredSaveException("wf_slc_furni_altitude requires 3 int params: comparison, filterExisting, invert");
        }

        this.comparison = this.normalizeComparison(params[0]);
        this.filterExisting = params[1] == 1;
        this.invert = params[2] == 1;
        this.altitude = this.parseAltitudeOrDefault(settings.getStringParam());
        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.comparison,
                this.formatAltitude(this.altitude),
                this.filterExisting,
                this.invert,
                this.getDelay()
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.comparison = this.normalizeComparison(data.comparison);
        this.altitude = this.parseAltitudeOrDefault(data.altitude);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.comparison = COMPARISON_EQUAL;
        this.altitude = 0.0D;
        this.filterExisting = false;
        this.invert = false;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.formatAltitude(this.altitude));
        message.appendInt(3);
        message.appendInt(this.comparison);
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private boolean matchesAltitude(HabboItem item) {
        if (item == null) {
            return false;
        }

        double normalizedAltitude = this.normalizeAltitude(item.getZ());

        switch (this.comparison) {
            case COMPARISON_LESS:
                return normalizedAltitude < this.altitude;
            case COMPARISON_GREATER:
                return normalizedAltitude > this.altitude;
            default:
                return BigDecimal.valueOf(normalizedAltitude).compareTo(BigDecimal.valueOf(this.altitude)) == 0;
        }
    }

    private int normalizeComparison(int value) {
        if (value < COMPARISON_LESS || value > COMPARISON_GREATER) {
            return COMPARISON_EQUAL;
        }

        return value;
    }

    private double normalizeAltitude(double value) {
        double clampedValue = Math.max(0.0D, Math.min(Room.MAXIMUM_FURNI_HEIGHT, value));
        return BigDecimal.valueOf(clampedValue).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double parseAltitudeOrDefault(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0D;
        }

        try {
            return this.normalizeAltitude(new BigDecimal(value.trim()).doubleValue());
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private String formatAltitude(double value) {
        BigDecimal decimal = BigDecimal.valueOf(this.normalizeAltitude(value)).stripTrailingZeros();
        return (decimal.scale() < 0 ? decimal.setScale(0, RoundingMode.DOWN) : decimal).toPlainString();
    }

    static class JsonData {
        int comparison;
        String altitude;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int comparison, String altitude, boolean filterExisting, boolean invert, int delay) {
            this.comparison = comparison;
            this.altitude = altitude;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
