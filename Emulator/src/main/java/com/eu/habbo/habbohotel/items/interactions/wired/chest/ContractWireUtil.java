package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;

/**
 * Puts contract requirements on the wire.
 *
 * <p>One decision runs through all of it: a requirement leaves here carrying the furnidata class id,
 * never the emulator's internal base item id. The client draws icons out of furnidata and knows
 * nothing about our item table — the chest packets already made that choice, and a requirements
 * bubble that cannot draw its own icons would be useless.
 */
public final class ContractWireUtil {
    /** A node the client can render: a pile of coins, or a pile of one kind of furni. */
    public static final int NODE_CURRENCY = 0;

    public static final int NODE_FURNI = 1;

    private ContractWireUtil() {}

    /**
     * Append the whole grammar: the alternatives a player may satisfy, then what the contract hands
     * back. The shape mirrors how the bubble reads it — rules are alternatives, nodes inside a rule
     * are joined.
     *
     * <pre>
     *   int ruleCount, [ int nodeCount, [ node ]* ]*,
     *   bool hasRewardRule, [ int nodeCount, [ node ]* ]
     * </pre>
     */
    public static void appendRules(ServerMessage message, ContractRules rules) {
        List<List<ContractTerm>> alternatives = rules.giveRules();

        message.appendInt(alternatives.size());
        for (List<ContractTerm> rule : alternatives) {
            appendRule(message, rule);
        }

        message.appendBoolean(!rules.givesNothing());
        if (!rules.givesNothing()) {
            appendRule(message, rules.getRule());
        }
    }

    /** One rule: every node in it has to be met together. */
    public static void appendRule(ServerMessage message, List<ContractTerm> rule) {
        message.appendInt(rule.size());
        for (ContractTerm term : rule) {
            appendNode(message, term);
        }
    }

    /**
     * One node. Wire layout: {@code int kind, int currencyType, bool wallItem, int spriteId, int
     * amount}. Both kinds carry every field so the client reads a fixed-width row and simply ignores
     * the half that does not apply.
     */
    public static void appendNode(ServerMessage message, ContractTerm term) {
        message.appendInt(term.isFurni() ? NODE_FURNI : NODE_CURRENCY);
        message.appendInt(term.isFurni() ? 0 : term.currencyType);
        message.appendBoolean(term.wallItem);
        message.appendInt(term.isFurni() ? spriteIdOf(term.baseItemId) : 0);
        message.appendInt(term.amount);
    }

    /**
     * Resolve the furnidata class id for an internal base item id, falling back to the internal id
     * when the item is unknown — a contract pointing at a furni that no longer exists should still
     * render as something rather than vanish from the bubble.
     */
    public static int spriteIdOf(int baseItemId) {
        if (baseItemId <= 0) return 0;
        if (Emulator.getGameEnvironment() == null
                || Emulator.getGameEnvironment().getItemManager() == null) {
            return baseItemId;
        }

        Item base = Emulator.getGameEnvironment().getItemManager().getItem(baseItemId);
        return (base == null || base.getSpriteId() <= 0) ? baseItemId : base.getSpriteId();
    }
}
