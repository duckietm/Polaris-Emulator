package polaris.messages.incoming.rooms.items;

import java.util.Arrays;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.commands.FurniDataCommand;
import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.items.interactions.pets.InteractionMonsterPlantSeed;
import polaris.hotel.pets.MonsterplantPet;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.pets.PetPackageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.plugin.Event;
import polaris.plugin.events.furniture.FurnitureToggleEvent;
import polaris.threading.runnables.QueryDeleteUserItem;

public class ToggleFloorItemEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToggleFloorItemEvent.class);

    private static HashSet<String> PET_BOXES = new HashSet<>(Arrays.asList(
            "val11_present",
            "gnome_box",
            "leprechaun_box",
            "velociraptor_egg",
            "pterosaur_egg",
            "petbox_epic",
            "cowbox",
            "cowbox_gold"));

    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        try {
            Room room = this.client.getUser().getUserInfo().getCurrentRoom();

            if (room == null) return;

            int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
            int state = this.packet.readInt();

            if (!RoomItemInputGuard.isPositiveId(itemId)) return;

            UserItem item = room.getUserItem(itemId);

            if (item == null || item instanceof InteractionDice) return;

            if (FurniDataCommand.isInspecting(this.client.getUser())) {
                this.client.getUser().alert(FurniDataCommand.buildItemInfo(item, state));
                return;
            }

            // AIR playtest mode: only furni that everybody may use (usage policy 2) can be used, so a
            // wired box does not open its editor either.
            if (!RoomPlayTestSupport.mayUse(this.client.getUser(), item)) return;

            WiredManager.cancelPendingUserClicksFurni(
                    room, this.client.getUser().getRoomUnit(), item);

            Event furnitureToggleEvent = new FurnitureToggleEvent(item, this.client.getUser(), state);
            Emulator.getPluginManager().fireEvent(furnitureToggleEvent);

            if (furnitureToggleEvent.isCancelled()) return;

            /*
            if (item.getBaseItem().getName().equalsIgnoreCase("totem_planet")) {
                Set<UserItem> items = room.getItemsAt(room.getLayout().getTile(item.getX(), item.getY()));
                UserItem totemLeg = null;
                UserItem totemHead = null;

                for (UserItem totemItem : items) {
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
                            room.giveEffect(this.client.getUser(), 23, -1);
                            return;
                        }

                        if (totemLeg.getExtradata().equals("10") && totemHead.getExtradata().equals("9")) {
                            room.giveEffect(this.client.getUser(), 26, -1);
                            return;
                        }
                    } else if (item.getExtradata().equals("0")) {
                        if (totemLeg.getExtradata().equals("7") && totemHead.getExtradata().equals("10")) {
                            room.giveEffect(this.client.getUser(), 24, -1);
                            return;
                        }

                    } else if (item.getExtradata().equals("1")) {
                        if (totemLeg.getExtradata().equals("9") && totemHead.getExtradata().equals("12")) {
                            room.giveEffect(this.client.getUser(), 25, -1);
                            return;
                        }
                    }
                }
            }*/

            // Do not move to onClick(). Wired could trigger it.
            if (item instanceof InteractionMonsterPlantSeed) {
                if (item.getUserId() != this.client.getUser().getUserInfo().getId()) {
                    return;
                }

                // The rarity is read before the seed is claimed: extradata that is not a number used to
                // throw here after the claim and the delete, and the seed was gone with no plant.
                boolean isRare = item.getBaseItem().getName().contains("rare");
                int rarity = seedRarity(item.getExtradata());

                if (rarity < 0) {
                    rarity = isRare
                            ? InteractionMonsterPlantSeed.randomGoldenRarityLevel()
                            : InteractionMonsterPlantSeed.randomRarityLevel();
                }

                // Claim the seed before anything is created from it: a pickup or a room
                // eviction racing this used to leave the seed alive in an inventory while
                // its row was already deleted, so it could be placed and grown again.
                if (!room.claimItem(item)) {
                    return;
                }

                Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));

                MonsterplantPet pet = Emulator.getGameEnvironment()
                        .getPetManager()
                        .createMonsterplant(
                                room,
                                this.client.getUser(),
                                isRare,
                                room.getLayout().getTile(item.getX(), item.getY()),
                                rarity);
                room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
                room.updateTile(room.getLayout().getTile(item.getX(), item.getY()));
                room.placePet(pet, item.getX(), item.getY(), item.getZ(), item.getRotation());
                pet.cycle();
                room.sendComposer(new RoomUserStatusComposer(pet.getRoomUnit()).compose());
                return;
            }

            if (PET_BOXES.contains(item.getBaseItem().getName())
                    && room.getCurrentPets().size() < Room.MAXIMUM_PETS) {
                this.client.sendResponse(new PetPackageComposer(item));
                return;
            }

            String previousState = item.getExtradata();
            item.onClick(this.client, room, new Object[] {state});
            if (!java.util.Objects.equals(previousState, item.getExtradata())) {
                polaris.hotel.quests.QuestProgressEvents.progress(
                        this.client.getUser(), polaris.hotel.quests.QuestGoalType.USE_FURNI, 1);
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    /** The rarity a seed's extradata asks for, or -1 for a random one (empty, zero or not a number). */
    static int seedRarity(String extradata) {
        try {
            return Integer.parseInt(extradata.trim()) - 1;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
