package polaris.messages.outgoing.furnieditor;

import java.util.List;
import java.util.Map;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class FurniEditorSearchComposer extends MessageComposer {
    private final List<Map<String, Object>> items;
    private final int total;
    private final int page;

    public FurniEditorSearchComposer(List<Map<String, Object>> items, int total, int page) {
        this.items = items;
        this.total = total;
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniEditorSearchComposer);
        this.response.appendInt(this.items.size());

        for (Map<String, Object> item : this.items) {
            this.response.appendInt((int) item.get("id"));
            this.response.appendInt((int) item.get("sprite_id"));
            this.response.appendString((String) item.getOrDefault("item_name", ""));
            this.response.appendString((String) item.getOrDefault("public_name", ""));
            this.response.appendString((String) item.getOrDefault("type", "s"));
            this.response.appendInt((int) item.getOrDefault("width", 1));
            this.response.appendInt((int) item.getOrDefault("length", 1));
            this.response.appendDouble((double) item.getOrDefault("stack_height", 0.0));
            this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_stack", "1"))));
            this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_walk", "0"))));
            this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_sit", "0"))));
            this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_lay", "0"))));
            this.response.appendString((String) item.getOrDefault("interaction_type", ""));
            this.response.appendInt((int) item.getOrDefault("interaction_modes_count", 0));
        }

        this.response.appendInt(this.total);
        this.response.appendInt(this.page);

        return this.response;
    }
}
