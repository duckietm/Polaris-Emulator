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
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSelector;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectFurniByType extends InteractionWiredEffect implements WiredOfficialSelector {

    public static final WiredEffectType type = WiredEffectType.FURNI_BYTYPE_SELECTOR;
    private static final int SOURCE_FURNI_PICKED = 0;
    private static final int SOURCE_FURNI_SIGNAL = 1;
    private static final int SOURCE_FURNI_TRIGGER = 2;

    private static final int MAX_PICKED_FURNI = 20;

    private int sourceType = SOURCE_FURNI_PICKED;
    private boolean matchState = false;
    private boolean filterExisting = false;
    private boolean invert = false;
    private List<Integer> pickedFurniIds = new ArrayList<>();

    public WiredEffectFurniByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        List<HabboItem> sourceFurni = resolveSourceFurni(ctx, room);

        Set<String> matchKeys = new LinkedHashSet<>();
        for (HabboItem src : sourceFurni) {
            String key = matchState
                    ? src.getBaseItem().getId() + ":" + src.getExtradata()
                    : String.valueOf(src.getBaseItem().getId());
            matchKeys.add(key);
        }

        // AIR matches floor and wall furni alike: a picked poster selects every poster of its type.
        Set<HabboItem> matched = new LinkedHashSet<>();
        java.util.function.Consumer<HabboItem> match = item -> {
            if (item == null || item.getBaseItem() == null) return;
            if (!includeWiredItems && item instanceof InteractionWired) return;
            String key = matchState
                    ? item.getBaseItem().getId() + ":" + item.getExtradata()
                    : String.valueOf(item.getBaseItem().getId());
            if (matchKeys.contains(key)) {
                matched.add(item);
            }
        };
        room.getFloorItems().forEach(match);
        room.getWallItems().forEach(match);

        Set<HabboItem> result = this.applySelectorModifiers(
                matched, this.getSelectableItems(room, ctx), ctx.targets().items(), filterExisting, invert);
        ctx.targets().setItems(result);
    }

    private List<HabboItem> resolveSourceFurni(WiredContext ctx, Room room) {
        switch (sourceType) {
            case SOURCE_FURNI_PICKED: {
                return pickedFurniIds.stream()
                        .map(room::getHabboItem)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            case SOURCE_FURNI_SIGNAL: {
                return WiredSourceUtil.resolveItemsRaw(ctx, WiredSourceUtil.SOURCE_SIGNAL, null);
            }
            case SOURCE_FURNI_TRIGGER: {
                return WiredSourceUtil.resolveItemsRaw(ctx, WiredSourceUtil.SOURCE_TRIGGER, null);
            }
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        // Official layout: [matchState], the furni whose types are wanted in the official source slot.
        this.sourceType = settings.getFurniSourceTypes().length > 0
                ? fromOfficialSource(settings.getFurniSourceTypes()[0])
                : SOURCE_FURNI_PICKED;
        this.matchState = params != null && params.length > 0 && params[0] == 1;
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();

        this.pickedFurniIds = new ArrayList<>();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (pickedFurniIds.size() >= MAX_PICKED_FURNI) break;
                pickedFurniIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(MAX_PICKED_FURNI);

        if (!pickedFurniIds.isEmpty()) {
            message.appendInt(pickedFurniIds.size());
            pickedFurniIds.forEach(message::appendInt);
        } else {
            message.appendInt(0);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");

        message.appendInt(1);
        message.appendInt(matchState ? 1 : 0);

        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    /** Our source of the wanted types as the official source number. */
    private int officialSource() {
        return switch (this.sourceType) {
            case SOURCE_FURNI_SIGNAL -> WiredSourceUtil.SOURCE_SIGNAL;
            case SOURCE_FURNI_TRIGGER -> WiredSourceUtil.SOURCE_TRIGGER;
            default -> WiredSourceUtil.SOURCE_SELECTED;
        };
    }

    private static int fromOfficialSource(int source) {
        return switch (source) {
            case WiredSourceUtil.SOURCE_SIGNAL -> SOURCE_FURNI_SIGNAL;
            case WiredSourceUtil.SOURCE_TRIGGER -> SOURCE_FURNI_TRIGGER;
            default -> SOURCE_FURNI_PICKED;
        };
    }

    @Override
    public boolean selectorFilter() {
        return this.filterExisting;
    }

    @Override
    public boolean selectorInvert() {
        return this.invert;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.officialSource()};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
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
        return WiredManager.getGson()
                .toJson(new JsonData(sourceType, matchState, filterExisting, invert, pickedFurniIds, getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
            if (data == null) return;

            this.sourceType = normalizeSourceType(data.sourceType);
            this.matchState = data.matchState;
            this.filterExisting = data.filterExisting;
            this.invert = data.invert;
            this.pickedFurniIds = data.pickedFurniIds != null ? data.pickedFurniIds : new ArrayList<>();
            this.setDelay(data.delay);
        }
    }

    @Override
    public void onPickUp() {
        this.sourceType = SOURCE_FURNI_PICKED;
        this.matchState = false;
        this.filterExisting = false;
        this.invert = false;
        this.pickedFurniIds = new ArrayList<>();
        this.setDelay(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private int normalizeSourceType(int value) {
        switch (value) {
            case SOURCE_FURNI_SIGNAL:
            case SOURCE_FURNI_TRIGGER:
            case SOURCE_FURNI_PICKED:
                return value;
            default:
                return SOURCE_FURNI_PICKED;
        }
    }

    static class JsonData {
        int sourceType;
        boolean matchState;
        boolean filterExisting;
        boolean invert;
        List<Integer> pickedFurniIds;
        int delay;

        JsonData(
                int sourceType,
                boolean matchState,
                boolean filterExisting,
                boolean invert,
                List<Integer> pickedFurniIds,
                int delay) {
            this.sourceType = sourceType;
            this.matchState = matchState;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.pickedFurniIds = pickedFurniIds;
            this.delay = delay;
        }
    }
}
