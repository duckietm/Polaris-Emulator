package polaris.hotel.items;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;

public class NewUserGift implements ISerialize {
    private final int id;
    private final Type type;
    private final String imageUrl;
    private Map<String, String> items = new HashMap<>();

    public NewUserGift(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.type = Type.valueOf(set.getString("type").toUpperCase());
        this.imageUrl = set.getString("image");
        this.items.put(
                this.type == Type.ROOM ? "" : set.getString("value"),
                this.type == Type.ROOM ? set.getString("value") : "");
    }

    public NewUserGift(int id, Type type, String imageUrl, Map<String, String> items) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.type = type;
        this.items = items;
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString(this.imageUrl);
        message.appendInt(this.items.size());
        for (Map.Entry<String, String> entry : this.items.entrySet()) {
            message.appendString(entry.getKey()); // Item Name
            message.appendString(entry.getValue()); // Extra Info
        }
    }

    public void give(User user) {
        if (this.type == Type.ITEM) {
            for (Map.Entry<String, String> set : this.items.entrySet()) {
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(set.getKey());

                if (item != null) {
                    UserItem createdItem = Emulator.getGameEnvironment()
                            .getItemManager()
                            .createItem(user.getUserInfo().getId(), item, 0, 0, "");

                    if (createdItem != null) {
                        user.addFurniture(createdItem);
                    }
                }
            }
        } else if (this.type == Type.ROOM) {
            // TODO Give room
        }
    }

    public int getId() {
        return this.id;
    }

    public Type getType() {
        return this.type;
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public Map<String, String> getItems() {
        return this.items;
    }

    public enum Type {
        ITEM,
        ROOM
    }
}
