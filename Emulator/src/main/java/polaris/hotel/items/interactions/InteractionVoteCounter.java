package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.messages.ServerMessage;

public class InteractionVoteCounter extends UserItem {

    /** The counter sums the number signs a user holds up: 0 to 10, one vote per user per round. */
    static final int MIN_VOTE_SIGN = 0;

    static final int MAX_VOTE_SIGN = 10;

    private boolean frozen;
    private int votes;
    private final Set<Integer> votedUsers = ConcurrentHashMap.newKeySet();

    public InteractionVoteCounter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.load(this.getExtradata());
    }

    public InteractionVoteCounter(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.load(this.getExtradata());
    }

    /** Reads "frozen,votes"; anything malformed starts a frozen counter at zero. */
    private void load(String extradata) {
        String[] bits = extradata == null ? new String[0] : extradata.split(",");
        if (bits.length < 2) {
            this.frozen = true;
            this.votes = 0;
        } else {
            this.frozen = bits[0].equals("1");
            this.votes = parseVotes(bits[1]);
        }
        this.updateExtradata();
    }

    static int parseVotes(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean isVoteSign(int signId) {
        return signId >= MIN_VOTE_SIGN && signId <= MAX_VOTE_SIGN;
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0) + 3);
        serverMessage.appendString(this.frozen ? "0" : "1");
        serverMessage.appendInt(this.votes);
        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    private void updateExtradata() {
        this.setExtradata((this.frozen ? "1" : "0") + "," + this.votes);
    }

    @Override
    public synchronized void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (!((client != null && room != null && room.hasRights(client.getUser()))
                || (objects.length >= 2 && objects[1] instanceof WiredEffectType))) return;

        this.frozen = !this.frozen;

        if (!frozen) {
            this.votes = 0;
            this.votedUsers.clear();
        }

        updateExtradata();
        this.needsUpdate(true);
        room.updateItem(this);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    public synchronized void vote(Room room, int UserId, int vote) {
        if (frozen) return;

        if (!isVoteSign(vote)) return;

        if (!votedUsers.add(UserId)) return;

        votes += vote;
        updateExtradata();
        this.needsUpdate(true);
        room.updateItem(this);
    }

    public int getVotes() {
        return this.votes;
    }
}
