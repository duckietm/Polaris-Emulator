package polaris.messages.outgoing.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AddUserItemComposer extends MessageComposer {
    private Collection<UserItem> itemsList;
    private UserItem item;
    private int[] ids;
    private AddUserItemCategory category;
    private Map<AddUserItemCategory, List<Integer>> entries;

    public AddUserItemComposer(Collection<UserItem> itemsList) {
        this.itemsList = itemsList;
        this.category = AddUserItemCategory.OWNED_FURNI;
    }

    public AddUserItemComposer(UserItem item) {
        this.item = item;
        this.category = AddUserItemCategory.OWNED_FURNI;
    }

    public AddUserItemComposer(int[] ids, AddUserItemCategory category) {
        this.ids = ids;
        this.category = category;
    }

    public AddUserItemComposer(int id, AddUserItemCategory category) {
        this.ids = new int[] {id};
        this.category = category;
    }

    public AddUserItemComposer(Map<AddUserItemCategory, List<Integer>> entries) {
        this.entries = entries;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AddUserItemComposer);

        if (this.ids != null) {
            this.response.appendInt(1);

            this.response.appendInt(this.category.number);
            this.response.appendInt(this.ids.length);
            for (int id : this.ids) {
                this.response.appendInt(id);
            }
        } else if (this.entries != null) {
            this.response.appendInt(this.entries.size());

            for (Map.Entry<AddUserItemCategory, List<Integer>> item : this.entries.entrySet()) {
                this.response.appendInt(item.getKey().number);

                this.response.appendInt(item.getValue().size());
                for (int id : item.getValue()) {
                    this.response.appendInt(id);
                }
            }
        } else if (this.item == null) {
            this.response.appendInt(1);
            this.response.appendInt(1);
            this.response.appendInt(this.itemsList.size());
            for (UserItem userItem : this.itemsList) {
                this.response.appendInt(BuildersClubItemIds.toWire(userItem.getId()));
            }
        } else {
            this.response.appendInt(1);
            this.response.appendInt(1);
            this.response.appendInt(1);
            this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        }

        return this.response;
    }

    public enum AddUserItemCategory {
        OWNED_FURNI(1),
        RENTED_FURNI(2),
        PET(3),
        BADGE(4),
        BOT(5),
        GAME(6),
        HABBICON(8);

        public final int number;

        AddUserItemCategory(int number) {
            this.number = number;
        }
    }

    public Collection<UserItem> getItemsList() {
        return itemsList;
    }

    public UserItem getItem() {
        return item;
    }

    public int[] getIds() {
        return ids;
    }

    public AddUserItemCategory getCategory() {
        return category;
    }

    public Map<AddUserItemCategory, List<Integer>> getEntries() {
        return entries;
    }
}
