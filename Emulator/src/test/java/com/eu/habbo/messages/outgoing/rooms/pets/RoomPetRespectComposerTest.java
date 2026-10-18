package com.eu.habbo.messages.outgoing.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.pets.Pet;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

/** The pet respect notification starts with the pet's respect count and its owner, as Habbo's does. */
class RoomPetRespectComposerTest {

    @Test
    void theNotificationCarriesTheRespectCountAndTheOwner() {
        Pet pet = mock(Pet.class);
        when(pet.getRespect()).thenReturn(12);
        when(pet.getUserId()).thenReturn(77);

        for (int type : new int[] {RoomPetRespectComposer.PET_RESPECTED, RoomPetRespectComposer.PET_TREATED}) {
            ByteBuf packet = new RoomPetRespectComposer(pet, type).compose().get();
            packet.skipBytes(6);

            assertEquals(12, packet.readInt());
            assertEquals(77, packet.readInt());
        }
    }
}
