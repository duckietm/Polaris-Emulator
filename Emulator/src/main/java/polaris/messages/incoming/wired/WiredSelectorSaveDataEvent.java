package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.InteractionWired;

/** The official AIR selector (3883) save; saved like any effect or extra box. */
public class WiredSelectorSaveDataEvent extends WiredEffectSaveDataEvent {
    @Override
    protected InteractionWired.SaveKind saveKind() {
        return InteractionWired.SaveKind.SELECTOR;
    }
}
