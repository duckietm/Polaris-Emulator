package com.eu.habbo.messages.outgoing.rooms.pets.breeding;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * What two monsterplants produced: one seed for each owner, side by side, as the result window shows
 * them. A seed nobody got is written as an empty slot rather than left out, because the client reads
 * both blocks unconditionally.
 */
public class MonsterplantBreedingResultComposer extends MessageComposer {

    /** One side of the result: the seed a player walked away with. */
    public record Seed(HabboItem item, int userId, String userName, int rarityLevel, boolean mutation) {
        public static final Seed NONE = new Seed(null, 0, "", 0, false);
    }

    private final Seed one;
    private final Seed two;

    public MonsterplantBreedingResultComposer(Seed one, Seed two) {
        this.one = one == null ? Seed.NONE : one;
        this.two = two == null ? Seed.NONE : two;
    }

    private void append(Seed seed) {
        this.response.appendInt(seed.item() == null ? 0 : seed.item().getId());
        this.response.appendInt(
                seed.item() == null ? 0 : seed.item().getBaseItem().getSpriteId());
        this.response.appendString(
                seed.item() == null ? "" : seed.item().getBaseItem().getName());
        this.response.appendInt(seed.userId());
        this.response.appendString(seed.userName() == null ? "" : seed.userName());
        this.response.appendInt(seed.rarityLevel());
        this.response.appendBoolean(seed.mutation());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MonsterplantBreedingResultComposer);
        this.append(this.one);
        this.append(this.two);
        return this.response;
    }

    public Seed getOne() {
        return one;
    }

    public Seed getTwo() {
        return two;
    }
}
