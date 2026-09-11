package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.TalentTrackEmailVerifiedComposer;

/**
 * Official {@code GetEmailStatusMessageComposer} (2557): the talent-track task dialog asks for
 * the account e-mail and whether it is verified, and gets {@code EmailStatus} (612) back with
 * {@code (email, isVerified, allowChange)}.
 */
public class GetEmailStatusEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String mail = this.client.getHabbo().getHabboInfo().getMail();
        boolean verified = this.client.getHabbo().getHabboInfo().isMailVerified();

        this.client.sendResponse(new TalentTrackEmailVerifiedComposer(
                mail == null ? "" : mail,
                verified,
                Emulator.getConfig().getBoolean("hotel.email.change.enabled", true)));
    }
}
