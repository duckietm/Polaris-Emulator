package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The answer to a capacity purchase: {@code int chestId, int resultCode}.
 *
 * <p>The purchase used to answer a refusal with silence, which left the window sitting there with its
 * buy button greyed and nothing said. The official sends a code back and the client turns it into a
 * notification, so every outcome — including "you cannot afford this" — reaches the person who asked.
 *
 * <p>{@link #RESULT_OK} is the only success. The rest are rendered client-side as
 * {@code wiredchests.upgrade.result.error.<code>}.
 */
public class ChestUpgradeResultComposer extends MessageComposer {
    public static final int RESULT_OK = 0;

    /** Only the chest's owner buys capacity for it, room rights or not. */
    public static final int RESULT_NOT_OWNER = 1;

    /** Already at the ceiling, or the requested amount would pass it. */
    public static final int RESULT_AT_CAPACITY = 2;

    public static final int RESULT_NOT_ENOUGH_CURRENCY = 3;

    /** The chest stopped existing between opening the window and pressing buy. */
    public static final int RESULT_CHEST_GONE = 4;

    /** A starter chest cannot be upgraded at all. Ten is the code the official client uses for it. */
    public static final int RESULT_STARTER_CHEST = 10;

    private final int chestId;
    private final int resultCode;

    public ChestUpgradeResultComposer(int chestId, int resultCode) {
        this.chestId = chestId;
        this.resultCode = resultCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestUpgradeResultComposer);
        this.response.appendInt(this.chestId);
        this.response.appendInt(this.resultCode);
        return this.response;
    }
}
