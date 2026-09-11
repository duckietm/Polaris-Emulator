package com.eu.habbo.messages.outgoing.rooms.pets;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code PetSupplementedNotification} (header 3441): tells the room
 * which Habbo gave which supplement to which pet, so every client plays the
 * watering / lighting animation over the plant.
 */
public class PetSupplementedNotificationComposer extends MessageComposer {

    private final int petId;
    private final int userId;
    private final int supplementType;

    public PetSupplementedNotificationComposer(int petId, int userId, int supplementType) {
        this.petId = petId;
        this.userId = userId;
        this.supplementType = supplementType;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetSupplementedNotificationComposer);
        this.response.appendInt(this.petId);
        this.response.appendInt(this.userId);
        this.response.appendInt(this.supplementType);
        return this.response;
    }

    public int getPetId() {
        return petId;
    }

    public int getUserId() {
        return userId;
    }

    public int getSupplementType() {
        return supplementType;
    }
}
