package com.eu.habbo.messages.outgoing.events.resolution;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The promise was kept: the furni the owner made it on, and the badge that stands for it. The client
 * reads the furni's class name first and the badge second.
 */
public class NewYearResolutionCompletedComposer extends MessageComposer {
    public final String badge;

    private final String stuffCode;

    public NewYearResolutionCompletedComposer(String stuffCode, String badge) {
        this.stuffCode = stuffCode;
        this.badge = badge;
    }

    /**
     * @deprecated Sends the badge as the furni class name too, which is what this composer did
     *     before the furni was known; prefer the two-argument constructor.
     */
    @Deprecated
    public NewYearResolutionCompletedComposer(String badge) {
        this(badge, badge);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewYearResolutionCompletedComposer);
        this.response.appendString(this.stuffCode);
        this.response.appendString(this.badge);
        return this.response;
    }

    public String getBadge() {
        return badge;
    }
}
