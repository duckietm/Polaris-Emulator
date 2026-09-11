package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code ObjectRemoveConfirm} (official id 3488, parser
 * {@code class_3462}): {@code int isWallItem, int id, str confirmTitle, str
 * confirmBody}. The client shows a confirm dialog and, on OK, sends the pick-up
 * again with the confirmation flag set.
 */
public class ObjectRemoveConfirmComposer extends MessageComposer {

    private final boolean wallItem;
    private final int itemId;
    private final String confirmTitle;
    private final String confirmBody;

    public ObjectRemoveConfirmComposer(boolean wallItem, int itemId, String confirmTitle, String confirmBody) {
        this.wallItem = wallItem;
        this.itemId = itemId;
        this.confirmTitle = confirmTitle;
        this.confirmBody = confirmBody;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ObjectRemoveConfirmComposer);

        this.response.appendInt(this.wallItem ? 1 : 0);
        this.response.appendInt(this.itemId);
        this.response.appendString(this.confirmTitle);
        this.response.appendString(this.confirmBody);

        return this.response;
    }

    public boolean isWallItem() {
        return this.wallItem;
    }

    public int getItemId() {
        return this.itemId;
    }

    public String getConfirmTitle() {
        return this.confirmTitle;
    }

    public String getConfirmBody() {
        return this.confirmBody;
    }
}
