package com.eu.habbo.habbohotel.wired.leaderboard;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableStore;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.habbohotel.wired.core.WiredVariableTextConnectorSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows of one leaderboard chunk, read from the room's user variables: the users in the room
 * live, everybody else from their saved rows. Ranks are the position in the sort order, starting at
 * one; ties keep the store's order (by user id).
 */
public final class WiredLeaderboardQuery {

    /** Holders a filtered leaderboard reads before filtering; beyond this the rest are not ranked. */
    static final int MAX_FILTERED_HOLDERS = 5_000;

    private WiredLeaderboardQuery() {}

    /** Chunk {@code chunkIndex} of {@code variableId} on a panel with this config. */
    public static WiredLeaderboardChunk chunk(
            Room room, WiredLeaderboardConfig config, String variableId, int chunkIndex) {
        int definitionItemId = parseId(variableId);
        if (room == null || definitionItemId <= 0 || chunkIndex < 0 || room.getRoomSpecialTypes() == null) {
            return new WiredLeaderboardChunk(chunkIndex, 0, List.of());
        }
        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(definitionItemId);
        if (!(extra instanceof WiredExtraUserVariable definition)) {
            return new WiredLeaderboardChunk(chunkIndex, 0, List.of());
        }

        RoomUserVariableStore store = room.getUserVariableManager().getStore();
        RoomUserVariableStore.Order order = orderOf(config.sortMode());
        boolean descending = isDescending(config.sortMode());
        long offset = (long) chunkIndex * WiredLeaderboardChunk.SIZE;

        int total;
        List<RoomUserVariableStore.Holder> holders;
        if (!config.filterEnabled()) {
            total = store.count(definitionItemId);
            holders = offset >= total
                    ? List.of()
                    : store.page(definitionItemId, order, descending, (int) offset, WiredLeaderboardChunk.SIZE);
        } else {
            List<RoomUserVariableStore.Holder> matching =
                    filtered(room, store, config, definitionItemId, order, descending);
            total = matching.size();
            holders = offset >= total
                    ? List.of()
                    : matching.subList((int) offset, (int) Math.min(total, offset + WiredLeaderboardChunk.SIZE));
        }
        return new WiredLeaderboardChunk(chunkIndex, total, rows(room, config, definition, holders, offset));
    }

    /** The holders of the variable who also hold the filter variable, with the operand's value if compared. */
    private static List<RoomUserVariableStore.Holder> filtered(
            Room room,
            RoomUserVariableStore store,
            WiredLeaderboardConfig config,
            int definitionItemId,
            RoomUserVariableStore.Order order,
            boolean descending) {
        int filterItemId = parseId(config.filterVariableId());
        if (filterItemId <= 0) {
            return List.of();
        }
        Long operand = null;
        if (config.compareEnabled()) {
            operand = operandOf(room, config);
            if (operand == null) {
                return List.of();
            }
        }
        Map<Integer, Integer> filterValues = new HashMap<>();
        for (RoomUserVariableStore.Holder holder :
                store.page(filterItemId, RoomUserVariableStore.Order.ID, false, 0, MAX_FILTERED_HOLDERS)) {
            filterValues.put(holder.userId(), holder.value());
        }
        List<RoomUserVariableStore.Holder> matching = new ArrayList<>();
        for (RoomUserVariableStore.Holder holder :
                store.page(definitionItemId, order, descending, 0, MAX_FILTERED_HOLDERS)) {
            if (!filterValues.containsKey(holder.userId())) {
                continue;
            }
            if (operand != null) {
                Integer value = filterValues.get(holder.userId());
                if (value == null || value.longValue() != operand) {
                    continue;
                }
            }
            matching.add(holder);
        }
        return matching;
    }

    /** The number the filter variable is compared with; null when it has none (nobody matches). */
    static Long operandOf(Room room, WiredLeaderboardConfig config) {
        if (config.operandKind() == WiredLeaderboardCodec.OPERAND_LITERAL) {
            try {
                return Long.parseLong(config.comparisonLiteral());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        int globalItemId = parseId(config.operandVariableId());
        if (globalItemId <= 0 || !room.getRoomVariableManager().hasVariable(globalItemId)) {
            return null;
        }
        return (long) room.getRoomVariableManager().getCurrentValue(globalItemId);
    }

    static List<WiredLeaderboardRow> rows(
            Room room,
            WiredLeaderboardConfig config,
            WiredExtraUserVariable definition,
            List<RoomUserVariableStore.Holder> holders,
            long offset) {
        if (holders.isEmpty()) {
            return List.of();
        }
        List<Integer> userIds = new ArrayList<>(holders.size());
        for (RoomUserVariableStore.Holder holder : holders) {
            userIds.add(holder.userId());
        }
        Map<Integer, String> figures = room.getUserVariableManager().getStore().figures(userIds);
        InteractionWiredExtra levelSystem = config.hasColumn(WiredLeaderboardCodec.COLUMN_LEVEL)
                ? WiredVariableLevelSystemSupport.getLevelSystem(room, definition)
                : null;
        Map<Integer, String> texts = config.hasColumn(WiredLeaderboardCodec.COLUMN_TEXT_CONNECTION)
                ? WiredVariableTextConnectorSupport.mappings(room, definition.getId())
                : Map.of();

        // As in Habbo, a row carries its creation and last-update times only when the board shows that column.
        boolean showsCreation = config.hasColumn(WiredLeaderboardCodec.COLUMN_CREATION);
        boolean showsUpdate = config.hasColumn(WiredLeaderboardCodec.COLUMN_LAST_UPDATE);
        List<WiredLeaderboardRow> rows = new ArrayList<>(holders.size());
        for (int i = 0; i < holders.size(); i++) {
            RoomUserVariableStore.Holder holder = holders.get(i);
            Integer value = holder.value();
            Integer level = levelSystem != null
                    ? WiredVariableLevelSystemSupport.getDerivedValue(
                            levelSystem, WiredExtraVariableLevelUpSystem.SUB_CURRENT_LEVEL, value)
                    : null;
            String text = value != null ? texts.get(value) : null;
            rows.add(new WiredLeaderboardRow(
                    (int) Math.min(Integer.MAX_VALUE, offset + i + 1),
                    holder.userId(),
                    holder.name() != null ? holder.name() : "",
                    figures.getOrDefault(holder.userId(), ""),
                    value != null ? value.toString() : "",
                    showsCreation ? holder.createdAt() * 1000L : 0L,
                    showsUpdate ? holder.updatedAt() * 1000L : 0L,
                    level,
                    text));
        }
        return rows;
    }

    static RoomUserVariableStore.Order orderOf(int sortMode) {
        return switch (sortMode) {
            case WiredLeaderboardCodec.SORT_OLDEST_CREATION, WiredLeaderboardCodec.SORT_LATEST_CREATION ->
                RoomUserVariableStore.Order.CREATION_TIME;
            case WiredLeaderboardCodec.SORT_OLDEST_UPDATE, WiredLeaderboardCodec.SORT_LATEST_UPDATE ->
                RoomUserVariableStore.Order.UPDATE_TIME;
            default -> RoomUserVariableStore.Order.VALUE;
        };
    }

    static boolean isDescending(int sortMode) {
        return sortMode == WiredLeaderboardCodec.SORT_HIGHEST_VALUE
                || sortMode == WiredLeaderboardCodec.SORT_LATEST_CREATION
                || sortMode == WiredLeaderboardCodec.SORT_LATEST_UPDATE;
    }

    static int parseId(String id) {
        if (id == null || id.isEmpty() || id.length() > 10) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(id));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
