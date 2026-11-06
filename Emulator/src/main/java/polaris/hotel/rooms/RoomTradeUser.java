package polaris.hotel.rooms;

import java.util.HashSet;
import java.util.Set;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class RoomTradeUser {
    private final User user;
    private final Set<UserItem> items;
    private int userId;
    private boolean accepted;
    private boolean confirmed;

    public RoomTradeUser(User user) {
        this.user = user;

        if (this.user != null) {
            this.userId = this.user.getUserInfo().getId();
        }

        this.accepted = false;
        this.confirmed = false;
        this.items = new HashSet<>();
    }

    public int getUserId() {
        return this.userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public User getUser() {
        return this.user;
    }

    public boolean getAccepted() {
        return this.accepted;
    }

    public void setAccepted(boolean value) {
        this.accepted = value;
    }

    public boolean getConfirmed() {
        return this.confirmed;
    }

    public void confirm() {
        this.confirmed = true;
    }

    public void setConfirmed(boolean value) {
        this.confirmed = value;
    }

    public void addItem(UserItem item) {
        this.items.add(item);
    }

    public UserItem getItem(int itemId) {
        for (UserItem item : this.items) {
            if (item.getId() == itemId) {
                return item;
            }
        }

        return null;
    }

    public Set<UserItem> getItems() {
        return this.items;
    }

    public void putItemsIntoInventory() {
        this.user.getInventory().getItemsComponent().addItems(this.items);
    }

    public void clearItems() {
        this.items.clear();
    }
}
