package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official AIR 13 {@code WiredClickSettings}: how the room wants avatar and furni clicks handled.
 *
 * <p>{@code userOption} is 0 default, 1 click-walk-behind, 2 pass-through; {@code furniOption} is
 * 0 default, 1 pass-through.
 */
public class WiredClickSettingsComposer extends MessageComposer {
    public static final int CLICK_USER_DEFAULT = 0;

    public static final int CLICK_USER_CLICK_WALK_BEHIND = 1;

    public static final int CLICK_USER_PASS_THROUGH = 2;

    public static final int CLICK_FURNI_DEFAULT = 0;

    public static final int CLICK_FURNI_PASS_THROUGH = 1;

    private final int userOption;
    private final int furniOption;

    public WiredClickSettingsComposer(int userOption, int furniOption) {
        this.userOption = userOption;
        this.furniOption = furniOption;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickSettingsComposer);
        this.response.appendInt(this.userOption);
        this.response.appendInt(this.furniOption);
        return this.response;
    }
}
