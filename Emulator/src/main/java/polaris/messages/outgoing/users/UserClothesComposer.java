package polaris.messages.outgoing.users;

import java.util.ArrayList;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.catalog.ClothItem;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserClothesComposer extends MessageComposer {
    private static class ClothEntry {
        private final String name;
        private final int[] setIds;

        private ClothEntry(String name, int[] setIds) {
            this.name = name;
            this.setIds = setIds;
        }
    }

    private final ArrayList<Integer> idList = new ArrayList<>();
    private final ArrayList<String> nameList = new ArrayList<>();
    private final ArrayList<ClothEntry> clothEntries = new ArrayList<>();

    public UserClothesComposer(User user) {
        Map<Integer, ClothItem> clothing =
                Emulator.getGameEnvironment().getCatalogManager().getClothingSnapshot();

        for (int value : user.getInventory().getWardrobeComponent().getClothing()) {
            ClothItem item = clothing.get(value);

            if (item != null) {
                for (Integer j : item.setId) {
                    this.idList.add(j);
                }

                this.nameList.add(item.name);
            }
        }

        for (int setId : user.getInventory().getWardrobeComponent().getCollectibleSets()) {
            if (!this.idList.contains(setId)) this.idList.add(setId);
        }

        for (ClothItem item : clothing.values()) {
            if (item != null) {
                this.clothEntries.add(new ClothEntry(item.name, item.setId));
            }
        }
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserClothesComposer);
        this.response.appendInt(this.idList.size());
        this.idList.forEach(this.response::appendInt);
        this.response.appendInt(this.nameList.size());
        this.nameList.forEach(this.response::appendString);
        this.response.appendInt(this.clothEntries.size());

        for (ClothEntry entry : this.clothEntries) {
            this.response.appendString(entry.name);
            this.response.appendInt(entry.setIds.length);

            for (int setId : entry.setIds) {
                this.response.appendInt(setId);
            }
        }

        return this.response;
    }

    public ArrayList<Integer> getIdList() {
        return idList;
    }

    public ArrayList<String> getNameList() {
        return nameList;
    }
}
