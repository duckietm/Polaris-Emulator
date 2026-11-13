package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;

public class EnableEffectEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        int effectId = this.packet.readInt();

        if (effectId > 0) {
            if (this.client.getUser().getInventory().getEffectsComponent().ownsEffect(effectId)) {
                this.client.getUser().getInventory().getEffectsComponent().enableEffect(effectId);
            }
        } else {
            this.client.getUser().getInventory().getEffectsComponent().activatedEffect = 0;
            this.client.getUser().getInventory().getEffectsComponent().setCommandEffect(0);

            if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
                this.client
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .giveEffect(this.client.getUser().getRoomUnit(), 0, -1);
            }
        }
    }
}
