package polaris.hotel.items.interactions.wired.extra;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.label.WiredLabelSupport;
import polaris.messages.ServerMessage;

/**
 * The official furniture label (wf_label): a note in a speech bubble above the furni, for
 * documenting wired. It is not a wired box: clicking it opens the bubble in the client, people who
 * may modify the room's wired type into it, and its colour, who may see it and whether it stays
 * open are set from the infostand. The room sends the labels to each player, leaving out the ones
 * only wired inspectors may see (see {@link WiredLabelSupport}).
 *
 * <p>It is kept in {@code wired_data} like a wired box, so it needs no table of its own.
 */
public class WiredExtraLabel extends InteractionWiredExtra {
    public static final int VISIBILITY_EVERYONE = 0;
    public static final int VISIBILITY_WIRED_INSPECTORS = 1;

    /** The official client's colours: blue, teal, green, gold, orange, red, purple, charcoal. */
    static final int[] COLORS = {4278912, 2651759, 4157508, 8416562, 8409402, 8404554, 6834048, 5592405};

    public static final int DEFAULT_COLOR = COLORS[0];
    /** The official label input's max_chars. */
    public static final int MAX_TEXT_LENGTH = 1000;

    private String text = "";
    private int color = DEFAULT_COLOR;
    private int visibility = VISIBILITY_WIRED_INSPECTORS;
    private boolean alwaysOpen = false;

    public WiredExtraLabel(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraLabel(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public String getText() {
        return this.text;
    }

    public int getColor() {
        return this.color;
    }

    public int getVisibility() {
        return this.visibility;
    }

    public boolean isAlwaysOpen() {
        return this.alwaysOpen;
    }

    /** Whether this player may see the label at all. */
    public boolean isVisibleTo(Room room, User user) {
        return this.visibility == VISIBILITY_EVERYONE || (room != null && user != null && room.canInspectWired(user));
    }

    public void setText(String text) {
        this.text = normalizeText(text);
        this.needsUpdate(true);
    }

    /** Settings as the official client sends them; an unknown colour falls back to blue. */
    public void setSettings(int color, int visibility, boolean alwaysOpen) {
        this.color = isKnownColor(color) ? color : DEFAULT_COLOR;
        this.visibility = (visibility == VISIBILITY_EVERYONE) ? VISIBILITY_EVERYONE : VISIBILITY_WIRED_INSPECTORS;
        // Only a label everyone may see can stay open, as in the official settings window.
        this.alwaysOpen = alwaysOpen && this.visibility == VISIBILITY_EVERYONE;
        this.needsUpdate(true);
    }

    static boolean isKnownColor(int color) {
        for (int known : COLORS) {
            if (known == color) return true;
        }
        return false;
    }

    /** Line breaks are kept as "\n"; other control characters go, and the text is capped. */
    static String normalizeText(String text) {
        if (text == null) return "";

        StringBuilder normalized = new StringBuilder(Math.min(text.length(), MAX_TEXT_LENGTH));
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < unified.length() && normalized.length() < MAX_TEXT_LENGTH; i++) {
            char c = unified.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) normalized.append(c == '\t' ? ' ' : c);
        }
        return normalized.toString();
    }

    /** The bubble opens in the client; the server has nothing to do on a click. */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) {}

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.text, this.color, this.visibility, this.alwaysOpen));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        JsonData data = WiredExtraPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data == null) return;

        this.text = normalizeText(data.text);
        this.color = isKnownColor(data.color) ? data.color : DEFAULT_COLOR;
        this.visibility = (data.visibility == VISIBILITY_EVERYONE) ? VISIBILITY_EVERYONE : VISIBILITY_WIRED_INSPECTORS;
        this.alwaysOpen = data.alwaysOpen && this.visibility == VISIBILITY_EVERYONE;
    }

    @Override
    public void onPickUp() {
        this.text = "";
        this.color = DEFAULT_COLOR;
        this.visibility = VISIBILITY_WIRED_INSPECTORS;
        this.alwaysOpen = false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {}

    static class JsonData {
        String text;
        int color;
        int visibility;
        boolean alwaysOpen;

        JsonData(String text, int color, int visibility, boolean alwaysOpen) {
            this.text = text;
            this.color = color;
            this.visibility = visibility;
            this.alwaysOpen = alwaysOpen;
        }
    }
}
