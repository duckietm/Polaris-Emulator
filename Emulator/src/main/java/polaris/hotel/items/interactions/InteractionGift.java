package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionGift extends UserItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionGift.class);

    public boolean explode = false;
    private final AtomicBoolean opening = new AtomicBoolean(false);
    private int[] itemId;
    private int colorId = 0;
    private int ribbonId = 0;
    private boolean showSender = false;
    private boolean trustedSender = false;
    private String message = "";
    private String sender = "";
    private String look = "";

    public InteractionGift(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        try {
            this.loadData();
        } catch (Exception e) {
            LOGGER.warn("Incorrect extradata for gift with ID {}", this.getId());
        }
    }

    public InteractionGift(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        try {
            this.loadData();
        } catch (Exception e) {
            LOGGER.warn("Incorrect extradata for gift with ID {}", this.getId());
        }
    }

    /**
     * Claims the right to open this gift, returning true exactly once. Guards
     * against two near-simultaneous OpenRecycleBox packets both scheduling an
     * (async, delayed) OpenGift before the wrapper is removed from the room.
     */
    public boolean tryStartOpening() {
        return this.opening.compareAndSet(false, true);
    }

    /** Whether an unwrap of this gift has been scheduled; the gift may not be picked up meanwhile. */
    public boolean isOpening() {
        return this.opening.get();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        // serverMessage.appendInt(this.colorId * 1000 + this.ribbonId);
        // Map stuff data; the limited flag tells the client the LTD numbers super appends follow.
        serverMessage.appendInt(1 | (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(7);
        serverMessage.appendString("EXTRA_PARAM");
        serverMessage.appendString("");
        serverMessage.appendString("MESSAGE");
        serverMessage.appendString(this.message);
        serverMessage.appendString("PURCHASER_NAME");
        serverMessage.appendString(this.showSender ? this.sender : "");
        serverMessage.appendString("PURCHASER_FIGURE");
        serverMessage.appendString(this.showSender ? this.look : "");
        serverMessage.appendString("PRODUCT_CODE");
        serverMessage.appendString(""); // this.gift.getItemId()
        serverMessage.appendString("state");
        serverMessage.appendString(this.explode ? "1" : "0");
        // The official card warns about links in the message unless the gift comes from staff or the hotel.
        serverMessage.appendString("TRUSTED_SENDER");
        serverMessage.appendString(this.trustedSender ? "true" : "false");

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    private void loadData() throws NumberFormatException {
        String[] data = null;

        if (this.getExtradata().contains("\t")) data = this.getExtradata().split("\t");

        if (data != null && data.length >= 5) {
            int count = Integer.parseInt(data[0]);

            this.itemId = new int[count];

            for (int i = 0; i < count; i++) {
                this.itemId[i] = Integer.parseInt(data[i + 1]);
            }

            this.colorId = Integer.parseInt(data[count + 1]);
            this.ribbonId = Integer.parseInt(data[count + 2]);
            this.showSender = data[count + 3].equalsIgnoreCase("1");
            this.message = data[count + 4];

            if (data.length - count >= 7 && this.showSender) {
                this.sender = data[count + 5];
                this.look = data[count + 6];
            }

            this.trustedSender = isTrustedSenderData(data, count);
        } else {
            this.itemId = new int[0];
            this.colorId = 0;
            this.ribbonId = 0;
            this.showSender = false;
            this.message = "Please delete this present. Thanks!";
        }
    }

    /** The last field of a gift's data: "1" when staff or the hotel itself sent it. */
    public static final String TRUSTED_SENDER = "1";

    /** The field a gift's data ends with: whether staff or the hotel sent it. */
    public static String trustedSenderField(boolean trusted) {
        return "\t" + (trusted ? TRUSTED_SENDER : "0");
    }

    /**
     * Whether the gift's data says staff or the hotel sent it: the field after the sender's look. Gifts wrapped before
     * that field existed have none and count as sent by a player.
     */
    static boolean isTrustedSenderData(String[] data, int count) {
        return data != null && data.length - count >= 8 && TRUSTED_SENDER.equals(data[count + 7]);
    }

    public boolean isTrustedSender() {
        return this.trustedSender;
    }

    public int getColorId() {
        return this.colorId;
    }

    public int getRibbonId() {
        return this.ribbonId;
    }

    public Set<UserItem> loadItems() {
        Set<UserItem> items = new HashSet<>();
        for (int anItemId : this.itemId) {
            if (anItemId == 0) continue;

            items.add(Emulator.getGameEnvironment().getItemManager().loadUserItem(anItemId));
        }

        return items;
    }
}
