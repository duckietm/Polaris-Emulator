package polaris.messages.incoming.housekeeping;

import java.util.List;
import polaris.hotel.commands.Command;
import polaris.hotel.commands.UpdateCatalogCommand;
import polaris.hotel.commands.UpdateConfigCommand;
import polaris.hotel.commands.UpdateItemsCommand;
import polaris.hotel.commands.UpdateNavigatorCommand;
import polaris.hotel.commands.UpdatePermissionsCommand;
import polaris.hotel.commands.UpdateTextsCommand;
import polaris.hotel.commands.UpdateWordFilterCommand;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Hot reload of one hotel table from the panel. Each target runs the same
 * {@code :update_*} command a staff member could type, so the reload does
 * exactly what the command does, and it is refused unless the operator also
 * holds that command's own permission.
 */
public class HousekeepingReloadEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.HOTEL;
    }

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
        if (!this.allowed()) {
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

        if (!this.client.getUser().hasPermission(command.permission)) {
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
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                actionKey,
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "target=" + target,
                this.client.getUser().getUserInfo().getIpLogin());
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
