package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.PetSupplementedNotificationComposer;

/**
 * Official {@code PetSupplementComposer} (AIR 13, header 2868; the older id 749
 * is still in use by our client) - the "give water" and "give light" entries of
 * the own-pet menu, sent by
 * {@code InfoStandWidgetHandler} as {@code class_2522(petId, supplement)}.
 *
 * <p>Both supplements keep a monsterplant alive: the plant's energy is the time
 * left before it withers, so a supplement pushes the death timestamp back the
 * same way treating it does, and the room is told who did it.
 */
public class PetSupplementEvent extends MessageHandler {

    /** Official {@code PetSupplementType}. */
    public static final int WATER = 0;

    public static final int LIGHT = 1;

    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();
        int supplementType = this.packet.readInt();

        if (supplementType != WATER && supplementType != LIGHT) {
            return;
        }

        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        Pet pet = room.getPet(petId);
        if (!(pet instanceof MonsterplantPet plant)) {
            return;
        }

        if (plant.isDead() || plant.getUserId() != habbo.getHabboInfo().getId()) {
            return;
        }

        plant.setDeathTimestamp(Emulator.getIntUnixTimestamp() + MonsterplantPet.timeToLive);
        plant.addHappiness(10);
        Emulator.getThreading().run(plant);

        room.sendComposer(new PetStatusUpdateComposer(plant).compose());
        room.sendComposer(new PetSupplementedNotificationComposer(
                        petId, habbo.getHabboInfo().getId(), supplementType)
                .compose());
    }
}
