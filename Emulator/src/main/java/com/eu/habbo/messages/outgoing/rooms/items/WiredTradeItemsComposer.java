package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractTerm;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractWireUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * The state of the table, pushed after every change on either side. Wire layout:
 *
 * <pre>
 *   int state, bool canAccept, int secondsLeft,
 *   int offeredCount, [ int itemId, bool wallItem, int spriteId ]*,
 *   int rewardFurniCount, [ int spriteId, int amount ]*,
 *   int rewardCurrencyCount, [ int currencyType, int amount ]*,
 *   int missingCount, [ &lt;node&gt; ]*
 * </pre>
 *
 * <p>The reward is split into furni and currency rather than sent as one list, because the window
 * draws them differently — a pile of coins is not an inventory icon. The missing list is what the
 * requirements indicator reads: empty means the offer covers a rule, and the accept button is live.
 */
public class WiredTradeItemsComposer extends MessageComposer {
    private final int state;
    private final boolean canAccept;
    private final int secondsLeft;
    private final List<OfferedItem> offered;
    private final List<ContractTerm> reward;
    private final List<ContractTerm> missing;

    public WiredTradeItemsComposer(
            int state,
            boolean canAccept,
            int secondsLeft,
            List<OfferedItem> offered,
            List<ContractTerm> reward,
            List<ContractTerm> missing) {
        this.state = state;
        this.canAccept = canAccept;
        this.secondsLeft = secondsLeft;
        this.offered = offered == null ? List.of() : offered;
        this.reward = reward == null ? List.of() : reward;
        this.missing = missing == null ? List.of() : missing;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeItemsComposer);
        this.response.appendInt(this.state);
        this.response.appendBoolean(this.canAccept);
        this.response.appendInt(this.secondsLeft);

        this.response.appendInt(this.offered.size());
        for (OfferedItem item : this.offered) {
            this.response.appendInt(item.itemId());
            this.response.appendBoolean(item.wallItem());
            this.response.appendInt(ContractWireUtil.spriteIdOf(item.baseItemId()));
        }

        List<ContractTerm> rewardFurni =
                this.reward.stream().filter(ContractTerm::isFurni).toList();
        this.response.appendInt(rewardFurni.size());
        for (ContractTerm term : rewardFurni) {
            this.response.appendInt(ContractWireUtil.spriteIdOf(term.baseItemId));
            this.response.appendInt(term.amount);
        }

        List<ContractTerm> rewardCurrency =
                this.reward.stream().filter(ContractTerm::isCurrency).toList();
        this.response.appendInt(rewardCurrency.size());
        for (ContractTerm term : rewardCurrency) {
            this.response.appendInt(term.currencyType);
            this.response.appendInt(term.amount);
        }

        this.response.appendInt(this.missing.size());
        for (ContractTerm term : this.missing) {
            ContractWireUtil.appendNode(this.response, term);
        }

        return this.response;
    }
}
