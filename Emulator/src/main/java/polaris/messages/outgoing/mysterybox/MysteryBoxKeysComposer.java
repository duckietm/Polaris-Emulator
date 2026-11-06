package polaris.messages.outgoing.mysterybox;

import java.util.Locale;
import polaris.Emulator;
import polaris.hotel.items.interactions.mysterybox.MysteryBoxColour;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The two colours in play, drawn in the tracker on the right of the room. A hotel names them in
 * {@code mysterybox.tracker.box.colour} and {@code mysterybox.tracker.key.colour}; a colour the
 * client cannot draw, or none at all, leaves the tracker hidden.
 */
public class MysteryBoxKeysComposer extends MessageComposer {
    private final String boxColour;
    private final String keyColour;

    /** The colours the hotel has configured. */
    public MysteryBoxKeysComposer() {
        this(
                Emulator.getConfig().getValue("mysterybox.tracker.box.colour", ""),
                Emulator.getConfig().getValue("mysterybox.tracker.key.colour", ""));
    }

    public MysteryBoxKeysComposer(String boxColour, String keyColour) {
        this.boxColour = drawable(boxColour);
        this.keyColour = drawable(keyColour);
    }

    private static String drawable(String colour) {
        if (colour == null) return "";

        String trimmed = colour.trim().toLowerCase(Locale.ROOT);

        return MysteryBoxColour.isKnown(trimmed) ? trimmed : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MysteryBoxKeysComposer);
        this.response.appendString(this.boxColour);
        this.response.appendString(this.keyColour);
        return this.response;
    }
}
