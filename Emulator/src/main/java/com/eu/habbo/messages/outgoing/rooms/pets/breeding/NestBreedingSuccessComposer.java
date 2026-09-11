package com.eu.habbo.messages.outgoing.rooms.pets.breeding;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code NestBreedingSuccess} (AIR 13, header 1901): the offspring a
 * breeding nest produced and the rarity bucket its breed falls in.
 * {@code AvatarInfoWidgetHandler.onNestBreedingSuccessEvent} opens the success
 * dialog from it.
 */
public class NestBreedingSuccessComposer extends MessageComposer {

    private final int petId;
    private final int rarityCategory;

    public NestBreedingSuccessComposer(int petId, int rarityCategory) {
        this.petId = petId;
        this.rarityCategory = rarityCategory;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NestBreedingSuccessComposer);
        this.response.appendInt(this.petId);
        this.response.appendInt(this.rarityCategory);
        return this.response;
    }

    public int getPetId() {
        return petId;
    }

    public int getRarityCategory() {
        return rarityCategory;
    }
}
