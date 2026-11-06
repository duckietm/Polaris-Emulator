package polaris.hotel.guides;

public class GuideChatMessage {
    public final int userId;
    public final String message;
    public final int timestamp;

    public GuideChatMessage(int userId, String message, int timestamp) {
        this.userId = userId;
        this.message = message;
        this.timestamp = timestamp;
    }
}
