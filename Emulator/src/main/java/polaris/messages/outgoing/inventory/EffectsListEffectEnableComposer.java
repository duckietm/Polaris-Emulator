package polaris.messages.outgoing.inventory;

import polaris.hotel.users.inventory.EffectsComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EffectsListEffectEnableComposer extends MessageComposer {
    public final EffectsComponent.UserEffect effect;

    public EffectsListEffectEnableComposer(EffectsComponent.UserEffect effect) {
        this.effect = effect;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EffectsListEffectEnableComposer);
        this.response.appendInt(this.effect.effect); // Type
        this.response.appendInt(this.effect.duration); // Duration
        this.response.appendBoolean(this.effect.enabled); // activated
        return this.response;
    }

    public EffectsComponent.UserEffect getEffect() {
        return effect;
    }
}
