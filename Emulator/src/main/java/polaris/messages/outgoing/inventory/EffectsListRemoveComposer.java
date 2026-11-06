package polaris.messages.outgoing.inventory;

import polaris.hotel.users.inventory.EffectsComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class EffectsListRemoveComposer extends MessageComposer {
    public final EffectsComponent.UserEffect effect;

    public EffectsListRemoveComposer(EffectsComponent.UserEffect effect) {
        this.effect = effect;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.EffectsListRemoveComposer);
        this.response.appendInt(this.effect.effect);
        return this.response;
    }

    public EffectsComponent.UserEffect getEffect() {
        return effect;
    }
}
