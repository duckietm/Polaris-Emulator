package com.eu.habbo.habbohotel.items.interactions.wired;

/**
 * A wired save the box refuses with an official localization key, for saves whose method cannot throw the checked
 * {@link com.eu.habbo.messages.incoming.wired.WiredSaveException} (conditions, and boxes that keep their published signature). The save handlers send
 * the key to the editor.
 */
public class WiredSaveRefusedException extends RuntimeException {
    public WiredSaveRefusedException(String key) {
        super(key);
    }
}
