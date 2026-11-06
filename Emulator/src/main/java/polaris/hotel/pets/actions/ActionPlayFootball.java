package polaris.hotel.pets.actions;

import polaris.hotel.items.interactions.InteractionPushable;
import polaris.hotel.items.interactions.games.football.InteractionFootball;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetAction;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;

public class ActionPlayFootball extends PetAction {
    public ActionPlayFootball() {
        super(PetTasks.PLAY_FOOTBALL, false);
    }

    @Override
    public boolean apply(Pet pet, User user, String[] data) {
        Room room = pet.getRoom();

        if (room == null || room.getLayout() == null) {
            return false;
        }

        if (pet.getRoomUnit() == null) {
            return false;
        }

        if (pet.getTask() != PetTasks.PLAY_FOOTBALL
                && Pet.GLOBAL_FOOTBALL_PET_COUNT.get() >= InteractionFootball.MAX_FOOTBALL_PETS) {
            if (user != null && user.getClient() != null) {
                user.getClient()
                        .sendResponse(new GenericAlertComposer(
                                "Sorry, you already have 5 pets playing football in the rooms"));
            }
            return false;
        }

        UserItem nearestBall = null;
        double nearestDistance = Double.MAX_VALUE;
        RoomTile petTile = pet.getRoomUnit().getCurrentLocation();

        if (petTile == null) {
            return false;
        }

        for (UserItem item : room.getFloorItems()) {
            if (item instanceof InteractionPushable) {
                RoomTile ballTile = room.getLayout().getTile(item.getX(), item.getY());
                if (ballTile != null) {
                    double distance = petTile.distance(ballTile);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestBall = item;
                    }
                }
            }
        }

        if (nearestBall == null) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.DISOBEY));
            return false;
        }

        pet.setTask(PetTasks.PLAY_FOOTBALL);
        pet.getRoomUnit().setCanWalk(true);
        pet.getRoomUnit().setGoalLocation(room.getLayout().getTile(nearestBall.getX(), nearestBall.getY()));

        if (pet.getHappiness() > 75) {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.PLAYFUL));
        } else {
            pet.say(pet.getPetData().randomVocal(PetVocalsType.GENERIC_NEUTRAL));
        }

        return true;
    }
}
