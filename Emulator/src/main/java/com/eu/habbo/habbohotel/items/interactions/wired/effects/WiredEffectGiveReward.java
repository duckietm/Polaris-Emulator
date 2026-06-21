package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredGiveRewardItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class WiredEffectGiveReward extends InteractionWiredEffect {
    public static final int LIMIT_ONCE = 0;
    public static final int LIMIT_N_DAY = 1;
    public static final int LIMIT_N_HOURS = 2;
    public static final int LIMIT_N_MINUTES = 3;

    public static final WiredEffectType type = WiredEffectType.GIVE_REWARD;
    
    public int limit;
    public int limitationInterval;
    public AtomicInteger given = new AtomicInteger(0);
    public int rewardTime;
    public boolean uniqueRewards;
    public List<WiredGiveRewardItem> rewardItems = new CopyOnWriteArrayList<>();
    public int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectGiveReward(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveReward(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) {
                WiredManager.getReward(habbo, this);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        ArrayList<WiredGiveRewardItem> rewards = new ArrayList<>(this.rewardItems);
        return WiredManager.getGson().toJson(new JsonData(this.limit, this.given.get(), this.rewardTime, this.uniqueRewards, this.limitationInterval, rewards, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.limit = data.limit;
            this.given.set(data.given);
            this.rewardTime = data.reward_time;
            this.uniqueRewards = data.unique_rewards;
            this.limitationInterval = data.limit_interval;
            this.rewardItems.clear();
            this.rewardItems.addAll(data.rewards);
            this.userSource = data.userSource;
        }
        else {
            String[] data = wiredData.split(":");
            if (data.length > 0) {
                this.limit = Integer.parseInt(data[0]);
                this.given.set(Integer.parseInt(data[1]));
                this.rewardTime = Integer.parseInt(data[2]);
                this.uniqueRewards = data[3].equals("1");
                this.limitationInterval = Integer.parseInt(data[4]);
                this.setDelay(Integer.parseInt(data[5]));

                if (data.length > 6) {
                    if (!data[6].equalsIgnoreCase("\t")) {
                        String[] items = data[6].split(";");

                        this.rewardItems.clear();

                        for (String s : items) {
                            try {
                                this.rewardItems.add(new WiredGiveRewardItem(s));
                            } catch (Exception e) {
                            }
                        }
                    }
                }

                this.needsUpdate(true);
                this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.limit = 0;
        this.limitationInterval = 0;
        this.given.set(0);
        this.rewardTime = 0;
        this.uniqueRewards = false;
        this.rewardItems.clear();
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(this.rewardItems.size());
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        StringBuilder s = new StringBuilder();

        for (WiredGiveRewardItem item : this.rewardItems) {
            s.append(item.wiredString()).append(";");
        }
        message.appendString(s.toString());
        message.appendInt(5);
        message.appendInt(this.rewardTime);
        message.appendInt(this.uniqueRewards);
        message.appendInt(this.limit);
        message.appendInt(this.limitationInterval);
        message.appendInt(this.userSource);
        message.appendInt(this.limit > 0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (gameClient.getHabbo().hasPermission(Permission.ACC_SUPERWIRED)) {
            if(settings.getIntParams().length < 5) throw new WiredSaveException("Invalid data");
            this.rewardTime = settings.getIntParams()[0];
            this.uniqueRewards = settings.getIntParams()[1] == 1;
            this.limit = settings.getIntParams()[2];
            this.limitationInterval = settings.getIntParams()[3];
            this.userSource = settings.getIntParams()[4];
            this.given.set(0);

            String data = settings.getStringParam();

            String[] items = data.split(";");

            this.rewardItems.clear();

            int i = 1;
            for (String s : items) {
                String[] d = s.split(",", -1);

                if (d.length == 3) {
                    if (!(d[1].contains(":") || d[1].contains(";"))) {
                        try {
                            this.rewardItems.add(new WiredGiveRewardItem(i, d[0].equalsIgnoreCase("0"), d[1], Integer.parseInt(d[2].trim())));
                            continue;
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }

                gameClient.sendResponse(new UpdateFailedComposer(Emulator.getTexts().getValue("alert.superwired.invalid")));
                return false;
            }

            this.setDelay(settings.getDelay());

            WiredManager.dropRewards(this.getId());
            return true;
        }

        gameClient.getHabbo().whisper("U cannot do this.", RoomChatMessageBubbles.ALERT);
        return false;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_NONE;
    }

    static class JsonData {
        int limit;
        int given;
        int reward_time;
        boolean unique_rewards;
        int limit_interval;
        List<WiredGiveRewardItem> rewards;
        int delay;
        int userSource;

        public JsonData(int limit, int given, int reward_time, boolean unique_rewards, int limit_interval, List<WiredGiveRewardItem> rewards, int delay, int userSource) {
            this.limit = limit;
            this.given = given;
            this.reward_time = reward_time;
            this.unique_rewards = unique_rewards;
            this.limit_interval = limit_interval;
            this.rewards = rewards;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
    
    // Getters and Setters
    
    public int getLimit() {
        return this.limit;
    }
    
    public void setLimit(int limit) {
        this.limit = limit;
    }
    
    public int getLimitationInterval() {
        return this.limitationInterval;
    }
    
    public void setLimitationInterval(int limitationInterval) {
        this.limitationInterval = limitationInterval;
    }
    
    public int getGiven() {
        return this.given.get();
    }
    
    public void setGiven(int given) {
        this.given.set(given);
    }
    
    public void incrementGiven() {
        this.given.incrementAndGet();
    }
    
    public int getRewardTime() {
        return this.rewardTime;
    }
    
    public void setRewardTime(int rewardTime) {
        this.rewardTime = rewardTime;
    }
    
    public boolean isUniqueRewards() {
        return this.uniqueRewards;
    }
    
    public void setUniqueRewards(boolean uniqueRewards) {
        this.uniqueRewards = uniqueRewards;
    }
    
    public List<WiredGiveRewardItem> getRewardItems() {
        return this.rewardItems;
    }
    
    public void setRewardItems(List<WiredGiveRewardItem> rewardItems) {
        this.rewardItems = rewardItems;
    }
}
