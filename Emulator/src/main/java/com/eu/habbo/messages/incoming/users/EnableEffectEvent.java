package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;

public class EnableEffectEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        int effectId = this.packet.readInt();

        if (effectId > 0) {
            if (this.client.getHabbo().getInventory().getEffectsComponent().ownsEffect(effectId)) {
                this.client.getHabbo().getInventory().getEffectsComponent().enableEffect(effectId);
            }
        } else {
            this.client.getHabbo().getInventory().getEffectsComponent().activatedEffect = 0;

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client
                        .getHabbo()
                        .getHabboInfo()
                        .getCurrentRoom()
                        .giveEffect(this.client.getHabbo().getRoomUnit(), 0, -1);
            }
        }
    }
}
