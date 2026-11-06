package polaris.messages.outgoing.navigator;

import java.util.Set;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TagsComposer extends MessageComposer {
    private final Set<String> tags;

    public TagsComposer(Set<String> tags) {
        this.tags = tags;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TagsComposer);
        this.response.appendInt(this.tags.size());

        int i = 1;
        for (String s : this.tags) {
            this.response.appendString(s);
            this.response.appendInt(i);
            i++;
        }

        return this.response;
    }

    public Set<String> getTags() {
        return tags;
    }
}
