package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionDice;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionMonsterPlantSeed;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.PetPackageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;

public class ToggleFloorItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleFloorItemEvent.class);

    private static HashSet<String> PET_BOXES = new HashSet<>(Arrays.asList("val11_present", "gnome_box", "leprechaun_box", "velociraptor_egg", "pterosaur_egg", "petbox_epic"));

    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        try {
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

            if (room == null)
                return;

            int itemId = this.packet.readInt();
            int state = this.packet.readInt();

            if (!RoomItemInputGuard.isPositiveId(itemId))
                return;

            HabboItem item = room.getHabboItem(itemId);

            if (item == null || item instanceof InteractionDice)
                return;

            WiredManager.cancelPendingUserClicksFurni(room, this.client.getHabbo().getRoomUnit(), item);

            Event furnitureToggleEvent = new FurnitureToggleEvent(item, this.client.getHabbo(), state);
            Emulator.getPluginManager().fireEvent(furnitureToggleEvent);

            if (furnitureToggleEvent.isCancelled())
                return;

            /*
            if (item.getBaseItem().getName().equalsIgnoreCase("totem_planet")) {
                THashSet<HabboItem> items = room.getItemsAt(room.getLayout().getTile(item.getX(), item.getY()));
                HabboItem totemLeg = null;
                HabboItem totemHead = null;

                for (HabboItem totemItem : items) {
                    if (totemLeg != null && totemHead != null) {
                        break;
                    }
                    if (totemItem.getBaseItem().getName().equalsIgnoreCase("totem_leg")) {
                        totemLeg = totemItem;
                    }
                    if (totemItem.getBaseItem().getName().equalsIgnoreCase("totem_head")) {
                        totemHead = totemItem;
                    }
                }

                if (totemHead != null && totemLeg != null) {
                    if (item.getExtradata().equals("2")) {
                        if (totemLeg.getExtradata() == null || totemHead.getExtradata() == null)
                            return;

                        if (totemLeg.getExtradata().equals("2") && totemHead.getExtradata().equals("5")) {
                            room.giveEffect(this.client.getHabbo(), 23, -1);
                            return;
                        }

                        if (totemLeg.getExtradata().equals("10") && totemHead.getExtradata().equals("9")) {
                            room.giveEffect(this.client.getHabbo(), 26, -1);
                            return;
                        }
                    } else if (item.getExtradata().equals("0")) {
                        if (totemLeg.getExtradata().equals("7") && totemHead.getExtradata().equals("10")) {
                            room.giveEffect(this.client.getHabbo(), 24, -1);
                            return;
                        }

                    } else if (item.getExtradata().equals("1")) {
                        if (totemLeg.getExtradata().equals("9") && totemHead.getExtradata().equals("12")) {
                            room.giveEffect(this.client.getHabbo(), 25, -1);
                            return;
                        }
                    }
                }
            }*/

            // Do not move to onClick(). Wired could trigger it.
            if (item instanceof InteractionMonsterPlantSeed) {
                if (item.getUserId() != this.client.getHabbo().getHabboInfo().getId()) {
                    return;
                }

                Emulator.getThreading().run(new QueryDeleteHabboItem(item.getId()));

                boolean isRare = item.getBaseItem().getName().contains("rare");
                int rarity = 0;

                if (item.getExtradata().isEmpty() || Integer.parseInt(item.getExtradata()) - 1 < 0) {
                    rarity = isRare ? InteractionMonsterPlantSeed.randomGoldenRarityLevel() : InteractionMonsterPlantSeed.randomRarityLevel();
                } else {
                    try {
                        rarity = Integer.parseInt(item.getExtradata()) - 1;
                    } catch (Exception e) {
                    }
                }
                MonsterplantPet pet = Emulator.getGameEnvironment().getPetManager().createMonsterplant(room, this.client.getHabbo(), isRare, room.getLayout().getTile(item.getX(), item.getY()), rarity);
                room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
                room.removeHabboItem(item);
                room.updateTile(room.getLayout().getTile(item.getX(), item.getY()));
                room.placePet(pet, item.getX(), item.getY(), item.getZ(), item.getRotation());
                pet.cycle();
                room.sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
                return;
            }

            if (PET_BOXES.contains(item.getBaseItem().getName()) && room.getCurrentPets().size() < Room.MAXIMUM_PETS) {
                this.client.sendResponse(new PetPackageComposer(item));
                return;
            }

            item.onClick(this.client, room, new Object[]{state});
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }
}
