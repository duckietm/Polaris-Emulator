package com.eu.habbo.messages.incoming.rooms.pets;

import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.breeding.MonsterplantBreedingRequests;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.pets.breeding.PetBreedingStartComposer;

/**
 * Breeding two monsterplants. When both belong to the same player there is nobody to ask and it
 * happens at once; when they belong to two, the owner of the second plant is asked and the plants
 * are only spent once they have said yes.
 */
public class BreedMonsterplantsEvent extends MessageHandler {
    /** The states of the official {@code BreedPets} composer. */
    private static final int STATE_START = 0;

    private static final int STATE_CANCEL = 1;
    private static final int STATE_ACCEPT = 2;

    /** The states the client reads back: 0 means "somebody is asking you". */
    private static final int ANSWER_ASKED = 0;

    private static final int ANSWER_CANCELLED = 1;
    private static final int ANSWER_ACCEPTED = 2;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int state = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        Pet petOne = room.getPet(this.packet.readInt());
        Pet petTwo = room.getPet(this.packet.readInt());

        if (!(petOne instanceof MonsterplantPet plantOne) || !(petTwo instanceof MonsterplantPet plantTwo)) return;

        if (plantOne == plantTwo || !plantOne.breedable() || !plantTwo.breedable()) return;

        int senderId = this.client.getHabbo().getHabboInfo().getId();

        // One of the two plants has to be the sender's, and the other has to be theirs as well or
        // open to everybody.
        boolean ownsOne = plantOne.getUserId() == senderId;
        boolean ownsTwo = plantTwo.getUserId() == senderId;

        if (!ownsOne && !ownsTwo) return;

        if (!ownsOne && !plantOne.isPubliclyBreedable()) return;

        if (!ownsTwo && !plantTwo.isPubliclyBreedable()) return;

        // The sender always speaks about their own plant first.
        MonsterplantPet mine = ownsOne ? plantOne : plantTwo;
        MonsterplantPet theirs = ownsOne ? plantTwo : plantOne;

        switch (state) {
            case STATE_START -> this.start(room, mine, theirs);
            case STATE_CANCEL -> this.cancel(room, mine, theirs);
            case STATE_ACCEPT -> this.accept(room, mine, theirs);
            default -> {}
        }
    }

    private void start(Room room, MonsterplantPet mine, MonsterplantPet theirs) {
        if (theirs.getUserId() == mine.getUserId()) {
            // Both plants are the sender's: there is nobody to ask.
            mine.breed(theirs);
            return;
        }

        Habbo other = room.getHabbo(theirs.getUserId());

        if (other == null || other.getClient() == null) return;

        MonsterplantBreedingRequests.getInstance().open(mine.getUserId(), mine.getId(), theirs.getId());

        other.getClient().sendResponse(new PetBreedingStartComposer(ANSWER_ASKED, theirs.getId(), mine.getId()));
    }

    /**
     * Either side can walk away: the one who asked withdraws the request standing on the other
     * plant, the one who was asked refuses the request standing on their own.
     */
    private void cancel(Room room, MonsterplantPet mine, MonsterplantPet theirs) {
        MonsterplantBreedingRequests requests = MonsterplantBreedingRequests.getInstance();
        int key = requests.on(mine.getId()) != null ? mine.getId() : theirs.getId();
        MonsterplantBreedingRequests.Request request = requests.close(key);

        if (request == null) return;

        this.tell(room, request.requesterId(), new PetBreedingStartComposer(ANSWER_CANCELLED, 0, 0));
        this.tell(room, theirs.getUserId(), new PetBreedingStartComposer(ANSWER_CANCELLED, 0, 0));
        this.client.sendResponse(new PetBreedingStartComposer(ANSWER_CANCELLED, 0, 0));
    }

    private void accept(Room room, MonsterplantPet mine, MonsterplantPet theirs) {
        MonsterplantBreedingRequests.Request request =
                MonsterplantBreedingRequests.getInstance().on(mine.getId());

        // Only the plant that was asked for can accept, and only for the plant that asked.
        if (request == null || request.requesterPetId() != theirs.getId()) return;

        MonsterplantBreedingRequests.getInstance().close(mine.getId());

        this.tell(room, request.requesterId(), new PetBreedingStartComposer(ANSWER_ACCEPTED, 0, 0));
        this.client.sendResponse(new PetBreedingStartComposer(ANSWER_ACCEPTED, 0, 0));

        theirs.breed(mine);
    }

    private void tell(Room room, int userId, PetBreedingStartComposer composer) {
        Habbo habbo = room.getHabbo(userId);

        if (habbo != null && habbo.getClient() != null) habbo.getClient().sendResponse(composer);
    }
}
