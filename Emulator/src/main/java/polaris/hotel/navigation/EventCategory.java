package polaris.hotel.navigation;

import polaris.messages.ServerMessage;

public class EventCategory {
    private int id;
    private String caption;
    private boolean visible;

    public EventCategory(int id, String caption, boolean visible) {
        this.id = id;
        this.caption = caption;
        this.visible = visible;
    }

    public EventCategory(String serialized) throws Exception {
        String[] parts = serialized.split(",");

        if (parts.length != 3) throw new Exception("A serialized event category should contain 3 fields");

        this.id = Integer.parseInt(parts[0]);
        this.caption = parts[1];
        this.visible = parts[2].equalsIgnoreCase("true");
    }

    public int getId() {
        return id;
    }

    public String getCaption() {
        return caption;
    }

    public boolean isVisible() {
        return visible;
    }

    public void serialize(ServerMessage message) {
        message.appendInt(this.id);
        message.appendString(this.caption);
        message.appendBoolean(this.visible);
    }
}
