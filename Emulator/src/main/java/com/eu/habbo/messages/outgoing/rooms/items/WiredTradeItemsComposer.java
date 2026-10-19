package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestAirWire;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractRequirementEvaluator.OfferedItem;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ContractWireUtil;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract.Term;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * The state of the wired trade table (AIR {@code WiredTradeItems}), pushed after every change:
 *
 * <pre>
 *   int firstUserId, int n, item[n], int firstUserNumItems, int firstUserNumCredits,
 *   int secondUserId, int m, item[m], int secondUserNumItems, int secondUserNumCredits,
 *   bool canAccept, int extra
 * </pre>
 *
 * The first side is what the player offers (credits = the value of the credit furni among it), the
 * second what the chests hand back (credits = the reward's coins); {@code extra} is how many times the
 * requirement is met.
 */
public class WiredTradeItemsComposer extends MessageComposer {
    private final int userId;
    private final List<HabboItem> offered;
    private final List<OfferedItem> legacyOffered;
    private final int offeredCredits;
    private final List<ChestFurniStoredItem> rewardFurni;
    private final int rewardCoins;
    private final boolean canAccept;
    private final int extra;

    public WiredTradeItemsComposer(
            int userId,
            List<HabboItem> offered,
            int offeredCredits,
            List<ChestFurniStoredItem> rewardFurni,
            int rewardCoins,
            boolean canAccept,
            int extra) {
        this.userId = userId;
        this.offered = offered == null ? List.of() : offered;
        this.legacyOffered = List.of();
        this.offeredCredits = offeredCredits;
        this.rewardFurni = rewardFurni == null ? List.of() : rewardFurni;
        this.rewardCoins = rewardCoins;
        this.canAccept = canAccept;
        this.extra = extra;
    }

    /**
     * Kept for callers compiled against it. Only the offered items' types are known here, so they are
     * sent with empty furni data, and the reward's coins are the currency terms added up.
     */
    public WiredTradeItemsComposer(
            int state,
            boolean canAccept,
            int secondsLeft,
            List<OfferedItem> offered,
            List<Term> reward,
            List<Term> missing) {
        this.userId = 0;
        this.offered = List.of();
        this.legacyOffered = offered == null ? List.of() : offered;
        this.offeredCredits = 0;
        this.rewardFurni = List.of();
        this.rewardCoins = reward == null
                ? 0
                : reward.stream()
                        .filter(Term::isCurrency)
                        .mapToInt(term -> term.amount)
                        .sum();
        this.canAccept = canAccept;
        this.extra = canAccept ? 1 : 0;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeItemsComposer);

        this.response.appendInt(this.userId);
        this.response.appendInt(this.offered.size() + this.legacyOffered.size());
        for (HabboItem item : this.offered) ChestAirWire.appendTradeItem(this.response, item);
        for (OfferedItem item : this.legacyOffered) appendLegacy(item);
        this.response.appendInt(this.offered.size() + this.legacyOffered.size());
        this.response.appendInt(this.offeredCredits);

        this.response.appendInt(0);
        this.response.appendInt(this.rewardFurni.size());
        for (ChestFurniStoredItem row : this.rewardFurni) ChestAirWire.appendTradeItem(this.response, row);
        this.response.appendInt(this.rewardFurni.size());
        this.response.appendInt(this.rewardCoins);

        this.response.appendBoolean(this.canAccept);
        this.response.appendInt(this.extra);
        return this.response;
    }

    private void appendLegacy(OfferedItem item) {
        this.response.appendInt(BuildersClubItemIds.toWire(item.itemId()));
        this.response.appendString(item.wallItem() ? FurnitureType.WALL.code : FurnitureType.FLOOR.code);
        this.response.appendInt(BuildersClubItemIds.toWire(item.itemId()));
        this.response.appendInt(ContractWireUtil.spriteIdOf(item.baseItemId()));
        this.response.appendInt(0);
        this.response.appendBoolean(true);
        this.response.appendInt(0);
        this.response.appendString("");
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        if (!item.wallItem()) this.response.appendInt(0);
    }
}
