package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 15 {@code class_3300} / {@code class_4190} (350): the avatar collectible the user wears (its token id) and
 * the look it replaced, which the editor shows on every tab but the NFT one. Only sent while one is worn.
 */
public class SelectedNftWardrobeOutfitComposer extends MessageComposer {
    private final String tokenId;
    private final String fallbackFigure;
    private final String fallbackGender;

    public SelectedNftWardrobeOutfitComposer(String tokenId, String fallbackFigure, String fallbackGender) {
        this.tokenId = tokenId == null ? "" : tokenId;
        this.fallbackFigure = fallbackFigure == null ? "" : fallbackFigure;
        this.fallbackGender = fallbackGender == null ? "" : fallbackGender;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SelectedNftWardrobeOutfitComposer);
        this.response.appendString(this.tokenId);
        this.response.appendString(this.fallbackFigure);
        this.response.appendString(this.fallbackGender);
        return this.response;
    }
}
