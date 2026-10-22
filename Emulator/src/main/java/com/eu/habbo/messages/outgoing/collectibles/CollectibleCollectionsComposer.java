package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectionState;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Entry;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * AIR 15 collections list (official 3560, taken here by the weekly leaderboard: 9420), one
 * {@code class_4341} per set: its products with the amount owned, id, name, the user's XP in the set,
 * the set's goal, the completion XP, the optional bonus and reward products, the bonus window in
 * milliseconds (-1 none), a status (1 complete), then the bonus and reward claims.
 */
public class CollectibleCollectionsComposer extends MessageComposer {
    private final List<CollectionState> collections;

    public CollectibleCollectionsComposer(List<CollectionState> collections) {
        this.collections = List.copyOf(collections);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleCollectionsComposer);
        this.response.appendInt(this.collections.size());
        for (CollectionState state : this.collections) {
            this.response.appendInt(state.entries().size());
            for (Entry entry : state.entries()) {
                CollectibleWire.appendProduct(this.response, entry.product(), entry.amount());
            }
            this.response.appendString(state.collection().collectionId());
            this.response.appendString(state.collection().name());
            this.response.appendInt(state.score());
            this.response.appendInt(state.goal());
            this.response.appendInt(state.collection().completionXp());
            boolean bonus = state.bonusProduct() != null && state.bonusClaim() != null;
            this.response.appendBoolean(bonus);
            if (bonus) CollectibleWire.appendProduct(this.response, state.bonusProduct(), 0);
            boolean reward = state.rewardProduct() != null && state.rewardClaim() != null;
            this.response.appendBoolean(reward);
            if (reward) CollectibleWire.appendProduct(this.response, state.rewardProduct(), 0);
            boolean window = bonus && state.collection().bonusEnd() > 0;
            CollectibleWire.appendLong(
                    this.response, window ? state.collection().bonusStart() * 1000L : -1L);
            CollectibleWire.appendLong(
                    this.response, window ? state.collection().bonusEnd() * 1000L : -1L);
            this.response.appendShort(state.complete() ? 1 : 0);
            if (bonus) CollectibleWire.appendClaim(this.response, state.bonusClaim());
            if (reward) CollectibleWire.appendClaim(this.response, state.rewardClaim());
        }
        return this.response;
    }
}
