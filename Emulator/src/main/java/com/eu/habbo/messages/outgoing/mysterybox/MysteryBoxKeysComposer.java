package com.eu.habbo.messages.outgoing.mysterybox;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.mysterybox.MysteryBoxColour;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Locale;

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
