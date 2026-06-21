package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.ClothItem;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;

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

    public UserClothesComposer(Habbo habbo) {
        for (int value : habbo.getInventory().getWardrobeComponent().getClothing()) {
            ClothItem item = Emulator.getGameEnvironment().getCatalogManager().clothing.get(value);

            if (item != null) {
                for (Integer j : item.setId) {
                    this.idList.add(j);
                }

                this.nameList.add(item.name);
            }
        }

        for (ClothItem item : Emulator.getGameEnvironment().getCatalogManager().clothing.values()) {
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
