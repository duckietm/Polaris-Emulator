package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official {@code UserEffectActivate} (2959): the avatar editor starts the timer of an effect the
 * user owns. Putting one on the avatar is {@link EnableEffectEvent} (1752), which is also where the
 * client sends {@code -1} to take the current effect off.
 */
public class ActivateEffectEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        int effectId = this.packet.readInt();
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        // Only an effect the user owns. This used to fall through to room.giveEffect for
        // everything else, gated solely by special_enables - which holds 102, 136 and 178
        // by default - so anyone could wear every effect the catalogue sells, the club
        // ones and the Freeze and Banzai team colours without owning a single one. Staff
        // keep :enable, which checks cmd_enable and the per-rank block list.
        if (!habbo.getInventory().getEffectsComponent().ownsEffect(effectId)) return;

        habbo.getInventory().getEffectsComponent().activateEffect(effectId);
    }
}
