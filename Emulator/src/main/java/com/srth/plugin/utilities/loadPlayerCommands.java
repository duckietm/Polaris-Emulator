package com.srth.plugin.utilities;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import com.srth.plugin.commands.EmptyInventoryCommandCustomizado;
import com.srth.plugin.commands.PickallCommand;
import com.srth.plugin.commands.RedeemCommand;
import com.srth.plugin.commands.UpdateRPConfig;
import com.srth.plugin.commands.UpdateRPTexts;
import com.srth.plugin.utilities.rpcommands.CommandsHackerCajeros;
import com.srth.plugin.utilities.rpcommands.CommandsVidayHambre;

public class loadPlayerCommands {

    public static void ILoadPlayerCommands() {

        CommandHandler.addCommand(new EmptyInventoryCommandCustomizado());

        new CommandsVidayHambre().load();
        new CommandsHackerCajeros().load();

        // Comandos para actualizar
        CommandHandler.addCommand(new UpdateRPConfig("cmd_update_rp_config", Emulator.getTexts().getValue("commands.keys.cmd_update_rp_config").split(";")));
        CommandHandler.addCommand(new UpdateRPTexts("cmd_update_rp_text", Emulator.getTexts().getValue("commands.keys.cmd_update_rp_text").split(";")));

        // Comando oficiales pero customizados
        CommandHandler.addCommand(new PickallCommand());
        CommandHandler.addCommand(new RedeemCommand());

    }

}
