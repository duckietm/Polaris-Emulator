package polaris.hotel.modtool;

public class ModToolChatLog implements Comparable<ModToolChatLog> {
    public final int timestamp;
    public final int userId;
    public final String username;
    public final String message;
    public final boolean highlighted;

    public ModToolChatLog(int timestamp, int userId, String username, String message) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.highlighted = false;
    }

    public ModToolChatLog(int timestamp, int userId, String username, String message, boolean highlighted) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.highlighted = highlighted;
    }

    @Override
    public int compareTo(ModToolChatLog o) {
        return o.timestamp - this.timestamp;
    }
}
