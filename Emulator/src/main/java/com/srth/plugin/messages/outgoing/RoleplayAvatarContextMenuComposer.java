package com.srth.plugin.messages.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import java.util.List;

public class RoleplayAvatarContextMenuComposer extends MessageComposer {
    private final int requestId;
    private final int targetType;
    private final int targetId;
    private final boolean roleplayRoom;
    private final RoleplayMenu menu;
    private final List<String> menuPath;

    public RoleplayAvatarContextMenuComposer(
            int requestId, int targetType, int targetId, boolean roleplayRoom, RoleplayAvatarMenuManager.Menu menu) {
        this(requestId, targetType, targetId, roleplayRoom, fromLegacyMenu(menu), List.of());
    }

    public RoleplayAvatarContextMenuComposer(
            int requestId,
            int targetType,
            int targetId,
            boolean roleplayRoom,
            RoleplayMenu menu,
            List<String> menuPath) {
        this.requestId = requestId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.roleplayRoom = roleplayRoom;
        this.menu = menu;
        this.menuPath = List.copyOf(menuPath);
    }

    private static RoleplayMenu fromLegacyMenu(RoleplayAvatarMenuManager.Menu menu) {
        if (menu == null) return null;

        return new RoleplayMenu(
                menu.title(),
                menu.buttons().stream()
                        .map(button -> new RoleplayMenu.Button(button.label(), button.actionId(), button.enabled()))
                        .toList());
    }

    @Override
    protected ServerMessage composeInternal() {
        RoleplayMenu page = this.menu == null ? null : this.menu.page(this.requestId, this.menuPath);
        this.response.init(Outgoing.RoleplayAvatarContextMenuComposer);
        this.response.appendInt(this.requestId);
        this.response.appendInt(this.targetType);
        this.response.appendInt(this.targetId);
        this.response.appendBoolean(this.roleplayRoom);
        this.response.appendString(page == null ? "" : page.title());
        this.response.appendInt(page == null ? 0 : page.buttons().size());

        if (page != null) {
            for (RoleplayMenu.Button button : page.buttons()) {
                this.response.appendString(button.label());
                this.response.appendString(button.actionId());
                this.response.appendBoolean(button.enabled());
            }
        }

        return this.response;
    }
}
