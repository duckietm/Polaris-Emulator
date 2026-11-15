package com.srth.plugin.utilities.rpcommands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.commands.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.srth.plugin.commands.vidayhambre.*;

public class CommandsVidayHambre {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandsVidayHambre.class);
    
    public void load(){
        this.VidayHambre();
    }

    private void VidayHambre(){
        try {
            
            CommandHandler.addCommand(new curarCommand("cmd_curar", Emulator.getTexts().getValue("commands.keys.cmd_curar").split(";")));
            CommandHandler.addCommand(new comerCommand("cmd_comer", Emulator.getTexts().getValue("commands.keys.cmd_comer").split(";")));

            CommandHandler.addCommand(new giveMedicoCommand("cmd_give_medico", Emulator.getTexts().getValue("cmd_give_medico.keys").split(";")));

        } catch (Exception ex) {
            LOGGER.error("[WEBSOCKETS ROOMS INFO] Error al cargar los comandos ", ex);
        }
    }
}
