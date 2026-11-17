package com.srth.plugin.messages.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager;

public class RoleplayAvatarContextMenuComposer extends MessageComposer {
    private final int requestId;
    private final int targetType;
    private final int targetId;
    private final boolean roleplayRoom;
    private final RoleplayAvatarMenuManager.Menu menu;

    public RoleplayAvatarContextMenuComposer(
            int requestId, int targetType, int targetId, boolean roleplayRoom, RoleplayAvatarMenuManager.Menu menu) {
        this.requestId = requestId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.roleplayRoom = roleplayRoom;
        this.menu = menu;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoleplayAvatarContextMenuComposer);
        this.response.appendInt(this.requestId);
        this.response.appendInt(this.targetType);
        this.response.appendInt(this.targetId);
        this.response.appendBoolean(this.roleplayRoom);
        this.response.appendString(this.menu == null ? "" : this.menu.title());
        this.response.appendInt(this.menu == null ? 0 : this.menu.buttons().size());

        if (this.menu != null) {
            for (RoleplayAvatarMenuManager.Button button : this.menu.buttons()) {
                this.response.appendString(button.label());
                this.response.appendString(button.actionId());
                this.response.appendBoolean(button.enabled());
            }
        }

        return this.response;
    }
}
