package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

public class WiredConditionMatchStatePosition extends InteractionWiredCondition implements InteractionWiredMatchFurniSettings {
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.MATCH_SSHOT;

    private THashSet<WiredMatchFurniSetting> settings;

    private boolean state;
    private boolean position;
    private boolean direction;
    private boolean altitude;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionMatchStatePosition(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new THashSet<>();
    }

    public WiredConditionMatchStatePosition(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new THashSet<>();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.settings.size());

        for (WiredMatchFurniSetting item : this.settings)
            message.appendInt(item.item_id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(6);
        message.appendInt(this.state ? 1 : 0);
        message.appendInt(this.direction ? 1 : 0);
        message.appendInt(this.position ? 1 : 0);
        message.appendInt(this.altitude ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 3) return false;
        int[] params = settings.getIntParams();
        this.state = params[0] == 1;
        this.direction = params[1] == 1;
        this.position = params[2] == 1;
        this.altitude = (params.length > 3) && (params[3] == 1);
        this.furniSource = (params.length > 4) ? params[4] : ((params.length > 3 && params[3] > 1) ? params[3] : WiredSourceUtil.SOURCE_TRIGGER);
        this.quantifier = (params.length > 5) ? this.normalizeQuantifier(params[5]) : QUANTIFIER_ALL;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            return true;

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        this.settings.clear();

        for (int i = 0; i < count; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem item = room.getHabboItem(itemId);

            if (item != null)
                this.settings.add(new WiredMatchFurniSetting(item.getId(), item.getExtradata(), item.getRotation(), item.getX(), item.getY(), item.getZ()));
        }

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        this.refresh();

        if (this.settings.isEmpty())
            return true;

        if (this.quantifier == QUANTIFIER_ANY) {
            return this.evaluateAnyTargetMatches(ctx);
        }

        return this.evaluateAllTargetsMatch(ctx);
    }

    protected boolean evaluateAllTargetsMatch(WiredContext ctx) {
        Room room = ctx.room();

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, null);
            if (targets.isEmpty()) return false;

            for (HabboItem item : targets) {
                if (item == null) return false;

                WiredMatchFurniSetting setting = this.resolveSettingForTarget(room, item);
                if (setting == null) {
                    return false;
                }

                if (!this.matchesSetting(item, setting)) {
                    return false;
                }
            }

            return true;
        }

        for (WiredMatchFurniSetting setting : this.settings) {
            HabboItem item = room.getHabboItem(setting.item_id);
            if (item == null) continue;
            if (!this.matchesSetting(item, setting))
                return false;
        }

        return true;
    }

    protected boolean evaluateAnyTargetMatches(WiredContext ctx) {
        Room room = ctx.room();

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            List<HabboItem> targets = WiredSourceUtil.resolveItems(ctx, this.furniSource, null);
            if (targets.isEmpty()) return false;

            for (HabboItem item : targets) {
                if (item == null) continue;

                WiredMatchFurniSetting setting = this.resolveSettingForTarget(room, item);
                if (setting != null && this.matchesSetting(item, setting)) {
                    return true;
                }
            }

            return false;
        }

        for (WiredMatchFurniSetting setting : this.settings) {
            HabboItem item = room.getHabboItem(setting.item_id);
            if (item == null) continue;

            if (this.matchesSetting(item, setting)) {
                return true;
            }
        }

        return false;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    private WiredMatchFurniSetting resolveSettingForTarget(Room room, HabboItem target) {
        WiredMatchFurniSetting fallback = null;

        for (WiredMatchFurniSetting setting : this.settings) {
            HabboItem sourceItem = room.getHabboItem(setting.item_id);
            if (sourceItem == null) continue;
            if (sourceItem.getBaseItem().getId() != target.getBaseItem().getId()) continue;

            if (setting.state.equals(target.getExtradata())) {
                return setting;
            }

            if (fallback == null) {
                fallback = setting;
            }
        }

        return fallback;
    }

    private boolean matchesSetting(HabboItem item, WiredMatchFurniSetting setting) {
        if (this.state && !item.getExtradata().equals(setting.state))
            return false;

        if (this.position && !(setting.x == item.getX() && setting.y == item.getY()))
            return false;

        if (this.altitude && BigDecimal.valueOf(item.getZ()).compareTo(BigDecimal.valueOf(setting.z)) != 0)
            return false;

        return !this.direction || setting.rotation == item.getRotation();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.state,
                this.position,
                this.direction,
                this.altitude,
                new ArrayList<>(this.settings),
                this.furniSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.state = data.state;
            this.position = data.position;
            this.direction = data.direction;
            this.altitude = data.altitude;
            if (data.settings != null) {
                this.settings.addAll(data.settings);
            }
            this.furniSource = data.furniSource;
            this.quantifier = this.normalizeQuantifier(data.quantifier);
        } else {
            String[] data = wiredData.split(":");

            if (data.length >= 5) {
                try {
                    int itemCount = Integer.parseInt(data[0]);

                    String[] items = data[1].split(";");

                    for (int i = 0; i < itemCount && i < items.length; i++) {
                        String[] stuff = items[i].split("-");

                        if (stuff.length >= 6)
                            this.settings.add(new WiredMatchFurniSetting(Integer.parseInt(stuff[0]), stuff[1], Integer.parseInt(stuff[2]), Integer.parseInt(stuff[3]), Integer.parseInt(stuff[4]), Double.parseDouble(stuff[5])));
                        else if (stuff.length >= 5)
                            this.settings.add(new WiredMatchFurniSetting(Integer.parseInt(stuff[0]), stuff[1], Integer.parseInt(stuff[2]), Integer.parseInt(stuff[3]), Integer.parseInt(stuff[4])));
                    }

                    this.state = data[2].equals("1");
                    this.direction = data[3].equals("1");
                    this.position = data[4].equals("1");
                } catch (NumberFormatException ignored) {
                    // malformed legacy data — keep whatever was parsed plus defaults
                }
            }

            this.altitude = false;
            this.furniSource = this.settings.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.quantifier = QUANTIFIER_ALL;
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.direction = false;
        this.position = false;
        this.state = false;
        this.altitude = false;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    private int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected void refresh() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null) {
            THashSet<WiredMatchFurniSetting> remove = new THashSet<>();

            for (WiredMatchFurniSetting setting : this.settings) {
                HabboItem item = room.getHabboItem(setting.item_id);
                if (item == null) {
                    remove.add(setting);
                }
            }

            for (WiredMatchFurniSetting setting : remove) {
                this.settings.remove(setting);
            }
        }
    }

    @Override
    public THashSet<WiredMatchFurniSetting> getMatchFurniSettings() {
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
        boolean position;
        boolean direction;
        boolean altitude;
        List<WiredMatchFurniSetting> settings;
        int furniSource;
        int quantifier;

        public JsonData(boolean state, boolean position, boolean direction, boolean altitude, List<WiredMatchFurniSetting> settings, int furniSource, int quantifier) {
            this.state = state;
            this.position = position;
            this.direction = direction;
            this.altitude = altitude;
            this.settings = settings;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
        }
    }
}
