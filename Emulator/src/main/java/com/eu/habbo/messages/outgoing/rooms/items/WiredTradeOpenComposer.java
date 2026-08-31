package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRules;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractWireUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Opens a wired contract negotiation on the player's side. Wire layout:
 *
 * <pre>
 *   int contractType (0 payment, 1 trade, 2 reward),
 *   string rewardText, string layoutType,
 *   bool showRequirementsImmediate, bool overridePreviousTrade,
 *   int timeoutSeconds,
 *   &lt;rules, see ContractWireUtil&gt;
 * </pre>
 *
 * <p>{@code overridePreviousTrade} tells the client this replaces a negotiation it may already have
 * open, rather than being a second one — a player only ever has one.
 */
public class WiredTradeOpenComposer extends MessageComposer {
    private final int contractType;
    private final String rewardText;
    private final String layoutType;
    private final boolean showRequirementsImmediate;
    private final boolean overridePreviousTrade;
    private final int timeoutSeconds;
    private final ContractRules rules;

    public WiredTradeOpenComposer(
            int contractType,
            String rewardText,
            String layoutType,
            boolean showRequirementsImmediate,
            boolean overridePreviousTrade,
            int timeoutSeconds,
            ContractRules rules) {
        this.contractType = contractType;
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
        this.response.appendInt(this.contractType);
        this.response.appendString(this.rewardText);
        this.response.appendString(this.layoutType);
        this.response.appendBoolean(this.showRequirementsImmediate);
        this.response.appendBoolean(this.overridePreviousTrade);
        this.response.appendInt(this.timeoutSeconds);

        ContractWireUtil.appendRules(this.response, this.rules);

        return this.response;
    }
}
