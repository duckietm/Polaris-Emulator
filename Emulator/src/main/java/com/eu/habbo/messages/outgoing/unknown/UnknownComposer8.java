package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.outgoing.rooms.pets.PetSupplementedNotificationComposer;

/**
 * @deprecated the packet was identified as the official
 *     {@code PetSupplementedNotification}; use
 *     {@link PetSupplementedNotificationComposer}. Kept as a delegate so plugins
 *     built against the old name keep working.
 */
@Deprecated
public class UnknownComposer8 extends PetSupplementedNotificationComposer {

    public UnknownComposer8(int unknownInt1, int userId, int unknownInt2) {
        super(unknownInt1, userId, unknownInt2);
    }

    public int getUnknownInt1() {
        return this.getPetId();
    }

    public int getUnknownInt2() {
        return this.getSupplementType();
    }
}
