package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;

public class ModToolRequestUserInfoEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            ModToolManager.requestUserInfo(this.client, this.packet);
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.userinfo")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
