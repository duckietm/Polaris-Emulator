package com.eu.habbo.messages.outgoing.quests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.UnsupportedOutgoing;

public class QuestionInfoComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(UnsupportedOutgoing.QuestionInfoComposer);

        return this.response;
    }
}
