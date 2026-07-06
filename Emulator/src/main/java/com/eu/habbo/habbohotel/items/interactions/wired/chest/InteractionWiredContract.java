package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wired contract extra (payment / reward / trade / custom). Terms are stored as
 * {@code intParams = [count, dir, type, amount, ...]} and an optional linked chest
 * furni id (first selected furni) for depositing PAY or sourcing RECEIVE.
 */
public abstract class InteractionWiredContract extends InteractionWiredExtra {
    private int[] termParams = new int[] { 0 };
    private String termPosters = "";
    private int chestItemId = 0;

    protected InteractionWiredContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** Client {@code WiredActionLayoutCode} for this contract dialog. */
    public abstract int getLayoutCode();

    public List<ContractTerm> getTerms() {
        return ContractTerm.parse(this.termParams, this.termPosters);
    }

    public InteractionWiredChest resolveLinkedChest(Room room) {
        if (room == null || this.chestItemId <= 0) {
            return null;
        }
        HabboItem item = room.getHabboItem(this.chestItemId);
        return (item instanceof InteractionWiredChest chest) ? chest : null;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        List<ContractTerm> terms = ContractTerm.parse(params, settings.getStringParam());
        this.termParams = ContractTerm.serialize(terms);
        this.termPosters = ContractTerm.serializePosters(terms);
        this.chestItemId = 0;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int[] furniIds = settings.getFurniIds();
        if (furniIds != null) {
            for (int furniId : furniIds) {
                HabboItem selected = room.getHabboItem(furniId);
                if (selected instanceof InteractionWiredChest) {
                    this.chestItemId = furniId;
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<Integer> selectedChest = new ArrayList<>();
        if (this.chestItemId > 0 && room.getHabboItem(this.chestItemId) != null) {
            selectedChest.add(this.chestItemId);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(selectedChest.size());
        for (int id : selectedChest) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.termPosters == null ? "" : this.termPosters);
        message.appendInt(this.termParams.length);
        for (int param : this.termParams) {
            message.appendInt(param);
        }
        message.appendInt(0);
        message.appendInt(this.getLayoutCode());
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.termParams, this.termPosters, this.chestItemId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }
        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.termParams = (data.terms != null && data.terms.length > 0) ? data.terms : new int[] { 0 };
                this.termPosters = data.posters == null ? "" : data.posters;
                this.chestItemId = data.chestItemId;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.termParams = new int[] { 0 };
        this.termPosters = "";
        this.chestItemId = 0;
    }

    static class JsonData {
        int[] terms;
        String posters;
        int chestItemId;

        JsonData() {
        }

        JsonData(int[] terms, String posters, int chestItemId) {
            this.terms = terms;
            this.posters = posters;
            this.chestItemId = chestItemId;
        }
    }

    public static final class Payment extends InteractionWiredContract {
        public static final int CODE = 110;

        public Payment(ResultSet set, Item baseItem) throws SQLException {
            super(set, baseItem);
        }

        public Payment(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
            super(id, userId, item, extradata, limitedStack, limitedSells);
        }

        @Override
        public int getLayoutCode() {
            return CODE;
        }
    }

    public static final class Reward extends InteractionWiredContract {
        public static final int CODE = 111;

        public Reward(ResultSet set, Item baseItem) throws SQLException {
            super(set, baseItem);
        }

        public Reward(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
            super(id, userId, item, extradata, limitedStack, limitedSells);
        }

        @Override
        public int getLayoutCode() {
            return CODE;
        }
    }

    public static final class Trade extends InteractionWiredContract {
        public static final int CODE = 112;

        public Trade(ResultSet set, Item baseItem) throws SQLException {
            super(set, baseItem);
        }

        public Trade(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
            super(id, userId, item, extradata, limitedStack, limitedSells);
        }

        @Override
        public int getLayoutCode() {
            return CODE;
        }
    }

    public static final class Custom extends InteractionWiredContract {
        public static final int CODE = 113;

        public Custom(ResultSet set, Item baseItem) throws SQLException {
            super(set, baseItem);
        }

        public Custom(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
            super(id, userId, item, extradata, limitedStack, limitedSells);
        }

        @Override
        public int getLayoutCode() {
            return CODE;
        }
    }
}
