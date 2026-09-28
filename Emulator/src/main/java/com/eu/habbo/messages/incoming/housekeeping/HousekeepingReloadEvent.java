package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.commands.Command;
import com.eu.habbo.habbohotel.commands.UpdateCatalogCommand;
import com.eu.habbo.habbohotel.commands.UpdateConfigCommand;
import com.eu.habbo.habbohotel.commands.UpdateItemsCommand;
import com.eu.habbo.habbohotel.commands.UpdateNavigatorCommand;
import com.eu.habbo.habbohotel.commands.UpdatePermissionsCommand;
import com.eu.habbo.habbohotel.commands.UpdateTextsCommand;
import com.eu.habbo.habbohotel.commands.UpdateWordFilterCommand;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.util.List;

/**
 * Hot reload of one hotel table from the panel. Each target runs the same
 * {@code :update_*} command a staff member could type, so the reload does
 * exactly what the command does, and it is refused unless the operator also
 * holds that command's own permission.
 */
public class HousekeepingReloadEvent extends MessageHandler {
    static final String ACTION_PREFIX = "hotel.reload.";

    static final String CATALOG = "catalog";
    static final String TEXTS = "texts";
    static final String PERMISSIONS = "permissions";
    static final String ITEMS = "items";
    static final String NAVIGATOR = "navigator";
    static final String CONFIG = "config";
    static final String WORD_FILTER = "wordfilter";

    static final List<String> TARGETS = List.of(CATALOG, TEXTS, PERMISSIONS, ITEMS, NAVIGATOR, CONFIG, WORD_FILTER);

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!HousekeepingAccess.check(this.client)) {
            return;
        }

        String target = this.packet.readString();
        String actionKey = ACTION_PREFIX + target;
        Command command = commandFor(target);

        if (command == null) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.reload_unknown"));
            return;
        }

        if (!this.client.getHabbo().hasPermission(command.permission)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, HousekeepingAccess.DENIED_MESSAGE));
            return;
        }

        try {
            command.handle(this.client, new String[] {command.keys.length > 0 ? command.keys[0] : target});
        } catch (Exception e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.reload_failed"));
            return;
        }

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                actionKey,
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "target=" + target,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, 0, ""));
    }

    private static Command commandFor(String target) {
        return switch (target) {
            case CATALOG -> new UpdateCatalogCommand();
            case TEXTS -> new UpdateTextsCommand();
            case PERMISSIONS -> new UpdatePermissionsCommand();
            case ITEMS -> new UpdateItemsCommand();
            case NAVIGATOR -> new UpdateNavigatorCommand();
            case CONFIG -> new UpdateConfigCommand();
            case WORD_FILTER -> new UpdateWordFilterCommand();
            default -> null;
        };
    }
}
