package polaris.messages.outgoing.inventory;

import java.util.Collection;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.inventory.EffectsComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserEffectsListComposer extends MessageComposer {
    public final User user;
    public final Collection<EffectsComponent.UserEffect> effects;

    public UserEffectsListComposer(User user, Collection<EffectsComponent.UserEffect> effects) {
        this.user = user;
        this.effects = effects;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserEffectsListComposer);

        if (this.user == null
                || this.user.getInventory() == null
                || this.user.getInventory().getEffectsComponent() == null
                || this.user.getInventory().getEffectsComponent().effects == null) {
            this.response.appendInt(0);
        } else {
            synchronized (this.user.getInventory().getEffectsComponent().effects) {
                this.response.appendInt(this.effects.size());

                for (EffectsComponent.UserEffect effect : effects) {
                    UserEffectsListComposer.this.response.appendInt(effect.effect);
                    UserEffectsListComposer.this.response.appendInt(0);
                    UserEffectsListComposer.this.response.appendInt(
                            effect.duration > 0 ? effect.duration : Integer.MAX_VALUE);
                    UserEffectsListComposer.this.response.appendInt(
                            (effect.duration > 0 ? (effect.total - (effect.isActivated() ? 1 : 0)) : 0));

                    if (!effect.isActivated() && effect.duration > 0) {
                        UserEffectsListComposer.this.response.appendInt(0);
                    } else {
                        UserEffectsListComposer.this.response.appendInt(
                                effect.duration > 0
                                        ? (Emulator.getIntUnixTimestamp() - effect.activationTimestamp)
                                                + effect.duration
                                        : 0);
                    }
                    UserEffectsListComposer.this.response.appendBoolean(effect.duration <= 0); // is perm
                }
            }
        }
        return this.response;
    }

    public User getUser() {
        return user;
    }

    public Collection<EffectsComponent.UserEffect> getEffects() {
        return effects;
    }
}
