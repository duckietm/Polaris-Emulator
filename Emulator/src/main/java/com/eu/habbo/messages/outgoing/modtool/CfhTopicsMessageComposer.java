package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.CfhCategory;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CfhTopicsMessageComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CfhTopicsMessageComposer);

        this.response.appendInt(Emulator.getGameEnvironment().getModToolManager().getCfhCategories().values().size());

        for (CfhCategory category : Emulator.getGameEnvironment().getModToolManager().getCfhCategories().values()) {
            this.response.appendString(category.getName());
            this.response.appendInt(category.getTopics().values().size());
            for (CfhTopic topic : category.getTopics().values()) {
                this.response.appendString(topic.name);
                this.response.appendInt(topic.id);
                this.response.appendString(topic.action.toString());
            }
        }

        return this.response;
    }
}
