package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredExtraOrEval extends InteractionWiredExtra {
    public static final int CODE = 66;
    public static final int MODE_ALL = 0;
    public static final int MODE_AT_LEAST_ONE = 1;
    public static final int MODE_NOT_ALL = 2;
    public static final int MODE_NONE = 3;
    public static final int MODE_LESS_THAN = 4;
    public static final int MODE_EXACTLY = 5;
    public static final int MODE_MORE_THAN = 6;
    public static final int MIN_COMPARE_VALUE = 0;
    public static final int MAX_COMPARE_VALUE = 100;

    private final THashSet<HabboItem> items;
    private int evaluationMode = MODE_ALL;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int compareValue = 1;

    public WiredExtraOrEval(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredExtraOrEval(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.evaluationMode = normalizeEvaluationMode((params.length > 0) ? params[0] : MODE_ALL);
        this.furniSource = normalizeFurniSource((params.length > 1) ? params[1] : WiredSourceUtil.SOURCE_TRIGGER);
        this.compareValue = normalizeCompareValue((params.length > 2) ? params[2] : this.compareValue);
        this.items.clear();

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        if (settings.getFurniIds().length > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            return false;
        }

        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);

            if (isSelectableConditionOrExtra(item)) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.evaluationMode,
                this.furniSource,
                this.compareValue,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.evaluationMode);
        message.appendInt(this.furniSource);
        message.appendInt(this.compareValue);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.evaluationMode = normalizeEvaluationMode(data.evaluationMode);
                this.furniSource = normalizeFurniSource(data.furniSource);
                this.compareValue = normalizeCompareValue(data.compareValue);

                if (data.itemIds != null) {
                    for (Integer itemId : data.itemIds) {
                        HabboItem item = room.getHabboItem(itemId);

                        if (isSelectableConditionOrExtra(item)) {
                            this.items.add(item);
                        }
                    }
                }
            }

            return;
        }

        String[] legacyData = wiredData.split("[;\t]");

        try {
            if (legacyData.length > 0) {
                this.evaluationMode = normalizeEvaluationMode(Integer.parseInt(legacyData[0]));
            }

            if (legacyData.length > 1) {
                this.furniSource = normalizeFurniSource(Integer.parseInt(legacyData[1]));
            }

            if (legacyData.length > 2) {
                this.compareValue = normalizeCompareValue(Integer.parseInt(legacyData[2]));
            }
        } catch (NumberFormatException ignored) {
            this.evaluationMode = MODE_ALL;
            this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.compareValue = 1;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.evaluationMode = MODE_ALL;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.compareValue = 1;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public int getEvaluationMode() {
        return this.evaluationMode;
    }

    public int getFurniSource() {
        return this.furniSource;
    }

    public int getCompareValue() {
        return this.compareValue;
    }

    private void refresh(Room room) {
        THashSet<HabboItem> remove = new THashSet<>();

        for (HabboItem item : this.items) {
            HabboItem roomItem = room.getHabboItem(item.getId());

            if (!isSelectableConditionOrExtra(roomItem)) {
                remove.add(item);
            }
        }

        for (HabboItem item : remove) {
            this.items.remove(item);
        }
    }

    public static boolean matchesMode(int evaluationMode, int matchedRequirements, int totalRequirements, int compareValue) {
        if (totalRequirements <= 0) {
            return true;
        }

        switch (normalizeEvaluationMode(evaluationMode)) {
            case MODE_AT_LEAST_ONE:
                return matchedRequirements > 0;
            case MODE_NOT_ALL:
                return matchedRequirements > 0 && matchedRequirements < totalRequirements;
            case MODE_NONE:
                return matchedRequirements == 0;
            case MODE_LESS_THAN:
                return matchedRequirements < normalizeCompareValue(compareValue);
            case MODE_EXACTLY:
                return matchedRequirements == normalizeCompareValue(compareValue);
            case MODE_MORE_THAN:
                return matchedRequirements > normalizeCompareValue(compareValue);
            case MODE_ALL:
            default:
                return matchedRequirements >= totalRequirements;
        }
    }

    private static int normalizeEvaluationMode(int value) {
        switch (value) {
            case MODE_ALL:
            case MODE_AT_LEAST_ONE:
            case MODE_NOT_ALL:
            case MODE_NONE:
            case MODE_LESS_THAN:
            case MODE_EXACTLY:
            case MODE_MORE_THAN:
                return value;
            default:
                return MODE_ALL;
        }
    }

    private static int normalizeCompareValue(int value) {
        return Math.max(MIN_COMPARE_VALUE, Math.min(MAX_COMPARE_VALUE, value));
    }

    private static int normalizeFurniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
            case WiredSourceUtil.SOURCE_TRIGGER:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    private static boolean isSelectableConditionOrExtra(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();
        if (interaction == null) {
            return false;
        }

        String normalizedInteraction = interaction.toLowerCase();
        return normalizedInteraction.startsWith("wf_cnd_") || normalizedInteraction.startsWith("wf_xtra_");
    }

    static class JsonData {
        int evaluationMode;
        int furniSource;
        int compareValue;
        List<Integer> itemIds;

        JsonData(int evaluationMode, int furniSource, int compareValue, List<Integer> itemIds) {
            this.evaluationMode = evaluationMode;
            this.furniSource = furniSource;
            this.compareValue = compareValue;
            this.itemIds = itemIds;
        }
    }
}
