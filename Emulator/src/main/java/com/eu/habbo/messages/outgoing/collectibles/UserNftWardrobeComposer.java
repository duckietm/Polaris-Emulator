package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.WardrobeOutfit;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * AIR 15 {@code class_3782} / {@code class_4131} (2236): the avatar collectibles of the editor's NFT tab, each an
 * {@code NftWardrobeItem} of id, figure, gender, token id and contract key. The id and the token id are the
 * token's asset id: the editor sends the id back to wear it (133) and matches the worn token (350) on the token id.
 */
public class UserNftWardrobeComposer extends MessageComposer {
    private final List<WardrobeOutfit> outfits;

    public UserNftWardrobeComposer(List<WardrobeOutfit> outfits) {
        this.outfits = List.copyOf(outfits);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserNftWardrobeComposer);
        this.response.appendInt(this.outfits.size());
        for (WardrobeOutfit outfit : this.outfits) {
            String id = Long.toString(outfit.token().assetId());
            this.response.appendString(id);
            this.response.appendString(outfit.outfit().figure());
            this.response.appendString(outfit.outfit().gender());
            this.response.appendString(id);
            this.response.appendString(outfit.outfit().contractKey());
        }
        return this.response;
    }
}
