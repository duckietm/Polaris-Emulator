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
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class WiredEffectFurniByType extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.FURNI_BYTYPE_SELECTOR;
    private static final int SOURCE_FURNI_PICKED   = 0;
    private static final int SOURCE_FURNI_SIGNAL   = 1;
    private static final int SOURCE_FURNI_TRIGGER  = 2;

    private static final int MAX_PICKED_FURNI = 20;

    private int            sourceType      = SOURCE_FURNI_PICKED;
    private boolean        matchState      = false;
    private boolean        filterExisting  = false;
    private boolean        invert          = false;
    private List<Integer>  pickedFurniIds  = new ArrayList<>();

    public WiredEffectFurniByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniByType(int id, int userId, Item item, String extradata,
                                  int limitedStack, int limitedSells) {
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

        Set<HabboItem> matched = new LinkedHashSet<>();
        room.getFloorItems().forEach(item -> {
            if (!includeWiredItems && item instanceof InteractionWired) return;
            String key = matchState
                ? item.getBaseItem().getId() + ":" + item.getExtradata()
                : String.valueOf(item.getBaseItem().getId());
            if (matchKeys.contains(key)) {
                matched.add(item);
            }
        });

        Set<HabboItem> result = this.applySelectorModifiers(matched, this.getSelectableFloorItems(room, ctx), ctx.targets().items(), filterExisting, invert);
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
        if (params == null || params.length < 4) {
            throw new WiredSaveException("wf_slc_furni_bytype: intParams must have at least 4 elements");
        }

        this.sourceType    = normalizeSourceType(params[0]);
        this.matchState    = params.length > 1 && params[1] == 1;
        this.filterExisting = params.length > 2 && params[2] == 1;
        this.invert        = params.length > 3 && params[3] == 1;

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

        message.appendInt(4);
        message.appendInt(this.sourceType);
        message.appendInt(matchState      ? 1 : 0);
        message.appendInt(filterExisting  ? 1 : 0);
        message.appendInt(invert          ? 1 : 0);

        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public WiredEffectType getType() { return type; }

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
        return WiredManager.getGson().toJson(
            new JsonData(sourceType, matchState, filterExisting, invert, pickedFurniIds, getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
            this.sourceType     = normalizeSourceType(data.sourceType);
            this.matchState     = data.matchState;
            this.filterExisting = data.filterExisting;
            this.invert         = data.invert;
            this.pickedFurniIds = data.pickedFurniIds != null ? data.pickedFurniIds : new ArrayList<>();
            this.setDelay(data.delay);
        }
    }

    @Override
    public void onPickUp() {
        this.sourceType     = SOURCE_FURNI_PICKED;
        this.matchState     = false;
        this.filterExisting = false;
        this.invert         = false;
        this.pickedFurniIds = new ArrayList<>();
        this.setDelay(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) { return false; }

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
        int            sourceType;
        boolean        matchState;
        boolean        filterExisting;
        boolean        invert;
        List<Integer>  pickedFurniIds;
        int            delay;

        JsonData(int sourceType, boolean matchState, boolean filterExisting, boolean invert,
                 List<Integer> pickedFurniIds, int delay) {
            this.sourceType     = sourceType;
            this.matchState     = matchState;
            this.filterExisting = filterExisting;
            this.invert         = invert;
            this.pickedFurniIds = pickedFurniIds;
            this.delay          = delay;
        }
    }
}
