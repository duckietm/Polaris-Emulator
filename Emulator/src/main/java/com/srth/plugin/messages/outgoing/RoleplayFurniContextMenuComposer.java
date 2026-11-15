package com.srth.plugin.messages.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.srth.plugin.ActionItems.RoleplayFurniMenuManager;

public class RoleplayFurniContextMenuComposer extends MessageComposer {
    private final int requestId;
    private final int objectId;
    private final String furniName;
    private final RoleplayFurniMenuManager.Menu menu;

    public RoleplayFurniContextMenuComposer(
            int requestId,
            int objectId,
            String furniName,
            RoleplayFurniMenuManager.Menu menu) {
        this.requestId = requestId;
        this.objectId = objectId;
        this.furniName = furniName;
        this.menu = menu;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoleplayFurniContextMenuComposer);
        this.response.appendInt(this.requestId);
        this.response.appendInt(this.objectId);
        this.response.appendBoolean(this.menu != null);
        this.response.appendString(this.menu == null ? this.furniName : this.menu.title());
        this.response.appendInt(this.menu == null ? 0 : this.menu.buttons().size());

        if (this.menu != null) {
            for (RoleplayFurniMenuManager.Button button : this.menu.buttons()) {
                this.response.appendString(button.label());
                this.response.appendString(button.actionId());
            }
        }

        return this.response;
    }
}
