package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared structures of the official AIR wired trading messages (WIN63-202610131318).
 *
 * <ul>
 *   <li>{@code TradeRequirementNode}: byte type (0 coins, 1 furni), int amount, and for furni a
 *       {@code ChestItemType}: bool wall, int typeId (furnidata id), string legacy poster id;
 *   <li>{@code TradeRequirementRule}: int count, then that many nodes;
 *   <li>a trade item: int itemId, string type ("I" wall / "S" floor), int roomItemId, int typeId,
 *       int category, bool groupable, stuff data, int creation day, month, year, and for floor furni
 *       an int extra -- the same item a room trade shows.
 * </ul>
 */
public final class ChestAirWire {
    public static final int NODE_COINS = 0;
    public static final int NODE_FURNI = 1;

    private ChestAirWire() {}

    public static void appendNode(ServerMessage message, Term term) {
        message.appendByte(term.isFurni() ? NODE_FURNI : NODE_COINS);
        message.appendInt(term.amount);
        if (term.isFurni()) {
            message.appendBoolean(term.wallItem);
            message.appendInt(ContractWireUtil.spriteIdOf(term.baseItemId));
            message.appendString(term.posterId() == null ? "" : term.posterId());
        }
    }

    public static void appendRule(ServerMessage message, List<Term> rule) {
        List<Term> nodes = rule == null ? List.of() : rule;
        message.appendInt(nodes.size());
        for (Term term : nodes) appendNode(message, term);
    }

    /**
     * What a reward actually handed out, as the rule the success notification shows: the coins first,
     * then one node per furni type.
     */
    public static List<Term> givenRule(int coins, List<ChestFurniStoredItem> furni) {
        List<Term> rule = new ArrayList<>();
        if (coins > 0) rule.add(Term.currency(InteractionWiredContract.DIR_RECEIVE, ChestStorage.COINS_TYPE, coins));

        Map<String, Term> byType = new LinkedHashMap<>();
        if (furni != null) {
            for (ChestFurniStoredItem row : furni) {
                String poster = row.wallItem && row.legacyPosterId != null ? row.legacyPosterId : "";
                String key = row.wallItem + ":" + row.baseItemId + ":" + poster;
                Term term = byType.get(key);
                if (term == null) {
                    byType.put(
                            key,
                            Term.furni(InteractionWiredContract.DIR_RECEIVE, row.wallItem, row.baseItemId, poster, 1));
                } else {
                    term.amount++;
                }
            }
        }
        rule.addAll(byType.values());
        return rule;
    }

    /** A trade item, as the room trade window reads it. */
    public static void appendTradeItem(ServerMessage message, HabboItem item) {
        boolean wall = item.getBaseItem().getType() == FurnitureType.WALL;
        message.appendInt(BuildersClubItemIds.toWire(item.getId()));
        message.appendString(item.getBaseItem().getType().code);
        message.appendInt(BuildersClubItemIds.toWire(item.getId()));
        message.appendInt(item.getBaseItem().getSpriteId());
        message.appendInt(0);
        message.appendBoolean(item.getBaseItem().allowInventoryStack() && !item.isLimited());
        item.serializeExtradata(message);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        if (!wall) message.appendInt(0);
    }

    /** A stored chest furni as a trade item (the chest's side of the table). */
    public static void appendTradeItem(ServerMessage message, ChestFurniStoredItem row) {
        message.appendInt(row.inventoryId);
        message.appendString(row.wallItem ? FurnitureType.WALL.code : FurnitureType.FLOOR.code);
        message.appendInt(row.inventoryId);
        message.appendInt(row.wireTypeId());
        message.appendInt(0);
        message.appendBoolean(row.groupable && row.limitedStack <= 0);
        message.appendInt((row.limitedStack > 0 ? 256 : 0) | (row.stuffDataFormat & 0xFF));
        message.appendString(row.extradata == null ? "" : row.extradata);
        if (row.limitedStack > 0) {
            message.appendInt(row.limitedSells);
            message.appendInt(row.limitedStack);
        }
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        if (!row.wallItem) message.appendInt(row.extra);
    }

    /**
     * The coins a credit furni ({@code CF_<n>} / {@code CFC_<n>}) is worth, or 0 for any other furni.
     * Unlike a room trade this does not depend on {@code redeem.currency.trade}: credit furni are
     * how coins reach a coin chest at all.
     */
    public static int creditValue(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getName() == null) return 0;

        String name = item.getBaseItem().getName();
        if (!name.startsWith("CF_") && !name.startsWith("CFC_")) return 0;
        try {
            return Math.max(0, Integer.parseInt(name.split("_")[1]));
        } catch (RuntimeException notAnAmount) {
            return 0;
        }
    }
}
