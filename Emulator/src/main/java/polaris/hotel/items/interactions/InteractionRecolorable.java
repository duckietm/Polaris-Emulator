package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.recolorable.RecolorablePalettes;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

/**
 * Habbo's recolourable Builders Club furni ({@code *_recolorable}, client type
 * "furniture_recolorable"): one furni per shape whose colour is chosen per item. The client reads the
 * colour from the furni's data map as decimal "r", "g" and "b" next to "state", and tints the layer
 * tagged "recolorable"; a furni without a colour shows white.
 *
 * <p>The state works as on any furni. The colour is kept with it in {@code extra_data} as
 * {@code state|r,g,b}; everything else in the emulator sees only the state through
 * {@link #getExtradata()}. A Builders Club placement may hand in just "R,G,B".
 *
 * <p>Like the multiheight furni it recolours (bc_cylinder and bc_cylinder_recolorable share their states), a
 * recolourable furni whose base item has a {@code multiheight} list takes the height of its state, and its state
 * cannot change while something stands stacked on it.
 */
public class InteractionRecolorable extends InteractionDefault implements VariableHeightItem {
    public static final int NO_COLOR = -1;
    private static final String SEPARATOR = "|";

    private int color = NO_COLOR;

    public InteractionRecolorable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.readStored(super.getExtradata());
    }

    public InteractionRecolorable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.readStored(super.getExtradata());
    }

    /** Whether its base item has a {@code multiheight} list, so its state sets its height. */
    public boolean hasMultiHeights() {
        return this.getBaseItem() != null && this.getBaseItem().getMultiHeights().length > 0;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (!this.hasMultiHeights() || room == null) {
            super.onClick(client, room, objects);
            return;
        }

        // A multiheight furni keeps its height while something is stacked on it.
        UserItem topItem = room.getTopItemAt(this.getX(), this.getY());
        if (topItem != null && !topItem.equals(this)) return;

        String state = this.getExtradata();
        super.onClick(client, room, objects);

        if (!state.equals(this.getExtradata())) {
            // A full update: the tiles take the new height and the users on it move with it.
            room.updateItem(this);
        }
    }

    @Override
    public void updateUnitsOnItem(Room room) {
        InteractionMultiHeight.updateUnitsOn(this, room);
    }

    /** The packed 0xRRGGBB colour, or {@link #NO_COLOR}. */
    public int getColor() {
        return this.color;
    }

    public boolean hasColor() {
        return this.color != NO_COLOR;
    }

    public void setColor(int rgb) {
        this.color = (rgb < 0) ? NO_COLOR : (rgb & 0xFFFFFF);
    }

    /** Whether furni of this base item are recolourable. */
    public static boolean isRecolorable(Item baseItem) {
        return baseItem != null
                && baseItem.getInteractionType() != null
                && InteractionRecolorable.class.isAssignableFrom(
                        baseItem.getInteractionType().getType());
    }

    /**
     * The extra data a Builders Club placement gives a new furni: the "R,G,B" the client chose when it is
     * in the furni's palette, else the palette's first colour (the official catalog preselects it).
     * Other furni keep the requested extra data, or the catalog's when none was sent.
     */
    public static String placementExtradata(Item baseItem, String requested, String catalogExtradata) {
        if (!isRecolorable(baseItem)) {
            return (requested != null && !requested.isEmpty()) ? requested : catalogExtradata;
        }
        // "R,G,B" from the client, or "state|R,G,B" as stored; a state that is not a number becomes "0".
        String state = "0";
        String colour = requested;
        int separator = (requested == null) ? -1 : requested.indexOf(SEPARATOR);
        if (separator >= 0) {
            String requestedState = requested.substring(0, separator);
            if (requestedState.matches("[0-9]{1,9}")) state = requestedState;
            colour = requested.substring(separator + 1);
        }

        java.util.List<Integer> palette = RecolorablePalettes.shipped().colorsFor(baseItem.getName());
        int rgb = RecolorablePalettes.parseRgb(colour);
        if (!palette.contains(rgb)) rgb = palette.isEmpty() ? NO_COLOR : palette.get(0);
        if (rgb == NO_COLOR) return state;
        return "0".equals(state)
                ? RecolorablePalettes.formatRgb(rgb)
                : state + SEPARATOR + RecolorablePalettes.formatRgb(rgb);
    }

    /** The colours this furni may take. */
    public java.util.List<Integer> palette() {
        return RecolorablePalettes.shipped()
                .colorsFor(this.getBaseItem() != null ? this.getBaseItem().getName() : null);
    }

    @Override
    public void setExtradata(String extradata) {
        if (extradata != null && (extradata.contains(SEPARATOR) || RecolorablePalettes.parseRgb(extradata) >= 0)) {
            this.readStored(extradata);
            return;
        }
        super.setExtradata(extradata);
    }

    @Override
    public String getDatabaseExtraData() {
        return this.hasColor()
                ? this.getExtradata() + SEPARATOR + RecolorablePalettes.formatRgb(this.color)
                : this.getExtradata();
    }

    /** "state|r,g,b", "r,g,b" or a plain state. */
    private void readStored(String stored) {
        String state = (stored == null || stored.isEmpty()) ? "0" : stored;
        int rgb = NO_COLOR;
        int separator = state.indexOf(SEPARATOR);

        if (separator >= 0) {
            rgb = RecolorablePalettes.parseRgb(state.substring(separator + 1));
            state = state.substring(0, separator);
        } else if (RecolorablePalettes.parseRgb(state) >= 0) {
            rgb = RecolorablePalettes.parseRgb(state);
            state = "0";
        }

        super.setExtradata(state.isEmpty() ? "0" : state);
        this.color = (rgb < 0) ? NO_COLOR : rgb;
    }

    /** Object data type 1 (a string map): "state", then "r", "g", "b" when the furni has a colour. */
    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(this.hasColor() ? 4 : 1);
        serverMessage.appendString("state");
        serverMessage.appendString(this.getExtradata());
        if (this.hasColor()) {
            serverMessage.appendString("r");
            serverMessage.appendString(Integer.toString((this.color >> 16) & 0xFF));
            serverMessage.appendString("g");
            serverMessage.appendString(Integer.toString((this.color >> 8) & 0xFF));
            serverMessage.appendString("b");
            serverMessage.appendString(Integer.toString(this.color & 0xFF));
        }
        if (this.isLimited()) {
            serverMessage.appendInt(this.getLimitedSells());
            serverMessage.appendInt(this.getLimitedStack());
        }
    }
}
