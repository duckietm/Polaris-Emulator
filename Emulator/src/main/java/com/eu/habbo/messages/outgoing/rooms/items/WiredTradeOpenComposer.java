package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestAirWire;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRules;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * Opens the wired trade window (AIR {@code WiredTradeOpen}):
 *
 * <pre>
 *   TradeRequirement: int type (0 any coins, 1 any furni, 2 anything, 4 rules), string youGetText,
 *                     string layoutType, and for type 4 TradeRequirementRules:
 *                       bool hasGive, [int n, rule[n]], bool hasGet, [rule],
 *                       int rulesType (0 normal, 1 multiplier, 2 auto), [int multiplier | int autoMax],
 *   bool showRequirementsImmediate, bool overridePreviousTrade, int timeoutSeconds
 * </pre>
 *
 * A plain deposit asks for any coins or any furni, by the chest's kind; a contract shows its rules.
 */
public class WiredTradeOpenComposer extends MessageComposer {
    public static final int REQUIREMENT_ANY_COINS = 0;
    public static final int REQUIREMENT_ANY_FURNI = 1;
    public static final int REQUIREMENT_ANYTHING = 2;
    public static final int REQUIREMENT_RULES = 4;

    public static final int RULES_NORMAL = 0;

    private final int requirementType;
    private final String rewardText;
    private final String layoutType;
    private final boolean showRequirementsImmediate;
    private final boolean overridePreviousTrade;
    private final int timeoutSeconds;
    private final ContractRules rules;

    /**
     * Kept for callers compiled against it: a chest deposit ({@code contractType} 3) asks for
     * anything, every other contract type shows its rules.
     */
    public WiredTradeOpenComposer(
            int contractType,
            String rewardText,
            String layoutType,
            boolean showRequirementsImmediate,
            boolean overridePreviousTrade,
            int timeoutSeconds,
            ContractRules rules) {
        this(
                contractType == 3 ? REQUIREMENT_ANYTHING : REQUIREMENT_RULES,
                rewardText,
                layoutType,
                showRequirementsImmediate,
                overridePreviousTrade,
                timeoutSeconds,
                rules,
                true);
    }

    public WiredTradeOpenComposer(
            int requirementType,
            String rewardText,
            String layoutType,
            boolean showRequirementsImmediate,
            boolean overridePreviousTrade,
            int timeoutSeconds,
            ContractRules rules,
            boolean airRequirement) {
        this.requirementType = requirementType;
        this.rewardText = rewardText == null ? "" : rewardText;
        this.layoutType = layoutType == null ? "" : layoutType;
        this.showRequirementsImmediate = showRequirementsImmediate;
        this.overridePreviousTrade = overridePreviousTrade;
        this.timeoutSeconds = timeoutSeconds;
        this.rules = rules;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeOpenComposer);
        this.response.appendInt(this.requirementType);
        this.response.appendString(this.rewardText);
        this.response.appendString(this.layoutType);
        if (this.requirementType == REQUIREMENT_RULES) {
            List<List<Term>> give = this.rules == null ? List.of() : this.rules.giveRules();
            boolean hasGive = give.stream().anyMatch(rule -> !rule.isEmpty());
            this.response.appendBoolean(hasGive);
            if (hasGive) {
                List<List<Term>> alternatives =
                        give.stream().filter(rule -> !rule.isEmpty()).toList();
                this.response.appendInt(alternatives.size());
                for (List<Term> rule : alternatives) ChestAirWire.appendRule(this.response, rule);
            }
            boolean hasGet = this.rules != null && !this.rules.givesNothing();
            this.response.appendBoolean(hasGet);
            if (hasGet) ChestAirWire.appendRule(this.response, this.rules.getRule());
            this.response.appendInt(RULES_NORMAL);
        }
        this.response.appendBoolean(this.showRequirementsImmediate);
        this.response.appendBoolean(this.overridePreviousTrade);
        this.response.appendInt(this.timeoutSeconds);
        return this.response;
    }
}
