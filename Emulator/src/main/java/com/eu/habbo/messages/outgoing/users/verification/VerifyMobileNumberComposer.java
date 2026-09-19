package com.eu.habbo.messages.outgoing.users.verification;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

// The header alias is deprecated in Outgoing; the composer stays for plugin compatibility.
@SuppressWarnings("deprecation")
public class VerifyMobileNumberComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.VerifyMobileNumberComposer);
        return this.response;
    }
}
