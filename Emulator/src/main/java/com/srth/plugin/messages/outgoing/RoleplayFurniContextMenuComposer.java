package com.srth.plugin.messages.outgoing;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.srth.plugin.ActionItems.RoleplayFurniMenuManager;
import com.srth.plugin.ActionUsers.RoleplayAvatarMenuManager.RoleplayMenu;
import java.util.List;

public class RoleplayFurniContextMenuComposer extends MessageComposer {
    private final int requestId;
    private final int objectId;
    private final String furniName;
    private final RoleplayMenu menu;
    private final List<String> menuPath;

    public RoleplayFurniContextMenuComposer(
            int requestId, int objectId, String furniName, RoleplayFurniMenuManager.Menu menu) {
        this(requestId, objectId, furniName, fromLegacyMenu(menu), List.of());
    }

    public RoleplayFurniContextMenuComposer(
            int requestId, int objectId, String furniName, RoleplayMenu menu, List<String> menuPath) {
        this.requestId = requestId;
        this.objectId = objectId;
        this.furniName = furniName;
        this.menu = menu;
        this.menuPath = List.copyOf(menuPath);
    }

    private static RoleplayMenu fromLegacyMenu(RoleplayFurniMenuManager.Menu menu) {
        if (menu == null) return null;

        return new RoleplayMenu(
                menu.title(),
                menu.buttons().stream()
                        .map(button ->
                                new RoleplayMenu.Button(button.label(), button.actionId(), true, button.visibleIf()))
                        .toList());
    }

    @Override
    protected ServerMessage composeInternal() {
        RoleplayMenu page = this.menu == null ? null : this.menu.page(this.requestId, this.menuPath);
        this.response.init(Outgoing.RoleplayFurniContextMenuComposer);
        this.response.appendInt(this.requestId);
        this.response.appendInt(this.objectId);
        this.response.appendBoolean(page != null);
        this.response.appendString(page == null ? this.furniName : page.title());
        this.response.appendInt(page == null ? 0 : page.buttons().size());

        if (page != null) {
            for (RoleplayMenu.Button button : page.buttons()) {
                this.response.appendString(button.label());
                this.response.appendString(button.actionId());
            }
        }

        return this.response;
    }
}
