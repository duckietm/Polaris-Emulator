package polaris.hotel.commands;

import java.util.Collections;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.outgoing.generic.alerts.MessagesForYouComposer;
import polaris.plugin.PolarisPlugin;

public class PluginsCommand extends Command {
    public PluginsCommand() {
        // It ran for everyone and ignored its own permission key; cmd_plugins now decides who may list the plugins.
        super(
                "cmd_plugins",
                Emulator.getTexts().getValue("commands.keys.cmd_plugins").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        StringBuilder message = new StringBuilder(
                "Plugins (" + Emulator.getPluginManager().getPlugins().size() + ")\r");

        for (PolarisPlugin plugin : Emulator.getPluginManager().getPlugins()) {
            message.append("\r")
                    .append(plugin.configuration.name)
                    .append(" By ")
                    .append(plugin.configuration.author);
        }

        if (Emulator.getConfig().getBoolean("commands.plugins.oldstyle")) {
            gameClient.sendResponse(new MessagesForYouComposer(Collections.singletonList(message.toString())));
        } else {
            gameClient.getUser().alert(message.toString());
        }

        return true;
    }
}
