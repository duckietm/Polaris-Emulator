package polaris.messages.incoming.users;

import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

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
        User user = this.client.getUser();
        if (user == null) return;

        // Only an effect the user owns. This used to fall through to room.giveEffect for
        // everything else, gated solely by special_enables - which holds 102, 136 and 178
        // by default - so anyone could wear every effect the catalogue sells, the club
        // ones and the Freeze and Banzai team colours without owning a single one. Staff
        // keep :enable, which checks cmd_enable and the per-rank block list.
        if (!user.getInventory().getEffectsComponent().ownsEffect(effectId)) return;

        user.getInventory().getEffectsComponent().activateEffect(effectId);
    }
}
