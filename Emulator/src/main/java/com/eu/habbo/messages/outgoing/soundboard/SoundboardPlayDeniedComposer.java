package com.eu.habbo.messages.outgoing.soundboard;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SoundboardPlayDeniedComposer extends MessageComposer {
    private final Reason reason;
    private final int remainingSeconds;

    public SoundboardPlayDeniedComposer(Reason reason, int remainingSeconds) {
        this.reason = reason;
        this.remainingSeconds = Math.max(0, remainingSeconds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardPlayDeniedComposer);
        this.response.appendInt(this.reason.wireCode());
        this.response.appendInt(this.remainingSeconds);
        return this.response;
    }

    public enum Reason {
        COOLDOWN(1),
        ROOM_DISABLED(2),
        UNAVAILABLE(3);

        private final int wireCode;

        Reason(int wireCode) {
            this.wireCode = wireCode;
        }

        public int wireCode() {
            return this.wireCode;
        }
    }
}
