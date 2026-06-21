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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class WiredEffectFurniNeighborhood extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectFurniNeighborhood.class);

    public static final WiredEffectType type = WiredEffectType.FURNI_NEIGHBORHOOD_SELECTOR;

    private static final int SOURCE_USER_TRIGGER  = 0;
    private static final int SOURCE_USER_SIGNAL   = 1;
    private static final int SOURCE_USER_CLICKED  = 2;
    private static final int SOURCE_FURNI_TRIGGER = 3;
    private static final int SOURCE_FURNI_PICKED  = 4;
    private static final int SOURCE_FURNI_SIGNAL  = 5;

    private static boolean isUserGroup(int src)  { return src <= SOURCE_USER_CLICKED;  }
    private static boolean isFurniGroup(int src) { return src >= SOURCE_FURNI_TRIGGER; }

    private static final int MAX_PICKED_FURNI = 20;
    private static final int MAX_TILE_OFFSETS  = 64;
    private static final int GRID_RANGE = 4;

    private int             sourceType      = SOURCE_USER_TRIGGER;
    private boolean         filterExisting  = false;
    private boolean         invert          = false;
    private int             targetOffsetX   = 0;
    private int             targetOffsetY   = 0;
    private List<int[]>     tileOffsets     = new ArrayList<>();
    private List<Integer>   pickedFurniIds  = new ArrayList<>();

    public WiredEffectFurniNeighborhood(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniNeighborhood(int id, int userId, Item item, String extradata,
                                        int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || tileOffsets.isEmpty()) return;

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        List<int[]> sourcePositions = resolveSourcePositions(ctx, room);
        if (sourcePositions.isEmpty()) return;

        int totalRaw = 0;
        int wiredSkipped = 0;
        Set<HabboItem> result = new LinkedHashSet<>();
        Set<HabboItem> neighborhoodItems = new LinkedHashSet<>();
        for (int[] src : sourcePositions) {
            LOGGER.info("[FurniNeighborhood] Source: ({},{}), offsets: {}", src[0], src[1], tileOffsets.size());
            for (int[] offset : getFullGridOffsets()) {
                int tx = src[0] + (offset[0] - this.targetOffsetX);
                int ty = src[1] + (offset[1] - this.targetOffsetY);

                for (HabboItem item : room.getItemsAt(tx, ty)) {
                    if (item != null && (includeWiredItems || !(item instanceof InteractionWired))) {
                        neighborhoodItems.add(item);
                    }
                }
            }

            for (int[] offset : tileOffsets) {
                int tx = src[0] + (offset[0] - this.targetOffsetX);
                int ty = src[1] + (offset[1] - this.targetOffsetY);
                for (HabboItem item : room.getItemsAt(tx, ty)) {
                    if (item == null) continue;
                    totalRaw++;
                    if (!includeWiredItems && item instanceof InteractionWired) {
                        wiredSkipped++;
                        LOGGER.info("[FurniNeighborhood]   SKIP wired item {} ({}) at ({},{})",
                                item.getId(), item.getClass().getSimpleName(), tx, ty);
                    } else {
                        result.add(item);
                        LOGGER.info("[FurniNeighborhood]   KEEP item {} ({}) at ({},{})",
                                item.getId(), item.getClass().getSimpleName(), tx, ty);
                    }
                }
            }
        }
        LOGGER.info("[FurniNeighborhood] Raw={}, wiredSkipped={}, kept={}", totalRaw, wiredSkipped, result.size());

        result = this.applyNeighborhoodModifiers(result, neighborhoodItems, ctx.targets().items());

        // Always set the selector result — even if empty.
        // An empty result means no items matched the neighborhood, so downstream
        // effects should target nothing rather than falling back to the original targets.
        ctx.targets().setItems(result);
        LOGGER.info("[FurniNeighborhood] Set {} items as targets", result.size());
    }

    private List<int[]> getFullGridOffsets() {
        List<int[]> offsets = new ArrayList<>();

        for (int y = -GRID_RANGE; y <= GRID_RANGE; y++) {
            for (int x = -GRID_RANGE; x <= GRID_RANGE; x++) {
                offsets.add(new int[]{ x, y });
            }
        }

        return offsets;
    }

    private LinkedHashSet<HabboItem> applyNeighborhoodModifiers(Set<HabboItem> matchedTargets,
                                                                Set<HabboItem> neighborhoodTargets,
                                                                Collection<HabboItem> existingTargets) {
        LinkedHashSet<HabboItem> matched = new LinkedHashSet<>(matchedTargets);

        if (this.invert) {
            LinkedHashSet<HabboItem> base = new LinkedHashSet<>(neighborhoodTargets);
            base.removeAll(matched);

            if (this.filterExisting) {
                base.retainAll(this.toLinkedHashSet(existingTargets));
            }

            return base;
        }

        if (this.filterExisting) {
            matched.retainAll(this.toLinkedHashSet(existingTargets));
        }

        return matched;
    }

    private List<int[]> resolveSourcePositions(WiredContext ctx, Room room) {
        switch (sourceType) {
            case SOURCE_USER_TRIGGER: {
                Optional<RoomUnit> actor = ctx.actor();
                if (actor.isPresent()) {
                    return Collections.singletonList(new int[]{ actor.get().getX(), actor.get().getY() });
                }

                return ctx.tile()
                        .map(tile -> Collections.singletonList(new int[]{ tile.x, tile.y }))
                        .orElse(Collections.emptyList());
            }
            case SOURCE_USER_SIGNAL: {
                List<int[]> positions = ctx.targets().users().stream()
                        .map(user -> new int[]{ user.getX(), user.getY() })
                        .collect(Collectors.toList());

                if (!positions.isEmpty()) {
                    return positions;
                }

                return ctx.actor()
                        .map(actor -> Collections.singletonList(new int[]{ actor.getX(), actor.getY() }))
                        .orElse(Collections.emptyList());
            }
            case SOURCE_USER_CLICKED: {
                if (ctx.event().getTargetUnit().isPresent()) {
                    RoomUnit targetUnit = ctx.event().getTargetUnit().get();

                    return Collections.singletonList(new int[]{ targetUnit.getX(), targetUnit.getY() });
                }

                List<int[]> positions = ctx.targets().users().stream()
                        .map(user -> new int[]{ user.getX(), user.getY() })
                        .collect(Collectors.toList());

                if (!positions.isEmpty()) {
                    return positions;
                }

                return Collections.emptyList();
            }
            case SOURCE_FURNI_TRIGGER: {
                return ctx.sourceItem()
                        .map(i -> Collections.singletonList(new int[]{ i.getX(), i.getY() }))
                        .orElse(Collections.emptyList());
            }
            case SOURCE_FURNI_PICKED: {
                return pickedFurniIds.stream()
                        .map(room::getHabboItem)
                        .filter(Objects::nonNull)
                        .map(i -> new int[]{ i.getX(), i.getY() })
                        .collect(Collectors.toList());
            }
            case SOURCE_FURNI_SIGNAL: {
                List<int[]> positions = ctx.targets().items().stream()
                        .map(i -> new int[]{ i.getX(), i.getY() })
                        .collect(Collectors.toList());

                if (!positions.isEmpty()) {
                    return positions;
                }

                return ctx.sourceItem()
                        .map(item -> Collections.singletonList(new int[]{ item.getX(), item.getY() }))
                        .orElse(Collections.emptyList());
            }
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params == null || params.length < 1) {
            throw new WiredSaveException("wf_slc_furni_neighborhood: intParams must have at least 1 element");
        }

        this.sourceType     = params[0];
        this.filterExisting = params.length > 1 && params[1] == 1;
        this.invert         = params.length > 2 && params[2] == 1;
        this.targetOffsetX  = params.length > 3 ? params[3] : 0;
        this.targetOffsetY  = params.length > 4 ? params[4] : 0;

        this.tileOffsets = new ArrayList<>();
        if (params.length > 5) {
            int n = params[5];
            for (int i = 0; i < n && i < MAX_TILE_OFFSETS; i++) {
                int xi = 6 + i * 2;
                if (xi + 1 < params.length) {
                    tileOffsets.add(new int[]{ params[xi], params[xi + 1] });
                }
            }
        }

        this.pickedFurniIds = new ArrayList<>();
        if (this.sourceType == SOURCE_FURNI_PICKED && settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (pickedFurniIds.size() >= MAX_PICKED_FURNI) break;
                pickedFurniIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());

        LOGGER.info("[FurniNeighborhood] saveData: sourceType={}, filterExisting={}, invert={}, target=({},{}), offsets={}, pickedFurniIds={}",
                sourceType, filterExisting, invert, targetOffsetX, targetOffsetY, tileOffsets.size(), pickedFurniIds);
        for (int[] o : tileOffsets) {
            LOGGER.info("[FurniNeighborhood]   offset: ({}, {})", o[0], o[1]);
        }
        LOGGER.info("[FurniNeighborhood]   raw intParams (len={}): {}", params.length, java.util.Arrays.toString(params));

        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        boolean pickMode = (sourceType == SOURCE_FURNI_PICKED);

        message.appendBoolean(pickMode);
        message.appendInt(pickMode ? MAX_PICKED_FURNI : 0);

        if (pickMode && !pickedFurniIds.isEmpty()) {
            message.appendInt(pickedFurniIds.size());
            pickedFurniIds.forEach(message::appendInt);
        } else {
            message.appendInt(0);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");

        int paramCount = 6 + tileOffsets.size() * 2;
        message.appendInt(paramCount);
        message.appendInt(sourceType);
        message.appendInt(filterExisting ? 1 : 0);
        message.appendInt(invert ? 1 : 0);
        message.appendInt(targetOffsetX);
        message.appendInt(targetOffsetY);
        message.appendInt(tileOffsets.size());
        for (int[] offset : tileOffsets) {
            message.appendInt(offset[0]);
            message.appendInt(offset[1]);
        }

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
    public boolean hasRequiredSelectorTargets(WiredContext ctx) {
        return ctx != null && ctx.targets().hasItems();
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(
                new JsonData(sourceType, filterExisting, invert, targetOffsetX, targetOffsetY, tileOffsets, pickedFurniIds, getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
            this.sourceType    = data.sourceType;
            this.filterExisting = data.filterExisting;
            this.invert        = data.invert;
            this.targetOffsetX = data.targetOffsetX;
            this.targetOffsetY = data.targetOffsetY;
            this.tileOffsets   = data.tileOffsets != null ? data.tileOffsets : new ArrayList<>();
            this.pickedFurniIds = data.pickedFurniIds != null ? data.pickedFurniIds : new ArrayList<>();
            this.setDelay(data.delay);
        }
    }

    @Override
    public void onPickUp() {
        this.sourceType    = SOURCE_USER_TRIGGER;
        this.filterExisting = false;
        this.invert        = false;
        this.targetOffsetX = 0;
        this.targetOffsetY = 0;
        this.tileOffsets   = new ArrayList<>();
        this.pickedFurniIds = new ArrayList<>();
        this.setDelay(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) { return false; }

    static class JsonData {
        int            sourceType;
        boolean        filterExisting;
        boolean        invert;
        int            targetOffsetX;
        int            targetOffsetY;
        List<int[]>    tileOffsets;
        List<Integer>  pickedFurniIds;
        int            delay;

        JsonData(int sourceType, boolean filterExisting, boolean invert, int targetOffsetX, int targetOffsetY,
                 List<int[]> tileOffsets, List<Integer> pickedFurniIds, int delay) {
            this.sourceType     = sourceType;
            this.filterExisting = filterExisting;
            this.invert         = invert;
            this.targetOffsetX  = targetOffsetX;
            this.targetOffsetY  = targetOffsetY;
            this.tileOffsets    = tileOffsets;
            this.pickedFurniIds = pickedFurniIds;
            this.delay          = delay;
        }
    }
}
