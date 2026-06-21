package com.eu.habbo.habbohotel.pets.actions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWater;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetAction;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Set;

public class ActionDip extends PetAction {
    public ActionDip() {
        super(null, true);
    }

    @Override
    public boolean apply(Pet pet, Habbo habbo, String[] data) {
        Set<HabboItem> waterItems = pet.getRoom().getRoomSpecialTypes().getItemsOfType(InteractionWater.class);

        if (waterItems.isEmpty())
            return false;

        HabboItem waterPatch = (HabboItem) waterItems.toArray()[Emulator.getRandom().nextInt(waterItems.size())];

        pet.getRoomUnit().setGoalLocation(pet.getRoom().getLayout().getTile(waterPatch.getX(), waterPatch.getY()));
        
        pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));

        return true;
    }
}
