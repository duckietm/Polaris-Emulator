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
import com.eu.habbo.habbohotel.wired.core.WiredExecutionOrderUtil;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSelector;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remote-selection selector (furni classname {@code wf_slc_remote}). A read-only selector that
 * resolves its targets from an incoming wired SIGNAL (the "remote" that fired the stack), with a
 * configured picked-furni set as a fallback when the stack was not signal-driven. Like the other
 * selectors it only narrows the target set for downstream effects — it never mutates room state, so
 * it is safe to run on a live room.
 *
 * <p>Semantics note: "remote selection" is not defined in the legacy enum; this implements the most
 * defensible reading (signal source + picked fallback). The {@code wired-phase1-deferred} design doc
 * tracks refining it if a different behaviour is desired.</p>
 */
public class WiredEffectRemoteSelector extends InteractionWiredEffect implements WiredOfficialSelector {
    public static final WiredEffectType type = WiredEffectType.REMOTE_SELECTOR;
    private static final int MAX_PICKED_FURNI = 20;

    private boolean filterExisting = false;
    private boolean invert = false;
    private List<Integer> pickedFurniIds = new ArrayList<>();
    /** Official int params: how the picked selectors' results combine, and how many of them run. */
    private boolean intersect = false;

    private int pickCount = 0;

    public WiredEffectRemoteSelector(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoteSelector(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null) {
            return;
        }

        Room room = ctx.room();
        if (room == null) {
            return;
        }

        if (this.runPickedSelectors(ctx, room)) {
            return;
        }

        boolean includeWiredItems = this.includeWiredTargets(ctx);

        Set<HabboItem> matched = new LinkedHashSet<>();

        for (HabboItem item : WiredSourceUtil.resolveItemsRaw(ctx, WiredSourceUtil.SOURCE_SIGNAL, null)) {
            if (item != null && (includeWiredItems || !(item instanceof InteractionWired))) {
                matched.add(item);
            }
        }

        if (matched.isEmpty()) {
            for (Integer id : this.pickedFurniIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null && (includeWiredItems || !(item instanceof InteractionWired))) {
                    matched.add(item);
                }
            }
        }

        Set<HabboItem> result = this.applySelectorModifiers(
                matched,
                this.getSelectableFloorItems(room, ctx),
                ctx.targets().items(),
                this.filterExisting,
                this.invert);
        ctx.targets().setItems(result);
    }

    /**
     * Habbo's remote selection: each picked box stands for its whole stack (every box on its tile), and the stack's
     * selectors run. A picked stack without a selector selects nothing. Union or intersection combine the stacks for
     * both kinds, so a furni-only stack intersected with a users-only stack selects neither. Filter keeps the
     * matches, Filter + Invert the rest, and Invert alone inverts both kinds over the whole room, Wired boxes and
     * temporary copies included. False when no Wired box is picked, so the box keeps its signal / picked-furni
     * behaviour. Behaviour observed in plus-emulator/PlusEMU 74b6b1b0, reimplemented.
     */
    private boolean runPickedSelectors(WiredContext ctx, Room room) {
        List<List<InteractionWiredEffect>> stacks = this.pickedStacks(room);
        if (stacks == null) {
            return false;
        }

        // "Filter the stacks": that many of the picked stacks, chosen at random.
        if (this.pickCount > 0 && this.pickCount < stacks.size()) {
            java.util.Collections.shuffle(stacks, java.util.concurrent.ThreadLocalRandom.current());
            stacks = new ArrayList<>(stacks.subList(0, this.pickCount));
        }

        Set<HabboItem> items = new LinkedHashSet<>();
        Set<RoomUnit> users = new LinkedHashSet<>();
        boolean first = true;

        for (List<InteractionWiredEffect> stack : stacks) {
            Set<HabboItem> selectedItems = new LinkedHashSet<>();
            Set<RoomUnit> selectedUsers = new LinkedHashSet<>();
            if (!stack.isEmpty()) {
                WiredContext scratch =
                        new WiredContext(ctx.event(), ctx.triggerItem(), ctx.services(), new WiredState(20));
                for (InteractionWiredEffect selector : stack) {
                    selector.execute(scratch);
                }
                if (scratch.targets().isItemsModifiedBySelector())
                    selectedItems.addAll(scratch.targets().items());
                if (scratch.targets().isUsersModifiedBySelector())
                    selectedUsers.addAll(scratch.targets().users());
            }

            // Union, or with "intersection" only what every picked stack chose.
            if (first || !this.intersect) {
                items.addAll(selectedItems);
                users.addAll(selectedUsers);
            } else {
                items.retainAll(selectedItems);
                users.retainAll(selectedUsers);
            }
            first = false;
        }

        LinkedHashSet<HabboItem> everyFurni = new LinkedHashSet<>();
        room.getFloorItems().forEach(item -> {
            if (item != null) everyFurni.add(item);
        });

        ctx.targets()
                .setItems(this.applySelectorModifiers(
                        items, everyFurni, ctx.targets().items(), this.filterExisting, this.invert));
        ctx.targets()
                .setUsers(this.applySelectorModifiers(
                        users, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert));
        return true;
    }

    /**
     * The selectors of every picked stack, one entry per stack (tile), in pick order; null when no Wired box is
     * picked. Remote selectors are left out so two of them cannot run each other forever.
     */
    private List<List<InteractionWiredEffect>> pickedStacks(Room room) {
        List<List<InteractionWiredEffect>> stacks = new ArrayList<>();
        Set<Long> tiles = new java.util.HashSet<>();
        boolean pickedWired = false;

        for (Integer id : this.pickedFurniIds) {
            HabboItem picked = room.getHabboItem(id);
            if (!(picked instanceof InteractionWired)) continue;
            pickedWired = true;

            long tile = (((long) picked.getX()) << 32) | (picked.getY() & 0xFFFFFFFFL);
            if (!tiles.add(tile)) continue;

            List<InteractionWiredEffect> selectors = new ArrayList<>();
            Set<HabboItem> onTile = room.getItemsAt(picked.getX(), picked.getY());
            if (onTile != null) {
                for (HabboItem item : WiredExecutionOrderUtil.sort(onTile)) {
                    if (item instanceof InteractionWiredEffect effect
                            && effect.isSelector()
                            && !(effect instanceof WiredEffectRemoteSelector)) {
                        selectors.add(effect);
                    }
                }
            }
            stacks.add(selectors);
        }

        return pickedWired ? stacks : null;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        // Official layout: [combine type (0 union, 1 intersection), random pick count (0 = all)].
        this.intersect = params.length > 0 && params[0] == 1;
        this.pickCount = params.length > 1 ? Math.clamp(params[1], 0, MAX_PICKED_FURNI) : 0;
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();

        this.pickedFurniIds = new ArrayList<>();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (this.pickedFurniIds.size() >= MAX_PICKED_FURNI) break;
                this.pickedFurniIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
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
        return new int[] {WiredSourceUtil.SOURCE_SELECTED};
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
        JsonData data = new JsonData(this.filterExisting, this.invert, this.pickedFurniIds, this.getDelay());
        data.intersect = this.intersect;
        data.pickCount = this.pickCount;
        return WiredManager.getGson().toJson(data);
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

        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.pickedFurniIds = (data.pickedFurniIds != null) ? data.pickedFurniIds : new ArrayList<>();
        this.intersect = data.intersect;
        this.pickCount = Math.clamp(data.pickCount, 0, MAX_PICKED_FURNI);
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.filterExisting = false;
        this.invert = false;
        this.pickedFurniIds = new ArrayList<>();
        this.intersect = false;
        this.pickCount = 0;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(MAX_PICKED_FURNI);

        if (!this.pickedFurniIds.isEmpty()) {
            message.appendInt(this.pickedFurniIds.size());
            this.pickedFurniIds.forEach(message::appendInt);
        } else {
            message.appendInt(0);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.intersect ? 1 : 0);
        message.appendInt(this.pickCount);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    static class JsonData {
        boolean intersect;
        int pickCount;

        boolean filterExisting;
        boolean invert;
        List<Integer> pickedFurniIds;
        int delay;

        JsonData(boolean filterExisting, boolean invert, List<Integer> pickedFurniIds, int delay) {
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.pickedFurniIds = pickedFurniIds;
            this.delay = delay;
        }
    }
}
