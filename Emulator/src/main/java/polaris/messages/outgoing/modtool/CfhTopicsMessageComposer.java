package polaris.messages.outgoing.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.CfhCategory;
import polaris.hotel.modtool.CfhTopic;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CfhTopicsMessageComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CfhTopicsMessageComposer);

        this.response.appendInt(Emulator.getGameEnvironment()
                .getModToolManager()
                .getCfhCategories()
                .values()
                .size());

        for (CfhCategory category : Emulator.getGameEnvironment()
                .getModToolManager()
                .getCfhCategories()
                .values()) {
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
