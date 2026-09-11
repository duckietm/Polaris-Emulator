package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code class_1691} / {@code class_2805} (official header 2703, ours 9470):
 * the pet scratch was refused because the account is younger than {@code requiredDays}.
 * The client shows {@code room.error.pets.respectfailed} and gives the pet respect back.
 */
public class PetRespectFailedComposer extends MessageComposer {
    private final int requiredDays;
    private final int avatarAgeInDays;

    public PetRespectFailedComposer(int requiredDays, int avatarAgeInDays) {
        this.requiredDays = requiredDays;
        this.avatarAgeInDays = avatarAgeInDays;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PetRespectFailedComposer);
        this.response.appendInt(this.requiredDays);
        this.response.appendInt(this.avatarAgeInDays);
        return this.response;
    }
}
