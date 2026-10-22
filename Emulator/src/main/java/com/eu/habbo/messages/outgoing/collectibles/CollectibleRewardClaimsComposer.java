package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.OpenReward;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;
import java.util.Map;

/**
 * AIR 15 reward claims (3865), one per level reward the user can claim: claim id, status, claimed amount,
 * claim limit, valid from / to, created and a spare time (milliseconds), the collection, product code and
 * wallet, then the product info with its set id and set name (AIR's {@code RewardCollectibleItemRenderer} shows
 * the {@code collectibles.set.<id>} text, else that name).
 */
public class CollectibleRewardClaimsComposer extends MessageComposer {
    private final List<OpenReward> rewards;
    private final String wallet;
    private final Map<String, String> setNames;

    public CollectibleRewardClaimsComposer(List<OpenReward> rewards, String wallet) {
        this(rewards, wallet, Map.of());
    }

    /** {@code setNames}: collection id to name; a set without one sends its id as the name. */
    public CollectibleRewardClaimsComposer(List<OpenReward> rewards, String wallet, Map<String, String> setNames) {
        this.rewards = List.copyOf(rewards);
        this.wallet = wallet;
        this.setNames = Map.copyOf(setNames);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleRewardClaimsComposer);
        this.response.appendInt(this.rewards.size());
        for (OpenReward open : this.rewards) {
            String collection =
                    open.product().collectionId() == null ? "" : open.product().collectionId();
            this.response.appendString(open.reward().claimKey());
            this.response.appendInt(0);
            this.response.appendInt(open.claimedAmount());
            this.response.appendInt(open.reward().claimLimit());
            CollectibleWire.appendLong(this.response, open.reward().validFrom() * 1000L);
            CollectibleWire.appendLong(this.response, open.reward().validTo() * 1000L);
            CollectibleWire.appendLong(this.response, open.reward().validFrom() * 1000L);
            CollectibleWire.appendLong(this.response, 0L);
            this.response.appendString(collection);
            this.response.appendString(open.product().productCode());
            this.response.appendString(this.wallet);
            CollectibleWire.appendProduct(this.response, open.product());
            this.response.appendString(collection);
            String name = this.setNames.get(collection);
            this.response.appendString(name == null || name.isEmpty() ? collection : name);
        }
        return this.response;
    }
}
