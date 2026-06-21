package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

public class WiredExtraRandom extends InteractionWiredExtra {
    public static final int CODE = 63;
    private static final int DEFAULT_PICK_AMOUNT = 1;
    private static final int DEFAULT_SKIP_EXECUTIONS = 0;
    private static final int MAX_PICK_AMOUNT = 1000;
    private static final int MAX_SKIP_EXECUTIONS = 1000;

    private final Deque<List<Integer>> recentExecutionEffectIds = new ArrayDeque<>();

    private int pickAmount = DEFAULT_PICK_AMOUNT;
    private int skipExecutions = DEFAULT_SKIP_EXECUTIONS;

    public WiredExtraRandom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraRandom(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int resolvedPickAmount = (settings.getIntParams().length > 0) ? settings.getIntParams()[0] : DEFAULT_PICK_AMOUNT;
        int resolvedSkipExecutions = (settings.getIntParams().length > 1) ? settings.getIntParams()[1] : DEFAULT_SKIP_EXECUTIONS;

        this.pickAmount = normalizePickAmount(resolvedPickAmount);
        this.skipExecutions = normalizeSkipExecutions(resolvedSkipExecutions);
        this.clearRecentExecutions();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.pickAmount, this.skipExecutions));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.pickAmount);
        message.appendInt(this.skipExecutions);
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
            this.pickAmount = normalizePickAmount((data != null) ? data.pickAmount : DEFAULT_PICK_AMOUNT);
            this.skipExecutions = normalizeSkipExecutions((data != null) ? data.skipExecutions : DEFAULT_SKIP_EXECUTIONS);
            return;
        }
    }

    @Override
    public void onPickUp() {
        this.pickAmount = DEFAULT_PICK_AMOUNT;
        this.skipExecutions = DEFAULT_SKIP_EXECUTIONS;
        this.clearRecentExecutions();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public void onMove(Room room, com.eu.habbo.habbohotel.rooms.RoomTile oldLocation, com.eu.habbo.habbohotel.rooms.RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.clearRecentExecutions();
    }

    public List<InteractionWiredEffect> selectEffects(List<InteractionWiredEffect> effects) {
        return this.selectRandomEffects(effects, InteractionWiredEffect::getId);
    }

    public List<IWiredEffect> selectWiredEffects(List<IWiredEffect> effects) {
        return this.selectRandomEffects(effects, effect -> {
            if (effect instanceof InteractionWiredEffect) {
                return ((InteractionWiredEffect) effect).getId();
            }

            return System.identityHashCode(effect);
        });
    }

    public int getPickAmount() {
        return this.pickAmount;
    }

    public int getSkipExecutions() {
        return this.skipExecutions;
    }

    private synchronized <T> List<T> selectRandomEffects(List<T> effects, ToIntFunction<T> idResolver) {
        if (effects == null || effects.isEmpty()) {
            return Collections.emptyList();
        }

        List<T> shuffledEffects = new ArrayList<>(effects);
        Collections.shuffle(shuffledEffects, Emulator.getRandom());

        int desiredAmount = Math.min(this.pickAmount, shuffledEffects.size());
        Set<Integer> recentEffectIds = this.getRecentEffectIds();
        LinkedHashSet<T> selectedEffects = new LinkedHashSet<>();

        for (T effect : shuffledEffects) {
            if (recentEffectIds.contains(idResolver.applyAsInt(effect))) {
                continue;
            }

            selectedEffects.add(effect);
            if (selectedEffects.size() >= desiredAmount) {
                break;
            }
        }

        if (selectedEffects.size() < desiredAmount) {
            for (T effect : shuffledEffects) {
                selectedEffects.add(effect);
                if (selectedEffects.size() >= desiredAmount) {
                    break;
                }
            }
        }

        this.recordExecution(selectedEffects, idResolver);
        return new ArrayList<>(selectedEffects);
    }

    private synchronized void clearRecentExecutions() {
        this.recentExecutionEffectIds.clear();
    }

    private Set<Integer> getRecentEffectIds() {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();

        if (this.skipExecutions <= 0) {
            return ids;
        }

        for (List<Integer> executionIds : this.recentExecutionEffectIds) {
            ids.addAll(executionIds);
        }

        return ids;
    }

    private <T> void recordExecution(Collection<T> selectedEffects, ToIntFunction<T> idResolver) {
        if (this.skipExecutions <= 0) {
            this.recentExecutionEffectIds.clear();
            return;
        }

        List<Integer> executionIds = new ArrayList<>();
        if (selectedEffects != null) {
            for (T effect : selectedEffects) {
                if (effect != null) {
                    executionIds.add(idResolver.applyAsInt(effect));
                }
            }
        }

        this.recentExecutionEffectIds.addLast(executionIds);

        while (this.recentExecutionEffectIds.size() > this.skipExecutions) {
            this.recentExecutionEffectIds.removeFirst();
        }
    }

    private static int normalizePickAmount(int value) {
        return Math.max(1, Math.min(MAX_PICK_AMOUNT, value));
    }

    private static int normalizeSkipExecutions(int value) {
        return Math.max(0, Math.min(MAX_SKIP_EXECUTIONS, value));
    }

    static class JsonData {
        int pickAmount;
        int skipExecutions;

        JsonData(int pickAmount, int skipExecutions) {
            this.pickAmount = pickAmount;
            this.skipExecutions = skipExecutions;
        }
    }
}
