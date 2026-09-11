package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomHanditemBlockSupport;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.threading.runnables.HabboGiveHandItemToPet;
import com.eu.habbo.threading.runnables.RoomUnitWalkToRoomUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The pet menu's "give hand item" entry: the sender walks up to the pet and hands over what they are
 * carrying, exactly like the entry that gives it to another user. The pet is addressed by its id, the
 * way the client's own pet infostand knows it.
 */
public class RoomUserGiveHandItemPetEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int petId = this.packet.readInt();

        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getRoomUnit() == null) return;

        Room room = this.currentRoom();

        if (room == null || RoomHanditemBlockSupport.isHanditemBlocked(room)) return;

        if (habbo.getRoomUnit().getHandItem() <= 0) return;

        Pet pet = room.getPet(petId);

        if (pet == null || pet.getRoomUnit() == null) return;

        List<Runnable> executable = new ArrayList<>();
        executable.add(new HabboGiveHandItemToPet(habbo, pet));
        Emulator.getThreading()
                .run(new RoomUnitWalkToRoomUnit(habbo.getRoomUnit(), pet.getRoomUnit(), room, executable, executable));
    }
}
