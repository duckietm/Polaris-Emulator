package com.srth.plugin.utilities.rpcommands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.srth.plugin.commands.hackercajeros.*;

public class CommandsHackerCajeros {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandsHackerCajeros.class);
    
    public void load(){
        this.VidayHambre();
    }

    private void VidayHambre(){
        try {

            CommandHandler.addCommand(new HandItemRPCommandCustom("cmd_hand_item", Emulator.getTexts().getValue("commands.keys.cmd_hand_item").split(";")));
            CommandHandler.addCommand(new EnableRPCommandCustom("cmd_enable",Emulator.getTexts().getValue("commands.keys.cmd_enable").split(";")));

            CommandHandler.addCommand(new arrestarCommand("cmd_arrestar", Emulator.getTexts().getValue("cmd_arrestar.keys").split(";")));

            CommandHandler.addCommand(new expedienteCommand("cmd_expediente", Emulator.getTexts().getValue("cmd_expediente.keys").split(";")));

            CommandHandler.addCommand(new givepoliceCommand("cmd_give_police", Emulator.getTexts().getValue("cmd_give_police.keys").split(";")));

            CommandHandler.addCommand(new robarCommand("cmd_robar", Emulator.getTexts().getValue("cmd_robar.keys").split(";")));

        } catch (Exception ex) {
            LOGGER.error("[ROLEPLAY HACK CAJEROS COMANDO] Error al cargar los comandos ", ex);
        }
    }
}
