package polaris.messages.outgoing.inventory;

import polaris.hotel.users.inventory.EffectsComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EffectsListAddComposer extends MessageComposer {
    public final EffectsComponent.UserEffect effect;

    public EffectsListAddComposer(EffectsComponent.UserEffect effect) {
        this.effect = effect;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EffectsListAddComposer);
        this.response.appendInt(this.effect.effect); // Type
        this.response.appendInt(0); // Unknown Costume?
        this.response.appendInt(effect.duration > 0 ? effect.duration : Integer.MAX_VALUE); // Duration
        this.response.appendBoolean(effect.duration <= 0); // Is active
        return this.response;
    }

    public EffectsComponent.UserEffect getEffect() {
        return effect;
    }
}
